-- [[ Lyric Scene · 微光 (Stardust) ]]
-- 脚本模式 (定义 onDraw): 竖屏布局, 黑白简约, 仿息屏 (AOD) 展示。
-- 布局: 居中偏上大时钟 + 中部微尘漂移 + 时钟下方的歌词 (卡拉OK)
--
-- v2 自适应排版 (彻底解决歌词压时钟):
--   1) 先按时钟尺寸算出"安全歌词区"(时钟下沿 + 间距 → 屏底 90%), 歌词绝不进入该区域;
--   2) 在该区域内按可用高度自动缩小字号 (等比回退到 minFontSizeRatio);
--   3) 缩到最小仍放不下时, 只显示当前行附近的一个"行窗口", 并上下淡出;
--   4) 最终绘制前再 clipRect 硬裁剪到歌词区, 物理上不可能叠到时钟。
--
-- 可调参数集中在 CONFIG:
--   fontSizeRatio     歌词字号基准 (屏高比例)
--   minFontSizeRatio  自适应缩小的下限
--   clockSizeRatio    时钟字号
--   particleCount     背景微尘数量

-- 本场景是否旋转 90° 绘制。横屏副本会把它改成 true;
-- 首行 -- rotation: 90 的声明只影响手势映射, 不影响这里的绘制。
local ROTATE = false

local CONFIG = {
    font = "SourceHanSansCN-Normal.ttf",
    fontSizeRatio = 0.075,
    minFontSizeRatio = 0.030,
    clockSizeRatio = 0.12,
    baseColor = "#5A5A5A",
    activeColor = "#FFFFFF",
    particleCount = 18,
    -- 音频频谱柱: 播放时自动采集 (无需开关), 暂停/静音时自动不绘制
    spectrumBars = true,
    -- 频谱折线采样点数量 (越密曲线越平滑)
    spectrumPoints = 48,
}

local font = tools.loadFont(CONFIG.font)

-- 时钟缓存: 分钟未变化不重新格式化
local clockStr = ""
local clockMinute = -1

-- 时钟: 作为文字蒙版的形状来源与底层灰字。
-- 先画灰色底字, 频谱柱随后绘制: 轮廓内高亮白, 轮廓外灰色。
local function clockBaseline(h)
    canvas:setTextSize(h * CONFIG.clockSizeRatio)
    return h * 0.3 + canvas:getTextCenterOffset()
end

local function drawClockSilhouette(w, h, now)
    local minute = math.floor(now / 60000)
    if minute ~= clockMinute then
        clockMinute = minute
        clockStr = os.date("%H:%M", math.floor(now / 1000))
    end
    canvas:setTextSize(h * CONFIG.clockSizeRatio)
    canvas:setTextAlign(1)
    canvas:setColor(CONFIG.baseColor)
    canvas:setAlpha(230)
    canvas:drawText(clockStr, w / 2, clockBaseline(h))
    canvas:setTextAlign(0)
    canvas:setAlpha(255)
end

-- 频谱柱: 整幅铺在时钟区域。
-- 数字轮廓外: 灰色; 数字轮廓内(clipText 文字蒙版): 高亮白色。
local function drawSpectrum(state, w, h)
    if not (state and state.spectrum) then return end
    local points = CONFIG.spectrumPoints
    local baseline = clockBaseline(h)
    local maxH = h * CONFIG.clockSizeRatio * 0.95
    local baseY = baseline + h * 0.008

    -- 16 个频段线性插值成 points 个采样点, 得到折线的 y 坐标
    local ys = {}
    for i = 1, points do
        local t = (i - 1) / (points - 1)
        local src = t * 15 + 1
        local lo = math.floor(src)
        if lo > 15 then lo = 15 end
        local frac = src - lo
        local v1 = state.spectrum[lo] or 0
        local v2 = state.spectrum[lo + 1] or v1
        local v = v1 + (v2 - v1) * frac
        ys[i] = baseY - maxH * math.min(1, v * 1.6)
    end

    local step = w / (points - 1)

    -- 只在数字轮廓内可见: 没被文字蒙到的部分完全不绘制
    canvas:setTextSize(h * CONFIG.clockSizeRatio)
    local textWidth = canvas:measureText(clockStr)
    canvas:setTextAlign(0)
    canvas:save()
    canvas:clipText(clockStr, (w - textWidth) / 2, baseline)
    canvas:setStyle(0)

    -- 面积填充: 平滑折线 (相邻采样点的中点作为二次贝塞尔终点)
    canvas:beginPath()
    canvas:moveTo(0, baseY)
    canvas:lineTo(0, ys[1])
    for i = 2, points - 1 do
        local midX = (i - 1) * step + step / 2
        local midY = (ys[i] + ys[i + 1]) / 2
        canvas:quadTo((i - 1) * step, ys[i], midX, midY)
    end
    canvas:lineTo(w, ys[points])
    canvas:lineTo(w, baseY)
    canvas:closePath()

    -- 上亮下淡的垂直渐变 (先重置 alpha, 否则会继承上一段绘制的透明度)
    canvas:setAlpha(255)
    canvas:setLinearGradient(
        0, baseY - maxH,
        0, baseY,
        "#FFFFFFFF", "#99FFFFFF"
    )
    canvas:drawPath()
    canvas:setAlpha(255)
    canvas:restore()
end

