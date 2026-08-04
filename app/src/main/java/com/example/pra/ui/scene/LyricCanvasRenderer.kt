package com.example.pra.ui.scene

import android.graphics.Canvas
import com.example.pra.ui.state.PlayerUiState

/**
 * 屏保渲染器抽象: 声明式场景渲染 与 Lua 脚本模式 (命令缓冲) 共用同一入口,
 * MainActivity 按场景加载结果选择实现
 */
interface LyricCanvasRenderer {
    fun render(
        canvas: Canvas,
        uiState: PlayerUiState,
        positionMs: Long,
        width: Float,
        height: Float,
        systemTimeMs: Long
    )
}

/** Lua 脚本模式渲染器: 每帧执行 onDraw → 命令缓冲 → 原生回放 */
class LuaScriptRenderer(private val engine: LuaScriptEngine) : LyricCanvasRenderer {

    /** 切歌动画值 (0..1), 由 MainActivity 更新 */
    var animValue: Float = 1f

    override fun render(
        canvas: Canvas,
        uiState: PlayerUiState,
        positionMs: Long,
        width: Float,
        height: Float,
        systemTimeMs: Long
    ) {
        engine.executeDraw(canvas, uiState, positionMs, width, height, systemTimeMs, animValue)
    }
}
