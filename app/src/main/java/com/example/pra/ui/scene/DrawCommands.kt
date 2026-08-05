package com.example.pra.ui.scene

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface

/**
 * 每帧绘制命令: Lua 脚本生成 (命令缓冲), 原生执行器批量回放。
 * 执行器复用单一 Paint 并缓存状态, 跳过重复设置 — 消除反射热路径与状态抖动。
 */
sealed class DrawCommand {
    class SetColor(val color: Int) : DrawCommand()
    class SetAlpha(val alpha: Int) : DrawCommand()
    class SetTextSize(val size: Float) : DrawCommand()
    class SetTypeface(val typeface: Typeface?) : DrawCommand()
    class SetTextAlign(val align: Paint.Align) : DrawCommand()
    class SetStyle(val style: Paint.Style) : DrawCommand()
    class SetStrokeWidth(val width: Float) : DrawCommand()
    class SetShadow(val radius: Float, val dx: Float, val dy: Float, val color: Int) : DrawCommand()
    object ClearShadow : DrawCommand()
    class SetLinearGradient(
        val x0: Float, val y0: Float, val x1: Float, val y1: Float,
        val c1: Int, val c2: Int
    ) : DrawCommand()

    class DrawText(val text: String, val x: Float, val y: Float) : DrawCommand()
    class DrawRect(val l: Float, val t: Float, val r: Float, val b: Float) : DrawCommand()
    class DrawRoundRect(val l: Float, val t: Float, val r: Float, val b: Float, val rad: Float) : DrawCommand()
    class DrawCircle(val cx: Float, val cy: Float, val radius: Float) : DrawCommand()
    class DrawLine(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : DrawCommand()
    class DrawArc(
        val l: Float, val t: Float, val r: Float, val b: Float,
        val start: Float, val sweep: Float, val useCenter: Boolean
    ) : DrawCommand()
    class DrawColor(val color: Int) : DrawCommand()

    class ClipRect(val l: Float, val t: Float, val r: Float, val b: Float) : DrawCommand()
    object Save : DrawCommand()
    object Restore : DrawCommand()
    class Translate(val dx: Float, val dy: Float) : DrawCommand()
    class Scale(val sx: Float, val sy: Float) : DrawCommand()
    class Rotate(val deg: Float) : DrawCommand()

    class DrawPath(val path: Path) : DrawCommand()
}

/** 命令执行器: 单一 Paint + 状态缓存, 零分配回放 */
class DrawCommandExecutor {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rectF = RectF()

    private var lastColor = -1
    private var lastAlpha = -1
    private var lastTextSize = -1f
    private var lastTypeface: Typeface? = null
    private var lastAlign: Paint.Align? = null
    private var lastStyle: Paint.Style? = null
    private var hasShadow = false

    fun execute(commands: List<DrawCommand>, canvas: Canvas) {
        lastColor = -1
        lastAlpha = -1
        lastTextSize = -1f
        lastTypeface = null
        lastAlign = null
        lastStyle = null
        hasShadow = false
        // 清除跨帧残留: 阴影与渐变填充 (若上一帧设置了而本帧未覆盖, 会污染后续绘制)
        paint.clearShadowLayer()
        paint.shader = null

        for (cmd in commands) {
            when (cmd) {
                is DrawCommand.SetColor -> if (cmd.color != lastColor) {
                    // ★★★ Paint.setColor(int) 会同时重置 alpha=255!
                    // 必须保留当前 alpha, 否则 "先 setAlpha 后 setColor" 的
                    // 命令序列 (歌词/粒子) 会让透明度失效, 并污染后续绘制
                    val keepAlpha = paint.alpha
                    paint.color = cmd.color
                    paint.alpha = keepAlpha
                    lastColor = cmd.color
                }

                is DrawCommand.SetAlpha -> if (cmd.alpha != lastAlpha) {
                    paint.alpha = cmd.alpha
                    lastAlpha = cmd.alpha
                }

                is DrawCommand.SetTextSize -> if (cmd.size != lastTextSize) {
                    paint.textSize = cmd.size
                    lastTextSize = cmd.size
                }

                is DrawCommand.SetTypeface -> if (cmd.typeface !== lastTypeface) {
                    paint.typeface = cmd.typeface
                    lastTypeface = cmd.typeface
                }

                is DrawCommand.SetTextAlign -> if (cmd.align != lastAlign) {
                    paint.textAlign = cmd.align
                    lastAlign = cmd.align
                }

                is DrawCommand.SetStyle -> if (cmd.style != lastStyle) {
                    paint.style = cmd.style
                    lastStyle = cmd.style
                }

                is DrawCommand.SetStrokeWidth -> paint.strokeWidth = cmd.width

                is DrawCommand.SetShadow -> {
                    paint.setShadowLayer(cmd.radius, cmd.dx, cmd.dy, cmd.color)
                    hasShadow = true
                }

                is DrawCommand.ClearShadow -> if (hasShadow) {
                    paint.clearShadowLayer()
                    hasShadow = false
                }

                is DrawCommand.SetLinearGradient -> paint.shader =
                    android.graphics.LinearGradient(
                        cmd.x0, cmd.y0, cmd.x1, cmd.y1,
                        cmd.c1, cmd.c2, android.graphics.Shader.TileMode.CLAMP
                    )

                is DrawCommand.DrawText -> canvas.drawText(cmd.text, cmd.x, cmd.y, paint)
                is DrawCommand.DrawRect -> canvas.drawRect(cmd.l, cmd.t, cmd.r, cmd.b, paint)
                is DrawCommand.DrawRoundRect -> {
                    rectF.set(cmd.l, cmd.t, cmd.r, cmd.b)
                    canvas.drawRoundRect(rectF, cmd.rad, cmd.rad, paint)
                }
                is DrawCommand.DrawCircle -> canvas.drawCircle(cmd.cx, cmd.cy, cmd.radius, paint)
                is DrawCommand.DrawLine -> canvas.drawLine(cmd.x1, cmd.y1, cmd.x2, cmd.y2, paint)
                is DrawCommand.DrawArc -> {
                    rectF.set(cmd.l, cmd.t, cmd.r, cmd.b)
                    canvas.drawArc(rectF, cmd.start, cmd.sweep, cmd.useCenter, paint)
                }
                is DrawCommand.DrawColor -> canvas.drawColor(cmd.color)

                is DrawCommand.ClipRect -> canvas.clipRect(cmd.l, cmd.t, cmd.r, cmd.b)
                is DrawCommand.Save -> canvas.save()
                is DrawCommand.Restore -> canvas.restore()
                is DrawCommand.Translate -> canvas.translate(cmd.dx, cmd.dy)
                is DrawCommand.Scale -> canvas.scale(cmd.sx, cmd.sy)
                is DrawCommand.Rotate -> canvas.rotate(cmd.deg)
                is DrawCommand.DrawPath -> canvas.drawPath(cmd.path, paint)
            }
        }
    }
}
