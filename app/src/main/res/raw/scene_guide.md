# 场景开发指南 (Scene Development Guide)

本文档指导你开发 Lyric Scene 场景文件。场景文件 `scene.lua` 位于:

```
/storage/emulated/0/Android/data/com.example.pra/files/lua_scripts/scene.lua
```

- 文件被修改保存后 **立即热重载**(FileObserver 监听,无需重启 App)
- 场景出错时自动回退默认模板,错误原因写入同目录 `scene_error.log`

---

## 1. 两种模式

场景文件 **自动检测**:是否定义了 `onDraw` 函数。

| 模式 | 触发条件 | 运行方式 | 适合 |
|---|---|---|---|
| **声明式场景** | 未定义 `onDraw` | 脚本只在加载时执行一次,产出场景描述;每帧由原生 Kotlin 渲染 | 参数化视觉风格(配色/字号/特效开关) |
| **Lua 脚本模式** | 定义了 `onDraw` | 每帧执行完整 Lua 逻辑,绘制命令由原生批量回放 | 任意效果(粒子/路径/逐帧动画/复杂逻辑) |

两种模式性能都足以跑满 60fps,脚本模式每帧额外开销约 0.5~1ms。

---

## 2. 模式 A: 声明式场景

### 2.1 完整示例

```lua
-- 字体 (assets 相对路径或绝对路径; 可选)
scene:setFont("SourceHanSansCN-Normal.ttf", 90)

-- 背景色 (可选)
scene:backgroundColor("#000000")

-- 当前行歌词节点 (可选)
scene:lyric {
    fontSize = 90,              -- 数字 或 表达式字符串
    baseColor = "#888888",      -- 未唱字颜色
    highlightColor = "#FFFFFF", -- 已唱字颜色
    -- shadow = "#FF3366",      -- 辉光颜色 (取消注释启用; 每帧对活动字生成阴影, 有性能开销)
    lineHeight = 1.5,           -- 行距倍数
    maxWidth = 0.85,            -- 最大行宽 (屏宽比例)
    y = "h / 2 + offsetY",      -- 垂直位置表达式
    fillByWord = true,          -- true=逐字填充, false=整行填充
}

-- 无歌词时的占位节点 (可选)
scene:placeholder {
    text = "Enjoy Music",       -- 留空则用歌曲标题
    fontSize = 60,
    color = "#FFFFFF",
    breath = true,              -- 呼吸缩放
    y = 0,                      -- 相对屏幕中心的垂直偏移
}

-- 叠加元素 (可多个)
scene:text    { text = "00:00", x = 30, y = 60, fontSize = 40, color = "#FFFFFF", align = 0, alpha = 1 }
scene:rect    { l = 0, t = 0, r = 200, b = 80, color = "#1A1A1A", radius = 12, alpha = 1 }
scene:circle  { cx = "w / 2", cy = 100, r = 30, color = "#FFFFFF", alpha = 1 }
```

### 2.2 节点字段总表

**scene:lyric{}**

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| fontSize | 数字/表达式 | 90 | 歌词字号 |
| baseColor | 颜色 | #888888 | 未唱字颜色 |
| highlightColor | 颜色 | #FFFFFF | 已唱字颜色 |
| shadow | 颜色 | 无 | 辉光颜色,不填则无辉光 |
| lineHeight | 数字 | 1.5 | 行距倍数 |
| maxWidth | 数字 | 0.85 | 最大行宽占屏宽比例 |
| y | 表达式 | "h / 2 + offsetY" | 歌词块垂直中心 |
| fillByWord | 布尔 | true | 逐字填充或整行填充 |

**scene:placeholder{}**

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| text | 字符串 | 歌曲标题 | 占位文本 |
| fontSize | 数字/表达式 | 60 | 字号 |
| color | 颜色 | #FFFFFF | 颜色 |
| breath | 布尔 | true | 呼吸缩放 |
| y | 数字/表达式 | 0 | 相对屏幕中心的偏移 |

**scene:text{}**: `text`(字符串) `x` `y` `fontSize`(数字/表达式) `color` `align`(0=左 1=中 2=右) `alpha`(数字/表达式)

**scene:rect{}**: `l` `t` `r` `b`(数字/表达式) `color` `radius`(数字/表达式) `alpha`

**scene:circle{}**: `cx` `cy` `r`(数字/表达式) `color` `alpha`

**颜色格式**: `"#RRGGBB"` / `"#AARRGGBB"` / 数字 (`0xFFFFFFFF`)

### 2.3 表达式系统

所有数值字段可写**表达式字符串**,每帧原生求值(零分配)。

**变量**:

