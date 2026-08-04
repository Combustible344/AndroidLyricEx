package com.example.pra

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.FileObserver
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.pra.ui.components.IdleScreenSaver
import com.example.pra.ui.components.ScreenSaverConfig
import com.example.pra.ui.components.ScreenSaverRenderer
import com.example.pra.ui.scene.LuaScriptEngine
import com.example.pra.ui.scene.LuaScriptRenderer
import com.example.pra.ui.scene.LyricCanvasRenderer
import com.example.pra.ui.scene.LyricSceneRenderer
import com.example.pra.ui.scene.SceneBuilder
import com.example.pra.ui.screens.PlayerScreen
import com.example.pra.ui.theme.PraTheme
import com.example.pra.ui.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

data class ScreenSaverAnimState(
    val timeString: String
)

class MainActivity : ComponentActivity() {

    private val viewModel: MusicPlayerViewModel by viewModels()

    // 双模式渲染: 声明式场景 (原生) / Lua 脚本 (命令缓冲)
    private val sceneBuilder = SceneBuilder()
    private val sceneRenderer = LyricSceneRenderer(this)
    private val scriptEngine = LuaScriptEngine(this)
    private val scriptRenderer = LuaScriptRenderer(scriptEngine)
    private var activeRenderer: LyricCanvasRenderer by mutableStateOf(sceneRenderer)
    private lateinit var sceneFile: File
    private var sceneObserver: FileObserver? = null
    private val sceneWatchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            Toast.makeText(this, "权限已获取", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "部分权限未获取，可能影响频谱或读写", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadSceneStrategy()

        enableEdgeToEdge()
        hideSystemBars()
        checkAndRequestPermissions()

        setContent {
            PraTheme {
                var interactionSignal by remember { mutableLongStateOf(System.currentTimeMillis()) }
                val uiState by viewModel.uiState.collectAsState()

                // 1. 系统时间更新 (1秒一次)
                var timeString by remember { mutableStateOf("") }
                LaunchedEffect(Unit) {
                    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                    while (true) {
                        timeString = formatter.format(Date())
                        delay(1.seconds)
                    }
                }

                var isPlaylistOpen by remember { mutableStateOf(false) }
                var overlayVisible by remember { mutableStateOf(false) }

                // 2. 进度校准: uiState 每次更新时记录同步点,
                //    帧内由 frameNanos 推演, 获得 60fps 平滑的播放位置
                var syncPosMs by remember { mutableLongStateOf(0L) }
                var syncAtNs by remember { mutableLongStateOf(0L) }
                LaunchedEffect(uiState) {
                    syncPosMs = (uiState.progress * uiState.durationMs).toLong()
                    syncAtNs = System.nanoTime()
                }

                // 3. 切歌淡入动画信号 (供 Lua 脚本模式的 env.anim 使用)
                val alphaAnim = remember { Animatable(0f) }
                LaunchedEffect(uiState.currentLyricLine) {
                    alphaAnim.snapTo(0f)
                    alphaAnim.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
                }
                scriptRenderer.animValue = alphaAnim.value

                // 4. 歌词注入 (仅脚本模式): 切歌时构建一次 global_lyrics
                LaunchedEffect(uiState.lyrics, activeRenderer) {
                    if (activeRenderer === scriptRenderer) {
                        withContext(Dispatchers.IO) {
                            val luaCode = if (uiState.lyrics.isNotEmpty()) {
                                scriptEngine.convertLyricsToLuaTable(uiState.lyrics)
                            } else {
                                "global_lyrics = {}"
                            }
                            withContext(Dispatchers.Main) { scriptEngine.evaluate(luaCode) }
                        }
                    }
                }

                // 5. 帧率自适应: 播放中或占位呼吸(无歌词)时需要连续动画 → 60fps;
                //    暂停且有歌词时画面静止 → 只画一帧, 帧循环休眠省电
                val frameRateActive = uiState.isPlaying || uiState.currentLyricLine == null

                IdleScreenSaver(
                    data = ScreenSaverAnimState(timeString),
                    config = ScreenSaverConfig(backgroundColor = Color.Black),
                    externalInteractionSignal = interactionSignal,
                    isEnabled = !isPlaylistOpen,
                    onOverlayVisibilityChange = { overlayVisible = it },
                    frameRateActive = frameRateActive,
                    renderer = ScreenSaverRenderer { scope, env, _, frameNanos ->
                        // 组合期收集的最新状态 + 帧内推演, 只失效本 Canvas 的绘制
                        val durationMs = uiState.durationMs.coerceAtLeast(1L)
                        val positionMs = if (uiState.isPlaying) {
                            (syncPosMs + (frameNanos - syncAtNs) / 1_000_000L).coerceIn(0L, durationMs)
                        } else {
                            syncPosMs
                        }
                        scope.drawIntoCanvas { canvas ->
                            activeRenderer.render(
                                canvas = canvas.nativeCanvas,
                                uiState = uiState,
                                positionMs = positionMs,
                                width = env.size.width,
                                height = env.size.height,
                                systemTimeMs = frameNanos / 1_000_000L
                            )
                        }
                    }
                ) {
                    PlayerScreen(
                        onUserActivity = { interactionSignal = System.currentTimeMillis() },
                        onPlaylistVisibilityChange = { isOpen ->
                            isPlaylistOpen = isOpen
                            // sheet 拖拽可能触发瞬时系统栏, 立即收回, 保持沉浸式
                            hideSystemBars()
                        },
                        backgroundActive = !overlayVisible
                    )
                }
            }
        }
    }

