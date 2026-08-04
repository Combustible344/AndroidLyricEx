package com.example.pra.ui.scene

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.example.pra.data.model.LyricLine
import com.example.pra.data.model.LyricWord
import com.example.pra.ui.state.PlayerUiState
import java.io.File
import kotlin.math.sin

/**
 * 原生场景渲染器: 每帧只做 [节点遍历 + 表达式求值 + Canvas 绘制],
 * 无脚本执行、无反射、无每帧分配。歌词布局按 (行, 宽度, 字号) 缓存。
 */
class LyricSceneRenderer(private val context: Context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fontMetrics = Paint.FontMetrics()
    private val typefaceCache = HashMap<String, Typeface>()
    private val vars = FrameVars()
    private val evalStack = FloatArray(64)

    private var scene: LyricScene? = null
    private var currentTypeface: Typeface? = null

    // 布局缓存: 同一行 + 同一屏宽 + 同一字号时复用
    private var cachedLine: LyricLine? = null
    private var cachedWidth: Float = -1f
    private var cachedFontSize: Float = -1f
    private var cachedLayout: LineLayout? = null

    companion object {
        private const val ENTER_MS = 400L
        private const val EXIT_MS = 300L
        private const val ENTER_OFFSET_PX = 50f
    }

    // ==================== 场景 ====================

    fun setScene(scene: LyricScene) {
        this.scene = scene
        currentTypeface = scene.fontPath?.let { path ->
            typefaceCache.getOrPut(path) { loadTypeface(path) }
        }
        // 预编译全部表达式, 首次渲染零解析开销
        scene.lyric?.fontSize?.let { vars.eval(it) }
        scene.lyric?.y?.let { vars.eval(it) }
        scene.placeholder?.fontSize?.let { vars.eval(it) }
        scene.placeholder?.y?.let { vars.eval(it) }
        scene.overlays.forEach { o ->
            o.alpha.let { vars.eval(it) }
            when (o) {
                is Overlay.TextNode -> {
                    o.x.let { vars.eval(it) }; o.y.let { vars.eval(it) }; o.fontSize.let { vars.eval(it) }
                }
                is Overlay.RectNode -> {
                    o.l.let { vars.eval(it) }; o.t.let { vars.eval(it) }
                    o.r.let { vars.eval(it) }; o.b.let { vars.eval(it) }; o.radius.let { vars.eval(it) }
                }
                is Overlay.CircleNode -> {
                    o.cx.let { vars.eval(it) }; o.cy.let { vars.eval(it) }; o.r.let { vars.eval(it) }
                }
            }
        }
        invalidateLayout()
    }

    fun invalidateLayout() {
        cachedLine = null
        cachedWidth = -1f
        cachedFontSize = -1f
        cachedLayout = null
    }

    // ==================== 渲染入口 ====================

    fun render(
        canvas: Canvas,
        uiState: PlayerUiState,
        positionMs: Long,
        width: Float,
        height: Float,
        systemTimeMs: Long
    ) {
        val scene = scene ?: return

        // --- 帧变量 ---
        val line = uiState.currentLyricLine
        val lineSt = line?.startTime ?: 0L
        val lineEt = line?.endTime ?: 0L
        val dur = (lineEt - lineSt).coerceAtLeast(1L).toFloat()
        val t = if (line != null) ((positionMs - lineSt).toFloat() / dur).coerceIn(0f, 1f) else 0f

        // 进出场动画 (原生计算, 表达式可通过 alpha/offsetY 引用)
        var alpha = 1f
        var offsetY = 0f
        if (line != null) {
            if (positionMs < lineSt + ENTER_MS) {
                val k = ((positionMs - lineSt).toFloat() / ENTER_MS).coerceIn(0f, 1f)
                alpha = k
                offsetY = (1f - k) * ENTER_OFFSET_PX
            } else if (positionMs > lineEt - EXIT_MS) {
                val k = ((positionMs - (lineEt - EXIT_MS)).toFloat() / EXIT_MS).coerceIn(0f, 1f)
                alpha = 1f - k
                offsetY = -k * ENTER_OFFSET_PX
            }
        }

        vars.set(FrameVars.T, t)
        vars.set(FrameVars.POS, positionMs.toFloat())
        vars.set(FrameVars.TIME, systemTimeMs.toFloat())
        vars.set(FrameVars.DUR, dur)
        vars.set(FrameVars.PLAYING, if (uiState.isPlaying) 1f else 0f)
        vars.set(FrameVars.W, width)
        vars.set(FrameVars.H, height)
        vars.set(FrameVars.ALPHA, alpha)
        vars.set(FrameVars.OFFSET_Y, offsetY)

        // --- 背景 ---
        scene.backgroundColor?.let { canvas.drawColor(it) }

        // --- 歌词 / 占位 ---
        if (line != null && scene.lyric != null) {
            drawLyric(canvas, line, positionMs, width, alpha)
        } else if (scene.placeholder != null) {
            drawPlaceholder(canvas, uiState, width, height, systemTimeMs)
        }

        // --- 叠加元素 ---
        scene.overlays.forEach { drawOverlay(canvas, it) }

        // 恢复 paint 默认状态
        paint.textAlign = Paint.Align.LEFT
        paint.alpha = 255
        paint.clearShadowLayer()
    }

    // ==================== 歌词 (卡拉OK) ====================

    private fun drawLyric(
        canvas: Canvas,
        line: LyricLine,
        positionMs: Long,
        width: Float,
        alpha: Float
    ) {
        val lyric = scene?.lyric ?: return
        val fontSize = vars.eval(lyric.fontSize)
        if (fontSize <= 0f) return

        val maxWidth = width * lyric.maxWidthRatio
        val layout = getLayout(line, maxWidth, fontSize, lyric.fillByWord)
        if (layout.groups.isEmpty()) return

        currentTypeface?.let { paint.typeface = it }
        paint.textSize = fontSize
        paint.getFontMetrics(fontMetrics)
        val centerOffset = -(fontMetrics.descent + fontMetrics.ascent) / 2
        val lineHeight = fontSize * lyric.lineHeightRatio
        val baseY = vars.eval(lyric.y)
        val startY = baseY - (layout.groups.size * lineHeight / 2f) + lineHeight / 2f + centerOffset
        val clipPad = fontSize * 0.6f

        paint.alpha = (255 * alpha).toInt().coerceIn(0, 255)

        for ((gi, group) in layout.groups.withIndex()) {
            val groupY = startY + gi * lineHeight
            var x = width / 2f - group.width / 2f
            for (unit in group.units) {
                // 底色
                paint.color = lyric.baseColor
                canvas.drawText(unit.text, x, groupY, paint)

                // 高亮 (按字进度裁剪)
                val progress = fillProgress(unit, positionMs)
                if (progress > 0f) {
                    paint.color = lyric.highlightColor
                    if (lyric.shadowColor != 0) {
                        paint.setShadowLayer(fontSize * 0.2f, 0f, 0f, lyric.shadowColor)
                    }
                    canvas.save()
                    canvas.clipRect(
                        x, groupY - fontSize - clipPad,
                        x + unit.width * progress, groupY + fontSize + clipPad
                    )
                    canvas.drawText(unit.text, x, groupY, paint)
                    canvas.restore()
                    paint.clearShadowLayer()
                }
                x += unit.width
            }
        }
    }

    private fun fillProgress(unit: LayoutUnit, positionMs: Long): Float {
        if (positionMs >= unit.endTime) return 1f
        if (positionMs <= unit.startTime) return 0f
        val d = unit.endTime - unit.startTime
        if (d <= 0) return 1f
        return ((positionMs - unit.startTime).toFloat() / d).coerceIn(0f, 1f)
    }

    // ==================== 占位 ====================

    private fun drawPlaceholder(
        canvas: Canvas,
        uiState: PlayerUiState,
        width: Float,
        height: Float,
        systemTimeMs: Long
    ) {
        val ph = scene?.placeholder ?: return
        val text = ph.text ?: uiState.songTitle.ifBlank { "Enjoy Music" }
        val fontSize = vars.eval(ph.fontSize)
        if (fontSize <= 0f) return

        currentTypeface?.let { paint.typeface = it }
        paint.textSize = fontSize
        paint.color = ph.color
        paint.alpha = 255
        paint.textAlign = Paint.Align.CENTER
        paint.getFontMetrics(fontMetrics)
        val dy = -(fontMetrics.descent + fontMetrics.ascent) / 2
        val baseY = height / 2f + vars.eval(ph.y)

        // 呼吸缩放 (4s 周期, 0.99 ~ 1.01)
        val scale = if (ph.breath) {
            1f + sin(systemTimeMs * 0.001570796f) * 0.01f
        } else 1f

        if (scale != 1f) {
            canvas.save()
            canvas.translate(width / 2f, baseY)
            canvas.scale(scale, scale)
            canvas.translate(-width / 2f, -baseY)
        }
        canvas.drawText(text, width / 2f, baseY + dy, paint)
        if (scale != 1f) canvas.restore()
        paint.textAlign = Paint.Align.LEFT
    }

    // ==================== 叠加元素 ====================

    private fun drawOverlay(canvas: Canvas, overlay: Overlay) {
        val alpha = vars.eval(overlay.alpha).coerceIn(0f, 1f)
        if (alpha <= 0f) return
        paint.alpha = (255 * alpha).toInt().coerceIn(0, 255)

        when (overlay) {
            is Overlay.TextNode -> {
                val fontSize = vars.eval(overlay.fontSize)
                if (fontSize <= 0f) return
                currentTypeface?.let { paint.typeface = it }
                paint.textSize = fontSize
                paint.color = overlay.color
                paint.textAlign = when (overlay.align) {
                    1 -> Paint.Align.CENTER
                    2 -> Paint.Align.RIGHT
                    else -> Paint.Align.LEFT
                }
                canvas.drawText(overlay.text, vars.eval(overlay.x), vars.eval(overlay.y), paint)
                paint.textAlign = Paint.Align.LEFT
            }

            is Overlay.RectNode -> {
                paint.color = overlay.color
                val r = vars.eval(overlay.radius)
                val l = vars.eval(overlay.l)
                val t = vars.eval(overlay.t)
                val rr = vars.eval(overlay.r)
                val bb = vars.eval(overlay.b)
                if (r > 0f) {
                    canvas.drawRoundRect(l, t, rr, bb, r, r, paint)
                } else {
                    canvas.drawRect(l, t, rr, bb, paint)
                }
            }

            is Overlay.CircleNode -> {
                paint.color = overlay.color
                canvas.drawCircle(vars.eval(overlay.cx), vars.eval(overlay.cy), vars.eval(overlay.r), paint)
            }
        }
    }

    // ==================== 布局 ====================

    private fun getLayout(line: LyricLine, maxWidth: Float, fontSize: Float, fillByWord: Boolean): LineLayout {
        val cached = cachedLayout
        if (cached != null && cachedLine === line && cachedWidth == maxWidth && cachedFontSize == fontSize) {
            return cached
        }
        cachedLine = line
        cachedWidth = maxWidth
        cachedFontSize = fontSize
        val layout = buildLayout(line, maxWidth, fontSize, fillByWord)
        cachedLayout = layout
        return layout
    }

    private fun buildLayout(line: LyricLine, maxWidth: Float, fontSize: Float, fillByWord: Boolean): LineLayout {
        paint.textSize = fontSize
        currentTypeface?.let { paint.typeface = it }

        // 字级单位: 解析器保证 words 非空; fillByWord=false 时整行作为一个单位
        val words = if (fillByWord && line.words.isNotEmpty()) {
            line.words
        } else {
            listOf(LyricWord(line.text, line.startTime, line.endTime))
        }

        // 测量
        val units = ArrayList<LayoutUnit>(words.size)
        for (w in words) {
            if (w.text.isEmpty()) continue
            units.add(LayoutUnit(w.text, paint.measureText(w.text), w.startTime, w.endTime))
        }
        if (units.isEmpty()) return LineLayout(emptyList(), fontSize)

        // 自动换行
        val groups = mutableListOf<TextGroup>()
        var current = mutableListOf<LayoutUnit>()
        var currentWidth = 0f
        for (u in units) {
            if (currentWidth + u.width > maxWidth && current.isNotEmpty()) {
                groups.add(TextGroup(current, currentWidth))
                current = mutableListOf()
                currentWidth = 0f
            }
            current.add(u)
            currentWidth += u.width
        }
        if (current.isNotEmpty()) groups.add(TextGroup(current, currentWidth))

        return LineLayout(groups, fontSize)
    }

    private class LineLayout(val groups: List<TextGroup>, val fontSize: Float)
    private class TextGroup(val units: List<LayoutUnit>, val width: Float)
    private class LayoutUnit(val text: String, val width: Float, val startTime: Long, val endTime: Long)

    // ==================== 字体 ====================

    private fun loadTypeface(path: String): Typeface {
        try {
            return Typeface.createFromAsset(context.assets, path)
        } catch (_: Exception) {
            // assets 中不存在 → 尝试绝对路径
        }
        val file = File(path)
        if (file.exists()) {
            try {
                return Typeface.createFromFile(file)
            } catch (_: Exception) {
                // 字体损坏, 用默认字体
            }
        }
        return Typeface.DEFAULT
    }
}