-- 歌词区: 时钟下沿 + 间距 → 屏底 90%
local function lyricArea(w, h)
    local clockSize = h * CONFIG.clockSizeRatio
    local top = h * 0.30 + clockSize * 0.5 + h * 0.045
    local bottom = h * 0.90
    local minH = h * 0.10
    if bottom - top < minH then
        top = h * 0.42
        bottom = h * 0.90
    end
    return top, bottom
end

-- 按给定字号把逐字单元折行; 返回 rows 与每个单元的宽度
local function wrapUnits(units, fontSize, maxWidth)
    canvas:setTextSize(fontSize)
    local widths = {}
    for i = 1, #units do
        widths[i] = canvas:measureText(units[i].text)
    end
    local rows = {}
    local cur = { start = 1, count = 0, width = 0 }
    for i = 1, #units do
        if cur.count > 0 and cur.width + widths[i] > maxWidth then
            table.insert(rows, cur)
            cur = { start = i, count = 1, width = widths[i] }
        else
            cur.count = cur.count + 1
            cur.width = cur.width + widths[i]
        end
    end
    if cur.count > 0 then
        table.insert(rows, cur)
    end
    return rows, widths
end

function onDraw(state, env)
    local w, h = env.width, env.height
    local t = env.systemTime
    canvas:drawColor("#000000")
    if font then canvas:setTypeface(font) else canvas:setTypeface(nil) end

    -- 是否旋转 90° 绘制由**脚本自己**决定 (横屏副本会把上面的 ROTATE 改成 true);
    -- 首行 -- rotation: 90 只是给上层做手势方向映射用的声明, 不参与绘制。
    if ROTATE then
        canvas:save()
        canvas:translate(w, 0)
        canvas:rotate(90)
        w, h = h, w
    end

    -- ============ 背景微尘: 缓慢漂移 + 呼吸明灭 ============
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

    -- 时钟灰字底衬 (频谱柱会覆盖其轮廓内的部分)
    drawClockSilhouette(w, h, env.time or 0)

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
        drawClockSilhouette(w, h, env.time or 0)
        return
    end

    -- ============ 逐字单元 (优先字级, 否则按字符切) ============
    local units = lineObj.words
    if not units or #units == 0 then
        units = {}
        local chars = tools.toChars(lineObj.text or "")
        for i, v in ipairs(chars) do
            units[i] = { text = v, st = lineObj.startTime, et = lineObj.endTime }
        end
    end
    if #units == 0 then
        drawClockSilhouette(w, h, env.time or 0)
        return
    end

    local pos = state.position or 0
    local areaTop, areaBottom = lyricArea(w, h)
    local areaH = areaBottom - areaTop
    local maxWidth = w * 0.9

    -- ============ 自适应字号: 放得下就用大字号, 否则等比缩小 ============
    local fontSize = h * CONFIG.fontSizeRatio
    local minFontSize = h * CONFIG.minFontSizeRatio
    local rows, widths, lineHeight

    while true do
        rows, widths = wrapUnits(units, fontSize, maxWidth)
        lineHeight = fontSize * 1.5
        if #rows * lineHeight <= areaH or fontSize <= minFontSize then
            break
        end
        fontSize = math.max(minFontSize, fontSize * 0.94)
    end

    -- ============ 仍放不下: 只显示当前行附近的窗口 ============
    local maxRows = math.max(1, math.floor(areaH / lineHeight))
    local firstRow, lastRow = 1, #rows
    if #rows > maxRows then
        -- 以"已唱完的最后一行"的下一行作为锚点
        local anchorRow = 1
        for i = 1, #rows do
            local lastUnit = units[rows[i].start + rows[i].count - 1]
            if lastUnit and pos >= lastUnit.et then
                anchorRow = math.min(#rows, i + 1)
            end
        end
        firstRow = math.max(1, anchorRow - math.floor(maxRows / 2))
        lastRow = math.min(#rows, firstRow + maxRows - 1)
        firstRow = math.max(1, lastRow - maxRows + 1)
    end

    local visibleRows = lastRow - firstRow + 1
    local blockH = visibleRows * lineHeight
    local startY = areaTop + (areaH - blockH) / 2 + lineHeight / 2 + canvas:getTextCenterOffset()
    startY = math.max(areaTop + lineHeight * 0.6, startY)

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

    -- ============ 绘制 (硬裁剪到歌词区, 保证不压时钟) ============
    canvas:setTextSize(fontSize)
    canvas:setTextAlign(0)
    canvas:setAlpha(alpha)
    canvas:save()
    canvas:clipRect(0, areaTop, w, areaBottom)

    for li = firstRow, lastRow do
        local row = rows[li]
        local lineY = startY + (li - firstRow) * lineHeight + dy
        local x = (w - row.width) / 2
        for j = row.start, row.start + row.count - 1 do
            local u = units[j]
            canvas:setColor(CONFIG.baseColor)
            canvas:drawText(u.text, x, lineY)

            local prog = 0
            if pos >= u.st then
                if pos >= u.et then
                    prog = 1
                elseif u.et > u.st then
                    prog = (pos - u.st) / (u.et - u.st)
                else
                    prog = 1
                end
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

    canvas:restore()
    canvas:setAlpha(255)

    -- 频谱: 数字轮廓内高亮白, 轮廓外灰色 (以时钟文字作蒙版)
    if CONFIG.spectrumBars then
        drawSpectrum(state, w, h)
    end

    if ROTATE then
        canvas:restore()
    end
end
