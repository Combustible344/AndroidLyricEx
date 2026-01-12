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

                // ★★★ 交互信号：连接 PlayerScreen 和 IdleScreenSaver ★★★
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

                // 歌词切换动画信号 (传给 Lua env.anim)
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
                        onUserActivity = {
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
     */
    private fun loadScriptStrategy() {
        val scriptDir = File(getExternalFilesDir(null), "lua_scripts")
        if (!scriptDir.exists()) scriptDir.mkdirs()

        val scriptFile = File(scriptDir, "draw.lua")

        if (scriptFile.exists()) {
            try {
                val userScript = scriptFile.readText()
                luaEngine.loadScript(userScript)

                // ★★★ 新增提示：明确告知加载了外部文件 ★★★
                Toast.makeText(this, "已加载外部 Lua 配置", Toast.LENGTH_SHORT).show()
                Log.d("LuaLoader", "Loaded external script: ${scriptFile.absolutePath}")
            } catch (e: Exception) {
                // 如果外部文件语法有错，提示并降级
                Toast.makeText(this, "外部脚本错误，使用默认", Toast.LENGTH_LONG).show()
                luaEngine.loadScript(DEFAULT_LUA_SCRIPT)
            }
        } else {
            // 文件不存在，写入默认模板
            try {
                scriptFile.writeText(DEFAULT_LUA_SCRIPT)
            } catch (e: Exception) {
                Log.e("LuaLoader", "Write template failed", e)
            }

            // 加载内置
            luaEngine.loadScript(DEFAULT_LUA_SCRIPT)

            // ★★★ 新增提示：明确告知正在使用内置默认 ★★★
            Toast.makeText(this, "未找到配置，使用内置默认", Toast.LENGTH_SHORT).show()
        }
    }

    // ★★★ 内置脚本：淡彩 + 伪逐字特效 ★★★
    companion object {
        private val DEFAULT_LUA_SCRIPT = """
            -- [[ 梦幻淡彩 + 伪逐字歌词特效 ]] --

            function onDraw(state, env)
                local w = env.width
                local h = env.height
                local cx = w / 2
                local cy = h / 2

                -- 1. 背景：莫兰迪色渐变
                -- 顶部淡粉 -> 底部淡蓝
                canvas:setLinearGradient(0, 0, w, h, "#FFDEE9", "#B5FFFC")
                canvas:drawRoundRect(0, 0, w, h, 0)

                -- 2. 呼吸光晕
                canvas:save()
                local breathe = math.sin(os.clock()) * 0.05 + 0.95 -- 0.9 ~ 1.0
                canvas:translate(cx, cy)
                canvas:scale(breathe, breathe)
                canvas:setColor("#40FFFFFF") -- 25%透明白
                canvas:drawCircle(0, 0, w * 0.4)
                canvas:restore()

                -- 3. 歌词绘制区
                canvas:save()
                canvas:translate(cx, cy) -- 移动到屏幕中心

                local text = state.lyric
                if text == "" then text = state.title .. " - " .. state.artist end

                -- A. 拆分字符
                local chars = toChars(text)
                local charCount = #chars
                
                -- B. 测量与排版
                local fontSize = 60
                if env.isPortrait then fontSize = 48 end
                
                canvas:setTextSize(fontSize)
                local charWidths = {}
                local totalTextWidth = 0
                for i = 1, charCount do
                    local cw = canvas:measureText(chars[i])
                    charWidths[i] = cw
                    totalTextWidth = totalTextWidth + cw
                end

                -- C. 核心算法：计算当前唱到第几个字
                local lineDuration = state.lineEnd - state.lineStart
                local timeElapsed = state.position - state.lineStart
                
                if lineDuration <= 0 then lineDuration = 5000 end
                if timeElapsed < 0 then timeElapsed = 0 end
                
                -- 平均每个字的时长
                local durationPerChar = lineDuration / charCount
                if charCount == 0 then durationPerChar = 1 end
                
                -- 当前字索引
                local activeIndex = math.floor(timeElapsed / durationPerChar) + 1

                -- D. 绘制循环
                local currentX = -totalTextWidth / 2
                
                for i = 1, charCount do
                    local charStr = chars[i]
                    local cw = charWidths[i]
                    
                    canvas:save()
                    
                    if i < activeIndex then
                        -- [已唱]：灰紫色
                        canvas:setColor("#7D7090") 
                        canvas:drawText(charStr, currentX, 0)
                        
                    elseif i == activeIndex then
                        -- [当前]：高亮粉 + 心跳动画
                        canvas:setColor("#FF6B6B")
                        canvas:setShadow(15, 0, 0, "#FFFFFFFF")
                        
                        -- 0.0 -> 1.0
                        local charProgress = (timeElapsed % durationPerChar) / durationPerChar
                        -- 正弦波跳动
                        local jumpY = -20 * math.sin(charProgress * 3.14) 
                        local scale = 1.0 + (0.3 * math.sin(charProgress * 3.14))
                        
                        canvas:translate(currentX + cw/2, jumpY) 
                        canvas:scale(scale, scale)
                        canvas:translate(-(currentX + cw/2), 0)
                        
                        canvas:drawText(charStr, currentX, 0)
                        
                    else
                        -- [未唱]：半透明白
                        canvas:setColor("#80FFFFFF") 
                        canvas:drawText(charStr, currentX, 0)
                    end
                    
                    canvas:restore()
                    currentX = currentX + cw
                end
                
                canvas:restore()

                -- 4. 底部极简进度条
                local bottomY = h - 100
                local barW = w * 0.7
                
                -- 轨道
                canvas:setColor("#40000000")
                canvas:drawRoundRect((w-barW)/2, bottomY, (w+barW)/2, bottomY+6, 3)
                
                -- 进度
                if state.progress > 0 then
                    local progW = barW * state.progress
                    canvas:setLinearGradient((w-barW)/2, bottomY, (w-barW)/2 + progW, bottomY, "#FF9A9E", "#FECFEF")
                    canvas:drawRoundRect((w-barW)/2, bottomY, (w-barW)/2 + progW, bottomY+6, 3)
                end
                
                -- 左上角时钟
                canvas:setTextSize(36)
                canvas:setColor("#66707070")
                canvas:setTextAlign(0) -- Left
                canvas:drawText(env.time, 40, 80)
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
