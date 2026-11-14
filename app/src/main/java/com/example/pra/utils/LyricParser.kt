package com.example.pra.utils

import com.example.pra.data.model.LyricEntry
import com.example.pra.data.model.LyricLine
import com.example.pra.data.model.LyricWord
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * 增强型 LRC 解析器 (V4, 严格模式)
 *
 * 只认一种格式 —— 带逐字时间标签的增强型 LRC:
 *
 *     [00:05.67]<00:05.67>Composed<00:06.04><00:06.04>by<00:06.42>...
 *
 * 规则 (全部确定性, 没有任何兜底):
 *   1. 行头 `[mm:ss]` / `[mm:ss.xx]`, 必须从行首连续出现;
 *      一行多个行头 = 同一句歌词在多个时间点重复。
 *   2. 行头之后只按 `<mm:ss.xx>` 标签切分; 标签后到下一个标签之间的文本属于它。
 *   3. 标签时间是这段文本的**开始**时间, 下一个标签的时间是结束时间。
 *      紧凑写法 `<start>字<end>` 中空的 `<end>` 标签天然就是上一个字的结束。
 *   4. 时间强制单调不减: 文件写错导致倒退时钳制到上一个时间点, 卡拉OK 不会回跳。
 *   5. **没有 `<...>` 标签的行直接丢弃** —— 普通 LRC 不再解析, 也不再伪造逐字时间。
 *   6. 编码只接受 UTF-8 (允许 BOM); 解码失败即视为无效歌词, 不猜 GBK。
 *   7. `[offset:±ms]` 全局偏移生效; `[ar:] [ti:] [al:] [by:]` 等元信息整行跳过。
 *   8. 损坏/无法识别的标签直接丢弃 (文本切到 `<` 为止), 不做部分匹配。
 */
object LyricParser {

    /** 行头: [mm:ss] / [mm:ss.xx] / [mm:ss:xx] */
    private val LINE_TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    /** 逐字标签: <mm:ss.xx> */
    private val WORD_TAG = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")

    /** 全局偏移: [offset:+500] */
    private val OFFSET_TAG = Regex("""\[offset\s*:\s*([+-]?\d+)]""", RegexOption.IGNORE_CASE)

