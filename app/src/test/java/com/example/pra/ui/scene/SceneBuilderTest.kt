package com.example.pra.ui.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneBuilderTest {

    private val builder = SceneBuilder()

    @Test
    fun `default scene script builds successfully`() {
        // 与 res/raw/scene_default.lua 同步的模板
        val script = """
            local fontName = "SourceHanSansCN-Normal.ttf"

            scene:setFont(fontName, 90)
            scene:backgroundColor("#000000")

            scene:lyric {
                fontSize = 90,
                baseColor = "#888888",
                highlightColor = "#FFFFFF",
                lineHeight = 1.5,
                maxWidth = 0.85,
                y = "h / 2 + offsetY",
                fillByWord = true,
            }

            scene:placeholder {
                fontSize = 60,
                color = "#FFFFFF",
                breath = true,
            }
        """.trimIndent()

        val scene = builder.build(script).scene

        // setFont 数字参数必须被 LuaJ 正确桥接
        assertEquals("SourceHanSansCN-Normal.ttf", scene.fontPath)
        assertEquals(90f, scene.defaultFontSize, 0.001f)

        assertNotNull(scene.lyric)
        scene.lyric?.let { lyric ->
            assertEquals(AnimValue.Const(90f), lyric.fontSize)
            assertEquals(1.5f, lyric.lineHeightRatio, 0.001f)
            assertEquals(0.85f, lyric.maxWidthRatio, 0.001f)
            assertTrue(lyric.fillByWord)
            assertTrue(lyric.y is AnimValue.Expr)
            assertEquals("h / 2 + offsetY", (lyric.y as AnimValue.Expr).source)
            // 颜色解析依赖 android.graphics.Color (JVM stub 返回 0, 具体值真机验证)
        }

        assertNotNull(scene.placeholder)
        scene.placeholder?.let { ph ->
            assertTrue(ph.breath)
            assertEquals(AnimValue.Const(60f), ph.fontSize)
        }

        assertTrue(scene.overlays.isEmpty())
    }

    @Test
    fun `setFont without size keeps default`() {
        val scene = builder.build(
            """
            scene:setFont("custom.ttf")
            scene:lyric { fontSize = 72 }
            """.trimIndent()
        ).scene
        assertEquals("custom.ttf", scene.fontPath)
        assertEquals(72f, (scene.lyric?.fontSize as AnimValue.Const).v, 0.001f)
    }

    @Test
    fun `overlay nodes build`() {
        val scene = builder.build(
            """
            scene:backgroundColor("#112233")
            scene:text { text = "Time", x = 20, y = "h - 50", fontSize = 40, color = "#FFFFFF", align = 1 }
            scene:rect { l = 0, t = 0, r = 100, b = 80, color = "#FF0000", radius = 8 }
            scene:circle { cx = "w / 2", cy = 100, r = 30, color = 0xFFFF00 }
            """.trimIndent()
        ).scene

        assertEquals(3, scene.overlays.size)
        val text = scene.overlays[0] as Overlay.TextNode
        assertEquals("Time", text.text)
        assertEquals(AnimValue.Const(20f), text.x)
        assertEquals("h - 50", (text.y as AnimValue.Expr).source)
        assertEquals(1, text.align)

        val rect = scene.overlays[1] as Overlay.RectNode
        assertEquals(8f, (rect.radius as AnimValue.Const).v, 0.001f)

        val circle = scene.overlays[2] as Overlay.CircleNode
        assertEquals("w / 2", (circle.cx as AnimValue.Expr).source)
    }

    @Test(expected = Exception::class)
    fun `script error throws`() {
        builder.build("scene:noSuchMethod {}")
    }

    @Test
    fun `empty script builds empty scene`() {
        val scene = builder.build("").scene
        assertNull(scene.backgroundColor)
        assertNull(scene.lyric)
        assertTrue(scene.overlays.isEmpty())
    }

    @Test
    fun `script with onDraw detected as script mode`() {
        val result = builder.build("function onDraw(state, env) end")
        assertTrue(result.hasOnDraw)
        assertNull(result.scene.backgroundColor)
    }

    @Test
    fun `declarative script is not script mode`() {
        val result = builder.build("scene:backgroundColor('#000000')")
        assertTrue(!result.hasOnDraw)
    }
}
