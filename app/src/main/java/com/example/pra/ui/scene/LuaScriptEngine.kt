package com.example.pra.ui.scene

import android.content.Context
import android.graphics.Canvas
import android.util.Log
import com.example.pra.data.audio.SpectrumTap
import com.example.pra.data.model.LyricLine
import com.example.pra.ui.state.PlayerUiState
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import org.luaj.vm2.lib.jse.JsePlatform

/**
 * Lua 每帧脚本引擎 (命令缓冲模式)
 *
 * 脚本保留完整编程能力 (每帧任意逻辑/绘制调用), 但 canvas 不再直接操作
 * Android Canvas —— 每帧只生成 [DrawCommand] 列表, 由 [DrawCommandExecutor]
 * 原生批量回放。相比旧的"即时模式", 消除了反射热路径与 Paint 状态抖动。
 *
 * 脚本 API 与旧 draw.lua 完全兼容:
 *   canvas:setColor/setAlpha/setTextSize/setTextAlign/setStyle/setShadow/
 *           setLinearGradient/setTypeface/drawText/drawRect/drawCircle/
 *           drawLine/drawArc/save/restore/translate/scale/rotate/clipRect/
 *           measureText/beginPath/moveTo/lineTo/quadTo/closePath/drawPath/
 *           drawKaraokeLine
 *   anim:lerp/map/clamp/sineWave/pulse/easeInQuad/easeOutQuad/easeInOutSine/easeOutBack
 *   tools:toChars/loadFont
 *   state:title/artist/currentTime/totalTime/isPlaying/position/duration/
 *          progress/volume/playMode/isLiked/likedCount/currentLyricIndex/
 *          currentWordIndex/lyric/lineStart/lineEnd/lineProgress
 *   env:time/systemTime/width/height/isPortrait/anim
 *   global_lyrics: 歌词表 (切歌时注入, 不每帧)
 */
class LuaScriptEngine(private val context: Context?) {

    private val globals: Globals = JsePlatform.standardGlobals()
    private val buffer = ArrayList<DrawCommand>(256)
    private val adapter = CommandCanvasAdapter(buffer)
    private val animHelper = LuaAnimHelper()
    private val executor = DrawCommandExecutor()
    private val stateTable = LuaTable()
    private val envTable = LuaTable()
    private val spectrumTable = LuaTable()
    private var lastSpectrumLog = 0L

    private var luaOnDrawFunction: LuaValue = LuaValue.NIL

    init {
        globals.set("canvas", CoerceJavaToLua.coerce(adapter))
        globals.set("anim", CoerceJavaToLua.coerce(animHelper))
        LuaEnv.injectTools(globals, context)
    }

    /** 加载脚本并定位 onDraw (脚本模式入口) */
    fun loadScript(scriptContent: String) {
        try {
            globals.load(scriptContent).call()
            luaOnDrawFunction = globals.get("onDraw")
            Log.d("LuaScriptEngine", "onDraw loaded: ${!luaOnDrawFunction.isnil()}")
        } catch (e: Exception) {
            Log.e("LuaScriptEngine", "Script load failed", e)
            luaOnDrawFunction = LuaValue.NIL
        }
    }

    /** 注入数据 (歌词表等, 只在变化时调用) */
    fun evaluate(scriptCode: String) {
        try {
            globals.load(scriptCode).call()
        } catch (e: Exception) {
            Log.e("LuaScriptEngine", "Evaluate failed", e)
        }
    }

    /** 歌词转 Lua 表 (切歌时构建一次) */
    fun convertLyricsToLuaTable(lyrics: List<LyricLine>): String {
        val sb = StringBuilder(lyrics.size * 100)
        sb.append("global_lyrics = {\n")
        lyrics.forEachIndexed { index, line ->
            sb.append("  [${index + 1}] = {\n")
            sb.append("    startTime = ${line.startTime},\n")
            sb.append("    endTime = ${line.endTime},\n")
            sb.append("    text = [[${line.text}]],\n")
            if (line.words.isNotEmpty()) {
                sb.append("    words = {\n")
                line.words.forEachIndexed { wIdx, word ->
                    sb.append("      [${wIdx + 1}] = { text = [[${word.text}]], st = ${word.startTime}, et = ${word.endTime} },\n")
                }
                sb.append("    }\n")
            }
            sb.append("  },\n")
        }
        sb.append("}\n")
        return sb.toString()
    }

    // ==================== 每帧执行 ====================

