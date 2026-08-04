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
-- 脚本模式 (定义 onDraw): 竖屏布局, 黑白简约, 仿息屏 (AOD) 展示。
-- 布局: 居中偏上大时钟 + 中部微尘漂移 + 中下部分歌词 (卡拉OK)
-- 歌词字号恒定 (不缩放), 超宽自动换行多行显示, 不会溢出屏幕。
--
-- 可调参数集中在 CONFIG:
--   fontSizeRatio     歌词字号 (屏宽比例)
--   clockSizeRatio    时钟字号
--   particleCount     背景微尘数量

local CONFIG = {
    font = "SourceHanSansCN-Normal.ttf",
    fontSizeRatio = 0.075,
    clockSizeRatio = 0.12,
    baseColor = "#5A5A5A",
    activeColor = "#FFFFFF",
    particleCount = 18,
}

local font = tools.loadFont(CONFIG.font)

-- 时钟缓存: 分钟未变化不重新格式化
local clockStr = ""
local clockMinute = -1

function onDraw(state, env)
    local w, h = env.width, env.height
    local t = env.systemTime
    canvas:drawColor("#000000")
    if font then canvas:setTypeface(font) else canvas:setTypeface(nil) end

    -- ============ 居中偏上: 大时钟 (AOD 风格) ============
    local minute = math.floor((env.time or 0) / 60000)
    if minute ~= clockMinute then
        clockMinute = minute
        clockStr = os.date("%H:%M", math.floor((env.time or 0) / 1000))
    end
    canvas:setTextSize(h * CONFIG.clockSizeRatio)
    canvas:setTextAlign(1)
    canvas:setColor("#FFFFFF")
    canvas:setAlpha(200)
    canvas:drawText(clockStr, w / 2, h * 0.3 + canvas:getTextCenterOffset())
    canvas:setTextAlign(0)
    canvas:setAlpha(255)

    -- ============ 背景微尘: 缓慢漂移 + 呼吸明灭 ============
    -- 以短边为幅度基准, 均匀分布在中部偏上 (时钟与歌词之间)
    local s = math.min(w, h)
    canvas:setColor("#FFFFFF")
    for i = 1, CONFIG.particleCount do
        local seed = i * 7.31
        local speed = 1 + (i % 4) * 0.25
        local px = w * 0.5 + math.sin(t * 0.00004 * speed + seed) * s * 0.48
        local py = h * 0.46 + (math.sin(t * 0.00007 * speed + seed * 1.7) * 0.5 + 0.5) * s * 0.3
        local pr = 1 + (i % 3)
        local pa = 10 + (math.sin(t * 0.0001 * speed + seed * 2.3) * 0.5 + 0.5) * 12
        canvas:setAlpha(math.floor(pa))
        canvas:drawCircle(px, py, pr, "#FFFFFF")
    end
    canvas:setAlpha(255)

    local lineIdx = (state.currentLyricIndex or -1) + 1
    local lineObj = global_lyrics and global_lyrics[lineIdx]
    if not lineObj then
        -- 占位: 歌名居中呼吸 (在时钟下方)
        local fontSize = h * 0.04
        canvas:setTextSize(fontSize)
        canvas:setTextAlign(1)
        local scale = 1 + math.sin(t * 0.0015) * 0.02
        canvas:save()
        canvas:translate(w / 2, h * 0.62)
        canvas:scale(scale, scale)
        canvas:translate(-w / 2, -h * 0.62)
        canvas:setColor("#FFFFFF")
        canvas:setAlpha(200)
        canvas:drawText(state.title, w / 2, h * 0.62 + canvas:getTextCenterOffset())
        canvas:restore()
        canvas:setTextAlign(0)
        canvas:setAlpha(255)
        return
    end

    local pos = state.position or 0

    -- ============ 恒定字号: 超宽自动换行, 不缩放 ============
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

    -- 测量
    local widths = {}
    for i, u in ipairs(units) do
        widths[i] = canvas:measureText(u.text)
    end

    -- 按行宽自动换行分组
    local maxW = w * 0.9
    local lines = {}          -- { start, count, width }
    local cur = { start = 1, count = 0, width = 0 }
    for i = 1, #units do
        if cur.width + widths[i] > maxW and cur.count > 0 then
            table.insert(lines, cur)
            cur = { start = i, count = 1, width = widths[i] }
        else
            cur.count = cur.count + 1
            cur.width = cur.width + widths[i]
        end
    end
    table.insert(lines, cur)

    -- 多行块垂直居中于中下部 (垂直中心 62% 处)
    local lineHeight = fontSize * 1.5
    local blockH = #lines * lineHeight
    local startY = h * 0.62 - blockH / 2 + lineHeight / 2 + canvas:getTextCenterOffset()

    -- 进出场: 600ms 缓入上滑 / 缓出上移
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

    -- ============ 逐字卡拉OK (多行) ============
    for li, line in ipairs(lines) do
        local lineY = startY + (li - 1) * lineHeight + dy
        local x = (w - line.width) / 2
        for j = line.start, line.start + line.count - 1 do
            local u = units[j]
            canvas:setColor(CONFIG.baseColor)
            canvas:drawText(u.text, x, lineY)

            local prog = 0
            if pos >= u.st then
                if pos >= u.et then prog = 1
                elseif u.et > u.st then prog = (pos - u.st) / (u.et - u.st)
                else prog = 1 end
            end
            if prog > 0 then
                canvas:setColor(CONFIG.activeColor)
                canvas:save()
                canvas:clipRect(x, lineY - fontSize * 1.2, x + widths[j] * prog, lineY + fontSize * 0.8)
                canvas:drawText(u.text, x, lineY)
                canvas:restore()
            end
            x = x + widths[j]
        end
    end
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
