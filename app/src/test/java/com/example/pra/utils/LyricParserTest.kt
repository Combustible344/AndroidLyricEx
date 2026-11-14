package com.example.pra.utils

import com.example.pra.data.model.LyricEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

/**
 * 严格增强型 LRC 解析器测试 (V4)。
 * 覆盖: 标签配对 / 单调钳制 / 普通行丢弃 / 偏移 / 元信息 / 损坏标签 / 编码 / 空输入。
 */
class LyricParserTest {

    private fun parse(content: String): LyricEntry =
        LyricParser.parse(ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)))

    // ==================== 逐字标签配对 ====================

    @Test
    fun `enhanced lrc - pairs each tag with following text`() {
        // Seasons 风格: <start>字<end> 紧凑排列, 空 <end> 是上一个字的结束
        val entry = parse(
            "[00:00.00]<00:00.00>Rival<00:00.31><00:00.31>、<00:00.63><00:00.63>Cadmium<00:00.94>",
        )

        assertEquals(1, entry.lines.size)
        val line = entry.lines[0]
        assertEquals("Rival、Cadmium", line.text)
        assertEquals(0L, line.startTime)
        assertEquals(listOf("Rival", "、", "Cadmium"), line.words.map { it.text })
        assertEquals(listOf(0L, 310L, 630L), line.words.map { it.startTime })
        assertEquals(listOf(310L, 630L, 940L), line.words.map { it.endTime })
    }

    @Test
    fun `enhanced lrc - word ends at next tag`() {
        val entry = parse("[00:01.00]<00:01.00>风<00:01.50><00:01.50>错<00:02.00>")
        val words = entry.lines[0].words
        assertEquals(listOf("风", "错"), words.map { it.text })
        assertEquals(1000L, words[0].startTime)
        assertEquals(1500L, words[0].endTime)
        assertEquals(1500L, words[1].startTime)
        assertEquals(2000L, words[1].endTime)
    }

    @Test
    fun `enhanced lrc - decreasing timestamps are clamped, never go back`() {
        // 真实文件里存在时间倒退的损坏标签 (seasons.lrc 的制作人信息行)
        val entry = parse("[00:10.00]<00:10.00>A<00:11.00><00:05.00>B<00:11.50>")
        val words = entry.lines[0].words

        assertEquals(listOf("A", "B"), words.map { it.text })
        assertEquals(10_000L, words[0].startTime)
        assertEquals(11_000L, words[0].endTime)
        // B 的标签写了 5s, 但必须钳制到上一个时间点之后
        assertEquals(11_000L, words[1].startTime)
        assertEquals(11_500L, words[1].endTime)
        assertTrue(words.zipWithNext().all { (a, b) -> b.startTime >= a.endTime })
    }

    @Test
    fun `enhanced lrc - malformed tag dropped without leaking text`() {
        val entry = parse(
            "[02:01.90]<02:01.90>我<02:02.11><02:02.11>遗<02:02.302:08.19><02:08.19>眼<02:08.44><02:08.44>底",
        )
        val line = entry.lines[0]
        assertEquals(listOf("我", "遗", "眼", "底"), line.words.map { it.text })
        // 损坏标签被丢弃, 遗 的结束时间取下一个有效标签
        assertEquals(122_110L, line.words[1].startTime)
        assertEquals(128_190L, line.words[1].endTime)
        assertTrue(line.words.all { it.endTime >= it.startTime })
    }

    // ==================== 严格模式: 普通 LRC 一律丢弃 ====================

    @Test
    fun `plain lrc without word tags yields no lyrics`() {
        val entry = parse(
            """
            [00:01.00]第一句歌词
            [00:05.00]第二句歌词
            [00:09.00]第三句歌词
            """.trimIndent(),
        )
        assertTrue("普通 LRC 不应产出任何歌词行", entry.lines.isEmpty())
    }

    @Test
    fun `plain line mixed with enhanced lines is dropped`() {
        val entry = parse(
            """
            [00:01.00]这一行没有逐字标签
            [00:02.00]<00:02.00>这一行<00:02.50><00:02.50>有
            """.trimIndent(),
        )
        assertEquals(1, entry.lines.size)
        assertEquals("这一行有", entry.lines[0].text)
    }

    // ==================== 行头 / 元信息 / 偏移 ====================

    @Test
    fun `metadata lines are skipped`() {
        val entry = parse(
            """
            [ar:歌手]
            [ti:歌名]
            [al:专辑]
            [by:]
            [00:01.00]<00:01.00>歌词<00:01.50>
            """.trimIndent(),
        )
        assertEquals(1, entry.lines.size)
        assertEquals("歌词", entry.lines[0].text)
    }

    @Test
    fun `multiple line tags clone the same line`() {
        val entry = parse("[00:01.00][00:05.00]<00:01.00>同一句<00:02.00>")
        assertEquals(2, entry.lines.size)
        assertEquals(1000L, entry.lines[0].startTime)
        assertEquals(5000L, entry.lines[1].startTime)
        assertEquals("同一句", entry.lines[1].text)
    }

    @Test
    fun `offset applied to lines and words`() {
        val entry = parse(
            """
            [offset:+500]
            [00:01.00]<00:01.00>风<00:01.50>
            """.trimIndent(),
        )
        val line = entry.lines[0]
        assertEquals(1500L, line.startTime)
        assertEquals(1500L, line.words[0].startTime)
        assertEquals(2000L, line.words[0].endTime)

        val negative = parse(
            """
            [offset:-300]
            [00:01.00]<00:01.00>歌词<00:01.50>
            """.trimIndent(),
        )
        assertEquals(700L, negative.lines[0].startTime)
    }

    @Test
    fun `line without header is ignored`() {
        val entry = parse(
            """
            没有时间标签的一行
            [00:01.00]<00:01.00>正常歌词<00:02.00>
            """.trimIndent(),
        )
        assertEquals(1, entry.lines.size)
        assertEquals("正常歌词", entry.lines[0].text)
    }

    @Test
    fun `last line without closing tag still gets a duration`() {
        val entry = parse("[00:01.00]<00:01.00>没有收尾标签")
        val line = entry.lines[0]
        assertEquals(1000L, line.startTime)
        assertEquals(1800L, line.endTime) // 开放结尾: 最后一个字保留 800ms
    }

    // ==================== 编码 / 输入边界 ====================

    @Test
    fun `utf8 bom handled`() {
        val content = "[00:01.00]<00:01.00>歌词<00:01.50>"
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            content.toByteArray(Charsets.UTF_8)
        val entry = LyricParser.parse(ByteArrayInputStream(bytes))
        assertEquals("歌词", entry.lines[0].text)
    }

    @Test
    fun `non utf8 content is rejected instead of guessed`() {
        val gbk = "[00:01.00]<00:01.00>中文歌词<00:01.50>".toByteArray(Charset.forName("GBK"))
        assertTrue(LyricParser.parse(ByteArrayInputStream(gbk)).lines.isEmpty())
    }

    @Test
    fun `null and empty input return empty entry`() {
        assertTrue(LyricParser.parse(null).lines.isEmpty())
        assertTrue(parse("").lines.isEmpty())
    }

    // ==================== 真实文件回归 (seasons.lrc) ====================

    @Test
    fun `real seasons lrc - word timings stay monotonic`() {
        val stream = javaClass.classLoader?.getResourceAsStream("seasons.lrc")
            ?: error("seasons.lrc fixture missing")
        val entry = LyricParser.parse(stream)

        assertTrue("应解析出全部歌词行", entry.lines.size >= 20)
        entry.lines.forEach { line ->
            line.words.forEach { word ->
                assertTrue("负时长: ${word.text}", word.endTime >= word.startTime)
            }
            line.words.zipWithNext().forEach { (a, b) ->
                assertTrue(
                    "行内时间倒退: '${line.text}' 中 ${a.text}@${a.endTime} -> ${b.text}@${b.startTime}",
                    b.startTime >= a.endTime,
                )
            }
        }

        // 首行 (制作人信息) 原文里有 04.09 → 02.20 这类倒退标签, 必须被钳制
        val first = entry.lines.first()
        assertEquals("Rival", first.words.first().text)
        assertTrue(first.words.zipWithNext().all { (a, b) -> b.startTime >= a.startTime })
    }
}
