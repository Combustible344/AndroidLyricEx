package com.example.pra.ui.scene

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import java.io.File

/**
 * Lua 全局环境共享注入: tools (toChars / loadFont) 等构建期即需要的工具。
 * SceneBuilder (模式检测) 与 LuaScriptEngine (脚本模式执行) 共用, 保证
 * 脚本模式脚本 (顶层引用 tools) 在两种引擎下都能被解析。
 */
object LuaEnv {

    fun injectTools(globals: Globals, context: Context?) {
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
                    val ctx = context
                    if (ctx != null) typeface = Typeface.createFromAsset(ctx.assets, path)
                } catch (e: Exception) {
                    try {
                        val file = File(path)
                        if (file.exists()) typeface = Typeface.createFromFile(file)
                    } catch (e2: Exception) {
                        Log.e("LuaEnv", "Failed to load font: $path")
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

    private val fontCache = HashMap<String, Typeface>()
}