| 变量 | 含义 |
|---|---|
| `t` | 当前行进度 0~1 |
| `pos` | 播放位置 (毫秒, 帧内平滑推演) |
| `time` | 单调系统时间 (毫秒) |
| `dur` | 当前行时长 (毫秒) |
| `playing` | 1=播放中 0=暂停 |
| `w` `h` | 屏幕宽 / 高 |
| `alpha` | 进出场透明度 0~1 |
| `offsetY` | 进出场垂直偏移 (px) |

**函数**: `sin cos tan sqrt abs floor ceil frac` `min max pow` `clamp lerp map smoothstep`

**示例**:
```lua
y = "h / 2 + sin(t * 6.28) * 30"        -- 上下浮动
alpha = "1 - t"                          -- 随行进度淡出
fontSize = "w * 0.08 + sin(time * 0.001) * 4"   -- 动态字号
```

---

## 3. 模式 B: Lua 脚本模式

定义 `function onDraw(state, env)` 即启用。每帧执行,绘制命令由原生批量回放。

### 3.1 canvas API(绘制命令,全部通过命令缓冲执行)

**样式**:
| 方法 | 说明 |
|---|---|
| `canvas:setTypeface(font)` | 设置字体 (tools.loadFont 返回值; nil=默认) |
| `canvas:setColor(color)` | 颜色 (字符串或数字) |
| `canvas:setAlpha(a)` | 透明度 0~255 |
| `canvas:setTextSize(size)` | 字号 |
| `canvas:setStrokeWidth(w)` | 描边宽度 |
| `canvas:setStyle(s)` | 0=填充 1=描边 2=填充+描边 |
| `canvas:setTextAlign(a)` | 0=左 1=中 2=右 |
| `canvas:setShadow(r, dx, dy, color)` | 阴影/发光 (每帧调用有开销, 尽量只对少量元素) |
| `canvas:clearShadow()` | 清除阴影 |
| `canvas:setLinearGradient(x0,y0,x1,y1,c1,c2)` | 线性渐变填充 |

**基础绘制**:
| 方法 | 说明 |
|---|---|
| `canvas:drawColor(color)` | 全屏填充 (背景) |
| `canvas:drawText(text, x, y)` | 文本 (y 为基线) |
| `canvas:drawRect(l, t, r, b, color)` | 矩形 |
| `canvas:drawRoundRect(l, t, r, b, rad)` | 圆角矩形 |
| `canvas:drawCircle(cx, cy, r, color)` | 圆 |
| `canvas:drawLine(x1, y1, x2, y2)` | 直线 |
| `canvas:drawArc(l, t, r, b, start, sweep, useCenter, color)` | 圆弧 |

**变换与裁剪**:
| 方法 | 说明 |
|---|---|
| `canvas:save()` / `canvas:restore()` | 状态栈 (必须成对) |
| `canvas:translate(dx, dy)` | 平移 |
| `canvas:scale(sx, sy)` | 缩放 |
| `canvas:rotate(deg)` | 旋转 (角度) |
| `canvas:clipRect(l, t, r, b)` | 裁剪 (卡拉OK 高亮必备) |
| `canvas:clipText(text, x, y)` | 按文字轮廓裁剪 (文字蒙版): 之后的绘制只出现在字形内部 |

**测量**:
| 方法 | 说明 |
|---|---|
| `canvas:measureText(text)` | 文本宽度 |
| `canvas:measureTextHeight()` | 行高 |
| `canvas:getTextCenterOffset()` | 垂直居中偏移 (dy, 配合 drawText 居中) |

**路径**:
| 方法 | 说明 |
|---|---|
| `canvas:beginPath()` `canvas:moveTo(x,y)` `canvas:lineTo(x,y)` `canvas:quadTo(x1,y1,x2,y2)` `canvas:closePath()` `canvas:drawPath()` | 绘制任意路径 |

**卡拉OK 快捷方法**:
```lua
canvas:drawKaraokeLine(text, x, y, alpha, progress, baseColor, activeColor, shadowColor)
-- progress: 0~1, shadowColor 可为 nil
-- 内部自动完成: 底色 + 裁剪高亮 (可省略手动 clipRect)
```

### 3.2 anim API(数学与动画)

| 方法 | 说明 |
|---|---|
| `anim:lerp(a, b, f)` | 线性插值 |
| `anim:map(v, iMin, iMax, oMin, oMax)` | 范围映射 |
| `anim:clamp(v, min, max)` | 限制范围 |
| `anim:sineWave(timeMs, periodMs, min, max)` | 正弦呼吸 |
| `anim:pulse(timeMs, periodMs)` | 脉冲 (0~1) |
| `anim:easeInQuad(t)` `anim:easeOutQuad(t)` `anim:easeInOutSine(t)` `anim:easeOutBack(t)` | 缓动函数 |

