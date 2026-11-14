package com.example.pra.ui.scene

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface

/**
 * Lua 画布适配器 (命令缓冲版)
 *
 * 与旧 draw.lua 的 canvas API 完全兼容, 但不再直接操作 Canvas:
 * 每个调用只生成一条 [DrawCommand] 写入缓冲, 帧末由 [DrawCommandExecutor]
 * 原生批量回放。测量类调用 (measureText 等) 使用本地的逻辑 Paint, 立即返回。
 */
class CommandCanvasAdapter(private val buffer: MutableList<DrawCommand>) {

    // 逻辑 Paint: 记录 Lua 侧的样式状态, 供测量使用
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fontMetrics = Paint.FontMetrics()
    private val path = Path()

    // ==================== 样式 ====================

    fun setTypeface(font: Any?) {
        val tf = if (font is Typeface) font else Typeface.DEFAULT
        paint.typeface = tf
        buffer.add(DrawCommand.SetTypeface(tf))
    }

    fun setColor(color: Any) {
        val c = parseColor(color)
        // 与执行器一致: setColor 不改变 alpha (alpha 由 setAlpha 独占控制)
        val keepAlpha = paint.alpha
        paint.color = c
        paint.alpha = keepAlpha
        buffer.add(DrawCommand.SetColor(c))
    }

    fun setAlpha(alpha: Int) {
        val a = alpha.coerceIn(0, 255)
        paint.alpha = a
        buffer.add(DrawCommand.SetAlpha(a))
    }

    fun setTextSize(size: Float) {
        paint.textSize = size
        buffer.add(DrawCommand.SetTextSize(size))
    }

    fun setStrokeWidth(width: Float) {
        paint.strokeWidth = width
        buffer.add(DrawCommand.SetStrokeWidth(width))
    }

    /** 0: FILL, 1: STROKE, 2: FILL_AND_STROKE */
    fun setStyle(style: Int) {
        val s = when (style) {
            1 -> Paint.Style.STROKE
            2 -> Paint.Style.FILL_AND_STROKE
            else -> Paint.Style.FILL
        }
        paint.style = s
        buffer.add(DrawCommand.SetStyle(s))
    }

    /** 0: LEFT, 1: CENTER, 2: RIGHT */
    fun setTextAlign(align: Int) {
        val a = when (align) {
            1 -> Paint.Align.CENTER
            2 -> Paint.Align.RIGHT
            else -> Paint.Align.LEFT
        }
        paint.textAlign = a
        buffer.add(DrawCommand.SetTextAlign(a))
    }

    fun setShadow(radius: Float, dx: Float, dy: Float, color: String) {
        try {
            buffer.add(DrawCommand.SetShadow(radius, dx, dy, Color.parseColor(color)))
        } catch (e: Exception) {
            buffer.add(DrawCommand.ClearShadow)
        }
    }

    fun clearShadow() {
        buffer.add(DrawCommand.ClearShadow)
    }

    fun setLinearGradient(x0: Float, y0: Float, x1: Float, y1: Float, c1: String, c2: String) {
        try {
            buffer.add(DrawCommand.SetLinearGradient(x0, y0, x1, y1, Color.parseColor(c1), Color.parseColor(c2)))
        } catch (e: Exception) {
            buffer.add(DrawCommand.SetColor(Color.WHITE))
        }
    }

    // ==================== 基础绘制 ====================

    fun drawText(text: String, x: Float, y: Float) {
        buffer.add(DrawCommand.DrawText(text, x, y))
    }

    fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float) {
        buffer.add(DrawCommand.DrawLine(x1, y1, x2, y2))
    }

    fun drawRect(l: Float, t: Float, r: Float, b: Float, color: Any) {
        buffer.add(DrawCommand.SetColor(parseColor(color)))
        buffer.add(DrawCommand.DrawRect(l, t, r, b))
    }

    fun drawRoundRect(l: Float, t: Float, r: Float, b: Float, rad: Float) {
        buffer.add(DrawCommand.DrawRoundRect(l, t, r, b, rad))
    }

    fun drawCircle(cx: Float, cy: Float, radius: Float, color: Any) {
        buffer.add(DrawCommand.SetColor(parseColor(color)))
        buffer.add(DrawCommand.DrawCircle(cx, cy, radius))
    }

    fun drawArc(l: Float, t: Float, r: Float, b: Float, startAngle: Float, sweepAngle: Float, useCenter: Boolean, color: Any) {
        buffer.add(DrawCommand.SetColor(parseColor(color)))
        buffer.add(DrawCommand.DrawArc(l, t, r, b, startAngle, sweepAngle, useCenter))
    }

    fun drawColor(color: Any) {
        buffer.add(DrawCommand.DrawColor(parseColor(color)))
    }

    // ==================== 裁剪与变换 ====================

    fun save() { buffer.add(DrawCommand.Save) }
    fun restore() { buffer.add(DrawCommand.Restore) }
    fun translate(dx: Float, dy: Float) { buffer.add(DrawCommand.Translate(dx, dy)) }
    fun scale(sx: Float, sy: Float) { buffer.add(DrawCommand.Scale(sx, sy)) }
    fun rotate(deg: Float) { buffer.add(DrawCommand.Rotate(deg)) }
    fun clipText(text: String, x: Double, y: Double) {
        buffer.add(DrawCommand.ClipText(text, x.toFloat(), y.toFloat()))
    }

    fun clipRect(l: Float, t: Float, r: Float, b: Float) { buffer.add(DrawCommand.ClipRect(l, t, r, b)) }

    // ==================== 测量 ====================

    fun measureText(text: String): Float = paint.measureText(text)

    fun measureTextHeight(): Float {
        paint.getFontMetrics(fontMetrics)
        return fontMetrics.descent - fontMetrics.ascent
    }

    fun getTextCenterOffset(): Float {
        paint.getFontMetrics(fontMetrics)
        return -(fontMetrics.descent + fontMetrics.ascent) / 2
    }

    // ==================== 路径 ====================

    fun beginPath() { path.reset() }
    fun moveTo(x: Float, y: Float) { path.moveTo(x, y) }
    fun lineTo(x: Float, y: Float) { path.lineTo(x, y) }
    fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) { path.quadTo(x1, y1, x2, y2) }
    fun closePath() { path.close() }
    fun drawPath() { buffer.add(DrawCommand.DrawPath(path)) }

    // ==================== 卡拉OK 批量绘制 ====================

    fun drawKaraokeLine(
        text: String,
        x: Float,
        y: Float,
        alpha: Int,
        progress: Double,
        baseColor: String,
        activeColor: String,
        shadowColor: String?
    ) {
        buffer.add(DrawCommand.SetAlpha(alpha.coerceIn(0, 255)))
        buffer.add(DrawCommand.SetColor(parseColor(baseColor)))
        buffer.add(DrawCommand.DrawText(text, x, y))

        if (progress > 0) {
            buffer.add(DrawCommand.SetColor(parseColor(activeColor)))
            if (shadowColor != null) {
                try {
                    buffer.add(DrawCommand.SetShadow(20f, 0f, 0f, Color.parseColor(shadowColor)))
                } catch (e: Exception) {
                }
            }
            buffer.add(DrawCommand.Save)
            val textWidth = paint.measureText(text)
            buffer.add(DrawCommand.ClipRect(x, y - paint.textSize, x + (textWidth * progress).toFloat(), y + paint.textSize))
            buffer.add(DrawCommand.DrawText(text, x, y))
            buffer.add(DrawCommand.Restore)
            if (shadowColor != null) buffer.add(DrawCommand.ClearShadow)
        }
    }

    private fun parseColor(color: Any): Int = when (color) {
        is String -> try {
            Color.parseColor(color)
        } catch (e: Exception) {
            Color.WHITE
        }
        is Number -> color.toInt()
        else -> Color.WHITE
    }
}
