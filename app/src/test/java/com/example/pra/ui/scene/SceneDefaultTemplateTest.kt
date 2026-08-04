package com.example.pra.ui.scene

import android.graphics.Canvas
import com.example.pra.data.model.LyricLine
import com.example.pra.data.model.LyricWord
import com.example.pra.ui.state.PlayerUiState
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 默认息屏歌词模板 (res/raw/scene_default.lua) 验证。
 * 注意: 测试内嵌内容需与 res/raw 保持一致 (资源在 JVM 测试中不可读)。
 */
class SceneDefaultTemplateTest {

    private val template = """
-- [[ Lyric Scene · 微光 (Stardust) ]]
-- 脚本模式 (定义 onDraw): 简约黑底 + 逐字卡拉OK +
-- 背景微尘缓慢漂移 (正弦往返 + 呼吸明灭) + 底部进度线光点
--
-- 数值可调参数集中在 CONFIG:
--   fontSizeRatio    歌词字号 (屏高比例)
--   particleCount    背景微尘数量 (18 个已很淡, 性能可忽略)
--   progressLineRatio 底部进度线宽度 (屏宽比例)

local CONFIG = {
    font = "SourceHanSansCN-Normal.ttf",
    fontSizeRatio = 0.075,
    titleSizeRatio = 0.03,
    baseColor = "#5A5A5A",
    activeColor = "#FFFFFF",
    particleCount = 18,
    progressLineRatio = 0.8,
}

local font = tools.loadFont(CONFIG.font)

function onDraw(state, env)
    local w, h = env.width, env.height
    local t = env.systemTime
    canvas:drawColor("#000000")
    if font then canvas:setTypeface(font) else canvas:setTypeface(nil) end

    -- ============ 背景微尘: 缓慢漂移 + 呼吸明灭 ============
    -- 以短边为幅度基准, 竖屏/横屏都均匀分布在中部带 (避免竖屏下纵向拉太长)
    local s = math.min(w, h)
    canvas:setColor("#FFFFFF")
    for i = 1, CONFIG.particleCount do
        local seed = i * 7.31
        local speed = 1 + (i % 4) * 0.25
        local px = w * 0.5 + math.sin(t * 0.00004 * speed + seed) * s * 0.48
        local py = h * 0.38 + (math.sin(t * 0.00007 * speed + seed * 1.7) * 0.5 + 0.5) * s * 0.55
        local pr = 1 + (i % 3)
        local pa = 10 + (math.sin(t * 0.0001 * speed + seed * 2.3) * 0.5 + 0.5) * 12
        canvas:setAlpha(math.floor(pa))
        canvas:drawCircle(px, py, pr, "#FFFFFF")
    end
    canvas:setAlpha(255)

    -- ============ 顶部: 歌名 · 歌手 ============
    local title = (state.title or "") .. " · " .. (state.artist or "")
    canvas:setTextSize(h * CONFIG.titleSizeRatio)
    canvas:setTextAlign(1)
    canvas:setColor("#FFFFFF")
    canvas:setAlpha(80)
    canvas:drawText(title, w / 2, h * 0.06)
    canvas:setTextAlign(0)
    canvas:setAlpha(255)

    local lineIdx = (state.currentLyricIndex or -1) + 1
    local lineObj = global_lyrics and global_lyrics[lineIdx]
    if not lineObj then
        -- 占位: 歌名居中呼吸
        local fontSize = h * 0.05
        canvas:setTextSize(fontSize)
        canvas:setTextAlign(1)
        local scale = 1 + math.sin(t * 0.0015) * 0.02
        canvas:save()
        canvas:translate(w / 2, h / 2)
        canvas:scale(scale, scale)
        canvas:translate(-w / 2, -h / 2)
        canvas:setColor("#FFFFFF")
        canvas:setAlpha(200)
        canvas:drawText(state.title, w / 2, h / 2 + canvas:getTextCenterOffset())
        canvas:restore()
        canvas:setTextAlign(0)
        canvas:setAlpha(255)
        return
    end

    local pos = state.position or 0
    local fontSize = h * CONFIG.fontSizeRatio
    canvas:setTextSize(fontSize)

    local units = lineObj.words
    if not units or #units == 0 then
        units = {}
        local chars = tools.toChars(lineObj.text)
        for i, v in ipairs(chars) do
            units[i] = { text = v, st = lineObj.startTime, et = lineObj.endTime }
        end
    end

    -- 测量与居中 (超宽靠左)
    local widths = {}
    local totalW = 0
    for i, u in ipairs(units) do
        widths[i] = canvas:measureText(u.text)
        totalW = totalW + widths[i]
    end
    local maxW = w * 0.85
    local x = totalW > maxW and w * 0.05 or (w - totalW) / 2
    local y = h / 2 + canvas:getTextCenterOffset()

    -- ============ 进出场: 600ms 缓入上滑 / 缓出上移 ============
    local alpha, dy = 255, 0
    if pos < lineObj.startTime + 600 then
        local k = anim:clamp((pos - lineObj.startTime) / 600, 0, 1)
        alpha = math.floor(k * 255)
        dy = (1 - k) * fontSize * 0.5
    elseif pos > lineObj.endTime - 400 then
        local k = anim:clamp((pos - (lineObj.endTime - 400)) / 400, 0, 1)
        alpha = math.floor((1 - k) * 255)
        dy = -k * fontSize * 0.5
    end
    canvas:setAlpha(alpha)

    -- ============ 逐字卡拉OK ============
    for i, u in ipairs(units) do
        canvas:setColor(CONFIG.baseColor)
        canvas:drawText(u.text, x, y + dy)

        local prog = 0
        if pos >= u.st then
            if pos >= u.et then prog = 1
            elseif u.et > u.st then prog = (pos - u.st) / (u.et - u.st)
            else prog = 1 end
        end
        if prog > 0 then
            canvas:setColor(CONFIG.activeColor)
            canvas:save()
            canvas:clipRect(x, y + dy - fontSize * 1.2, x + widths[i] * prog, y + dy + fontSize * 0.8)
            canvas:drawText(u.text, x, y + dy)
            canvas:restore()
        end
        x = x + widths[i]
    end
    canvas:setAlpha(255)

    -- ============ 底部进度线 + 前端光点 ============
    local lineW = w * CONFIG.progressLineRatio
    local lineY = h * 0.93
    local lineX0 = (w - lineW) / 2
    local prog = anim:clamp(state.progress or 0, 0, 1)
    local endX = lineX0 + lineW * prog

    canvas:setColor("#FFFFFF")
    canvas:setAlpha(25)
    canvas:drawRect(lineX0, lineY, lineX0 + lineW, lineY + 2, "#FFFFFF")

    canvas:setAlpha(255)
    canvas:drawRect(lineX0, lineY, endX, lineY + 2, "#FFFFFF")

    -- 光点: 缓慢呼吸 + 微光
    local pulse = 0.5 + (math.sin(t * 0.004) * 0.5 + 0.5) * 0.5
    canvas:setAlpha(150 + math.floor(pulse * 100))
    canvas:setShadow(8, 0, 0, "#40FFFFFF")
    canvas:drawCircle(endX, lineY + 1, 4, "#FFFFFF")
    canvas:clearShadow()
    canvas:setAlpha(255)
end
    """.trimIndent()

