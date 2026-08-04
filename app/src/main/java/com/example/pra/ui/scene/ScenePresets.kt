package com.example.pra.ui.scene

/**
 * 息屏歌词预设主题 (Lua 脚本模式, 定义 onDraw)
 *
 * 使用方式: 把任一预设的内容整体替换设备上的 scene.lua:
 *   /storage/emulated/0/Android/data/com.example.pra/files/lua_scripts/scene.lua
 * (改文件后 3 秒内自动重载, 无需重启 App)
 */
object ScenePresets {

    // ============================================================
    // 预设 01 · 纯白极简 (Classic Minimal)
    // 黑底白字 · 逐字卡拉OK · 顶部歌名 · 底部整曲进度线
    // ============================================================
    val CLASSIC_MINIMAL = """
-- 预设 01 · 纯白极简 (Classic Minimal)
local CONFIG = {
    font = "SourceHanSansCN-Normal.ttf",
    fontSizeRatio = 0.075,
    titleSizeRatio = 0.035,
    baseColor = "#5A5A5A",
    activeColor = "#FFFFFF",
    maxWidthRatio = 0.85,
    lineWidthRatio = 0.8,
}

local font = tools.loadFont(CONFIG.font)

function onDraw(state, env)
    local w, h = env.width, env.height
    canvas:drawColor("#000000")
    if font then canvas:setTypeface(font) else canvas:setTypeface(nil) end

    -- 顶部: 歌名 · 歌手
    local title = (state.title or "") .. " · " .. (state.artist or "")
    canvas:setTextSize(h * CONFIG.titleSizeRatio)
    canvas:setTextAlign(1)
    canvas:setColor("#FFFFFF")
    canvas:setAlpha(90)
    canvas:drawText(title, w / 2, h * 0.07)
    canvas:setTextAlign(0)
    canvas:setAlpha(255)

    local lineIdx = (state.currentLyricIndex or -1) + 1
    local lineObj = global_lyrics and global_lyrics[lineIdx]
    if not lineObj then
        -- 占位: 歌名居中呼吸
        local fontSize = h * 0.05
        canvas:setTextSize(fontSize)
        canvas:setTextAlign(1)
        local scale = 1 + math.sin(env.systemTime * 0.0015) * 0.02
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
    local maxW = w * CONFIG.maxWidthRatio
    local x = totalW > maxW and w * 0.05 or (w - totalW) / 2

    local y = h / 2 + canvas:getTextCenterOffset()

    -- 进出场 (淡入上滑 / 淡出上移)
    local alpha, dy = 255, 0
    if pos < lineObj.startTime + 400 then
        local t = anim:clamp((pos - lineObj.startTime) / 400, 0, 1)
        alpha = math.floor(t * 255)
        dy = (1 - t) * fontSize * 0.4
    elseif pos > lineObj.endTime - 300 then
        local t = anim:clamp((pos - (lineObj.endTime - 300)) / 300, 0, 1)
        alpha = math.floor((1 - t) * 255)
        dy = -t * fontSize * 0.4
    end
    canvas:setAlpha(alpha)

    -- 逐字卡拉OK
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

    -- 底部整曲进度线
    local lineW = w * CONFIG.lineWidthRatio
    local lineY = h * 0.93
    canvas:setColor("#FFFFFF")
    canvas:setAlpha(30)
    canvas:drawRect((w - lineW) / 2, lineY, (w + lineW) / 2, lineY + 2, "#FFFFFF")
    canvas:setAlpha(255)
    local prog = anim:clamp(state.progress or 0, 0, 1)
    canvas:drawRect((w - lineW) / 2, lineY, (w - lineW) / 2 + lineW * prog, lineY + 2, "#FFFFFF")
end
    """.trimIndent()

