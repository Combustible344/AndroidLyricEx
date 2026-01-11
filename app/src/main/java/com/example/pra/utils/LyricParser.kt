package com.example.pra.utils

import com.example.pra.data.model.LyricEntry
import com.example.pra.data.model.LyricLine
import com.example.pra.data.model.LyricWord
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.regex.Pattern

object LyricParser {

    /**
     * 1. 匹配行头: [mm:ss.xxx]
     */
    private val LINE_REGEX = Pattern.compile("\\[(\\d{2}):(\\d{2})(\\.(\\d+))?](.*)")

    /**
     * 2. 匹配行内字: <mm:ss.xxx>字 或 (mm:ss.xxx)字
     * 兼容两种常见格式 <...> 和 (...)
     * Group 1: 分
     * Group 2: 秒
     * Group 4: 毫秒 (可选)
     * Group 5: 对应的文字内容 (非贪婪匹配到下一个标签前)
     */
    private val WORD_REGEX = Pattern.compile("[<\\(](\\d{2}):(\\d{2})(\\.(\\d+))?[>\\)]([^<\\(]*)")

    fun parse(inputStream: InputStream?): LyricEntry {
        if (inputStream == null) return LyricEntry()

        val lines = mutableListOf<LyricLine>()
        // 尝试 UTF-8，如果是乱码可改为 GBK
        val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))

        try {
            var lineStr: String? = reader.readLine()
            while (lineStr != null) {
                if (lineStr.isNotBlank()) {
                    parseLine(lineStr)?.let { lines.add(it) }
                }
                lineStr = reader.readLine()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            reader.close()
        }

        // 排序
        lines.sortBy { it.startTime }

        // 计算每一行 endTime
        val calculatedLines = lines.mapIndexed { index, lyricLine ->
            val nextTime = if (index < lines.size - 1) {
                lines[index + 1].startTime
            } else {
                lyricLine.startTime + 5000
            }
            lyricLine.copy(endTime = nextTime)
        }

        return LyricEntry(calculatedLines)
    }

    private fun parseLine(line: String): LyricLine? {
        val matcher = LINE_REGEX.matcher(line)
        if (matcher.matches()) {
            // 1. 解析整行开始时间
            val min = matcher.group(1)?.toLong() ?: 0
            val sec = matcher.group(2)?.toLong() ?: 0
            val millis = parseMillis(matcher.group(4))
            val startTime = min * 60 * 1000 + sec * 1000 + millis

            val rawContent = matcher.group(5) ?: ""

            // 2. 尝试解析行内逐字时间戳
            val words = parseWords(rawContent, startTime)

            // 3. 清洗出纯文本 (去掉所有 <...> 标签)
            // 如果 words 不为空，我们可以直接拼接 words 的 text
            // 如果 words 为空 (普通lrc)，直接用 rawContent
            val cleanText = if (words.isNotEmpty()) {
                words.joinToString("") { it.text }
            } else {
                rawContent
            }

            // 4. 如果没有解析出字 (普通lrc)，则创建一个默认的 Word 占满整行
            // 这样 UI 层可以统一处理 List<Word>
            val finalWords = if (words.isEmpty()) {
                listOf(LyricWord(cleanText, startTime, 0L)) // endTime 稍后外部会覆盖或自己计算
            } else {
                words
            }

            return LyricLine(startTime, 0L, cleanText.trim(), finalWords)
        }
        return null
    }

    private fun parseWords(content: String, lineStartTime: Long): List<LyricWord> {
        val words = mutableListOf<LyricWord>()
        val matcher = WORD_REGEX.matcher(content)

        while (matcher.find()) {
            val min = matcher.group(1)?.toLong() ?: 0
            val sec = matcher.group(2)?.toLong() ?: 0
            val millis = parseMillis(matcher.group(4))

            val wordStartTime = min * 60 * 1000 + sec * 1000 + millis
            val text = matcher.group(5) ?: ""

            // 只有当有文字内容时才添加 (有时候标签是连续的 <t1><t2>)
            // 或者即使是空字符串也添加，看你 UI 需不需要占位
            words.add(LyricWord(text, wordStartTime, 0L))
        }

        // 计算每个字的 endTime (等于下一个字的 startTime)
        if (words.isNotEmpty()) {
            return words.mapIndexed { index, word ->
                val nextTime = if (index < words.size - 1) {
                    words[index + 1].startTime
                } else {
                    // 最后一个字的结束时间，暂时给个 500ms 或直到下一行
                    word.startTime + 500
                }
                word.copy(endTime = nextTime)
            }
        }

        return emptyList()
    }

    private fun parseMillis(str: String?): Long {
        if (str.isNullOrEmpty()) return 0L
        val padded = if (str.length < 3) str.padEnd(3, '0') else str.substring(0, 3)
        return padded.toLongOrNull() ?: 0L
    }
}
