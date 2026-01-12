package com.example.pra

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.pra.ui.screens.PlayerScreen
import com.example.pra.ui.theme.PraTheme
import com.example.pra.ui.viewmodel.MusicPlayerViewModel
import com.example.pra.utils.LuaScriptEngine
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ScreenSaverAnimState(
    val timeString: String,
    val animAlpha: Float
)

class MainActivity : ComponentActivity() {

    private val viewModel: MusicPlayerViewModel by viewModels()
    private lateinit var luaEngine: LuaScriptEngine

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /*...*/ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        luaEngine = LuaScriptEngine(this)

        // ★★★ 核心修改：加载脚本策略 ★★★
        loadScriptStrategy()

        enableEdgeToEdge()
        hideSystemBars()
        checkAndRequestPermissions()

        setContent {
            PraTheme {
                val uiState by viewModel.uiState.collectAsState()

                // ★★★ 核心修改：定义交互信号状态 ★★★
                // 这个状态用于连接 PlayerScreen (包括 BottomSheet) 和 IdleScreenSaver
                var interactionSignal by remember { mutableLongStateOf(System.currentTimeMillis()) }

                // 时间更新逻辑
                var timeString by remember { mutableStateOf("") }
                LaunchedEffect(Unit) {
                    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                    while (true) {
                        timeString = formatter.format(Date())
                        delay(1000)
                    }
                }
                var isPlaylistOpen by remember { mutableStateOf(false) }
                // 歌词动画逻辑
                val alphaAnim = remember { Animatable(0f) }
                LaunchedEffect(uiState.currentLyricLine) {
                    alphaAnim.snapTo(0f)
                    alphaAnim.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
                }

                val animState = ScreenSaverAnimState(timeString, alphaAnim.value)

                IdleScreenSaver(
                    data = animState,
                    config = ScreenSaverConfig(backgroundColor = Color.Black),
                    // ★★★ 核心修改：传入信号给屏保组件，让它知道何时重置计时器 ★★★
                    externalInteractionSignal = interactionSignal,
                    isEnabled = !isPlaylistOpen,
                    renderer = ScreenSaverRenderer { scope, env, animData ->
                        scope.drawIntoCanvas { canvas ->
                            luaEngine.executeDraw(
                                canvas = canvas.nativeCanvas,
                                uiState = uiState,
                                timeString = animData.timeString,
                                width = env.size.width,
                                height = env.size.height,
                                animValue = animData.animAlpha
                            )
                        }
                    }
                ) {
                    // ★★★ 核心修改：监听播放器界面的用户活动 ★★★
                    PlayerScreen(
                        onUserActivity = {
                            // 当用户在 PlayerScreen 或 PlaylistBottomSheet 操作时，更新这个信号
                            interactionSignal = System.currentTimeMillis()
                        },
                        onPlaylistVisibilityChange = { isOpen ->
                            isPlaylistOpen = isOpen
                        }
                    )
                }
            }
        }
    }

    /**
     * ★★★ 脚本加载策略 ★★★
     * 1. 检查 /Android/data/包名/files/lua_scripts/draw.lua 是否存在
     * 2. 存在 -> 读取外部文件
     * 3. 不存在 -> 使用内置默认代码，并将其写入文件供用户参考
     */
    private fun loadScriptStrategy() {
        // 1. 获取外部存储的私有目录 (不需要额外的读写权限)
        // 路径通常是: /storage/emulated/0/Android/data/com.example.pra/files/lua_scripts
        val scriptDir = File(getExternalFilesDir(null), "lua_scripts")
        if (!scriptDir.exists()) {
            scriptDir.mkdirs()
        }

        val scriptFile = File(scriptDir, "draw.lua")

        if (scriptFile.exists()) {
            // --- 情况 A: 找到了用户自定义脚本 ---
            try {
                val userScript = scriptFile.readText()
                luaEngine.loadScript(userScript)
                Toast.makeText(this, "已加载外部 Lua 脚本", Toast.LENGTH_SHORT).show()
                Log.d("LuaLoader", "Loaded external script from: ${scriptFile.absolutePath}")
            } catch (e: Exception) {
                Toast.makeText(this, "外部脚本读取失败，使用默认", Toast.LENGTH_LONG).show()
                luaEngine.loadScript(DEFAULT_LUA_SCRIPT)
            }
        } else {
            // --- 情况 B: 没找到，使用内置脚本 ---
            // 将内置脚本写入文件，作为模板供用户修改
            try {
                scriptFile.writeText(DEFAULT_LUA_SCRIPT)
                Toast.makeText(this, "已生成默认脚本模板", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("LuaLoader", "Failed to write template", e)
            }

            // 加载内置
            luaEngine.loadScript(DEFAULT_LUA_SCRIPT)
            Log.d("LuaLoader", "Loaded built-in default script")
        }
    }

    // 为了代码整洁，把长字符串放在这里
    companion object {
        private val DEFAULT_LUA_SCRIPT = """
            -- [[ 默认内置的高级绘制脚本 ]] --
            
            -- 辅助函数：绘制居中文字
            function drawCenteredText(text, y, size, color, alpha)
                canvas:setTextSize(size)
                canvas:setColor(color)
                canvas:setAlpha(alpha)
                canvas:setTextAlign(1) -- Center
                canvas:drawText(text, 0, y)
            end

            function onDraw(state, env)
                canvas:save()
                
                -- 1. 坐标系归一化 (中心为 0,0)
                canvas:translate(env.width / 2, env.height / 2)
                
                -- 强制横屏逻辑
                if env.isPortrait then 
                    canvas:rotate(90) 
                end
                
                -- 计算虚拟宽高
                local vw = env.width
                local vh = env.height
                if env.isPortrait then vw = env.height; vh = env.width end
                
                -- 2. 绘制左上角时间
                canvas:setTextSize(40)
                canvas:setColor("#666666")
                canvas:setTextAlign(0) -- Left
                canvas:drawText(env.time, -vw/2 + 60, -vh/2 + 100)

                -- 3. 绘制歌词
                if state.lyric ~= "" then
                    canvas:setShadow(10, 0, 0, "#333333")
                    local lyricAlpha = math.floor(env.anim * 255)
                    local yOffset = 20 - (env.anim * 20)
                    drawCenteredText(state.lyric, yOffset, 80, "#FFFFFF", lyricAlpha)
                    canvas:setShadow(0, 0, 0, 0)
                else
                    drawCenteredText("等待播放...", 0, 60, "#888888", 255)
                end

                -- 4. 绘制底部信息栏
                local metaY = vh/2 - 120
                local metaInfo = state.title .. " - " .. state.artist
                drawCenteredText(metaInfo, metaY, 36, "#AAAAAA", 200)

                -- 5. 绘制底部进度条
                local barWidth = vw * 0.6
                local barHeight = 6
                local barY = vh/2 - 80
                local barLeft = -barWidth / 2
                
                canvas:setColor("#333333")
                canvas:drawRoundRect(barLeft, barY, barLeft + barWidth, barY + barHeight, 3)
                
                if state.totalTime ~= "0:00" then
                    local progressWidth = barWidth * state.progress
                    canvas:setColor("#FFFFFF")
                    canvas:setShadow(5, 0, 0, "#FFFFFF") 
                    canvas:drawRoundRect(barLeft, barY, barLeft + progressWidth, barY + barHeight, 3)
                    canvas:setShadow(0, 0, 0, 0)
                end
                
                local timeStr = state.currentTime .. " / " .. state.totalTime
                drawCenteredText(timeStr, barY + 40, 24, "#666666", 150)
                
                if state.isLiked then
                    canvas:setColor("#FF4081")
                    canvas:drawCircle(vw/2 - 60, -vh/2 + 100, 10)
                end

                canvas:restore()
            end
        """.trimIndent()
    }

    private fun checkAndRequestPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(permission)
        }
    }

    private fun hideSystemBars() {
        val wic = WindowCompat.getInsetsController(window, window.decorView)
        wic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        wic.hide(WindowInsetsCompat.Type.systemBars())
    }
}