    fun executeDraw(
        canvas: Canvas,
        uiState: PlayerUiState,
        positionMs: Long,
        width: Float,
        height: Float,
        systemTimeMs: Long,
        animValue: Float
    ) {
        if (luaOnDrawFunction.isnil()) return

        buffer.clear()

        // --- 状态注入 (table set, 支持 state.xxx 属性语法) ---
        val totalSeconds = parseTimeToSeconds(uiState.totalTime)
        val totalMs = totalSeconds * 1000L

        stateTable.set("title", LuaValue.valueOf(uiState.songTitle))
        stateTable.set("artist", LuaValue.valueOf(uiState.artistName))
        stateTable.set("currentTime", LuaValue.valueOf(uiState.currentTime))
        stateTable.set("totalTime", LuaValue.valueOf(uiState.totalTime))
        stateTable.set("isPlaying", LuaValue.valueOf(uiState.isPlaying))
        stateTable.set("position", LuaValue.valueOf(positionMs.toDouble()))
        stateTable.set("duration", LuaValue.valueOf(totalMs.toDouble()))
        stateTable.set("progress", LuaValue.valueOf(uiState.progress.toDouble()))
        stateTable.set("volume", LuaValue.valueOf(uiState.volume.toDouble()))
        stateTable.set("playMode", LuaValue.valueOf(uiState.playMode.name))
        stateTable.set("isLiked", LuaValue.valueOf(uiState.isCurrentSongLiked))
        stateTable.set("likedCount", LuaValue.valueOf(uiState.currentSongLikedCount))
        stateTable.set("currentLyricIndex", LuaValue.valueOf(uiState.currentLyricIndex))
        stateTable.set("currentWordIndex", LuaValue.valueOf(uiState.currentWordIndex))

        val line = uiState.currentLyricLine
        stateTable.set("lyric", LuaValue.valueOf(line?.text ?: ""))
        stateTable.set("lineStart", LuaValue.valueOf(line?.startTime?.toDouble() ?: 0.0))
        stateTable.set("lineEnd", LuaValue.valueOf(line?.endTime?.toDouble() ?: 0.0))

        var lineProg = 0.0
        if (line != null) {
            val duration = line.endTime - line.startTime
            if (duration > 0) {
                val elapsed = positionMs - line.startTime
                lineProg = (elapsed.toDouble() / duration).coerceIn(0.0, 1.0)
            }
        }
        stateTable.set("lineProgress", LuaValue.valueOf(lineProg))

        // --- 音频频谱注入 (设置里开启可视化后才有非零值) ---
        stateTable.set("spectrum", spectrumTable)
        stateTable.set("hasSpectrum", LuaValue.valueOf(SpectrumTap.enabled))
        stateTable.set("bass", LuaValue.valueOf(SpectrumTap.bass.toDouble()))
        stateTable.set("mid", LuaValue.valueOf(SpectrumTap.mid.toDouble()))
        stateTable.set("treble", LuaValue.valueOf(SpectrumTap.treble.toDouble()))
        stateTable.set("level", LuaValue.valueOf(SpectrumTap.level.toDouble()))
        val bars = SpectrumTap.bars
        for (i in 1..SpectrumTap.BAR_COUNT) {
            spectrumTable.set(i, LuaValue.valueOf(bars[i - 1].toDouble()))
        }
        if (SpectrumTap.enabled) {
            val now = System.currentTimeMillis()
            if (now - lastSpectrumLog >= 2000L) {
                lastSpectrumLog = now
                Log.d(
                    "LuaScriptEngine",
                    "spectrum bass=%.2f mid=%.2f treble=%.2f level=%.2f"
                        .format(SpectrumTap.bass, SpectrumTap.mid, SpectrumTap.treble, SpectrumTap.level),
                )
            }
        }

        // --- 环境注入 ---
        envTable.set("time", LuaValue.valueOf(System.currentTimeMillis().toDouble()))
        envTable.set("systemTime", LuaValue.valueOf(systemTimeMs.toDouble()))
        envTable.set("width", LuaValue.valueOf(width.toDouble()))
        envTable.set("height", LuaValue.valueOf(height.toDouble()))
        envTable.set("isPortrait", if (height > width) LuaValue.TRUE else LuaValue.FALSE)
        envTable.set("anim", LuaValue.valueOf(animValue.toDouble()))

        // --- 执行 (命令缓冲) + 原生回放 ---
        try {
            luaOnDrawFunction.call(stateTable, envTable)
            executor.execute(buffer, canvas)
        } catch (e: Exception) {
            Log.e("LuaScriptEngine", "onDraw failed", e)
        }
    }

    private fun parseTimeToSeconds(timeStr: String): Int {
        val parts = timeStr.split(":")
        if (parts.size != 2) return 0
        return try {
            parts[0].toInt() * 60 + parts[1].toInt()
        } catch (e: Exception) {
            0
        }
    }
}