    private val engine = LuaScriptEngine(null)

    private fun render(uiState: PlayerUiState, positionMs: Long) {
        engine.loadScript(template)
        engine.executeDraw(Canvas(), uiState, positionMs, 1080f, 2400f, 5000L, 1f)
    }

    @Test
    fun `template is script mode`() {
        assertTrue(SceneBuilder().build(template).hasOnDraw)
    }

    @Test
    fun `template renders lyrics with karaoke`() {
        val line = LyricLine(0L, 3000L, "风紧紧追着雨", listOf(
            LyricWord("风", 0L, 800L), LyricWord("紧", 800L, 1500L),
            LyricWord("紧", 1500L, 2200L), LyricWord("追", 2200L, 2600L),
            LyricWord("着", 2600L, 3000L), LyricWord("雨", 3000L, 3500L)
        ))
        val state = PlayerUiState(
            songTitle = "风错过雨", artistName = "小蓝背心", isPlaying = true,
            progress = 0.1f, durationMs = 200000L,
            lyrics = listOf(line), currentLyricIndex = 0, currentLyricLine = line
        )
        engine.evaluate(engine.convertLyricsToLuaTable(state.lyrics))
        render(state, 0L)       // 进场窗口
        render(state, 1500L)    // 行中
        render(state, 2800L)    // 离场窗口
    }

    @Test
    fun `template renders placeholder`() {
        render(PlayerUiState(songTitle = "风错过雨"), 1000L)
    }
}