    // ============================================================
    // 预设 02 · 呼吸光晕 (Breath Glow)
    // 文字整体轻微呼吸 · 唱到的字带柔光 · 背景极淡圆缓慢脉动
    // ============================================================
    val BREATH_GLOW = """
-- 预设 02 · 呼吸光晕 (Breath Glow)
local CONFIG = {
    font = "SourceHanSansCN-Normal.ttf",
    fontSizeRatio = 0.08,
    baseColor = "#6B6B6B",
    activeColor = "#FFFFFF",
    glowColor = "#66FFFFFF",
    breatheSpeed = 0.0012,
    breatheRange = 0.015,
}

local font = tools.loadFont(CONFIG.font)

function onDraw(state, env)
    local w, h = env.width, env.height
    canvas:drawColor("#000000")
    if font then canvas:setTypeface(font) else canvas:setTypeface(nil) end

    -- 背景: 极淡圆缓慢脉动
    local t = env.systemTime * 0.0008
    local bgR = w * 0.32 * (1 + math.sin(t) * 0.04)
    canvas:setColor("#FFFFFF")
    canvas:setAlpha(7)
    canvas:drawCircle(w / 2, h * 0.46, bgR, "#FFFFFF")
    canvas:setAlpha(255)

    local lineIdx = (state.currentLyricIndex or -1) + 1
    local lineObj = global_lyrics and global_lyrics[lineIdx]
    if not lineObj then
        local fontSize = h * 0.05
        canvas:setTextSize(fontSize)
        canvas:setTextAlign(1)
        local scale = 1 + math.sin(env.systemTime * 0.0015) * 0.025
        canvas:save()
        canvas:translate(w / 2, h / 2)
        canvas:scale(scale, scale)
        canvas:translate(-w / 2, -h / 2)
        canvas:setColor("#FFFFFF")
        canvas:setAlpha(190)
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

    local widths = {}
    local totalW = 0
    for i, u in ipairs(units) do
        widths[i] = canvas:measureText(u.text)
        totalW = totalW + widths[i]
    end
    local maxW = w * 0.85
    local x = totalW > maxW and w * 0.05 or (w - totalW) / 2
    local y = h / 2 + canvas:getTextCenterOffset()

    -- 整行呼吸缩放 (绕中心)
    local scale = 1 + math.sin(env.systemTime * CONFIG.breatheSpeed) * CONFIG.breatheRange
    canvas:save()
    canvas:translate(w / 2, h / 2)
    canvas:scale(scale, scale)
    canvas:translate(-w / 2, -h / 2)

    -- 进出场透明度
    local alpha = 255
    if pos < lineObj.startTime + 400 then
        alpha = math.floor(anim:clamp((pos - lineObj.startTime) / 400, 0, 1) * 255)
    elseif pos > lineObj.endTime - 300 then
        alpha = math.floor((1 - anim:clamp((pos - (lineObj.endTime - 300)) / 300, 0, 1)) * 255)
    end
    canvas:setAlpha(alpha)

    for i, u in ipairs(units) do
        local prog = 0
        if pos >= u.st then
            if pos >= u.et then prog = 1
            elseif u.et > u.st then prog = (pos - u.st) / (u.et - u.st)
            else prog = 1 end
        end

        -- 唱到的字: 柔光 + 高亮 (柔光仅作用于当前活动字, 每帧一次, 开销可控)
        if prog > 0 and prog < 1 then
            canvas:setColor(CONFIG.activeColor)
            canvas:setShadow(fontSize * 0.12, 0, 0, CONFIG.glowColor)
            canvas:drawText(u.text, x, y)
            canvas:clearShadow()
        elseif prog >= 1 then
            canvas:setColor(CONFIG.activeColor)
            canvas:drawText(u.text, x, y)
        else
            canvas:setColor(CONFIG.baseColor)
            canvas:drawText(u.text, x, y)
        end
        x = x + widths[i]
    end

    canvas:restore()
    canvas:setAlpha(255)
end
    """.trimIndent()

    // ============================================================
    // 预设 03 · 滑入时间线 (Slide Timeline)
    // 切行整行从左滑入 · 歌词下方圆点时间线标记每个字
    // ============================================================
    val SLIDE_TIMELINE = """
-- 预设 03 · 滑入时间线 (Slide Timeline)
local CONFIG = {
    font = "SourceHanSansCN-Normal.ttf",
    fontSizeRatio = 0.07,
    baseColor = "#666666",
    activeColor = "#FFFFFF",
    dimColor = "#3A3A3A",
    slideMs = 350,
}

local font = tools.loadFont(CONFIG.font)

function onDraw(state, env)
    local w, h = env.width, env.height
    canvas:drawColor("#000000")
    if font then canvas:setTypeface(font) else canvas:setTypeface(nil) end

    local lineIdx = (state.currentLyricIndex or -1) + 1
    local lineObj = global_lyrics and global_lyrics[lineIdx]
    if not lineObj then
        local fontSize = h * 0.05
        canvas:setTextSize(fontSize)
        canvas:setTextAlign(1)
        canvas:setColor("#FFFFFF")
        canvas:setAlpha(200)
        canvas:drawText(state.title, w / 2, h / 2 + canvas:getTextCenterOffset())
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

    local widths = {}
    local totalW = 0
    for i, u in ipairs(units) do
        widths[i] = canvas:measureText(u.text)
        totalW = totalW + widths[i]
    end
    local maxW = w * 0.85
    local baseX = totalW > maxW and w * 0.05 or (w - totalW) / 2

    -- 切行滑入: 整行从左滑到目标位置
    local slide = 1
    if pos < lineObj.startTime + CONFIG.slideMs then
        slide = anim:clamp((pos - lineObj.startTime) / CONFIG.slideMs, 0, 1)
    end
    local x = baseX - (1 - slide) * totalW * 0.12

    local y = h / 2 + canvas:getTextCenterOffset()
    local alpha = math.floor(anim:clamp(slide + 0.3, 0, 1) * 255)
    canvas:setAlpha(alpha)

    -- 逐字卡拉OK
    for i, u in ipairs(units) do
        canvas:setColor(CONFIG.baseColor)
        canvas:drawText(u.text, x, y)
        local prog = 0
        if pos >= u.st then
            if pos >= u.et then prog = 1
            elseif u.et > u.st then prog = (pos - u.st) / (u.et - u.st)
            else prog = 1 end
        end
        if prog > 0 then
            canvas:setColor(CONFIG.activeColor)
            canvas:save()
            canvas:clipRect(x, y - fontSize * 1.2, x + widths[i] * prog, y + fontSize * 0.8)
            canvas:drawText(u.text, x, y)
            canvas:restore()
        end
        x = x + widths[i]
    end
    canvas:setAlpha(255)

    -- 圆点时间线: 每字一个圆点, 已唱亮白 / 当前半透白 / 未唱暗灰
    local dotY = h / 2 + fontSize * 1.15
    local dotR = 3
    local dotX = baseX
    for i, u in ipairs(units) do
        local c = CONFIG.dimColor
        if pos >= u.et then
            c = CONFIG.activeColor
            canvas:setAlpha(120)
        elseif pos >= u.st then
            c = CONFIG.activeColor
            canvas:setAlpha(255)
        else
            canvas:setAlpha(150)
        end
        canvas:drawCircle(dotX + widths[i] / 2, dotY, dotR, c)
        canvas:setAlpha(255)
        dotX = dotX + widths[i]
    end
end
    """.trimIndent()

