package com.example.pra.ui.scene

/**
 * 场景属性值: 常量 或 每帧表达式绑定 (由 FrameVars 求值, 见 ExprEvaluator)
 */
sealed class AnimValue {
    data class Const(val v: Float) : AnimValue()

    /** 表达式字符串, 如 "h / 2 + sin(t * 6.28) * 20" */
    class Expr(val source: String) : AnimValue() {
        var program: ExprEvaluator.Program? = null
    }
}

/**
 * 完整场景描述 (由 Lua 脚本构建, 运行时纯原生执行)
 * 不持有任何 Lua 对象, 可安全跨线程传递
 */
data class LyricScene(
    val backgroundColor: Int? = null,
    val fontPath: String? = null,
    val defaultFontSize: Float = 90f,
    val lyric: LyricSpec? = null,
    val placeholder: PlaceholderSpec? = null,
    val overlays: List<Overlay> = emptyList()
)

/** 当前行歌词节点 (逐字卡拉OK) */
data class LyricSpec(
    val fontSize: AnimValue = AnimValue.Const(90f),
    val baseColor: Int = 0xFF888888.toInt(),
    val highlightColor: Int = 0xFFFFFFFF.toInt(),
    val shadowColor: Int = 0,                    // 0 = 禁用辉光
    val lineHeightRatio: Float = 1.5f,
    val maxWidthRatio: Float = 0.85f,
    val y: AnimValue = AnimValue.Expr("h / 2 + offsetY"),
    val fillByWord: Boolean = true               // true=逐字填充, false=整行填充
)

/** 无歌词时的占位节点 (通常显示歌名) */
data class PlaceholderSpec(
    val text: String? = null,                    // null = 使用歌曲标题
    val fontSize: AnimValue = AnimValue.Const(60f),
    val color: Int = 0xFFFFFFFF.toInt(),
    val breath: Boolean = true,                  // 呼吸缩放
    val y: AnimValue = AnimValue.Const(0f)       // 相对屏幕中心的偏移
)

/** 叠加元素 */
sealed class Overlay {
    abstract val alpha: AnimValue

    data class TextNode(
        val text: String,
        val x: AnimValue,
        val y: AnimValue,
        val fontSize: AnimValue,
        val color: Int,
        val align: Int,                          // 0=LEFT 1=CENTER 2=RIGHT
        override val alpha: AnimValue
    ) : Overlay()

    data class RectNode(
        val l: AnimValue,
        val t: AnimValue,
        val r: AnimValue,
        val b: AnimValue,
        val color: Int,
        val radius: AnimValue,
        override val alpha: AnimValue
    ) : Overlay()

    data class CircleNode(
        val cx: AnimValue,
        val cy: AnimValue,
        val r: AnimValue,
        val color: Int,
        override val alpha: AnimValue
    ) : Overlay()
}
