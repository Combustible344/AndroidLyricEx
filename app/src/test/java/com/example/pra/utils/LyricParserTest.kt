package com.example.pra.utils

import com.example.pra.data.model.LyricEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

class LyricParserTest {

    private fun parse(content: String): LyricEntry {
        return LyricParser.parse(ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)))
    }

    // ==================== 增强型 LRC ====================

    @Test
    fun `enhanced lrc - word level timestamps`() {
        val entry = parse(
            """
            [ar:小蓝背心]
            [ti:风错过雨]
            [00:00.18]<00:00.18>风<00:00.32><00:00.32>错<00:00.46><00:00.46>过<00:00.59><00:00.59>雨<00:00.66><00:00.66> - <00:00.72><00:00.72>小<00:00.85><00:00.85>蓝<00:00.99><00:00.99>背<00:01.14><00:01.14>心
            <00:01.14>
            """.trimIndent()
        )

        assertEquals(1, entry.lines.size)
        val line = entry.lines[0]
        assertEquals("风错过雨 - 小蓝背心", line.text)
        assertEquals(180L, line.startTime)
        assertEquals(9, line.words.size)

        // 逐字时间: 结束时间 = 下一个标签的时间
        assertEquals(listOf(180L, 320L, 460L, 590L, 660L, 720L, 850L, 990L, 1140L), line.words.map { it.startTime })
        assertEquals(listOf(320L, 460L, 590L, 660L, 720L, 850L, 990L, 1140L, 1140L), line.words.map { it.endTime })
        assertEquals(" - ", line.words[4].text)
    }

    @Test
    fun `enhanced lrc - standalone end marker line`() {
        val entry = parse(
            """
            [00:58.55]<00:58.55>着<00:58.55><00:58.55>你
            <00:59.58>
            [00:59.98]<00:59.98>风<01:00.16><01:00.16>遗
            """.trimIndent()
        )

        assertEquals(2, entry.lines.size)
        // 单独成行的 <00:59.58> 是上一行最后一个字 "你" 的结束标记
        // 00:58.55 = 58.55s = 58550ms
        val first = entry.lines[0]
        assertEquals(58550L, first.words.last().startTime)
        assertEquals(59580L, first.words.last().endTime)

        // 行级 endTime = 下一行开始时间 (00:59.98 = 59980ms)
        assertEquals(59980L, first.endTime)
    }

    @Test
    fun `enhanced lrc - line endTime from next line`() {
        val entry = parse(
            """
            [00:00.18]<00:00.18>风<00:00.32><00:00.32>错
            [00:01.00]<00:01.00>看<00:01.20><00:01.20>过
            """.trimIndent()
        )
        assertEquals(2, entry.lines.size)
        assertEquals(1000L, entry.lines[0].endTime)
        assertEquals(6000L, entry.lines[1].endTime) // 最后一行兜底 +5000
    }

    @Test
    fun `enhanced lrc - corrupt tag skipped gracefully`() {
        val entry = parse(
            """
            [02:01.90]<02:01.90>我<02:02.11><02:02.11>遗<02:02.302:08.19><02:08.19>眼<02:08.44><02:08.44>底
            """.trimIndent()
        )
        val line = entry.lines[0]
        assertEquals(listOf("我", "遗", "眼", "底"), line.words.map { it.text })
        // 损坏标签被跳过, 遗 的结束时间用下一个有效标签 (02:08.19 = 128190)
        assertEquals(122110L, line.words[1].startTime)
        assertEquals(128190L, line.words[1].endTime)
        // 所有字时长不为负
        assertTrue(line.words.all { it.endTime >= it.startTime })
    }

    @Test
    fun `enhanced lrc - zero duration words allowed`() {
        // 词曲信息区: <t>词<t> 无空标签重复, 字与字同时间
        val entry = parse(
            """
            [00:01.14]<00:01.14>词<00:01.14><00:01.14>：<00:01.14><00:01.14>贝<00:01.15><00:01.15>多<00:01.15><00:01.15>雯
            """.trimIndent()
        )
        val line = entry.lines[0]
        assertEquals("词：贝多雯", line.text)
        assertEquals(5, line.words.size)
        assertEquals(1140L, line.words[0].endTime) // 词@1140 end=1140
    }

    // ==================== 普通 LRC (插值构造) ====================

    @Test
    fun `plain lrc - interpolate words by weight`() {
        val entry = parse(
            """
            [00:00.00]风紧追着雨
            [00:10.00]Hello world test
            """.trimIndent()
        )
        val cjk = entry.lines[0]
        assertEquals("风紧追着雨", cjk.text)
        assertEquals(listOf("风", "紧", "追", "着", "雨"), cjk.words.map { it.text })
        // 5 个字均分 0~10000ms
        assertEquals(0L, cjk.words[0].startTime)
        assertEquals(2000L, cjk.words[0].endTime)
        assertEquals(10000L, cjk.words.last().endTime)

        val latin = entry.lines[1]
        // 按长度加权: Hello(5) space(1) world(5) space(1) test(4)
        // 最后一行兜底时长 5000ms (10000~15000)
        assertEquals(listOf("Hello", " ", "world", " ", "test"), latin.words.map { it.text })
        assertEquals(10000L, latin.words[0].startTime)
        assertEquals(10000L + 5000L * 5 / 16, latin.words[0].endTime)
        assertEquals(15000L, latin.words.last().endTime)
        assertTrue(latin.words.zipWithNext().all { (a, b) -> a.endTime == b.startTime })
    }

    @Test
    fun `plain lrc - single line no next line`() {
        val entry = parse("[00:10.00]只有一句歌词")
        val line = entry.lines[0]
        assertEquals(10000L, line.startTime)
        assertEquals(15000L, line.endTime) // +5000 兜底
        assertEquals("只有一句歌词", line.words.joinToString("") { it.text })
        assertEquals(6, line.words.size)
    }

    // ==================== 元信息 / 空行 ====================

    @Test
    fun `metadata and empty lines skipped`() {
        val entry = parse(
            """
            [ti:测试]
            [ar:歌手]
            [by:]
            [00:12.44]
            [00:20.00]歌词
            """.trimIndent()
        )
        assertEquals(1, entry.lines.size)
        assertEquals("歌词", entry.lines[0].text)
    }

    @Test
    fun `offset applied globally`() {
        val entry = parse(
            """
            [offset:+500]
            [00:00.00]<00:00.00>风<00:00.50><00:00.50>错
            """.trimIndent()
        )
        val line = entry.lines[0]
        assertEquals(500L, line.startTime)
        assertEquals(500L, line.words[0].startTime)
        assertEquals(1000L, line.words[0].endTime)

        val negative = parse(
            """
            [offset:-300]
            [00:01.00]歌词
            """.trimIndent()
        )
        assertEquals(700L, negative.lines[0].startTime)
    }

    // ==================== 编码 ====================

    @Test
    fun `gbk encoded lyrics decoded`() {
        val content = "[00:01.00]中文歌词"
        val gbkBytes = content.toByteArray(Charset.forName("GBK"))
        val entry = LyricParser.parse(ByteArrayInputStream(gbkBytes))
        assertEquals("中文歌词", entry.lines[0].text)
    }

    @Test
    fun `utf8 bom handled`() {
        val content = "[00:01.00]歌词"
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + content.toByteArray(Charsets.UTF_8)
        val entry = LyricParser.parse(ByteArrayInputStream(bytes))
        assertEquals("歌词", entry.lines[0].text)
    }

    @Test
    fun `empty input returns empty entry`() {
        assertTrue(LyricParser.parse(null).lines.isEmpty())
        assertTrue(parse("").lines.isEmpty())
    }

    @Test
    fun `multiple line tags clone the line`() {
        val entry = parse("[00:01.00][00:05.00]同一句")
        assertEquals(2, entry.lines.size)
        assertEquals(1000L, entry.lines[0].startTime)
        assertEquals(5000L, entry.lines[1].startTime)
        assertEquals("同一句", entry.lines[1].text)
    }
}
