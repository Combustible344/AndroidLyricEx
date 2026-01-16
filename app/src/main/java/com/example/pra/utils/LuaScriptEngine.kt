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

class LuaScriptEngine(private val context: Context) {

    private val globals: Globals = JsePlatform.standardGlobals()
    private val adapter = LuaCanvasAdapter()
    private var luaOnDrawFunction: LuaValue = LuaValue.NIL

    private val stateTable = LuaTable()
    private val envTable = LuaTable()
    private val fontCache = HashMap<String, Typeface>()

    // 脚本目录
    private val scriptDir = File(context.getExternalFilesDir(null), "lua_scripts")

    // ★★★ 核心修改：错误去重变量 ★★★
    // 记录上一次写入日志的错误信息，只有内容变了才写新的
    private var lastLoggedErrorMessage: String = ""

    private val errorPaint = Paint().apply {
        color = Color.RED
        textSize = 50f
        isAntiAlias = true
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
    }

    init {
        globals.set("canvas", CoerceJavaToLua.coerce(adapter))
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
                if (fontCache.containsKey(path)) {
                    return CoerceJavaToLua.coerce(fontCache[path])
                }
                var typeface: Typeface? = null
                try {
                    typeface = Typeface.createFromAsset(context.assets, path)
                } catch (e: Exception) {
                    try {
                        val file = File(path)
                        if (file.exists()) {
                            typeface = Typeface.createFromFile(file)
                        }
                    } catch (e2: Exception) {
                        Log.e("LuaScriptEngine", "Failed to load font: $path")
                    }
                }
                return if (typeface != null) {
                    fontCache[path] = typeface
                    CoerceJavaToLua.coerce(typeface)
                } else {
                    LuaValue.NIL
                }
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

            // ★★★ 重置错误状态 ★★★
            // 每次重新加载脚本时，清除历史错误记录，这样新出现的错误能被记录
            lastLoggedErrorMessage = ""

        } catch (e: Exception) {
            handleError(e)
        }
    }

    fun evaluate(scriptCode: String) {
        try {
            globals.load(scriptCode).call()
        } catch (e: Exception) {
            handleError(e) // 注入代码出错也记录
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

        if (luaOnDrawFunction.isnil()) {
            canvas.drawText("No Script Loaded", width / 2, height / 2, errorPaint)
            return
        }

        try {
            val totalSeconds = parseTimeToSeconds(uiState.totalTime)
            val totalMs = totalSeconds * 1000L
            val currentPosMs = (totalMs * uiState.progress).toLong()

            stateTable.set("title", LuaValue.valueOf(uiState.songTitle))
            stateTable.set("artist", LuaValue.valueOf(uiState.artistName))
            stateTable.set("isPlaying", LuaValue.valueOf(uiState.isPlaying))
            stateTable.set("progress", LuaValue.valueOf(uiState.progress.toDouble()))
            stateTable.set("currentTime", LuaValue.valueOf(uiState.currentTime))
            stateTable.set("totalTime", LuaValue.valueOf(uiState.totalTime))
            stateTable.set("isLiked", LuaValue.valueOf(uiState.isCurrentSongLiked))
            stateTable.set("currentLyricIndex", LuaValue.valueOf(uiState.currentLyricIndex))
            stateTable.set("currentWordIndex", LuaValue.valueOf(uiState.currentWordIndex))

            val line = uiState.currentLyricLine
            stateTable.set("lyric", LuaValue.valueOf(line?.text ?: ""))
            stateTable.set("position", LuaValue.valueOf(currentPosMs.toDouble()))
            stateTable.set("lineStart", LuaValue.valueOf(line?.startTime?.toDouble() ?: 0.0))
            stateTable.set("lineEnd", LuaValue.valueOf(line?.endTime?.toDouble() ?: 0.0))

            envTable.set("time", LuaValue.valueOf(timeString))
            envTable.set("width", LuaValue.valueOf(width.toDouble()))
            envTable.set("height", LuaValue.valueOf(height.toDouble()))
            envTable.set("anim", LuaValue.valueOf(animValue.toDouble()))
            envTable.set("isPortrait", LuaValue.valueOf(height > width))

            luaOnDrawFunction.call(stateTable, envTable)

        } catch (e: Exception) {
            // ★★★ 绘制时的错误处理 ★★★
            // 1. 记录日志 (带去重)
            handleError(e)

            // 2. 屏幕显示简化提示
            canvas.save()
            adapter.setTypeface(null)
            adapter.setTextSize(50f)
            // 在屏幕正中央画红字
            canvas.drawText("脚本出错请查看日志 (Script Error)", width / 2, height / 2, errorPaint)
            canvas.restore()
        }
    }

    // ★★★ 统一的错误处理与文件写入逻辑 ★★★
    private fun handleError(e: Exception) {
        val currentMsg = e.message ?: e.toString()

        // 核心逻辑：如果这次的错误信息和上一次记录的一模一样，就直接忽略
        // 只有不一样的错误才写入文件
        if (currentMsg != lastLoggedErrorMessage) {
            logErrorToFile(e)
            lastLoggedErrorMessage = currentMsg
        }
    }

    private fun logErrorToFile(e: Exception) {
        try {
            if (!scriptDir.exists()) scriptDir.mkdirs()
            val logFile = File(scriptDir, "err.log")

            val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val timestamp = timeFormat.format(Date())

            val sw = StringWriter()
            val pw = PrintWriter(sw)
            e.printStackTrace(pw)
            val stackTrace = sw.toString()

            val logContent = """
                ==================================================
                [Time]: $timestamp
                [Error]: ${e.message}
                [Stack Trace]:
                $stackTrace
                
            """.trimIndent()

            // true 表示追加模式 (append)
            FileWriter(logFile, true).use { writer ->
                writer.write(logContent)
            }

            Log.e("LuaScriptEngine", "New error logged to ${logFile.absolutePath}")

        } catch (ioe: Exception) {
            Log.e("LuaScriptEngine", "Failed to write error log", ioe)
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
