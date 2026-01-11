package com.example.pra.utils

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

class LuaCanvasAdapter {
    private var currentCanvas: Canvas? = null
    private val paint = Paint().apply { isAntiAlias = true }
    // 复用 RectF 避免内存抖动
    private val rectF = RectF()

    fun attachCanvas(canvas: Canvas) {
        this.currentCanvas = canvas
        paint.reset()
        paint.isAntiAlias = true
    }

    // --- 基础设置 ---
    fun setColor(color: Any) {
        if (color is String) {
            try { paint.color = Color.parseColor(color) } catch (e: Exception) {}
        } else if (color is Number) {
            paint.color = color.toInt()
        }
    }
    fun setAlpha(alpha: Int) { paint.alpha = alpha.coerceIn(0, 255) }
    fun setTextSize(size: Float) { paint.textSize = size }
    fun setTextAlign(align: Int) {
        paint.textAlign = when(align) { 1 -> Paint.Align.CENTER; 2 -> Paint.Align.RIGHT; else -> Paint.Align.LEFT }
    }
    fun setShadow(radius: Float, dx: Float, dy: Float, color: String) {
        try { paint.setShadowLayer(radius, dx, dy, Color.parseColor(color)) } catch (e: Exception) {}
    }
    fun setStyle(style: Int) { // 0: Fill, 1: Stroke
        paint.style = if (style == 1) Paint.Style.STROKE else Paint.Style.FILL
    }
    fun setStrokeWidth(width: Float) { paint.strokeWidth = width }

    // --- 绘图能力 ---

    fun drawText(text: String, x: Float, y: Float) {
        currentCanvas?.drawText(text, x, y, paint)
    }

    // ★ 新增：测量文字宽度 (用于居中计算)
    fun measureText(text: String): Float {
        return paint.measureText(text)
    }

    // ★ 新增：获取文字高度 (Ascent/Descent)
    fun getTextHeight(): Float {
        return paint.descent() - paint.ascent()
    }

    // ★ 新增：画线 (用于分割线)
    fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float) {
        currentCanvas?.drawLine(x1, y1, x2, y2, paint)
    }

    // ★ 新增：画圆角矩形 (用于进度条、背景卡片)
    fun drawRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float) {
        rectF.set(left, top, right, bottom)
        currentCanvas?.drawRoundRect(rectF, radius, radius, paint)
    }

    // ★ 新增：画圆 (用于点缀、指示器)
    fun drawCircle(cx: Float, cy: Float, radius: Float) {
        currentCanvas?.drawCircle(cx, cy, radius, paint)
    }

    // 变换操作
    fun translate(dx: Float, dy: Float) { currentCanvas?.translate(dx, dy) }
    fun rotate(degrees: Float) { currentCanvas?.rotate(degrees) }
    fun save() { currentCanvas?.save() }
    fun restore() { currentCanvas?.restore() }
}