### 3.3 tools API

| 方法 | 说明 |
|---|---|
| `tools.toChars(str)` | 按字符拆分为 Lua 表 (支持中文/emoji) |
| `tools.loadFont(path)` | 加载字体 (assets 相对路径或绝对路径; 失败返回 nil) |

### 3.4 state(播放状态,每帧更新)

| 字段 | 类型 | 说明 |
|---|---|---|
| `title` `artist` | 字符串 | 歌曲标题 / 歌手 |
| `currentTime` `totalTime` | 字符串 | 已播 / 总时长 "3:20" |
| `isPlaying` | 布尔 | 是否播放中 |
| `position` | 数字 | 播放位置 (毫秒, 帧内平滑) |
| `duration` | 数字 | 总时长 (毫秒) |
| `progress` | 数字 | 整曲进度 0~1 |
| `volume` | 数字 | 音量 0~1 |
| `playMode` | 字符串 | "LoopOne" / "Shuffle" |
| `isLiked` `likedCount` | 布尔/数字 | 点赞状态 |
| `currentLyricIndex` | 数字 | 当前行索引 (0 起始, -1=无) |
| `currentWordIndex` | 数字 | 当前字索引 |
| `lyric` | 字符串 | 当前行文本 |
| `lineStart` `lineEnd` | 数字 | 当前行起止时间 (毫秒) |
| `lineProgress` | 数字 | 当前行进度 0~1 |

### 3.5 env(环境,每帧更新)

| 字段 | 类型 | 说明 |
|---|---|---|
| `time` | 数字 | 真实时间戳 (毫秒, 可用于 os.date 显示时钟) |
| `systemTime` | 数字 | 单调时钟 (毫秒, 适合动画, 不受系统改时影响) |
| `width` `height` | 数字 | 屏幕尺寸 |
| `isPortrait` | 布尔 | 是否竖屏 |
| `anim` | 数字 | 切歌动画值 0~1 |

### 3.6 global_lyrics(歌词表,切歌时注入)

```lua
global_lyrics = {
  [1] = {
    startTime = 0, endTime = 3000,
    text = "风紧紧追着雨",
    words = {
      [1] = { text = "风", st = 0,    et = 800 },
      [2] = { text = "紧", st = 800,  et = 1500 },
      ...
    }
  },
  ...
}
```
- 索引 1 起始; `#global_lyrics` 可得总行数
- 普通 LRC 的逐字时间由解析器自动插值生成,words 恒非空
- 取当前行: `local line = global_lyrics[(state.currentLyricIndex or -1) + 1]`

### 3.7 完整示例: 极简逐字歌词

```lua
local CONFIG = {
    font = "SourceHanSansCN-Normal.ttf",
    fontSize = 90,
    baseColor = "#666666",
    activeColor = "#FFFFFF",
}

local font = tools.loadFont(CONFIG.font)

-- 示例布局已旋转 90° (横屏坐标系): 宽=屏高, 高=屏宽
function onDraw(state, env)
    local screenW, screenH = env.width, env.height
    canvas:save()
    canvas:translate(screenW, 0)
    canvas:rotate(90)

    local w, h = screenH, screenW
    canvas:drawColor("#000000")
    if font then canvas:setTypeface(font) else canvas:setTypeface(nil) end

    local line = global_lyrics and global_lyrics[(state.currentLyricIndex or -1) + 1]
    if not line then
        canvas:restore()
        return
    end

    local pos = state.position or 0
    local fontSize = h * CONFIG.fontSizeRatio  -- 注意: 示例用 CONFIG.fontSizeRatio 需在 CONFIG 定义
    canvas:setTextSize(CONFIG.fontSize)

    -- 测量 + 居中
    local widths, totalW = {}, 0
    local units = line.words
    for i, u in ipairs(units) do
        widths[i] = canvas:measureText(u.text)
        totalW = totalW + widths[i]
    end
    local x = (w - totalW) / 2
    local y = h / 2 + canvas:getTextCenterOffset()

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

    canvas:restore()
end
```

---

## 4. 性能指南

绘制流程为 **命令缓冲**:Lua 每帧生成命令列表,原生执行器批量回放(单一 Paint 复用 + 状态缓存,连续相同颜色/字号自动跳过重复设置)。

