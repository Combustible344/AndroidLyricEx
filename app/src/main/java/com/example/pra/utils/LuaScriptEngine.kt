package com.example.pra.utils

import android.content.Context
import android.graphics.Canvas
import android.util.Log
import com.example.pra.ui.state.PlayMode
import com.example.pra.ui.state.PlayerUiState
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import org.luaj.vm2.lib.jse.JsePlatform

class LuaScriptEngine(context: Context) {
    private val globals: Globals = JsePlatform.standardGlobals()
    private val adapter = LuaCanvasAdapter()
    private var luaOnDrawFunction: LuaValue = LuaValue.NIL

    init {
        globals.set("canvas", CoerceJavaToLua.coerce(adapter))
    }

    fun loadScript(scriptContent: String) {
        try {
            val chunk = globals.load(scriptContent)
            chunk.call()
            luaOnDrawFunction = globals.get("onDraw")
        } catch (e: Exception) {
            Log.e("LuaEngine", "Load Error", e)
        }
    }

    fun executeDraw(
        canvas: Canvas,
        uiState: PlayerUiState, // 传入完整的状态
        timeString: String,
        width: Float,
        height: Float,
        animValue: Float
    ) {
        if (luaOnDrawFunction.isnil()) return

        adapter.attachCanvas(canvas)

        // 1. 构建 State Table (播放器状态)
        val stateTable = LuaTable()
        stateTable.set("title", LuaValue.valueOf(uiState.songTitle))
        stateTable.set("artist", LuaValue.valueOf(uiState.artistName))
        stateTable.set("isPlaying", LuaValue.valueOf(uiState.isPlaying))
        stateTable.set("progress", LuaValue.valueOf(uiState.progress.toDouble())) // 0.0 - 1.0
        stateTable.set("currentTime", LuaValue.valueOf(uiState.currentTime))
        stateTable.set("totalTime", LuaValue.valueOf(uiState.totalTime))
        stateTable.set("isLiked", LuaValue.valueOf(uiState.isCurrentSongLiked))
        // 获取当前歌词文本
        val lyricText = uiState.currentLyricLine?.text ?: ""
        stateTable.set("lyric", LuaValue.valueOf(lyricText))

        // 2. 构建 Env Table (环境参数)
        val envTable = LuaTable()
        envTable.set("time", LuaValue.valueOf(timeString))
        envTable.set("width", LuaValue.valueOf(width.toDouble()))
        envTable.set("height", LuaValue.valueOf(height.toDouble()))
        envTable.set("anim", LuaValue.valueOf(animValue.toDouble()))

        // 传递辅助信息：是否竖屏
        val isPortrait = height > width
        envTable.set("isPortrait", LuaValue.valueOf(isPortrait))

        // 3. 调用 Lua: onDraw(state, env)
        try {
            luaOnDrawFunction.call(stateTable, envTable)
        } catch (e: Exception) {
            Log.e("LuaDraw", "Runtime Error: ${e.message}")
        }
    }
}
