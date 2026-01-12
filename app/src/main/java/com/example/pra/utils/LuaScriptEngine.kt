package com.example.pra.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import com.example.pra.ui.state.PlayerUiState
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import org.luaj.vm2.lib.jse.JsePlatform

class LuaScriptEngine(context: Context) {
    private val globals: Globals = JsePlatform.standardGlobals()
    private val adapter = LuaCanvasAdapter() // 确保你用了之前那个带 Path/Gradient 的版本
    private var luaOnDrawFunction: LuaValue = LuaValue.NIL

    // ★★★ 优化点 1: 复用 Table，减少 GC 压力 ★★★
    private val stateTable = LuaTable()
    private val envTable = LuaTable()

    // ★★★ 优化点 2: 错误提示画笔 ★★★
    private val errorPaint = Paint().apply {
        color = Color.RED
        textSize = 40f
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    init {
        // 1. 将 adapter 注入 Lua 全局环境
        globals.set("canvas", CoerceJavaToLua.coerce(adapter))

        // 2. ★★★ 注入字符串拆分工具 (解决 Lua 处理中文难题) ★★★
        val tools = LuaTable()
        tools.set("toChars", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                val str = arg.checkjstring() ?: return LuaTable()
                val table = LuaTable()
                // 按 CodePoint 遍历，兼容 Emoji 和中文
                var index = 1
                var offset = 0
                val length = str.length
                while (offset < length) {
                    val codePoint = str.codePointAt(offset)
                    val charCount = Character.charCount(codePoint)
                    // Lua 数组下标从 1 开始
                    table.set(index++, LuaValue.valueOf(str.substring(offset, offset + charCount)))
                    offset += charCount
                }
                return table
            }
        })
        globals.set("tools", tools)
        // 快捷方式：直接用 toChars(...)
        globals.set("toChars", tools.get("toChars"))
    }

    fun loadScript(scriptContent: String) {
        try {
            val chunk = globals.load(scriptContent)
            chunk.call()
            luaOnDrawFunction = globals.get("onDraw")
        } catch (e: Exception) {
            Log.e("LuaEngine", "Script Load Error", e)
        }
    }

    fun executeDraw(
        canvas: Canvas,
        uiState: PlayerUiState,
        timeString: String,
        width: Float,
        height: Float,
        animValue: Float
    ) {
        adapter.attachCanvas(canvas)

        // 如果脚本没加载成功，显示提示
        if (luaOnDrawFunction.isnil()) {
            canvas.drawText("No Script Loaded", 50f, 100f, errorPaint)
            return
        }

        try {
            // ★★★ 优化点 3: 只更新值，不创建新对象 ★★★

            // --- 1. 计算精确毫秒时间 ---
            val totalSeconds = parseTimeToSeconds(uiState.totalTime)
            val totalMs = totalSeconds * 1000L
            val currentPosMs = (totalMs * uiState.progress).toLong()

            // --- 2. 更新 State ---
            stateTable.set("title", LuaValue.valueOf(uiState.songTitle))
            stateTable.set("artist", LuaValue.valueOf(uiState.artistName))
            stateTable.set("isPlaying", LuaValue.valueOf(uiState.isPlaying))
            stateTable.set("progress", LuaValue.valueOf(uiState.progress.toDouble()))
            stateTable.set("currentTime", LuaValue.valueOf(uiState.currentTime))
            stateTable.set("totalTime", LuaValue.valueOf(uiState.totalTime))
            stateTable.set("isLiked", LuaValue.valueOf(uiState.isCurrentSongLiked))

            // 歌词文本与时间戳
            val line = uiState.currentLyricLine
            stateTable.set("lyric", LuaValue.valueOf(line?.text ?: ""))
            stateTable.set("position", LuaValue.valueOf(currentPosMs.toDouble()))
            stateTable.set("lineStart", LuaValue.valueOf(line?.startTime?.toDouble() ?: 0.0))
            stateTable.set("lineEnd", LuaValue.valueOf(line?.endTime?.toDouble() ?: 0.0))

            // --- 3. 更新 Env ---
            envTable.set("time", LuaValue.valueOf(timeString))
            envTable.set("width", LuaValue.valueOf(width.toDouble()))
            envTable.set("height", LuaValue.valueOf(height.toDouble()))
            envTable.set("anim", LuaValue.valueOf(animValue.toDouble()))
            envTable.set("isPortrait", LuaValue.valueOf(height > width))

            // --- 4. 执行 Lua 函数 ---
            luaOnDrawFunction.call(stateTable, envTable)

        } catch (e: Exception) {
            // ★★★ 优化点 4: 屏幕可视化的错误处理 ★★★
            Log.e("LuaDraw", "Runtime Error", e)

            canvas.save()
            // 确保错误信息画在最顶层
            adapter.setColor(Color.RED)
            adapter.setTextSize(30f)

            val errorMsg = "Lua Error: ${e.message}"
            // 简单的换行处理
            val lines = errorMsg.chunked(60)
            var y = 80f
            for (line in lines) {
                canvas.drawText(line, 20f, y, errorPaint)
                y += 40f
            }
            canvas.restore()
        }
    }

    private fun parseTimeToSeconds(timeStr: String): Int {
        val parts = timeStr.split(":")
        if (parts.size != 2) return 0
        return try {
            parts[0].toInt() * 60 + parts[1].toInt()
        } catch (e: Exception) { 0 }
    }
}
