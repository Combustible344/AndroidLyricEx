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
import androidx.compose.animation.core.*
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
import com.example.pra.ui.screens.PlayerScreen
import com.example.pra.ui.theme.PraTheme
import com.example.pra.ui.viewmodel.MusicPlayerViewModel
import com.example.pra.utils.LuaScriptEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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

        loadScriptStrategy()

        enableEdgeToEdge()
        hideSystemBars()
        checkAndRequestPermissions()

        setContent {
            PraTheme {
                val uiState by viewModel.uiState.collectAsState()
                var interactionSignal by remember { mutableLongStateOf(System.currentTimeMillis()) }

                // 1. 系统时间更新 (1秒一次，用于传给 Lua 显示时间，非动画用)
                var timeString by remember { mutableStateOf("") }
                LaunchedEffect(Unit) {
                    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                    while (true) {
                        timeString = formatter.format(Date())
                        delay(1000)
                    }
                }

                // 2. 歌词注入逻辑 (切歌时触发)
                LaunchedEffect(uiState.lyrics) {
                    if (uiState.lyrics.isNotEmpty()) {
                        withContext(Dispatchers.IO) {
                            val luaCode = luaEngine.convertLyricsToLuaTable(uiState.lyrics)
                            withContext(Dispatchers.Main) {
                                luaEngine.evaluate(luaCode)
                            }
                        }
                    } else {
                        luaEngine.evaluate("global_lyrics = {}")
                    }
                }

                var isPlaylistOpen by remember { mutableStateOf(false) }

                // 3. 歌词切换时的淡入动画信号
                val alphaAnim = remember { Animatable(0f) }
                LaunchedEffect(uiState.currentLyricLine) {
                    alphaAnim.snapTo(0f)
                    alphaAnim.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
                }

                // 4. ★★★ 60FPS 心跳驱动器 ★★★
                // 这是一个无限循环的动画值，它的存在是为了强迫 Compose 每帧重绘 Canvas
                val infiniteTransition = rememberInfiniteTransition(label = "render_beat")
                val frameBeat by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1000f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(10000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "frame_beat"
                )

                val animState = ScreenSaverAnimState(timeString, alphaAnim.value)

                IdleScreenSaver(
                    data = animState,
                    config = ScreenSaverConfig(backgroundColor = Color.Black),
                    externalInteractionSignal = interactionSignal,
                    isEnabled = !isPlaylistOpen,
                    renderer = ScreenSaverRenderer { scope, env, animData ->
                        // ★★★ 读取心跳值，触发每帧重绘 ★★★
                        val trigger = frameBeat

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

    private fun loadScriptStrategy() {
        val scriptDir = File(getExternalFilesDir(null), "lua_scripts")
        if (!scriptDir.exists()) scriptDir.mkdirs()

        val scriptFile = File(scriptDir, "draw.lua")
        val pathInjection = "script_dir = \"${scriptDir.absolutePath.replace("\\", "\\\\")}\"\n"

        if (scriptFile.exists()) {
            try {
                val userScript = scriptFile.readText()
                luaEngine.loadScript(pathInjection + userScript)
                Toast.makeText(this, "已加载外部配置", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "外部脚本错误，降级默认", Toast.LENGTH_LONG).show()
                luaEngine.loadScript(pathInjection + DEFAULT_LUA_SCRIPT)
            }
        } else {
            try { scriptFile.writeText(DEFAULT_LUA_SCRIPT) } catch (e: Exception) { }
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
-- [[ Ultimate Karaoke: Fixed Layout & Align ]] --

-- 1. 资源加载
local fontName = "SourceHanSansCN-Normal.ttf"
local fontPath = (script_dir or "") .. "/" .. fontName
local customFont = tools.loadFont(fontPath)

function onDraw(state, env)
    local w = env.width
    local h = env.height
    local cx = w / 2
    local cy = h / 2

    -- 1. 画背景
    canvas:drawRect(0, 0, w, h, "#000000")
    
    if customFont then canvas:setTypeface(customFont) else canvas:setTypeface(nil) end

    -- 2. 准备数据
    local allLyrics = global_lyrics
    local lineIdx = (state.currentLyricIndex or -1) + 1
    local currentLineObj = allLyrics and allLyrics[lineIdx] or nil
    
    -- 如果没有歌词，显示标题
    if not currentLineObj then
        drawPlaceholder(state, env, cx, cy)
        return
    end

    -- 3. 准备渲染单元
    local renderUnits = {}
    local useWordTime = false
    
    if currentLineObj.words and #currentLineObj.words > 0 then
        renderUnits = currentLineObj.words
        useWordTime = true
    else
        -- 降级：拆单字
        local chars = tools.toChars(currentLineObj.text)
        for k, v in pairs(chars) do
             table.insert(renderUnits, { text = v, st = 0, et = 0 })
        end
    end

    -- 4. 自动换行排版
    local fontSize = env.isPortrait and 90 or 120
    canvas:setTextSize(fontSize)
    
    -- ★★★ 修复核心：必须强制重置为左对齐，防止被 drawPlaceholder 污染 ★★★
    canvas:setTextAlign(0) 
    
    local centerOffset = canvas:getTextCenterOffset()
    
    local maxLineWidth = w * 0.85
    local lines = {}
    local currentLineUnits = {}
    local currentWidth = 0
    
    for i, unit in ipairs(renderUnits) do
        local unitW = canvas:measureText(unit.text)
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

    -- 5. 进出场动画计算
    local animAlpha = 255
    local animOffsetY = 0
    local currentPos = state.position or 0
    local lineSt = currentLineObj.startTime
    local lineEt = currentLineObj.endTime
    
    -- 进场 (Fade In + Slide Up)
    if currentPos < lineSt + 400 then
        local t = currentPos - lineSt
        animAlpha = anim:clamp(anim:map(t, 0, 400, 0, 255), 0, 255)
        animOffsetY = anim:clamp(anim:map(t, 0, 400, 50, 0), 0, 50)
    end
    
    -- 离场 (Fade Out + Slide Up)
    if currentPos > lineEt - 300 then
        local t = currentPos - (lineEt - 300)
        animAlpha = anim:clamp(anim:map(t, 0, 300, 255, 0), 0, 255)
        animOffsetY = anim:clamp(anim:map(t, 0, 300, 0, -50), -50, 0)
    end
    
    -- 6. 渲染循环
    local lineHeight = fontSize * 1.5
    local totalBlockHeight = #lines * lineHeight
    local startY = cy - (totalBlockHeight / 2) + (lineHeight / 2) - centerOffset + animOffsetY

    for lIdx, line in ipairs(lines) do
        local lineY = startY + (lIdx - 1) * lineHeight
        -- 计算这一行的起始 X (水平居中)
        local currentX = cx - (line.width / 2)
        
        for uIdx, unit in ipairs(line.units) do
            local x = currentX
            local y = lineY
            
            canvas:save()
            canvas:setAlpha(animAlpha)
            
            -- === 状态判定 ===
            local isPast = false
            local isActive = false
            local fillPercent = 0.0
            
            if useWordTime then
                if currentPos >= unit.et then
                    isPast = true
                elseif currentPos >= unit.st then
                    isActive = true
                    local dur = unit.et - unit.st
                    if dur > 0 then
                        fillPercent = (currentPos - unit.st) / dur
                    else
                        fillPercent = 1.0
                    end
                end
            else
                isPast = true 
            end
            
            -- A. 底层 (灰色)
            if not isPast then
                canvas:setColor("#888888") 
                canvas:drawText(unit.text, x, y)
            end
            
            -- B. 顶层 (白色/高亮)
            if isPast then
                -- 全亮
                canvas:setColor("#FFFFFF")
                canvas:drawText(unit.text, x, y)
                
            elseif isActive then
                -- 逐字填充 (Clip)
                local fillWidth = unit.drawWidth * fillPercent
                
                canvas:save()
                -- 裁剪区域：左边 fillWidth 的部分
                -- 稍微扩大上下边界防止字体被切头
                canvas:clipRect(x, y - fontSize * 1.2, x + fillWidth, y + fontSize * 0.5)
                
                canvas:setColor("#FFFFFF")
                -- 呼吸效果
                local breath = anim:sineWave(env.systemTime, 600, 1.0, 1.1)
                
                -- 注意：如果在裁剪状态下做缩放，必须以文字左侧为锚点，否则会错位
                -- 简单起见，这里只变色和发光，不缩放，保证对齐绝对精准
                canvas:setShadow(30, 0, 0, "#FF3366")
                
                canvas:drawText(unit.text, x, y)
                canvas:restore()
            end
            
            canvas:restore() 
            
            -- 移动 X 坐标到下一个字
            currentX = currentX + unit.drawWidth
        end
    end
end

function drawPlaceholder(state, env, cx, cy)
    local text = state.songTitle
    if text == nil or text == "" then text = "Listening..." end
    
    canvas:setTextSize(60)
    canvas:setColor("#FFFFFF")
    -- 这里设置了居中，如果不重置，会影响上面的 onDraw
    canvas:setTextAlign(1) 
    
    local scale = anim:sineWave(env.systemTime, 2000, 0.95, 1.05)
    
    canvas:save()
    canvas:translate(cx, cy)
    canvas:scale(scale, scale)
    canvas:translate(-cx, -cy)
    
    local dy = canvas:getTextCenterOffset()
    canvas:drawText(text, cx, cy + dy)
    canvas:restore()
end
        """.trimIndent()
    }

}
