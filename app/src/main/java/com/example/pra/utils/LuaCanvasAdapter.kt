package com.example.pra.utils

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader // 确保有这个

import android.graphics.Typeface // 引入 Typeface

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
    // ★★★ 新增：设置字体 ★★★
    fun setTypeface(font: Any?) {
        if (font is Typeface) {
            paint.typeface = font
        } else {
            // 如果传入 nil 或者无效对象，恢复默认字体
            paint.typeface = Typeface.DEFAULT
        }
    }
    // --- 基础属性设置 ---
    fun setColor(color: Any) {
        paint.shader = null
        if (color is String) {
            try {
                paint.color = Color.parseColor(color)
            } catch (e: Exception) {
            }
        } else if (color is Number) {
            paint.color = color.toInt()
        }
    }

    fun setAlpha(alpha: Int) {
        paint.alpha = alpha.coerceIn(0, 255)
    }

    fun setTextSize(size: Float) {
        paint.textSize = size
    }

    fun setStrokeWidth(width: Float) {
        paint.strokeWidth = width
    }

    fun setStyle(style: Int) {
        paint.style = when (style) {
            1 -> Paint.Style.STROKE
            2 -> Paint.Style.FILL_AND_STROKE
            else -> Paint.Style.FILL
        }
    }

    fun setTextAlign(align: Int) {
        paint.textAlign = when (align) {
            1 -> Paint.Align.CENTER; 2 -> Paint.Align.RIGHT; else -> Paint.Align.LEFT
        }
    }

    fun setShadow(radius: Float, dx: Float, dy: Float, color: String) {
        try {
            paint.setShadowLayer(radius, dx, dy, Color.parseColor(color))
        } catch (e: Exception) {
        }
    }

    // ★★★ 修复后的线性渐变 ★★★
    fun setLinearGradient(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        color1: String,
        color2: String
    ) {
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
    fun drawText(text: String, x: Float, y: Float) {
        currentCanvas?.drawText(text, x, y, paint)
    }

    fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float) {
        currentCanvas?.drawLine(x1, y1, x2, y2, paint)
    }

    fun drawCircle(cx: Float, cy: Float, radius: Float) {
        currentCanvas?.drawCircle(cx, cy, radius, paint)
    }

    fun drawRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float) {
        rectF.set(left, top, right, bottom)
        currentCanvas?.drawRoundRect(rectF, radius, radius, paint)
    }

