package com.example.pra.utils

import com.example.pra.data.model.LyricEntry
import com.example.pra.data.model.LyricLine
import com.example.pra.data.model.LyricWord
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/**
 * 增强型 LRC 解析器 (V2)
 *
 * 以 QQ 音乐/网易云增强歌词为标准格式:
 *   [00:00.18]<00:00.18>风<00:00.32><00:00.32>错<00:00.46><00:00.46>过
 *
 * 规则:
 *   1. 行头 [mm:ss.xxx] 或 [mm:ss:xxx],支持多个行头克隆同一行
 *   2. 逐字标签 <mm:ss.xxx>字,或 (mm:ss.xxx)字
 *   3. 空标签 <mm:ss.xxx>(后面没有文字)是上一个字的结束标记;
 *      单独成行的空标签同样属于上一行的最后一个字
 *   4. [offset:±ms] 全局时间偏移; [ar:..]/[ti:..] 等元信息直接跳过
 *   5. 非增强型 LRC(整行只有行头)没有逐字时间,
 *      按行内文字长度加权插值构造逐字时间,保证卡拉OK效果可用
 *   6. 编码自动探测: UTF-8 (含 BOM/严格校验) → 失败则按 GBK 解码
 */
object LyricParser {

    /** 行头: [mm:ss.xxx] 或 [mm:ss:xxx] */
    private val LINE_TAG_REGEX = Pattern.compile("\\[(\\d{2}):(\\d{2})(?:[.:](\\d+))?]")

    /** 全局时间偏移: [offset:+500] / [offset:-500] */
    private val OFFSET_REGEX = Pattern.compile("\\[offset:([+-]?\\d+)]", Pattern.CASE_INSENSITIVE)

    /** 元信息: [ar:..] [ti:..] [al:..] [by:..] [length:..] 等,直接跳过 */
    private val META_REGEX = Pattern.compile(
        "\\[(?:ar|ti|al|by|re|ve|length|language|total):.*]",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * 逐字标签: <mm:ss.xxx>字 或 (mm:ss.xxx)字
     * Group 1: 分, Group 2: 秒, Group 3: 毫秒/厘秒 (可选), Group 4: 文字 (直到下一个标签)
     */
    private val WORD_REGEX = Pattern.compile("[<(](\\d{2}):(\\d{2})(?:[.:](\\d+))?[>)]([^<(]*)")

    /** 没有下一行时, 最后一行的兜底时长 (毫秒) */
    private const val LAST_LINE_FALLBACK_MS = 5000L

    /** 解析期间的中间行: 一行歌词的一个时间点 */
    private class RawLine(
        val startTime: Long,
        val text: String,
        val words: MutableList<RawWord>
    )

    private class RawWord(val startTime: Long, val text: String)

    fun parse(inputStream: InputStream?): LyricEntry {
        if (inputStream == null) return LyricEntry()

        val content = decode(inputStream.readBytes())
        val offsetMs = findOffset(content)

        val rawLines = mutableListOf<RawLine>()
        // 上一行 (有逐字标签的行), 用于承接单独成行的结束标记
        var lastLineWithWords: RawLine? = null

        content.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach

            // 收集行头时间标签
            val times = mutableListOf<Long>()
            val lineMatcher = LINE_TAG_REGEX.matcher(line)
            var lastTagEnd = 0
            while (lineMatcher.find()) {
                times.add(
                    toMillis(lineMatcher.group(1), lineMatcher.group(2), lineMatcher.group(3)) + offsetMs
                )
                lastTagEnd = lineMatcher.end()
            }

            if (times.isEmpty()) {
                // 无行头 → 元信息直接跳过; 逐字结束标记则挂到上一行
                if (META_REGEX.matcher(line).matches()) return@forEach
                val markers = extractWords(line, offsetMs)
                if (markers.isNotEmpty() && lastLineWithWords != null) {
                    lastLineWithWords.words.addAll(markers)
                }
                return@forEach
            }

            val rawContent = line.substring(lastTagEnd)
            val sharedWords = extractWords(rawContent, offsetMs)
            times.forEach { t ->
                rawLines.add(RawLine(t, rawContent, sharedWords))
            }
            if (sharedWords.isNotEmpty()) lastLineWithWords = rawLines.last()
        }

        val entries = rawLines.filter { it.text.isNotBlank() || it.words.isNotEmpty() }
            .sortedBy { it.startTime }
        if (entries.isEmpty()) return LyricEntry()

        val lines = entries.mapIndexed { index, entry ->
            val lineEndMs = if (index < entries.size - 1) {
                entries[index + 1].startTime
            } else {
                entry.startTime + LAST_LINE_FALLBACK_MS
            }

            // 有逐字标签 → 严格使用; 没有 → 插值合成
            // (只有空标记的行: 标记消费完时间后没有真实文字, 同样退回插值)
            val finalized = if (entry.words.isNotEmpty()) {
                finalizeWords(entry.words, lineEndMs)
            } else {
                emptyList()
            }
            val strippedText = stripWordTags(entry.text)
            val words = if (finalized.isNotEmpty()) {
                finalized
            } else {
                synthesizeWords(strippedText, entry.startTime, lineEndMs)
            }

            val cleanText = if (words.isNotEmpty()) {
                words.joinToString("") { it.text }
            } else {
                strippedText.trim()
            }

            LyricLine(
                startTime = entry.startTime,
                endTime = lineEndMs,
                text = cleanText,
                words = words
            )
        }.filter { it.text.isNotBlank() }

        return LyricEntry(lines)
    }

