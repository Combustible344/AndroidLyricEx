package com.example.pra.ui.scene

import android.content.Context
import android.graphics.Color
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import org.luaj.vm2.lib.jse.JsePlatform

/**
 * Lua 场景构建器: 脚本只在加载时执行一次, 产出纯 Kotlin 数据 LyricScene。
 * 运行时每帧渲染 100% 由 LyricSceneRenderer 原生执行, Lua 不参与热路径。
 *
 * 脚本 API (scene 对象):
 *   scene:setFont(name, size)   -- 字体 (assets 相对路径或绝对路径), size 可选
 *   scene:backgroundColor(color)
 *   scene:lyric{ fontSize=, baseColor=, highlightColor=, shadow=, lineHeight=, maxWidth=, y=, fillByWord= }
 *   scene:placeholder{ text=, fontSize=, color=, breath=, y= }
 *   scene:text{ text=, x=, y=, fontSize=, color=, align=, alpha= }
 *   scene:rect{ l=, t=, r=, b=, color=, radius=, alpha= }
 *   scene:circle{ cx=, cy=, r=, color=, alpha= }
 *
 * 数值字段可以是常量数字, 也可以是表达式字符串 (每帧原生求值):
 *   变量: t pos time dur playing w h alpha offsetY
 *   函数: sin cos tan sqrt abs floor ceil frac min max pow clamp lerp map smoothstep
 */
/** 构建结果: 声明式场景 + 是否定义了每帧脚本 (onDraw) */
data class SceneBuildResult(
    val scene: LyricScene,
    val hasOnDraw: Boolean
)

class SceneBuilder(private val context: Context? = null) {

    private val globals: Globals = JsePlatform.standardGlobals()

    init {
        // 注入 tools (toChars/loadFont): 脚本模式脚本在顶层可能引用,
        // 保证 hasOnDraw 检测能正确执行构建期代码
        LuaEnv.injectTools(globals, context)
    }

    /** LuaJ globals 非线程安全, 构建串行化 */
    @Synchronized
    fun build(scriptContent: String): SceneBuildResult {
        val state = SceneBuildState()
        globals.set("scene", CoerceJavaToLua.coerce(SceneFacade(state)))
        globals.load(scriptContent).call()
        return SceneBuildResult(state.toScene(), hasOnDraw = !globals.get("onDraw").isnil())
    }

    private class SceneBuildState {
        var backgroundColor: Int? = null
        var fontPath: String? = null
        var defaultFontSize: Float = 90f
        var lyric: LyricSpec? = null
        var placeholder: PlaceholderSpec? = null
        val overlays = mutableListOf<Overlay>()

        fun toScene() = LyricScene(
            backgroundColor = backgroundColor,
            fontPath = fontPath,
            defaultFontSize = defaultFontSize,
            lyric = lyric,
            placeholder = placeholder,
            overlays = overlays.toList()
        )
    }

    private class SceneFacade(private val state: SceneBuildState) {

        /**
         * 注意: 参数用 Any? 而非 LuaValue —— LuaJ 桥接对 LuaValue 类型参数会把
         * Lua 数字解包成 java.lang.Integer/Double 再匹配, 导致 coercion error;
         * Object 参数则直接传 LuaValue 实例 (LuaInteger 同时实现 Number)
         */
        fun setFont(name: String, size: Any?) {
            state.fontPath = name
            if (size is Number) {
                state.defaultFontSize = size.toFloat()
            }
        }

        fun backgroundColor(color: Any?) {
            state.backgroundColor = colorValue(color, Color.BLACK)
        }

        fun lyric(table: LuaTable) {
            state.lyric = LyricSpec(
                fontSize = animValue(table.get("fontSize"), AnimValue.Const(state.defaultFontSize)),
                baseColor = colorValue(table.get("baseColor"), 0xFF888888.toInt()),
                highlightColor = colorValue(table.get("highlightColor"), 0xFFFFFFFF.toInt()),
                shadowColor = colorValue(table.get("shadow"), 0),
                lineHeightRatio = table.get("lineHeight").optdouble(1.5).toFloat(),
                maxWidthRatio = table.get("maxWidth").optdouble(0.85).toFloat(),
                y = animValue(table.get("y"), AnimValue.Expr("h / 2 + offsetY")),
                fillByWord = table.get("fillByWord").optboolean(true)
            )
        }

        fun placeholder(table: LuaTable) {
            state.placeholder = PlaceholderSpec(
                text = table.get("text").optjstring(null),
                fontSize = animValue(table.get("fontSize"), AnimValue.Const(60f)),
                color = colorValue(table.get("color"), 0xFFFFFFFF.toInt()),
                breath = table.get("breath").optboolean(true),
                y = animValue(table.get("y"), AnimValue.Const(0f))
            )
        }

        fun text(table: LuaTable) {
            state.overlays += Overlay.TextNode(
                text = table.get("text").optjstring(""),
                x = animValue(table.get("x"), AnimValue.Const(0f)),
                y = animValue(table.get("y"), AnimValue.Const(0f)),
                fontSize = animValue(table.get("fontSize"), AnimValue.Const(40f)),
                color = colorValue(table.get("color"), 0xFFFFFFFF.toInt()),
                align = table.get("align").optint(0),
                alpha = animValue(table.get("alpha"), AnimValue.Const(1f))
            )
        }

        fun rect(table: LuaTable) {
            state.overlays += Overlay.RectNode(
                l = animValue(table.get("l"), AnimValue.Const(0f)),
                t = animValue(table.get("t"), AnimValue.Const(0f)),
                r = animValue(table.get("r"), AnimValue.Const(0f)),
                b = animValue(table.get("b"), AnimValue.Const(0f)),
                color = colorValue(table.get("color"), 0xFFFFFFFF.toInt()),
                radius = animValue(table.get("radius"), AnimValue.Const(0f)),
                alpha = animValue(table.get("alpha"), AnimValue.Const(1f))
            )
        }

        fun circle(table: LuaTable) {
            state.overlays += Overlay.CircleNode(
                cx = animValue(table.get("cx"), AnimValue.Const(0f)),
                cy = animValue(table.get("cy"), AnimValue.Const(0f)),
                r = animValue(table.get("r"), AnimValue.Const(0f)),
                color = colorValue(table.get("color"), 0xFFFFFFFF.toInt()),
                alpha = animValue(table.get("alpha"), AnimValue.Const(1f))
            )
        }

        /** 数值字段: 数字 → 常量, 字符串 → 表达式 */
        private fun animValue(v: LuaValue, def: AnimValue): AnimValue = when {
            v.isnumber() -> AnimValue.Const(v.tofloat())
            v.isstring() -> AnimValue.Expr(v.tojstring())
            else -> def
        }

        /**
         * 颜色: 字符串 "#RRGGBB" 或数字。
         * 注意 Lua 侧桥接传入的值是 LuaValue 实例 (LuaString/LuaInteger),
         * 而 table.get() 读出的字段也是 LuaValue, 两种来源都要处理
         */
        private fun colorValue(v: Any?, def: Int): Int = when {
            v is String -> try {
                Color.parseColor(v)
            } catch (_: Exception) {
                def
            }
            v is Number -> v.toInt()
            v is LuaValue -> when {
                v.isstring() -> try {
                    Color.parseColor(v.tojstring())
                } catch (_: Exception) {
                    def
                }
                v.isnumber() -> v.toint()
                else -> def
            }
            else -> def
        }
    }
}