- **避免每帧 setShadowLayer**:阴影按字形生成纹理,开销大。只对少量元素(1~2 个)使用,或仅在活动字上使用
- **避免每帧字符串拼接/格式化**:如时钟文本,分钟不变时缓存(示例见默认模板 `clockStr`)
- **测量缓存**:`measureText` 在字号不变时结果不变,同一行只需测量一次
- **超宽歌词**:先按固定字号测量,超宽时**换行**而非缩小字号,保持字号恒定
- **粒子/动画用 `env.systemTime`**(单调时钟),显示时间用 `env.time`(真实时间)
- **`save/restore` 必须成对**,onDraw 内的 save 不要跨越帧

## 5. 调试

- 脚本错误 → Toast 提示 + 回退默认场景,错误详情写入 `scene_error.log`(与 scene.lua 同目录)
- 常见错误:
  - `attempt to index a nil value` → 访问了未定义的表(如 canvas:drawText 拼错方法名、global_lyrics 为空)
  - `coercion error` → 参数类型不匹配(如 setColor 传了错误类型)
  - 字段名大小写敏感:`fontSize` ≠ `fontsize`
- 修改文件即热重载,建议先改一个参数验证生效,再逐步调整

## 6. 最佳实践清单

- 顶部用 `local CONFIG = {...}` 集中管理可调参数
- 结构:`drawColor` 背景 → 背景装饰(粒子等) → 时钟/信息 → 歌词 → 叠加元素
- 时钟类文本按分钟缓存;粒子数量 20 个以内即可有氛围且零压力
- 注意字号与最大行宽:竖屏默认字号建议 `0.07~0.08 × 屏宽`,超宽自动换行

## 7. 音频频谱可视化

播放时自动采集实时频谱(无需开关,也不需要录音权限),场景里可直接读取。

| 字段 | 含义 |
| --- | --- |
| `state.hasSpectrum` | 当前是否有频谱数据 (恒为 true, 保留兼容) |
| `state.spectrum[1..16]` | 16 条频段能量,0.0~1.0,低频在前 |
| `state.bass` / `state.mid` / `state.treble` | 低/中/高频段能量,0.0~1.0 |
| `state.level` | 整体响度(柱状均值) |

可复制示例:

```lua
local function drawSpectrum(state, w, h)
    if not state.hasSpectrum then return end
    local n, gap = 16, 4
    local total = w * 0.72
    local bw = (total - gap * (n - 1)) / n
    local baseY, maxH = h * 0.155, h * 0.05
    local left = (w - total) / 2
    for i = 1, n do
        local bh = maxH * (state.spectrum[i] or 0)
        if bh > 1 then
            local x = left + (i - 1) * (bw + gap)
            canvas:drawRect(x, baseY - bh, x + bw, baseY, "#FFFFFF")
        end
    end
end
```

要点:

- 数值按当前曲目峰值**自适应归一化**,换歌不需要重调增益
- 关闭开关后所有值恒为 0,脚本用 `state.hasSpectrum` 判断即可,不必额外分支
- 采集与 FFT 跑在独立线程(约 120 次/秒),不会拖慢 60fps 的绘制帧

### 文字蒙版示例: 让频谱只出现在时钟数字里

`clipText` 用文字轮廓裁剪,配合 `save/restore` 可以做出"数字内部填充频谱、外部完全不画"的效果:

```lua
local function drawClockSpectrum(state, w, h)
    local clockSize = h * 0.12
    local baseline = h * 0.3
    canvas:setTextSize(clockSize)
    baseline = baseline + canvas:getTextCenterOffset()
    local text = os.date("%H:%M")
    local textWidth = canvas:measureText(text)

    -- 底衬灰字: 保证暂停时也能看清时间
    canvas:setTextAlign(1)
    canvas:setColor("#5A5A5A")
    canvas:setAlpha(230)
    canvas:drawText(text, w / 2, baseline)

    -- 数字内部: 高亮白频谱柱
    local n, gap = 32, 2
    local bw = (w - gap * (n - 1)) / n
    local maxH = clockSize * 0.95
    canvas:setTextAlign(0)
    canvas:save()
    canvas:clipText(text, (w - textWidth) / 2, baseline)
    canvas:setStyle(0)
    canvas:setColor("#FFFFFF")
    for i = 1, n do
        local v = state.spectrum[math.floor((i - 1) * 16 / n) + 1] or 0
        local bh = maxH * math.min(1, v * 2.4)
        if bh > 1 then
            local x = (i - 1) * (bw + gap)
            canvas:drawRoundRect(x, baseline - bh, x + bw, baseline, math.min(bw / 2, bh / 2))
        end
    end
    canvas:restore()
end
```

注意:`clipText` 的 `x` 是文字起点(左对齐坐标),居中显示时要用 `(w - measureText(text)) / 2` 自己换算。
