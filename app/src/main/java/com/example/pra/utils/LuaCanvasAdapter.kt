package com.example.pra.utils

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader // 确保有这个
// import android.graphics.TileMode  <-- 删掉这一行，这是报错的原因

class LuaCanvasAdapter {
    private var currentCanvas: Canvas? = null
    private val paint = Paint().apply { isAntiAlias = true }

    // --- 缓存对象 ---
    private val rectF = RectF()
    private val path = Path()

    fun attachCanvas(canvas: Canvas) {
        this.currentCanvas = canvas
    }

    // --- 基础属性设置 ---
    fun setColor(color: Any) {
        paint.shader = null
        if (color is String) {
            try { paint.color = Color.parseColor(color) } catch (e: Exception) {}
        } else if (color is Number) {
            paint.color = color.toInt()
        }
    }

    fun setAlpha(alpha: Int) { paint.alpha = alpha.coerceIn(0, 255) }
    fun setTextSize(size: Float) { paint.textSize = size }
    fun setStrokeWidth(width: Float) { paint.strokeWidth = width }

    fun setStyle(style: Int) {
        paint.style = when(style) {
            1 -> Paint.Style.STROKE
            2 -> Paint.Style.FILL_AND_STROKE
            else -> Paint.Style.FILL
        }
    }

    fun setTextAlign(align: Int) {
        paint.textAlign = when(align) { 1 -> Paint.Align.CENTER; 2 -> Paint.Align.RIGHT; else -> Paint.Align.LEFT }
    }

    fun setShadow(radius: Float, dx: Float, dy: Float, color: String) {
        try { paint.setShadowLayer(radius, dx, dy, Color.parseColor(color)) } catch (e: Exception) {}
    }

    // ★★★ 修复后的线性渐变 ★★★
    fun setLinearGradient(x0: Float, y0: Float, x1: Float, y1: Float, color1: String, color2: String) {
        try {
            val c1 = Color.parseColor(color1)
            val c2 = Color.parseColor(color2)
            // 修改点：使用 Shader.TileMode.CLAMP
            paint.shader = LinearGradient(x0, y0, x1, y1, c1, c2, Shader.TileMode.CLAMP)
        } catch (e: Exception) {
            paint.shader = null
        }
    }

    // --- 绘图指令 ---
    fun drawText(text: String, x: Float, y: Float) { currentCanvas?.drawText(text, x, y, paint) }
    fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float) { currentCanvas?.drawLine(x1, y1, x2, y2, paint) }
    fun drawCircle(cx: Float, cy: Float, radius: Float) { currentCanvas?.drawCircle(cx, cy, radius, paint) }

    fun drawRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float) {
        rectF.set(left, top, right, bottom)
        currentCanvas?.drawRoundRect(rectF, radius, radius, paint)
    }

    // Path 绘制
    fun beginPath() { path.reset() }
    fun moveTo(x: Float, y: Float) { path.moveTo(x, y) }
    fun lineTo(x: Float, y: Float) { path.lineTo(x, y) }
    fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) { path.quadTo(x1, y1, x2, y2) }
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) { path.cubicTo(x1, y1, x2, y2, x3, y3) }
    fun closePath() { path.close() }
    fun drawPath() { currentCanvas?.drawPath(path, paint) }

    // 辅助计算
    fun measureText(text: String): Float = paint.measureText(text)
    fun getTextHeight(): Float = paint.descent() - paint.ascent()

    // 变换
    fun translate(dx: Float, dy: Float) { currentCanvas?.translate(dx, dy) }
    fun rotate(degrees: Float) { currentCanvas?.rotate(degrees) }
    fun scale(sx: Float, sy: Float) { currentCanvas?.scale(sx, sy) }
    fun save() { currentCanvas?.save() }
    fun restore() { currentCanvas?.restore() }
}
