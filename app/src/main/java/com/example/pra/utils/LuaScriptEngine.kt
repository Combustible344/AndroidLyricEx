package com.example.pra.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import com.example.pra.data.model.LyricLine
import com.example.pra.ui.state.PlayerUiState
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class LuaScriptEngine(private val context: Context) {

    private val globals: Globals = JsePlatform.standardGlobals()
    private val adapter = LuaCanvasAdapter()
    // ★★★ 新增: 动画助手 ★★★
    private val animHelper = LuaAnimHelper()

    private var luaOnDrawFunction: LuaValue = LuaValue.NIL
    private val stateTable = LuaTable()
    private val envTable = LuaTable()
    private val fontCache = HashMap<String, Typeface>()
    private val scriptDir = File(context.getExternalFilesDir(null), "lua_scripts")

    private var lastLoggedErrorMessage: String = ""
    private val errorPaint = Paint().apply {
        color = Color.RED
        textSize = 50f
        isAntiAlias = true
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
    }

    init {
        // 1. 注入绘图能力
        globals.set("canvas", CoerceJavaToLua.coerce(adapter))

        // 2. ★★★ 注入动画计算能力 ★★★
        globals.set("anim", CoerceJavaToLua.coerce(animHelper))

        // 3. 注入工具类
        val tools = LuaTable()
        tools.set("toChars", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                val str = arg.checkjstring() ?: return LuaTable()
                val table = LuaTable()
                var index = 1
                var offset = 0
                val length = str.length
                while (offset < length) {
                    val codePoint = str.codePointAt(offset)
                    val charCount = Character.charCount(codePoint)
                    table.set(index++, LuaValue.valueOf(str.substring(offset, offset + charCount)))
                    offset += charCount
                }
                return table
            }
        })

        tools.set("loadFont", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                val path = arg.checkjstring() ?: return LuaValue.NIL
                if (fontCache.containsKey(path)) return CoerceJavaToLua.coerce(fontCache[path])
                var typeface: Typeface? = null
                try {
                    typeface = Typeface.createFromAsset(context.assets, path)
                } catch (e: Exception) {
                    try {
                        val file = File(path)
                        if (file.exists()) typeface = Typeface.createFromFile(file)
                    } catch (e2: Exception) {
                        Log.e("LuaScriptEngine", "Failed to load font: $path")
                    }
                }
                return if (typeface != null) {
                    fontCache[path] = typeface
                    CoerceJavaToLua.coerce(typeface)
                } else LuaValue.NIL
            }
        })

        globals.set("tools", tools)
        globals.set("toChars", tools.get("toChars"))
        globals.set("loadFont", tools.get("loadFont"))
    }

    fun loadScript(scriptContent: String) {
        try {
            val chunk = globals.load(scriptContent)
            chunk.call()
            luaOnDrawFunction = globals.get("onDraw")
            lastLoggedErrorMessage = ""
        } catch (e: Exception) { handleError(e) }
    }

    fun evaluate(scriptCode: String) {
        try {
            globals.load(scriptCode).call()
        } catch (e: Exception) { handleError(e) }
    }

    // ★★★ 核心：每帧绘制与状态注入 ★★★
    fun executeDraw(
        canvas: Canvas,
        uiState: PlayerUiState,
        timeString: String,
        width: Float,
        height: Float,
        animValue: Float
    ) {
        adapter.attachCanvas(canvas)

        if (luaOnDrawFunction.isnil()) {
            canvas.drawText("No Script Loaded", width / 2, height / 2, errorPaint)
            return
        }

        try {
            // --- 1. 数据预计算 ---
            val totalSeconds = parseTimeToSeconds(uiState.totalTime)
            val totalMs = totalSeconds * 1000L
            val currentPosMs = (totalMs * uiState.progress).toLong()

            // --- 2. 注入全面状态 (State Table) ---

            // 基础信息
            stateTable.set("title", LuaValue.valueOf(uiState.songTitle))
            stateTable.set("artist", LuaValue.valueOf(uiState.artistName))
            stateTable.set("currentTime", LuaValue.valueOf(uiState.currentTime)) // 字符串 "03:20"
            stateTable.set("totalTime", LuaValue.valueOf(uiState.totalTime))     // 字符串 "05:00"

            // 数值状态 (方便计算)
            stateTable.set("isPlaying", LuaValue.valueOf(uiState.isPlaying))
            stateTable.set("position", LuaValue.valueOf(currentPosMs.toDouble())) // 毫秒
            stateTable.set("duration", LuaValue.valueOf(totalMs.toDouble()))       // 毫秒
            stateTable.set("progress", LuaValue.valueOf(uiState.progress.toDouble())) // 0.0 ~ 1.0
            stateTable.set("volume", LuaValue.valueOf(uiState.volume.toDouble()))     // 0.0 ~ 1.0

            // 播放模式与互动
            stateTable.set("playMode", LuaValue.valueOf(uiState.playMode.name)) // "LoopOne" or "Shuffle"
            stateTable.set("isLiked", LuaValue.valueOf(uiState.isCurrentSongLiked))
            stateTable.set("likedCount", LuaValue.valueOf(uiState.currentSongLikedCount))

            // 歌词定位状态
            // Kotlin 0 起始，Lua 1 起始习惯，这里不转换，让 Lua 自己 +1 比较灵活
            stateTable.set("currentLyricIndex", LuaValue.valueOf(uiState.currentLyricIndex))
            stateTable.set("currentWordIndex", LuaValue.valueOf(uiState.currentWordIndex))

            // 当前行快捷方式 (方便简单脚本直接用，不用去查表)
            val line = uiState.currentLyricLine
            stateTable.set("lyric", LuaValue.valueOf(line?.text ?: ""))
            stateTable.set("lineStart", LuaValue.valueOf(line?.startTime?.toDouble() ?: 0.0))
            stateTable.set("lineEnd", LuaValue.valueOf(line?.endTime?.toDouble() ?: 0.0))

            // ★★★ 新增：当前行进度 (Line Progress) ★★★
            // 0.0 ~ 1.0，表示当前这句歌词唱了多少。做 KTV 抹擦效果神器
            var lineProg = 0.0
            if (line != null) {
                val duration = line.endTime - line.startTime
                if (duration > 0) {
                    val elapsed = currentPosMs - line.startTime
                    lineProg = (elapsed.toDouble() / duration).coerceIn(0.0, 1.0)
                }
            }
            stateTable.set("lineProgress", LuaValue.valueOf(lineProg))

            // --- 3. 注入环境参数 (Env Table) ---
            envTable.set("time", LuaValue.valueOf(timeString))
            envTable.set("systemTime", LuaValue.valueOf(System.currentTimeMillis().toDouble())) // 系统绝对时间
            envTable.set("width", LuaValue.valueOf(width.toDouble()))
            envTable.set("height", LuaValue.valueOf(height.toDouble()))
            envTable.set("isPortrait", LuaValue.valueOf(height > width))
            envTable.set("anim", LuaValue.valueOf(animValue.toDouble())) // 切歌动画值

            // --- 4. 执行 ---
            luaOnDrawFunction.call(stateTable, envTable)

        } catch (e: Exception) {
            handleError(e)
            canvas.save()
            adapter.setTypeface(null)
            adapter.setTextSize(50f)
            canvas.drawText("Script Error (See Log)", width / 2, height / 2, errorPaint)
            canvas.restore()
        }
    }

    // 错误处理与日志 (保持不变)
    private fun handleError(e: Exception) {
        val currentMsg = e.message ?: e.toString()
        if (currentMsg != lastLoggedErrorMessage) {
            logErrorToFile(e)
            lastLoggedErrorMessage = currentMsg
        }
    }

    private fun logErrorToFile(e: Exception) {
        try {
            if (!scriptDir.exists()) scriptDir.mkdirs()
            val logFile = File(scriptDir, "err.log")
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            e.printStackTrace(pw)
            val logContent = "== [Time]: $timestamp ==\n[Error]: ${e.message}\n[Stack]:\n$sw\n"
            FileWriter(logFile, true).use { it.write(logContent) }
            Log.e("LuaScriptEngine", "New error logged to ${logFile.absolutePath}")
        } catch (ioe: Exception) { }
    }

    private fun parseTimeToSeconds(timeStr: String): Int {
        val parts = timeStr.split(":")
        if (parts.size != 2) return 0
        return try { parts[0].toInt() * 60 + parts[1].toInt() } catch (e: Exception) { 0 }
    }

    // 歌词转 Lua 表 (保持不变)
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
}
