-- [[ Lyric Scene V1: 高性能原生渲染模板 ]]
--
-- 两种模式 (自动检测, 按本文件是否定义 onDraw 决定):
--
-- [模式 A] 声明式场景 (默认, 本模板): 性能最优
--   脚本只在加载时执行一次, 产出场景描述;
--   每帧热路径由原生 Kotlin 渲染器执行。
--   数值字段可写常量或表达式 (每帧求值):
--     变量: t(行进度) pos(播放位置ms) time(系统时间ms) dur(行时长ms)
--           playing w h alpha(进出场透明度) offsetY(进出场偏移px)
--     函数: sin cos tan sqrt abs floor ceil frac min max pow
--           clamp lerp map smoothstep
--
-- [模式 B] Lua 每帧脚本 (定义 onDraw 即自动启用): 任意效果
--   每帧执行完整 Lua 逻辑, 绘制命令由原生批量回放, 性能仍然可控。
--   可用 API:
--     canvas: setColor setAlpha setTextSize setTextAlign setStyle
--             setShadow setLinearGradient setTypeface setStrokeWidth
--             drawText drawRect drawCircle drawLine drawArc drawColor
--             save restore translate scale rotate clipRect
--             measureText measureTextHeight getTextCenterOffset
--             beginPath moveTo lineTo quadTo closePath drawPath
--             drawKaraokeLine
--     anim:  lerp map clamp sineWave pulse easeInQuad easeOutQuad
--            easeInOutSine easeOutBack
--     tools: toChars loadFont
--     state: title artist currentTime totalTime isPlaying position
--            duration progress volume playMode isLiked likedCount
--            currentLyricIndex currentWordIndex lyric lineStart lineEnd
--            lineProgress
--     env:   time systemTime width height isPortrait anim
--     global_lyrics: 歌词表 (1 起始, 含 startTime/endTime/text/words[st,et])

local fontName = "SourceHanSansCN-Normal.ttf"

scene:setFont(fontName, 90)
scene:backgroundColor("#000000")

scene:lyric {
    fontSize = 90,
    baseColor = "#888888",
    highlightColor = "#FFFFFF",
    -- shadow = "#FF3366",   -- 需要辉光时取消注释 (每帧对高亮字生成阴影, 有性能开销)
    lineHeight = 1.5,
    maxWidth = 0.85,
    y = "h / 2 + offsetY",
    fillByWord = true,
}

scene:placeholder {
    fontSize = 60,
    color = "#FFFFFF",
    breath = true,
}