    // ============================================================
    // 预设 04 · 均衡器 (EQ Pulse)
    // 底部细竖线模拟均衡器随行律动 · 歌词整行高亮填充
    // ============================================================
    val EQ_PULSE = """
-- 预设 04 · 均衡器 (EQ Pulse)
local CONFIG = {
    font = "SourceHanSansCN-Normal.ttf",
    fontSizeRatio = 0.07,
    baseColor = "#5F5F5F",
    activeColor = "#FFFFFF",
    barCount = 44,
    barMaxHeightRatio = 0.09,
    speed = 0.9,
}

local font = tools.loadFont(CONFIG.font)

function onDraw(state, env)
    local w, h = env.width, env.height
    canvas:drawColor("#000000")
    if font then canvas:setTypeface(font) else canvas:setTypeface(nil) end

    -- 底部均衡器: 两段正弦叠加模拟律动
    local t = env.systemTime * 0.001 * CONFIG.speed
    local barW = w / CONFIG.barCount
    local maxH = h * CONFIG.barMaxHeightRatio
    local baseY = h * 0.96
    canvas:setColor("#FFFFFF")
    for i = 0, CONFIG.barCount - 1 do
        local phase = i * 0.35
        local v = math.abs(math.sin(t * 2 + phase)) * 0.7 + math.abs(math.sin(t * 1.3 + phase * 1.7)) * 0.3
        local bh = v * maxH
        canvas:setAlpha(55 + math.floor(v * 80))
        canvas:drawRect(i * barW + 1, baseY - bh, (i + 1) * barW - 1, baseY, "#FFFFFF")
    end
    canvas:setAlpha(255)

    local lineIdx = (state.currentLyricIndex or -1) + 1
    local lineObj = global_lyrics and global_lyrics[lineIdx]
    if not lineObj then
        local fontSize = h * 0.05
        canvas:setTextSize(fontSize)
        canvas:setTextAlign(1)
        canvas:setColor("#FFFFFF")
        canvas:setAlpha(200)
        canvas:drawText(state.title, w / 2, h * 0.42 + canvas:getTextCenterOffset())
        canvas:setTextAlign(0)
        canvas:setAlpha(255)
        return
    end

    local pos = state.position or 0
    local fontSize = h * CONFIG.fontSizeRatio
    canvas:setTextSize(fontSize)

    -- 整行高亮填充 (行级进度, 简约)
    local text = lineObj.text
    local textW = canvas:measureText(text)
    local maxW = w * 0.85
    local x = textW > maxW and w * 0.05 or (w - textW) / 2
    local y = h * 0.42 + canvas:getTextCenterOffset()

    -- 进出场
    local alpha = 255
    local dy = 0
    if pos < lineObj.startTime + 400 then
        local k = anim:clamp((pos - lineObj.startTime) / 400, 0, 1)
        alpha = math.floor(k * 255)
        dy = (1 - k) * fontSize * 0.35
    elseif pos > lineObj.endTime - 300 then
        local k = anim:clamp((pos - (lineObj.endTime - 300)) / 300, 0, 1)
        alpha = math.floor((1 - k) * 255)
        dy = -k * fontSize * 0.35
    end
    canvas:setAlpha(alpha)

    canvas:setColor(CONFIG.baseColor)
    canvas:drawText(text, x, y + dy)

    local lineProg = anim:clamp(state.lineProgress or 0, 0, 1)
    if lineProg > 0 then
        canvas:setColor(CONFIG.activeColor)
        canvas:save()
        canvas:clipRect(x, y + dy - fontSize * 1.2, x + textW * lineProg, y + dy + fontSize * 0.8)
        canvas:drawText(text, x, y + dy)
        canvas:restore()
    end
    canvas:setAlpha(255)
end
    """.trimIndent()

    /** 全部预设 (按展示顺序) */
    val all: List<Pair<String, String>> = listOf(
        "纯白极简" to CLASSIC_MINIMAL,
        "呼吸光晕" to BREATH_GLOW,
        "滑入时间线" to SLIDE_TIMELINE,
        "均衡器" to EQ_PULSE
    )
}