//    // 在 LuaCanvasAdapter 类中
//
//    /**
//     * 高级频谱绘制
//     * @param data 频谱数据 (ByteArray)
//     * @param x, y, w, h 绘制区域
//     * @param barCount 柱子数量 (推荐 32~64)
//     * @param colorLow 渐变起始色 (底部/中心)
//     * @param colorHigh 渐变结束色 (顶部/边缘)
//     * @param bgColor 背景轨道颜色 (传 "" 空字符串则不画背景)
//     * @param gap 柱子间距
//     * @param radius 柱子圆角半径
//     * @param align 对齐方式: 0=底部对齐, 1=居中对称(镜像), 2=顶部对齐
//     */
//    fun drawVisualizerBars(
//        data: Any?,
//        x: Float, y: Float, w: Float, h: Float,
//        barCount: Int,
//        colorLow: String, colorHigh: String,
//        bgColor: String,
//        gap: Float,
//        radius: Float,
//        align: Int
//    ) {
//        if (data !is ByteArray) return
//        val n = data.size
//        if (n < barCount) return
//
//        // 1. 准备画笔和渐变
//        // 注意：为了性能，这里假设 colorLow/High 是十六进制字符串
//        val cLow = try {
//            Color.parseColor(colorLow)
//        } catch (e: Exception) {
//            Color.WHITE
//        }
//        val cHigh = try {
//            Color.parseColor(colorHigh)
//        } catch (e: Exception) {
//            cLow
//        }
//        val cBg = if (bgColor.isNotEmpty()) try {
//            Color.parseColor(bgColor)
//        } catch (e: Exception) {
//            0
//        } else 0
//
//        // 设置线性渐变 (从下到上，或者从中心向外)
//        // 这里简化处理：统一使用垂直渐变
//        paint.shader = LinearGradient(x, y + h, x, y, cLow, cHigh, Shader.TileMode.CLAMP)
//        paint.style = Paint.Style.FILL
//
//        val step = n / barCount
//        val barWidth = (w - (barCount - 1) * gap) / barCount
//
//        for (i in 0 until barCount) {
//            // 2. 数据采样 (简单的峰值采样)
//            var magnitude = 0
//            for (j in 0 until step) {
//                val idx = i * step + j
//                if (idx < n) {
//                    val byteVal = kotlin.math.abs(data[idx].toInt())
//                    if (byteVal > magnitude) magnitude = byteVal
//                }
//            }
//
//            // 归一化 (0.0 ~ 1.0)
//            // 乘以一个系数放大效果，防止太矮
//            var percent = (magnitude / 128f) * 2.5f
//            if (percent > 1f) percent = 1f
//
//            // 计算当前柱子的高度
//            val barHeight = h * percent
//
//            // 计算坐标 (基于对齐模式)
//            var left = x + i * (barWidth + gap)
//            var top = 0f
//            var bottom = 0f
//
//            // 3. 绘制背景轨道 (如果有)
//            if (cBg != 0) {
//                paint.shader = null
//                paint.color = cBg
//                // 背景通常占满高度
//                when (align) {
//                    1 -> { // 居中
//                        rectF.set(left, y, left + barWidth, y + h)
//                    }
//
//                    else -> { // 底部或顶部
//                        rectF.set(left, y, left + barWidth, y + h)
//                    }
//                }
//                currentCanvas?.drawRoundRect(rectF, radius, radius, paint)
//
//                // 恢复渐变
//                paint.shader = LinearGradient(x, y + h, x, y, cLow, cHigh, Shader.TileMode.CLAMP)
//            }
//
//            // 4. 绘制前景柱子
//            when (align) {
//                1 -> { // 居中对称 (Mirror)
//                    val centerY = y + h / 2
//                    top = centerY - barHeight / 2
//                    bottom = centerY + barHeight / 2
//                }
//
//                2 -> { // 顶部对齐 (倒吊)
//                    top = y
//                    bottom = y + barHeight
//                }
//
//                else -> { // 0 = 底部对齐 (默认)
//                    top = y + h - barHeight
//                    bottom = y + h
//                }
//            }
//
//            // 只有高度大于0才画
//            if (barHeight > 0) {
//                rectF.set(left, top, left + barWidth, bottom)
//                currentCanvas?.drawRoundRect(rectF, radius, radius, paint)
//            }
//        }
//
//        // 清理 Shader，以免影响后续绘制
//        paint.shader = null
//    }
//

    // Path 绘制
    fun beginPath() {
        path.reset()
    }

    fun moveTo(x: Float, y: Float) {
        path.moveTo(x, y)
    }

    fun lineTo(x: Float, y: Float) {
        path.lineTo(x, y)
    }

    fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) {
        path.quadTo(x1, y1, x2, y2)
    }

    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        path.cubicTo(x1, y1, x2, y2, x3, y3)
    }

    fun closePath() {
        path.close()
    }

    fun drawPath() {
        currentCanvas?.drawPath(path, paint)
    }

    // 辅助计算
    fun measureText(text: String): Float = paint.measureText(text)
    fun getTextHeight(): Float = paint.descent() - paint.ascent()

    // 变换
    fun translate(dx: Float, dy: Float) {
        currentCanvas?.translate(dx, dy)
    }

    fun rotate(degrees: Float) {
        currentCanvas?.rotate(degrees)
    }

    fun scale(sx: Float, sy: Float) {
        currentCanvas?.scale(sx, sy)
    }

    fun save() {
        currentCanvas?.save()
    }

    fun restore() {
        currentCanvas?.restore()
    }
}
