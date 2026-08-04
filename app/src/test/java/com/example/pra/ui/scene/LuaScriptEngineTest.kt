package com.example.pra.ui.scene

import android.graphics.Canvas
import android.graphics.Typeface
import com.example.pra.ui.state.PlayerUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lua 脚本模式 (命令缓冲) 集成测试。
 * 依赖 build.gradle 的 unitTests.isReturnDefaultValues (Android stub 不抛异常)。
 */
class LuaScriptEngineTest {

    private val engine = LuaScriptEngine(null)

    @Test
    fun `script with onDraw executes without crash`() {
        engine.loadScript(
            """
            function onDraw(state, env)
                canvas:drawColor("#000000")
                canvas:setColor("#FFFFFF")
                canvas:setTextSize(90)
                canvas:drawText("测试歌词", env.width / 2, env.height / 2)
            end
            """.trimIndent()
        )

        engine.executeDraw(
            canvas = Canvas(),
            uiState = PlayerUiState(songTitle = "测试", isPlaying = true),
            positionMs = 1000L,
            width = 1080f,
            height = 2400f,
            systemTimeMs = 0L,
            animValue = 1f
        )
        // 不抛异常即通过 (命令缓冲生成 + 原生回放)
    }

    @Test
    fun `script with karaoke and transforms`() {
        engine.loadScript(
            """
            function onDraw(state, env)
                canvas:save()
                canvas:translate(100, 200)
                canvas:rotate(15)
                canvas:drawKaraokeLine("逐字歌词", 0, 0, 255, 0.5, "#888888", "#FFFFFF", "#FF3366")
                canvas:beginPath()
                canvas:moveTo(0, 0)
                canvas:lineTo(100, 100)
                canvas:quadTo(150, 50, 200, 100)
                canvas:closePath()
                canvas:drawPath()
                canvas:restore()
                canvas:drawCircle(50, 50, 30, "#FF0000")
                canvas:drawRect(0, 0, 100, 50, 0xFF00FF00.toInt())
            end
            """.trimIndent()
        )
        engine.executeDraw(Canvas(), PlayerUiState(), 0L, 800f, 1600f, 0L, 1f)
    }

    @Test
    fun `script uses state and anim`() {
        engine.loadScript(
            """
            function onDraw(state, env)
                local p = state.position or 0
                local t = env.anim or 0
                local y = anim:lerp(100, 200, t)
                canvas:drawText("pos=" .. tostring(p) .. " y=" .. tostring(y), 10, 10)
            end
            """.trimIndent()
        )
        engine.executeDraw(Canvas(), PlayerUiState(progress = 0.5f, durationMs = 10000L), 5000L, 800f, 1600f, 0L, 0.5f)
    }

    @Test
    fun `lyrics table injection`() {
        engine.evaluate("global_lyrics = {}")
        val code = engine.convertLyricsToLuaTable(
            listOf(
                com.example.pra.data.model.LyricLine(0L, 3000L, "第一行", emptyList()),
                com.example.pra.data.model.LyricLine(3000L, 6000L, "第二行", emptyList())
            )
        )
        assertTrue(code.contains("第一行"))
        assertTrue(code.contains("startTime = 3000"))
        engine.evaluate(code)
        // 注入后不再抛异常
        engine.executeDraw(Canvas(), PlayerUiState(), 1000L, 800f, 1600f, 0L, 1f)
    }

    @Test
    fun `script without onDraw is a no-op`() {
        engine.loadScript("local x = 1")
        engine.executeDraw(Canvas(), PlayerUiState(), 0L, 800f, 1600f, 0L, 1f)
    }

    @Test
    fun `font loading api exists`() {
        engine.loadScript(
            """
            function onDraw(state, env)
                local f = tools.loadFont("nonexistent.ttf")
                assert(f == nil)
            end
            """.trimIndent()
        )
        engine.executeDraw(Canvas(), PlayerUiState(), 0L, 800f, 1600f, 0L, 1f)
    }
}
