package com.example.pra

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


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

                LaunchedEffect(uiState.lyrics) {
                    if (uiState.lyrics.isNotEmpty()) {
                        // 在 IO 线程做字符串拼接，防止卡 UI
                        withContext(Dispatchers.IO) {
                            val luaCode = luaEngine.convertLyricsToLuaTable(uiState.lyrics)
                            // 注入动作必须很快，可以直接在 IO 线程调用 evaluate (Luaj 是线程安全的吗？如果不确定，切回 Main)
                            withContext(Dispatchers.Main) {
                                luaEngine.evaluate(luaCode)
                            }
                        }
                    } else {
                        luaEngine.evaluate("global_lyrics = {}")
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
-- [[ Minimalist True Karaoke Version (Adapted) ]] --

-- 1. 字体加载
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

    -- 1. 纯黑背景
    -- ★★★ 适配点：你的 Adapter 没有 drawRect，用 drawRoundRect 代替 ★★★
    canvas:setColor("#000000")
    canvas:drawRoundRect(0, 0, w, h, 0)
    
    -- ★★★ 适配点：现在 setTypeface 可以用了 ★★★
    if fontLoaded then canvas:setTypeface(customFont) else canvas:setTypeface(nil) end

    -- 2. 获取数据
    local allLyrics = global_lyrics
    -- Kotlin 传的是 0 起始，Lua 表是 1 起始，所以 +1
    local lineIdx = (state.currentLyricIndex or -1) + 1
    
    local currentLineObj = nil
    if allLyrics and lineIdx > 0 and lineIdx <= #allLyrics then
        currentLineObj = allLyrics[lineIdx]
    end

    -- 3. 准备渲染单元 (renderUnits)
    local renderUnits = {}
    local useWordTime = false 

    if currentLineObj then
        -- A. 有歌词数据
        if currentLineObj.words and #currentLineObj.words > 0 then
            -- A1. 有逐字时间戳 (Rich Lyrics)
            renderUnits = currentLineObj.words
            useWordTime = true
        else
            -- A2. 普通歌词 -> 拆分成单字
            local chars = tools.toChars(currentLineObj.text)
            for k, v in pairs(chars) do
                 table.insert(renderUnits, { text = v, st = 0, et = 0 })
            end
        end
    else
        -- B. 没有歌词 (显示歌名)
        local title = state.songTitle
        if title == nil or title == "" then title = "Listening..." end
        local chars = tools.toChars(title)
        for k, v in pairs(chars) do
             table.insert(renderUnits, { text = v, st = 0, et = 0 })
        end
    end

    -- 4. 布局计算 (排版 & 换行)
    local fontSize = env.isPortrait and 90 or 120
    canvas:setTextSize(fontSize)
    
    local maxLineWidth = w * 0.9
    local lines = {}
    local currentLineUnits = {}
    local currentWidth = 0
    
    for i, unit in ipairs(renderUnits) do
        local unitW = canvas:measureText(unit.text)
        
        -- 换行判断
        if currentWidth + unitW > maxLineWidth and #currentLineUnits > 0 then
            table.insert(lines, { units = currentLineUnits, width = currentWidth })
            currentLineUnits = {}
            currentWidth = 0
        end
        
        unit.drawWidth = unitW
        table.insert(currentLineUnits, unit)
        currentWidth = currentWidth + unitW
    end
    if #currentLineUnits > 0 then
        table.insert(lines, { units = currentLineUnits, width = currentWidth })
    end

    -- 5. 绘制循环
    local lineHeight = fontSize * 1.4
    local totalHeight = #lines * lineHeight
    local startY = cy - (totalHeight / 2) + (fontSize / 2) * 0.8 

    local currentPos = state.position or 0
    
    for lIdx, line in ipairs(lines) do
        local lineY = startY + (lIdx - 1) * lineHeight
        local currentX = cx - (line.width / 2)
        
        for uIdx, unit in ipairs(line.units) do
            local x = currentX
            local y = lineY
            
            -- 颜色逻辑
            local color = "#888888" 
            local scale = 1.0
            
            if not currentLineObj then
                color = "#FFFFFF" 
            elseif useWordTime then
                -- 真·逐字
                if currentPos >= unit.st and currentPos < unit.et then
                    color = "#FF3366" -- 高亮色
                    local breath = math.sin(currentPos / 150) * 0.15
                    scale = 1.2 + breath
                elseif currentPos >= unit.et then
                    color = "#FFFFFF" -- 已唱过
                else
                    color = "#444444" -- 未唱
                end
            else
                -- 普通行
                color = "#FFFFFF"
            end
            
            -- 执行绘制
            canvas:save()
            canvas:translate(x + unit.drawWidth / 2, y)
            canvas:scale(scale, scale)
            canvas:translate(-(x + unit.drawWidth / 2), -y)
            
            canvas:setColor(color)
            if scale > 1.0 then
                canvas:setShadow(20, 0, 0, color)
            else
                canvas:setShadow(0, 0, 0, 0)
            end
            
            canvas:drawText(unit.text, x, y)
            canvas:restore()
            
            currentX = currentX + unit.drawWidth
        end
    end
end
        """.trimIndent()
    }

}