    /**
     * 为逐字时间列表计算结束时间: 下一个标签的时间就是当前字的结束时间
     * (空标签 = 结束标记, 计算完结束时间后被过滤掉)
     * 末尾没有标记时, 用行结束时间兜底
     */
    private fun finalizeWords(raw: List<RawWord>, lineEndMs: Long): List<LyricWord> {
        val result = ArrayList<LyricWord>(raw.size)
        for (i in raw.indices) {
            val word = raw[i]
            val nextStart = if (i < raw.size - 1) raw[i + 1].startTime else lineEndMs
            val end = maxOf(nextStart, word.startTime) // 防止损坏文件导致负时长
            result.add(LyricWord(word.text, word.startTime, end))
        }
        return result.filter { it.text.isNotEmpty() }
    }

    /**
     * 非增强型 LRC: 整行没有逐字时间, 按文字长度加权插值构造逐字时间
     * 权重 = 字的长度 (英文单词按长度分配更多时间, 中文单字权重为 1)
     */
    private fun synthesizeWords(text: String, lineStartMs: Long, lineEndMs: Long): List<LyricWord> {
        val units = splitKaraokeUnits(text)
        if (units.isEmpty()) return emptyList()

        val duration = (lineEndMs - lineStartMs).coerceAtLeast(100L)
        val totalWeight = units.sumOf { it.length }

        val result = ArrayList<LyricWord>(units.size)
        var cursor = lineStartMs
        units.forEachIndexed { index, unit ->
            val weight = unit.length.coerceAtLeast(1)
            // 最后一个字强制对齐行尾, 避免整数除法累积误差
            val end = if (index == units.size - 1) {
                lineEndMs
            } else {
                cursor + duration * weight / totalWeight
            }
            result.add(LyricWord(unit, cursor, end))
            cursor = end
        }
        return result
    }

    /**
     * 卡拉OK切分单元:
     * - 连续的拉丁字母/数字/撇号合并为一个词 (Hello / don't / Wuli)
     * - 其余字符 (中文、标点、空格、全角符号) 每个单独成字
     */
    fun splitKaraokeUnits(text: String): List<String> {
        val units = mutableListOf<String>()
        var i = 0
        val n = text.length
        while (i < n) {
            val cp = text.codePointAt(i)
            val charCount = Character.charCount(cp)
            if (isLatinWordChar(cp)) {
                var j = i + charCount
                while (j < n && isLatinWordChar(text.codePointAt(j))) {
                    j += Character.charCount(text.codePointAt(j))
                }
                units.add(text.substring(i, j))
                i = j
            } else {
                units.add(text.substring(i, i + charCount))
                i += charCount
            }
        }
        return units
    }

    private fun isLatinWordChar(cp: Int): Boolean =
        cp in 'a'.code..'z'.code || cp in 'A'.code..'Z'.code || cp in '0'.code..'9'.code || cp == '\''.code

    private fun extractWords(content: String, offsetMs: Long): MutableList<RawWord> {
        val words = mutableListOf<RawWord>()
        val matcher = WORD_REGEX.matcher(content)
        while (matcher.find()) {
            val start = toMillis(matcher.group(1), matcher.group(2), matcher.group(3)) + offsetMs
            words.add(RawWord(start, matcher.group(4) ?: ""))
        }
        return words
    }

    /** 去掉所有逐字时间标签, 保留纯文本 */
    private fun stripWordTags(content: String): String = WORD_REGEX.matcher(content).replaceAll("")

    private fun toMillis(min: String?, sec: String?, frac: String?): Long {
        val m = min?.toLongOrNull() ?: 0L
        val s = sec?.toLongOrNull() ?: 0L
        return m * 60_000L + s * 1000L + parseMillis(frac)
    }

    private fun parseMillis(str: String?): Long {
        if (str.isNullOrEmpty()) return 0L
        val padded = when (str.length) {
            1 -> str + "00"
            2 -> str + "0"
            else -> str.substring(0, 3)
        }
        return padded.toLongOrNull() ?: 0L
    }

