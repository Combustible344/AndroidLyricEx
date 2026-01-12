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
import com.example.pra.R

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
          -- [[ Midnight Large Text & Auto-Wrap Version ]] --

-- ★★★ 1. 字体加载逻辑 ★★★
local fontName = "SourceHanSansCN-Normal.ttf"
local fontLoaded = false
local fontPath = fontName
if script_dir ~= nil then fontPath = script_dir .. "/" .. fontName end
local customFont = tools.loadFont(fontPath)
if customFont then fontLoaded = true end

function onDraw(state, env)
    local w = env.width
    local h = env.height
    local cx = w / 2
    local cy = h / 2

    -- 1. 背景
    canvas:setColor("#000000")
    canvas:drawRoundRect(0, 0, w, h, 0)
    
    if fontLoaded then canvas:setTypeface(customFont) else canvas:setTypeface(nil) end

    -- 2. AOD 时钟 (稍微上移一点，给大歌词腾地方)
    canvas:setTextAlign(1)
    canvas:setTextSize(100) -- 时钟字号
    canvas:setColor("#33FFFFFF")
    canvas:drawText(env.time, cx, h * 0.15) -- 上移到 15% 处
    
    -- 3. 核心歌词显示 (自动换行 + 大字体)
    canvas:save()
    canvas:translate(cx, cy) 

    local text = state.lyric
    local isPlaceholder = false
    if text == "" then 
        text = state.title 
        isPlaceholder = true
    end

    local chars = toChars(text)
    local charCount = #chars
    
    -- ★★★ 配置区域 ★★★
    -- 字号大幅提升
    local fontSize = env.isPortrait and 80 or 100
    local lineHeight = fontSize * 1.3 -- 行高 (字号的1.3倍)
    local maxLineWidth = w * 0.85   -- 每一行最宽只允许占屏幕的 85%
    
    canvas:setTextSize(fontSize)

    -- ★★★ 步骤 A: 预计算分行 (Wrapping Logic) ★★★
    local lines = {}      -- 存放每一行的数据
    local currentLine = {} -- 当前正在拼凑的行
    local currentWidth = 0 
    
    for i = 1, charCount do
        local charStr = chars[i]
        local charW = canvas:measureText(charStr)
        
        -- 如果加上这个字会超宽，且当前行不是空的，就换行
        if currentWidth + charW > maxLineWidth and #currentLine > 0 then
            table.insert(lines, { chars = currentLine, totalW = currentWidth })
            currentLine = {}
            currentWidth = 0
        end
        
        -- 将字加入当前行 (同时记录它是全文第几个字，用于高亮)
        table.insert(currentLine, { str = charStr, w = charW, globalIndex = i })
        currentWidth = currentWidth + charW
    end
    -- 别忘了把最后一行加进去
    if #currentLine > 0 then
        table.insert(lines, { chars = currentLine, totalW = currentWidth })
    end

    -- ★★★ 步骤 B: 计算卡拉OK高亮进度 ★★★
    local activeIndex = 0
    if not isPlaceholder and state.isPlaying then
        local lineDuration = state.lineEnd - state.lineStart
        local timeElapsed = state.position - state.lineStart
        if lineDuration <= 0 then lineDuration = 1 end
        
        local progressPercent = timeElapsed / lineDuration
        if progressPercent < 0 then progressPercent = 0 end
        if progressPercent > 1 then progressPercent = 1 end
        activeIndex = math.floor(progressPercent * charCount) + 1
    end

    -- ★★★ 步骤 C: 绘制多行文字 (垂直居中) ★★★
    local totalBlockHeight = #lines * lineHeight
    -- 计算第一行的 Y 坐标 (从中心点向上偏移总高度的一半，再修正字体的基线)
    -- 文字通常以基线绘制，所以为了垂直居中，需要向下偏移半个行高左右
    local startY = -(totalBlockHeight / 2) + (lineHeight / 2) * 0.6 

    for l = 1, #lines do
        local lineData = lines[l]
        local lineChars = lineData.chars
        
        -- 每一行都在水平方向居中
        local currentX = -(lineData.totalW / 2)
        local currentY = startY + (l - 1) * lineHeight
        
        for c = 1, #lineChars do
            local charObj = lineChars[c]
            local globalIdx = charObj.globalIndex
            local x = currentX
            
            -- 绘图逻辑 (和之前一样，只是判断条件变成了 globalIdx)
            if isPlaceholder then
                canvas:setColor("#AAAAAA")
                canvas:setShadow(0,0,0,0)
                canvas:drawText(charObj.str, x, currentY)
            else
                if globalIdx < activeIndex then
                    -- 已唱过
                    canvas:setColor("#444444")
                    canvas:setShadow(0, 0, 0, 0)
                    canvas:drawText(charObj.str, x, currentY)
                elseif globalIdx == activeIndex then
                    -- 当前唱到的字 (呼吸效果)
                    local breath = math.sin(state.position / 200) * 0.1 
                    
                    canvas:save()
                    -- 注意：缩放中心点应该是字符的中心 (x + w/2, currentY - size/2 左右)
                    -- 这里简单处理：以基线中心缩放
                    canvas:translate(x + charObj.w/2, currentY)
                    canvas:scale(1.2 + breath, 1.2 + breath)
                    canvas:translate(-(x + charObj.w/2), -currentY)
                    
                    canvas:setColor("#FFFFFF")
                    canvas:setShadow(30, 0, 0, "#FFFFFFFF")
                    canvas:drawText(charObj.str, x, currentY)
                    canvas:restore()
                else
                    -- 未唱
                    canvas:setColor("#888888")
                    canvas:setShadow(0, 0, 0, 0)
                    canvas:drawText(charObj.str, x, currentY)
                end
            end
            
            currentX = currentX + charObj.w
        end
    end
    canvas:restore()

    -- 4. 进度条 (底部)
    local barY = h - 80
    local barWidth = w * 0.6
    local startX = (w - barWidth) / 2
    
    canvas:setColor("#1AFFFFFF")
    canvas:drawRoundRect(startX, barY, startX + barWidth, barY + 4, 2)
    
    if state.totalTime ~= "0:00" and state.progress > 0 then
        local currentW = barWidth * state.progress
        canvas:setLinearGradient(startX, barY, startX + currentW, barY, "#00FFFFFF", "#FFFFFFFF")
        canvas:drawRoundRect(startX, barY, startX + currentW, barY + 4, 2)
        canvas:setColor("#FFFFFF") 
    end

    -- 5. 底部信息 (只显示歌手，防止太挤)
    canvas:setTextSize(30)
    canvas:setColor("#666666")
    canvas:setTextAlign(1)
    canvas:drawText(state.artist, cx, h - 40)
end
        """.trimIndent()
    }
}