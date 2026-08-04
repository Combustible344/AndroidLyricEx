# Update Log

记录主要修改时间与内容(时间倒序)。

## 2026-08-04 — Lua 脚本模式回归: 命令缓冲架构 (任意效果 + 可控性能)

- **双模式自动检测**: scene.lua 定义 `onDraw` 即启用 Lua 每帧脚本模式, 否则用声明式场景 (原生渲染)
- 新增命令缓冲体系 (`ui/scene/`):
  - `DrawCommands.kt` — 绘制命令模型 + 原生执行器 (单一 Paint 复用 + 状态缓存, 跳过重复设置)
  - `CommandCanvasAdapter.kt` — Lua 画布适配器, 与旧 draw.lua API 完全兼容 (setColor/drawText/路径/变换/卡拉OK 等), 但只生成命令不直接画
  - `LuaScriptEngine.kt` — 每帧执行 onDraw → 命令缓冲 → 原生回放; state/env/global_lyrics 注入
  - 恢复 `LuaAnimHelper` (anim: lerp/map/clamp/sineWave/缓动函数)
- `LyricCanvasRenderer` 抽象接口: 声明式渲染器与脚本渲染器共用入口, MainActivity 按场景加载结果切换
- 歌词注入恢复: 切歌时构建 global_lyrics (仅脚本模式), 动画信号 env.anim 恢复
- 脚本模式性能: 每帧 LuaJ 解释 + 桥接 ~1ms, 绘制零反射, 仍富余 15ms 帧预算
- `unitTests.returnDefaultValues` 启用, 新增 `LuaScriptEngineTest` (6 个用例: 基础绘制/卡拉OK/状态读取/歌词注入/无 onDraw/字体 API)

## 2026-08-04 — 新增歌曲管理页与设置页 (框架)

- 新增 `MusicLibraryActivity`: 歌曲与歌词管理全屏页 (扫描/添加/删除/歌词绑定, 逻辑后期填充), 与播放页右下角"即时查看"底部弹层互补
- 新增 `SettingsActivity`: 设置页 (屏保/播放/歌词相关设置项, 后期填充)
- 入口: 播放列表底部弹层新增"歌曲管理"/"设置"两个按钮, 从 `PlaylistBottomSheet` 回调经 Intent 跳转
- 两个 Activity 均注册于 Manifest (`exported=false`), 深色主题与播放页一致, 不影响 MainActivity 现有屏保结构

## 2026-08-04 — 播放模式固定为两种 (随机 / 单曲循环)

- `syncMode()` 由"从播放器反推状态"改为"把 UI 选定的模式推送到播放器"——UI 状态成为唯一权威, 系统通知栏的 shuffle/重复按钮改动会被强制纠正, 杜绝隐藏的"顺序播放"第三状态
- 枚举本身维持两种: `PlayMode.Shuffle` (REPEAT_MODE_ALL + shuffle) / `PlayMode.LoopOne` (REPEAT_MODE_ONE)

## 2026-08-04 — 屏保渲染性能优化

- **场景构建移出主线程**: `loadScene()` 整体在 IO 线程执行 (LuaJ 构建 + 字体文件 IO), 完成后再切回主线程更新渲染器; `SceneBuilder.build` 加 `@Synchronized` 防 LuaJ globals 并发竞争; `LyricSceneRenderer.setScene` 改为先预编译表达式再发布场景引用, IO 线程写入与主线程渲染零竞争
- **帧循环自适应**: `frameRateActive = isPlaying || 无歌词` — 播放中或占位呼吸时 60fps; 暂停且有歌词 (画面静止) 时只渲染一帧后帧循环休眠省电; 切回动态时自动恢复 60fps
- **微优化**: 渲染器时间改用 `frameNanos` 派生 (单调时钟, 呼吸动画不受系统时间跳变影响); 歌词绘制加 paint 颜色缓存, 连续同色跳过重复 `setColor`

## 2026-08-04 — 场景脚本体系完善