    private fun findOffset(content: String): Long {
        content.lineSequence().forEach { raw ->
            val matcher = OFFSET_REGEX.matcher(raw.trim())
            if (matcher.matches()) return matcher.group(1)?.toLong() ?: 0L
        }
        return 0L
    }

    // ==================== 编码探测 ====================

    private fun decode(bytes: ByteArray): String {
        // UTF-8 BOM
        if (bytes.size >= 3 &&
            (bytes[0].toInt() and 0xFF) == 0xEF &&
            (bytes[1].toInt() and 0xFF) == 0xBB &&
            (bytes[2].toInt() and 0xFF) == 0xBF
        ) {
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        }
        // UTF-16 BOM
        if (bytes.size >= 2 &&
            (bytes[0].toInt() and 0xFF) == 0xFF &&
            (bytes[1].toInt() and 0xFF) == 0xFE
        ) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 &&
            (bytes[0].toInt() and 0xFF) == 0xFE &&
            (bytes[1].toInt() and 0xFF) == 0xFF
        ) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        // 无 BOM: 严格校验 UTF-8, 失败则按 GBK
        if (isValidUtf8(bytes)) {
            val utf8 = String(bytes, StandardCharsets.UTF_8)
            // GBK 编码的中文通常也能"通过"UTF-8 校验,
            // 但解码结果是希腊/西里尔/希伯来/阿拉伯等可疑字符 → 按 GBK 重解
            return if (looksLikeMisdecodedChinese(utf8)) {
                String(bytes, Charset.forName("GBK"))
            } else {
                utf8
            }
        }
        return String(bytes, Charset.forName("GBK"))
    }

    /** GBK 中文被误按 UTF-8 解码后, 会产生希腊/西里尔/希伯来/阿拉伯区字符;
     *  正常 UTF-8 歌词基本不会出现这些区 (中文走 CJK 区), 所以只需绝对计数 */
    private fun looksLikeMisdecodedChinese(text: String): Boolean {
        var suspicious = 0
        var i = 0
        val n = text.length
        while (i < n) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (cp in 0x0370..0x06FF || cp in 0x1E00..0x1EFF) {
                suspicious++
                if (suspicious >= 2) return true
            }
        }
        return false
    }

    /** 严格 UTF-8 校验 (含过长的 3/4 字节序列与代理区检查) */
    private fun isValidUtf8(bytes: ByteArray): Boolean {
        var i = 0
        val n = bytes.size
        while (i < n) {
            val b0 = bytes[i].toInt() and 0xFF
            when {
                b0 < 0x80 -> i += 1

                b0 in 0xC2..0xDF -> {
                    if (i + 1 >= n || (bytes[i + 1].toInt() and 0xC0) != 0x80) return false
                    i += 2
                }

                b0 == 0xE0 -> {
                    if (i + 2 >= n) return false
                    val b1 = bytes[i + 1].toInt() and 0xFF
                    if (b1 !in 0xA0..0xBF || (bytes[i + 2].toInt() and 0xC0) != 0x80) return false
                    i += 3
                }

                b0 in 0xE1..0xEC || b0 in 0xEE..0xEF -> {
                    if (i + 2 >= n) return false
                    if ((bytes[i + 1].toInt() and 0xC0) != 0x80 ||
                        (bytes[i + 2].toInt() and 0xC0) != 0x80
                    ) return false
                    i += 3
                }

                b0 == 0xED -> {
                    if (i + 2 >= n) return false
                    val b1 = bytes[i + 1].toInt() and 0xFF
                    if (b1 !in 0x80..0x9F || (bytes[i + 2].toInt() and 0xC0) != 0x80) return false
                    i += 3
                }

                b0 == 0xF0 -> {
                    if (i + 3 >= n) return false
                    val b1 = bytes[i + 1].toInt() and 0xFF
                    if (b1 !in 0x90..0xBF) return false
                    if ((bytes[i + 2].toInt() and 0xC0) != 0x80 ||
                        (bytes[i + 3].toInt() and 0xC0) != 0x80
                    ) return false
                    i += 4
                }

                b0 in 0xF1..0xF3 -> {
                    if (i + 3 >= n) return false
                    if ((bytes[i + 1].toInt() and 0xC0) != 0x80 ||
                        (bytes[i + 2].toInt() and 0xC0) != 0x80 ||
                        (bytes[i + 3].toInt() and 0xC0) != 0x80
                    ) return false
                    i += 4
                }

                b0 == 0xF4 -> {
                    if (i + 3 >= n) return false
                    val b1 = bytes[i + 1].toInt() and 0xFF
                    if (b1 !in 0x80..0x8F) return false
                    if ((bytes[i + 2].toInt() and 0xC0) != 0x80 ||
                        (bytes[i + 3].toInt() and 0xC0) != 0x80
                    ) return false
                    i += 4
                }

                else -> return false
            }
        }
        return true
    }
}
