package com.example.pra.utils

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/**
 * Lua 绘图适配器 (V1.1 Fix)
 * 修复了 FontMetrics 调用报错的问题
 */
class LuaCanvasAdapter {
    private var currentCanvas: Canvas? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rectF = RectF()
    private val path = Path()

    fun attachCanvas(canvas: Canvas) {
        this.currentCanvas = canvas
    }

    // ==========================================
    // 1. 样式设置 (Style)
    // ==========================================

    fun setTypeface(font: Any?) {
        paint.typeface = if (font is Typeface) font else Typeface.DEFAULT
    }

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

    /** 0: FILL, 1: STROKE, 2: FILL_AND_STROKE */
    fun setStyle(style: Int) {
        paint.style = when (style) {
            1 -> Paint.Style.STROKE; 2 -> Paint.Style.FILL_AND_STROKE; else -> Paint.Style.FILL
        }
    }

    /** 0: LEFT, 1: CENTER, 2: RIGHT */
    fun setTextAlign(align: Int) {
        paint.textAlign = when (align) {
            1 -> Paint.Align.CENTER; 2 -> Paint.Align.RIGHT; else -> Paint.Align.LEFT
        }
    }

    fun setShadow(radius: Float, dx: Float, dy: Float, color: String) {
        try { paint.setShadowLayer(radius, dx, dy, Color.parseColor(color)) } catch (e: Exception) { paint.clearShadowLayer() }
    }

    fun setLinearGradient(x0: Float, y0: Float, x1: Float, y1: Float, c1: String, c2: String) {
        try {
            paint.shader = LinearGradient(x0, y0, x1, y1, Color.parseColor(c1), Color.parseColor(c2), Shader.TileMode.CLAMP)
        } catch (e: Exception) { paint.shader = null }
    }

    // ==========================================
    // 2. 基础绘图 (Drawing)
    // ==========================================

    fun drawText(text: String, x: Float, y: Float) {
        currentCanvas?.drawText(text, x, y, paint)
    }

    fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float) {
        currentCanvas?.drawLine(x1, y1, x2, y2, paint)
    }

    fun drawRect(l: Float, t: Float, r: Float, b: Float, color: Any) {
        val old = paint.color; val oldS = paint.shader
        setColor(color)
        currentCanvas?.drawRect(l, t, r, b, paint)
        paint.color = old; paint.shader = oldS
    }

    fun drawRoundRect(l: Float, t: Float, r: Float, b: Float, rad: Float) {
        rectF.set(l, t, r, b)
        currentCanvas?.drawRoundRect(rectF, rad, rad, paint)
    }

    fun drawCircle(cx: Float, cy: Float, radius: Float, color: Any) {
        val old = paint.color; val oldS = paint.shader
        setColor(color)
        currentCanvas?.drawCircle(cx, cy, radius, paint)
        paint.color = old; paint.shader = oldS
    }

    fun drawArc(l: Float, t: Float, r: Float, b: Float, startAngle: Float, sweepAngle: Float, useCenter: Boolean, color: Any) {
        val old = paint.color; val oldS = paint.shader
        setColor(color)
        rectF.set(l, t, r, b)
        currentCanvas?.drawArc(rectF, startAngle, sweepAngle, useCenter, paint)
        paint.color = old; paint.shader = oldS
    }

    // ==========================================
    // 3. 裁剪与变换 (Clip & Transform)
    // ==========================================

    fun save() { currentCanvas?.save() }
    fun restore() { currentCanvas?.restore() }
    fun translate(dx: Float, dy: Float) { currentCanvas?.translate(dx, dy) }
    fun scale(sx: Float, sy: Float) { currentCanvas?.scale(sx, sy) }
    fun rotate(deg: Float) { currentCanvas?.rotate(deg) }

    fun clipRect(l: Float, t: Float, r: Float, b: Float) {
        currentCanvas?.clipRect(l, t, r, b)
    }

    // ==========================================
    // 4. 测量与辅助 (Metrics)
    // ==========================================
    fun measureText(text: String): Float = paint.measureText(text)

    /**
     * ★★★ 修复点：使用 fontMetrics 获取高度 ★★★
     */
    fun measureTextHeight(): Float {
        val fm = paint.fontMetrics
        return fm.descent - fm.ascent
    }

    /**
     * ★★★ 修复点：使用 fontMetrics 获取偏移 ★★★
     */
    fun getTextCenterOffset(): Float {
        val fm = paint.fontMetrics
        return -(fm.descent + fm.ascent) / 2
    }

    // ==========================================
    // 5. 路径 (Path)
    // ==========================================
    fun beginPath() { path.reset() }
    fun moveTo(x: Float, y: Float) { path.moveTo(x, y) }
    fun lineTo(x: Float, y: Float) { path.lineTo(x, y) }
    fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) { path.quadTo(x1, y1, x2, y2) }
    fun closePath() { path.close() }
    fun drawPath() { currentCanvas?.drawPath(path, paint) }
}