- 默认场景模板移入 `res/raw/scene_default.lua` (原 MainActivity 硬编码常量), 支持资源版本管理
- 脚本错误写入 `scene_error.log` (与 scene.lua 同目录), Toast 显示具体错误原因
- 修复 LuaJ 桥接 coercion error: `setFont`/`backgroundColor` 参数从 `LuaValue` 改为 `Any?`; `colorValue` 兼容 LuaValue/table 字段/字符串三种来源
- 新增 `SceneBuilderTest` (5 个用例), 覆盖模板构建、字体桥接、颜色解析、overlay 节点、错误脚本

## 2026-08-04 — 高性能渲染重构 + LyricParser V2 (提交 8e0bb10)

### 渲染架构: Lua 每帧执行 → 场景描述 + 原生渲染

- 新增 `ui/scene/` 场景体系:
  - `SceneGraph.kt` — 场景数据模型 (歌词/占位/叠加元素, 属性支持常量或表达式绑定)
  - `ExprEvaluator.kt` — 表达式引擎: 解析一次编译为指令流, 预分配栈机零分配求值 (变量 t/pos/time/dur/playing/w/h/alpha/offsetY, 函数 sin/cos/lerp/clamp/map/smoothstep 等)
  - `SceneBuilder.kt` — LuaJ 场景构建 DSL (`scene:setFont/backgroundColor/lyric/placeholder/text/rect/circle`), 脚本只在加载时执行一次
  - `LyricSceneRenderer.kt` — 原生渲染器: 布局按 (行, 屏宽, 字号) 缓存, 字级卡拉OK仅对活动字 clipRect, 无 setShadowLayer 热路径
- 删除 `LuaScriptEngine.kt` / `LuaCanvasAdapter.kt` / `LuaAnimHelper.kt` (每帧 Lua 解释 + 反射桥接是卡顿根因)
- 帧驱动: 移除全局 `frameTime` 心跳, 改为 overlay 内 `withFrameNanos` 只失效屏保 Canvas 绘制, PlayerScreen 不再每帧重组
- 进度 100ms 校准 + 帧内推演, 卡拉OK填充 60fps 平滑 (原 10fps 台阶)
- 背景 `AtmosphericFluidBackground` 增加 `active` 参数, 屏保覆盖时冻结 180dp 模糊动画
- 场景文件 `scene.lua` 修改 3 秒内自动热重载; 旧 `draw.lua` 自动备份为 `.bak`

### LyricParser V2 (以 QQ 音乐/网易云增强歌词为标准)

- 增强格式: 空标签 `<time>` 为上一字结束标记, 单独成行的空标签归属上一行最后一字, 多行头克隆, 损坏标签容错
- 普通 LRC: 按文字长度加权插值构造逐字时间 (中文单字权重 1, 英文按长度占比), 尾字对齐行尾
- 编码探测: UTF-8 BOM / UTF-16 BOM / 严格 UTF-8 校验 + 可疑字符启发式 (GBK 回退)
- 新增 `LyricParserTest` (13 个用例)

### 问题修复

- 签名密钥 `app/src/main/key` 从版本库移除, 改由本地 `keystore.properties` 读取 (已 gitignore)
- 补 `POST_NOTIFICATIONS` 权限 (Android 13+ 媒体通知), 删除未使用的 `RECORD_AUDIO`, Manifest 去重
- `onCleared` 的 `runBlocking` 主线程阻塞 → `NonCancellable + IO` (ANR 风险)
- `seekTo` 增加 `C.TIME_UNSET` 校验 (防溢出)
- `MusicService`: `playWhenReady` → `isPlaying` 判断; 修复 ExoPlayer 双重 release
- 删除未使用依赖 `volley` / `generativeai`; `PlayerUiState` 增加 `durationMs` 字段
- `.gitattributes` EOL 归一化 (仓库 LF), `app/release/` 构建产物退出版本库

### 测试

- 26 个单元测试全部通过: LyricParser 13 + ExprEvaluator 8 + SceneBuilder 5 + 示例 1
