-- [[ Lyric Scene V1: 高性能原生渲染模板 ]]
-- 本脚本只在加载时执行一次 (启动 / 文件修改), 产出场景描述;
-- 每帧热路径由原生 Kotlin 渲染器执行, 这里不写每帧逻辑。
--
-- 数值字段可写常量或表达式 (每帧求值):
--   变量: t(行进度) pos(播放位置ms) time(系统时间ms) dur(行时长ms)
--         playing w h alpha(进出场透明度) offsetY(进出场偏移px)
--   函数: sin cos tan sqrt abs floor ceil frac min max pow
--         clamp lerp map smoothstep

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
