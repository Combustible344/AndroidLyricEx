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

    // ★★★ 修复 1: 使用 RequestMultiplePermissions 一次请求多个权限 ★★★
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

        luaEngine = LuaScriptEngine(this)

        // ★★★ 加载策略：会注入路径变量 ★★★
        loadScriptStrategy()

        enableEdgeToEdge()
        hideSystemBars()
        checkAndRequestPermissions()

        setContent {
            PraTheme {
                val uiState by viewModel.uiState.collectAsState()
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

                // 歌词切换动画信号
                val alphaAnim = remember { Animatable(0f) }
                LaunchedEffect(uiState.currentLyricLine) {
                    alphaAnim.snapTo(0f)
                    alphaAnim.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
                }

                val animState = ScreenSaverAnimState(timeString, alphaAnim.value)

                IdleScreenSaver(
                    data = animState,
                    config = ScreenSaverConfig(backgroundColor = Color.Black),
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
                    PlayerScreen(
                        onUserActivity = { interactionSignal = System.currentTimeMillis() },
                        onPlaylistVisibilityChange = { isOpen -> isPlaylistOpen = isOpen }
                    )
                }
            }
        }
    }

    /**
     * ★★★ 核心修改：脚本加载策略 ★★★
     */
    private fun loadScriptStrategy() {
        // 1. 确定脚本目录: /Android/data/com.example.pra/files/lua_scripts/
        val scriptDir = File(getExternalFilesDir(null), "lua_scripts")
        if (!scriptDir.exists()) scriptDir.mkdirs()

        val scriptFile = File(scriptDir, "draw.lua")

        // 2. 准备注入代码：将路径作为全局变量 script_dir 注入到 Lua 环境中
        // 这样 Lua 脚本就可以用 script_dir .. "/font.ttf" 来引用文件了
        val pathInjection = "script_dir = \"${scriptDir.absolutePath.replace("\\", "\\\\")}\"\n"

        if (scriptFile.exists()) {
            try {
                val userScript = scriptFile.readText()
                // 拼接：路径变量定义 + 用户代码
                luaEngine.loadScript(pathInjection + userScript)

                Toast.makeText(this, "已加载外部配置", Toast.LENGTH_SHORT).show()
                Log.d("LuaLoader", "Loaded script from: ${scriptFile.absolutePath}")
            } catch (e: Exception) {
                Toast.makeText(this, "外部脚本错误，降级默认", Toast.LENGTH_LONG).show()
                luaEngine.loadScript(pathInjection + DEFAULT_LUA_SCRIPT)
            }
        } else {
            // 写入默认模板
            try {
                scriptFile.writeText(DEFAULT_LUA_SCRIPT)
            } catch (e: Exception) {
                Log.e("LuaLoader", "Write template failed", e)
            }
            // 加载内置 (同样注入路径)
            luaEngine.loadScript(pathInjection + DEFAULT_LUA_SCRIPT)
            Toast.makeText(this, "使用内置默认配置", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissions.add(Manifest.permission.RECORD_AUDIO)

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            // 使用 registerForActivityResult 的 launch 传入数组
            requestPermissionLauncher.launch(permissionsToRequest)
        }
    }

    private fun hideSystemBars() {
        val wic = WindowCompat.getInsetsController(window, window.decorView)
        wic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        wic.hide(WindowInsetsCompat.Type.systemBars())
    }

    companion object {
        private val DEFAULT_LUA_SCRIPT = """
            -- [[ 自定义字体 OLED 歌词 ]] --
            
            -- ★★★ 1. 加载字体逻辑 ★★★
            -- script_dir 变量由 Kotlin 在加载时自动注入
            local fontName = "SourceHanSansCN-Normal.ttf"
            local fontPath = script_dir .. "/" .. fontName
            
            local customFont = tools.loadFont(fontPath)
            
            -- 定义显示的字体名称
            local fontDisplayName = "Default Font"
            if customFont then
                fontDisplayName = "SourceHanSansCN-Normal"
            end

            function onDraw(state, env)
                local w = env.width
                local h = env.height
                local cx = w / 2
                local cy = h / 2

                -- 设置背景黑
                canvas:setColor("#000000")
                canvas:drawRoundRect(0, 0, w, h, 0)
                
                -- ★★★ 2. 应用字体 ★★★
                if customFont then
                    canvas:setTypeface(customFont)
                else
                    -- 显式重置为默认，防止状态污染
                    canvas:setTypeface(nil)
                end

                -- 歌词区域
                canvas:save()
                canvas:translate(cx, cy) 

                local text = state.lyric
                if text == "" then text = state.title .. " - " .. state.artist end

                local chars = toChars(text)
                local charCount = #chars
                
                local fontSize = 56
                if env.isPortrait then fontSize = 42 end
                canvas:setTextSize(fontSize)
                
                -- 测量文字
                local charWidths = {}
                local totalTextWidth = 0
                for i = 1, charCount do
                    local cw = canvas:measureText(chars[i])
                    charWidths[i] = cw
                    totalTextWidth = totalTextWidth + cw
                end

                -- 计算进度
                local lineDuration = state.lineEnd - state.lineStart
                local timeElapsed = state.position - state.lineStart
                if lineDuration <= 0 then lineDuration = 5000 end
                if timeElapsed < 0 then timeElapsed = 0 end
                
                local durationPerChar = lineDuration / charCount
                if charCount == 0 then durationPerChar = 1 end
                
                local activeIndex = math.floor(timeElapsed / durationPerChar) + 1

                -- 绘制文字
                local currentX = -totalTextWidth / 2
                
                for i = 1, charCount do
                    local charStr = chars[i]
                    local cw = charWidths[i]
                    local x = currentX
                    
                    if i < activeIndex then
                        canvas:setColor("#666666")
                        canvas:drawText(charStr, x, 0)
                    elseif i == activeIndex then
                        canvas:setColor("#FFDDAA")
                        canvas:setShadow(10, 0, 0, "#66FFDDAA")
                        canvas:save()
                        canvas:translate(x + cw/2, 0)
                        canvas:scale(1.1, 1.1)
                        canvas:translate(-(x + cw/2), 0)
                        canvas:drawText(charStr, x, 0)
                        canvas:restore()
                    else
                        canvas:setColor("#222222")
                        canvas:setShadow(0, 0, 0, 0)
                        canvas:drawText(charStr, x, 0)
                    end
                    currentX = currentX + cw
                end
                
                canvas:restore()


                -- 4. 进度条
                local barY = h - 60
                local barW = w * 0.5
                local progW = barW * state.progress
                
                if state.progress > 0 then
                    canvas:setColor("#404040")
                    canvas:drawRoundRect((w-barW)/2, barY, (w+barW)/2, barY+2, 1)
                    canvas:setColor("#AAAAAA")
                    canvas:drawRoundRect((w-barW)/2, barY, (w-barW)/2 + progW, barY+2, 1)
                end
                
                -- 5. 显示字体名称 (替代原来的时钟)
                canvas:setTextSize(32)
                canvas:setColor("#444444")
                canvas:setTextAlign(1) -- Center
                -- 绘制字体名称
                canvas:drawText(fontDisplayName, w/2, 80)
            end
        """.trimIndent()
    }
}