    /** 元信息: [ar:..] [ti:..] [al:..] [by:..] 等, 整行跳过 */
    private val META_TAG = Regex(
        """\[(?:ar|ti|al|by|re|ve|length|language|total)\s*:.*]""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * 文件**最后一行且没有结束标签**时, 用来给最后一个字一个展示时长。
     * 仅在"标签已经用完"这一种情况生效; 有结束标签就严格用标签时间。
     */
    private const val OPEN_ENDED_WORD_MS = 800L

    private class RawWord(val startMs: Long, val text: String)

    private class DraftLine(
        val startMs: Long,
        val rawWords: List<RawWord>,
    )

    fun parse(inputStream: InputStream?): LyricEntry {
        val bytes = inputStream?.use { it.readBytes() } ?: return LyricEntry()
        val content = decodeUtf8(bytes) ?: return LyricEntry()

        val offsetMs = OFFSET_TAG.find(content)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

        val drafts = ArrayList<DraftLine>()
        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            if (META_TAG.matches(line) || OFFSET_TAG.matches(line)) return@forEach

            val head = collectLineStarts(line, offsetMs)
            if (head.starts.isEmpty()) return@forEach

            val body = line.substring(head.bodyStart)
            val rawWords = parseRawWords(body, offsetMs)
            // 严格: 整行没有任何逐字标签 → 不是增强型歌词, 直接丢弃
            if (rawWords.none { it.text.isNotEmpty() }) return@forEach
            head.starts.forEach { startMs ->
                drafts += DraftLine(startMs, rawWords)
            }
        }
        if (drafts.isEmpty()) return LyricEntry()

        // 稳定排序: 同一时间点保持文件中的出现顺序
        val ordered = drafts.sortedBy { it.startMs }

        val lines = ordered.mapIndexedNotNull { index, draft ->
            val nextStart = ordered.getOrNull(index + 1)?.startMs
            // 文件自带的收尾空标签 (例如 <00:05.67>) 就是这一句最后一个字的结束时间
            val closingTagMs = draft.rawWords.lastOrNull()
                ?.takeIf { it.text.isEmpty() }
                ?.startMs

            val lineEnd = nextStart
                ?: closingTagMs
                ?: (draft.startMs + OPEN_ENDED_WORD_MS)
            val lastWordEnd = closingTagMs ?: lineEnd

            val words = buildWords(
                rawWords = draft.rawWords,
                lineStartMs = draft.startMs,
                lastWordEndMs = lastWordEnd,
            )
            if (words.isEmpty()) return@mapIndexedNotNull null

            LyricLine(
                startTime = draft.startMs,
                endTime = maxOf(lineEnd, draft.startMs, words.last().endTime),
                text = words.joinToString("") { it.text },
                words = words,
            )
        }

        return LyricEntry(lines)
    }

    // ==================== 行头 ====================

    private class LineHead(val starts: MutableList<Long>, val bodyStart: Int)

    private fun collectLineStarts(line: String, offsetMs: Long): LineHead {
        val starts = ArrayList<Long>(1)
        var bodyStart = 0
        for (match in LINE_TAG.findAll(line)) {
            // 行头必须从行首开始连续出现, 中间夹了别的内容就停止
            if (match.range.first != bodyStart) break
            starts += toMillis(
                match.groupValues[1], match.groupValues[2], match.groupValues[3],
            ) + offsetMs
            bodyStart = match.range.last + 1
        }
        return LineHead(starts, bodyStart)
    }

    // ==================== 逐字标签 ====================

    /** 把 `body` 按 `<time>` 切成 (时间, 文本); 文本遇到 `<` 即截断, 丢弃损坏标签 */
    private fun parseRawWords(body: String, offsetMs: Long): List<RawWord> {
        val matches = WORD_TAG.findAll(body).toList()
        if (matches.isEmpty()) return emptyList()

        val words = ArrayList<RawWord>(matches.size)
        matches.forEachIndexed { index, match ->
            val startMs = toMillis(
                match.groupValues[1], match.groupValues[2], match.groupValues[3],
            ) + offsetMs
            val textBegin = match.range.last + 1
            val textEnd = if (index + 1 < matches.size) matches[index + 1].range.first else body.length
            val text = body.substring(textBegin, textEnd).substringBefore('<')
            words += RawWord(startMs, text)
        }
        return words
    }

    /**
     * 逐字时间: 每个字的结束时间 = 下一个标签的时间; 最后一个字结束于
     * [lastWordEndMs] (文件给的收尾标签, 或下一行开始时间)。
     * 全程 `max()` 保护, 任何时间倒退都会被钳制成零时长而不是回跳。
     */
    private fun buildWords(
        rawWords: List<RawWord>,
        lineStartMs: Long,
        lastWordEndMs: Long,
    ): List<LyricWord> {
        if (rawWords.isEmpty()) return emptyList()

        val result = ArrayList<LyricWord>(rawWords.size)
        var cursor = lineStartMs

        rawWords.forEachIndexed { index, raw ->
            val start = maxOf(raw.startMs, cursor, lineStartMs)
            val next = rawWords.getOrNull(index + 1)
            val end = if (next != null) {
                maxOf(next.startMs, start)
            } else {
                maxOf(lastWordEndMs, start)
            }
            if (raw.text.isNotEmpty()) {
                result += LyricWord(raw.text, start, end)
            }
            cursor = maxOf(cursor, end)
        }
        return result
    }

    // ==================== 时间 / 编码 ====================

    private fun toMillis(min: String, sec: String, frac: String): Long {
        val minutes = min.toLongOrNull() ?: 0L
        val seconds = sec.toLongOrNull() ?: 0L
        return minutes * 60_000L + seconds * 1_000L + fracToMillis(frac)
    }

    /** 小数部分: 1 位=0.1s, 2 位=0.01s, 3 位=0.001s */
    private fun fracToMillis(frac: String): Long {
        if (frac.isEmpty()) return 0L
        val padded = when (frac.length) {
            1 -> frac + "00"
            2 -> frac + "0"
            3 -> frac
            else -> frac.substring(0, 3)
        }
        return padded.toLongOrNull() ?: 0L
    }

    /** 严格 UTF-8 解码 (允许 BOM); 非法字节序列返回 null, 不再回退 GBK */
    private fun decodeUtf8(bytes: ByteArray): String? {
        val hasBom = bytes.size >= 3 &&
            (bytes[0].toInt() and 0xFF) == 0xEF &&
            (bytes[1].toInt() and 0xFF) == 0xBB &&
            (bytes[2].toInt() and 0xFF) == 0xBF
        val body = if (hasBom) bytes.copyOfRange(3, bytes.size) else bytes
        if (body.isEmpty()) return ""
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(body))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }
}
