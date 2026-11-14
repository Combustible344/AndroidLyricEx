package com.example.pra.data.scene

import android.content.Context
import java.io.File

/**
 * 多场景管理。
 *
 * 每个场景是 `lua_scripts/scenes/<名字>.lua`，当前使用的场景名记录在
 * SharedPreferences 里；旧的单文件 `lua_scripts/scene.lua` 会在首次初始化时迁移成「默认」场景。
 */
object SceneRepository {

    private const val PREFS = "scene_prefs"
    private const val KEY_ACTIVE = "active_scene"
    const val DEFAULT_SCENE = "默认"
    const val LANDSCAPE_SCENE = "横屏"

    /** 横屏版: 复制默认脚本, 打开 rotation 头并标注旋转角 */
    fun landscapeContent(defaultContent: String): String {
        val rotated = defaultContent.replace("rotation = 0,", "rotation = 90,")
        return "-- rotation: 90\n" + rotated
    }

    fun scenesDir(context: Context): File =
        File(context.getExternalFilesDir(null), "lua_scripts/scenes").apply { mkdirs() }

    fun names(context: Context): List<String> =
        scenesDir(context)
            .listFiles { f -> f.isFile && f.name.endsWith(".lua") }
            ?.map { it.nameWithoutExtension }
            ?.sorted()
            ?: emptyList()

    fun file(context: Context, name: String): File = File(scenesDir(context), "$name.lua")

    fun activeName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACTIVE, null) ?: DEFAULT_SCENE
    }

    fun activeFile(context: Context): File = file(context, activeName(context))

    fun setActive(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ACTIVE, name).apply()
    }

    /** 首次使用: 把旧 scene.lua 或内置默认写成「默认」场景, 并保证「横屏」场景存在 */
    fun ensureScenes(context: Context, defaultContent: String) {
        val existing = names(context)
        if (existing.isEmpty()) {
            val legacy = File(context.getExternalFilesDir(null), "lua_scripts/scene.lua")
            val content = runCatching {
                if (legacy.exists()) legacy.readText().ifBlank { defaultContent } else defaultContent
            }.getOrDefault(defaultContent)
            runCatching { file(context, DEFAULT_SCENE).writeText(content) }
        } else if (!existing.contains(DEFAULT_SCENE)) {
            runCatching { file(context, DEFAULT_SCENE).writeText(defaultContent) }
        }
        // 内置横屏场景: 内容旋转 90°, 设备仍保持竖屏
        if (!names(context).contains(LANDSCAPE_SCENE)) {
            runCatching {
                file(context, LANDSCAPE_SCENE).writeText(landscapeContent(defaultContent))
            }
        }
        if (activeName(context) !in names(context)) {
            setActive(context, DEFAULT_SCENE)
        }
    }

    fun create(context: Context, name: String, content: String): Boolean {
        val safe = sanitize(name)
        if (safe.isBlank()) return false
        val target = file(context, safe)
        if (target.exists()) return false
        return runCatching {
            target.parentFile?.mkdirs()
            target.writeText(content)
            true
        }.getOrDefault(false)
    }

    fun delete(context: Context, name: String): Boolean {
        if (name == DEFAULT_SCENE) return false
        val removed = runCatching { file(context, name).delete() }.getOrDefault(false)
        if (removed && activeName(context) == name) {
            setActive(context, DEFAULT_SCENE)
        }
        return removed
    }

    /** 只保留中英文、数字、下划线、短横线和空格, 避免路径问题 */
    fun sanitize(name: String): String =
        name.trim().replace(Regex("[^\\p{L}\\p{N}_\\- ]"), "").take(24)
}