    // ==================== 场景加载 ====================

    private fun loadSceneStrategy() {
        val scriptDir = File(getExternalFilesDir(null), "lua_scripts")
        if (!scriptDir.exists()) scriptDir.mkdirs()
        sceneFile = File(scriptDir, "scene.lua")

        // 旧 draw.lua 一次性备份迁移 (旧格式不再兼容)
        val oldFile = File(scriptDir, "draw.lua")
        if (oldFile.exists() && !File(scriptDir, "draw.lua.bak").exists()) {
            try {
                oldFile.renameTo(File(scriptDir, "draw.lua.bak"))
            } catch (_: Exception) {
            }
        }

        if (!sceneFile.exists()) {
            try {
                sceneFile.writeText(readDefaultSceneScript())
            } catch (_: Exception) {
            }
        }

        // 首次运行写入场景开发指南 (scene_guide.md), 指导场景文件开发
        val guideFile = File(scriptDir, "scene_guide.md")
        if (!guideFile.exists()) {
            try {
                guideFile.writeText(readRawText(R.raw.scene_guide))
            } catch (_: Exception) {
            }
        }

        loadScene()

        // 文件变化监听: FileObserver 事件驱动, 只有文件被修改 (CLOSE_WRITE) 才回调,
        // 替代原先每 3 秒轮询 mtime 的方式
        startSceneWatch()
    }

    /** 监听 scene.lua 的写入事件, 修改即重载 */
    private fun startSceneWatch() {
        stopSceneWatch()
        val dir = sceneFile.parentFile ?: return
        sceneObserver = object : FileObserver(dir.absolutePath, FileObserver.CLOSE_WRITE) {
            override fun onEvent(event: Int, path: String?) {
                if (path == sceneFile.name) {
                    loadScene()
                }
            }
        }.also { it.startWatching() }
    }

    private fun stopSceneWatch() {
        sceneObserver?.stopWatching()
        sceneObserver = null
    }

    /** 读取内置默认场景脚本 (res/raw/scene_default.lua) */
    private fun readDefaultSceneScript(): String = readRawText(R.raw.scene_default)

    /** 读取内置 raw 资源文本 */
    private fun readRawText(rawId: Int): String {
        return try {
            resources.openRawResource(rawId).bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            ""
        }
    }

    /** 场景构建在 IO 线程 (LuaJ 解析 + 字体文件 IO), 完成后再切回主线程更新渲染器 */
    private fun loadScene() {
        sceneWatchScope.launch {
            val defaultScript = readDefaultSceneScript()
            var usedFallback = false
            val result = try {
                sceneBuilder.build(sceneFile.readText())
            } catch (e: Exception) {
                // 脚本错误 → 记录原因 + 回退默认场景
                writeSceneErrorLog(e.message ?: e.toString())
                usedFallback = true
                sceneBuilder.build(defaultScript)
            }
            withContext(Dispatchers.Main) {
                if (result.hasOnDraw) {
                    // Lua 脚本模式: 每帧执行 onDraw (命令缓冲)
                    scriptEngine.loadScript(sceneFile.readText())
                    activeRenderer = scriptRenderer
                } else {
                    // 声明式场景模式: 原生渲染
                    sceneRenderer.setScene(result.scene)
                    activeRenderer = sceneRenderer
                }
                Toast.makeText(
                    this@MainActivity,
                    when {
                        usedFallback -> "场景脚本错误，已回退默认"
                        result.hasOnDraw -> "已加载脚本模式场景"
                        else -> "场景已加载"
                    },
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /** 脚本错误写入 scene_error.log, 与 scene.lua 同目录 */
    private fun writeSceneErrorLog(message: String) {
        try {
            val errorFile = File(sceneFile.parentFile, "scene_error.log")
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            errorFile.writeText("[$timestamp] $message\n")
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        stopSceneWatch()
        sceneWatchScope.cancel()
        super.onDestroy()
    }

    // ==================== 权限 ====================

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest)
        }
    }

    private fun hideSystemBars() {
        val wic = WindowCompat.getInsetsController(window, window.decorView)
        wic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        wic.hide(WindowInsetsCompat.Type.systemBars())
    }
}
