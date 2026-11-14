# Update Log

记录主要修改时间与内容(时间倒序)。

## 2026-11-14 — 包体瘦身 / 听歌排行 / 场景编辑器 / 音频频谱

### 构建与包体

- release 开启 R8 (`minifyEnabled`) + 资源收缩 (`shrinkResources`), debug APK **70.99 MB → release 5.00 MB**, dex 69.58 MB → 4.16 MB
- 新增 LuaJ 反射保活规则 (`org.luaj.**`、`ui.scene.**` 的 public 成员、渲染器实现、前台服务), 否则场景脚本会在 release 包中静默失效
- 没有 `keystore.properties` 时 release 自动退回 debug 签名, 方便本地装机验证; 正式分发前需配好 keystore

### 首页听歌排行

- 新增 `ListeningStatsStore` (DataStore): 每首歌累计播放次数 + 排行显示条数
- 计数规则: 主动点播与自动切歌各计一次, 同一首重播不重复累加, 冷启动恢复现场不计数
- 首页「最近添加」改为「听歌排行」: 名次(前三名强调色)、封面、播放次数
- 设置 → 首页 → 听歌排行数量: 点按在 5 / 10 / 20 / 50 之间切换, 默认 10

### 播放修复

- 修复播放中在首页/曲库再次点击同一首歌会把进度重置为 0: 改为只恢复播放, 不重建队列 (实测点击后位置保持 3.9s 继续播放)

### 场景编辑器 v1

- 设置 → 伪息屏 → 场景编辑器: 直接编辑设备上的 `lua_scripts/scene.lua`, 等宽字体、可滚动
- 保存即生效: 复用已有的 `FileObserver(CLOSE_WRITE)` 热重载, 无需重启
- 支持「恢复默认」写回内置模板、「API 手册」切换 `scene_guide.md`、脚本报错时展示 `scene_error.log`

### 音频频谱可视化

- 先尝试 `Visualizer` 方案: 该 ROM 对会话与全局混音均返回 `ERROR_NO_INIT (-3)`, 已整体移除并删除 `RECORD_AUDIO` 权限
- 改为从播放管线内部采集:
  - `TapAudioProcessor`: 挂在 AudioSink 前端的旁路处理器, 原样放行音频并拷贝 PCM (16-bit / float)
  - `SpectrumTap`: 环形缓冲 + 1024 点基-2 FFT + 汉宁窗, 每 33ms 输出 16 条柱子与 bass/mid/treble/level
  - 自适应归一化: 跟踪曲目峰值包络 + sqrt 压缩, 换歌无需重调增益
- 场景脚本注入: `state.spectrum[1..16]`、`state.bass/mid/treble/level`、`state.hasSpectrum`; 无开关, 播放时自动采集
- 默认场景频谱柱 (`CONFIG.spectrumBars`, 默认开启): 完全以时钟文字轮廓为蒙版 —— 只有数字内部可见; 暂停/静音时数值为 0, 数字退回灰色底字
- 频谱画法改为「平滑折线 + 下方面积填充」(`CONFIG.spectrumPoints`, 默认 48 个采样点), 上亮下淡的垂直渐变, 避免柱状竖缝把数字切碎
- 采集帧率约 120Hz, 上升/回落按时间常数平滑
- 新增绘制命令 `ClipText`: 用文字轮廓裁剪 (蒙版), 供场景脚本做"文字内填充"类效果
- `scene_guide.md` 新增「音频频谱可视化」章节与可复制示例
- 实测: `SpectrumTap` 每秒接收约 88k PCM 采样, 柱子数值稳定在 0.01–0.67, 与场景引擎读到的值一致

## 2026-11-14 — 前端重设计 + 播放层重构 + 伪息屏修复与手势系统

### 概览

- 单 Activity + `NavHost`: `main`(三 Tab) / `player`(全屏播放页) / `gestures`(手势配置)
- 新增设计系统 (语义色板 / 排版 / 形状 / 间距 / 动效 tokens)
- 播放层与曲库解耦, 四种播放模式, DataStore 持久化, 进度锚点 + 帧插值
- 歌词解析重写为**严格增强型 LRC**, 新增自动绑定与文件夹导入
- 伪息屏: 修复全黑、重写自适应排版、新增可组合手势; 二级页与播放页不再进入息屏

### 设计系统与主题

- `ui/theme/Color.kt` — 新增 `PraColors` 语义色板 (深色/浅色两套), 界面只经 `praColors()` 取色
- `ui/theme/Type.kt` — 全站 15 档排版层级; **移除内置思源黑体**, 改用系统字体 (`FontFamily.Default`)
- `ui/theme/Shape.kt` / `Dimens.kt` — 圆角、间距、动效时长与曲线 token (`PraSpacing` / `PraMotion` / `PraShapes`)
- `ui/theme/Theme.kt` — `PraTheme` 固定深色优先, 映射 M3 ColorScheme, 同步系统栏图标明暗

### 信息架构与界面

- `ui/app/AppRoot.kt` — 三 Tab (首页/曲库/设置) + 常驻 MiniPlayer + 路由转场 (播放页自下而上)
- `ui/app/MiniPlayer.kt` — 玻璃卡片 + 顶部进度线, 点击进入播放页
- `ui/player/NowPlayingScreen.kt` — 封面取色渐变背景、封面↔歌词 Pager、自绘进度条、队列覆盖层
- `ui/home/HomeScreen.kt` — 时段问候、继续播放、快捷入口、最近添加
- `ui/library/LibraryScreen.kt` — 搜索 / 排序 / 长按绑定歌词或移除 / `+` 导入文件
- `ui/settings/SettingsScreen.kt` — 曲库、外观、伪息屏、关于
- `ui/settings/GestureSettingsScreen.kt` — 手势绑定列表与步骤编辑器
- 队列面板由 `ModalBottomSheet` 改为**应用内覆盖层**: 避免弹窗窗口重新拉起系统栏

### 播放数据层

- 队列与曲库分离: 新增 `library` / `queue`, `playFromList(list, songId)`; 曲库页按当前筛选结果起播
- 四种播放模式: 顺序 / 列表循环 / 单曲循环 / 随机; 以播放器状态为准反推 UI, 不再强行纠偏
- 播放状态改由 DataStore (`player_prefs`) 持久化, 替代 `player_prefs.json`
  - 修复程序化切模式时回调风暴覆盖新值
  - 修复冷启动 `pause()` 回调早于 UI 更新导致模式回退
- 进度改为**锚点 + 帧插值**: `positionMs` + `positionAnchorNanos`, 播放页 `withFrameNanos` 补间; 每秒一次锚点, 每 10 秒落盘
- `uiState` 改为派生流, 每次发射前向播放器取实时位置 (修复伪息屏逐字回跳)
- MediaStore `ContentObserver` + 1.2s 去抖增量刷新

### 歌词

- 自动绑定: 按「歌名 / 歌手-歌名 / 歌手-歌名」归一化匹配 `lyrics/` 目录下同名 `.lrc`, 只补空缺
- 设置页新增「从文件夹导入歌词」(SAF `OpenDocumentTree`), 递归复制 `.lrc` 后重新绑定
- `utils/LyricParser.kt` 重写为 V4 严格模式:
  - 只解析带 `<mm:ss.xx>` 逐字标签的增强型 LRC; 无标签行整行丢弃
  - 标签时间配对: 文本开始 = 标签时间, 结束 = 下一标签时间; 空标签即上一字的结束
  - 时间强制单调不减 (文件里时间倒退会被钳制, 永不回跳)
  - 损坏标签直接丢弃; 严格 UTF-8 (含 BOM), 不再猜测 GBK
  - 移除按字长插值伪造逐字时间等全部兜底逻辑
- 测试: 15 个用例, 含真实 `seasons.lrc` 全文单调性回归 (夹具 `app/src/test/resources/seasons.lrc`)

### 伪息屏 (AOD)

- 修复整屏全黑: `DrawCommandExecutor` 颜色缓存哨兵 `-1` 与不透明白 `0xFFFFFFFF` 冲突, 每帧首条白色 `setColor` 被跳过 → 改为 `Int.MIN_VALUE`
- 场景脚本 `res/raw/scene_default.lua` 重写为自适应排版:
  - 先按时钟尺寸预留安全歌词区 (时钟下沿 + 间距 → 屏底 90%)
  - 在区域内按可用高度等比缩小字号 (下限 `minFontSizeRatio`)
  - 仍放不下时只显示当前行附近的行窗口, 上下淡出
  - 绘制前 `clipRect` 硬裁剪到歌词区, 结构上不可能压到时钟
- 场景升级: 仅当设备 `scene.lua` 与旧内置默认版逐字节一致 (SHA-256) 时替换, 用户自定义场景不受影响; 只读文件先删再写
- 手势系统 `data/gesture/Gestures.kt`:
  - 手势 = 参数化步骤序列 (点击 / 长按 300–2000ms / 四向滑动, 每步可设连续次数, 最多 3 步)
  - 动作 = 播放暂停 / 上一首 / 下一首 / 快进 10s / 快退 10s / 无操作
  - 严格顺序匹配, 550ms 静默窗口结算 (区分「左滑」与「左滑×2」)
  - 1 秒内连点 5 次仍为解锁, 且不误触已绑定的点击类手势
  - 同一手势签名只允许一条绑定 (保存 / 写入 / 读取三处去重, 后保存者覆盖)
- 息屏不再打断专注操作: 播放详情页与设置二级页通过路由抑制息屏, 回到 Tab 主页恢复

### 工程 / 包体

- 新增依赖: `navigation-compose 2.9.8`、`datastore-preferences 1.1.7`、`palette-ktx 1.1.0`
- 删除 `res/font/source_normal.ttf` / `source_bold.ttf` (19.57 MB), 改用系统字体
  - debug APK 82.87 MB → **70.99 MB**
  - 注意: AGP 增量打包不会回收已删除资源占用的空间, 删资源后需先删除旧 APK 或 `clean` 再打包
- 新增 `data/prefs/` (播放偏好 / 手势绑定)、`ui/util/TimeFormat.kt`、`ui/components/PraArtwork.kt`、`PraSongRow.kt`

### 验证

- `:app:assembleDebug` 通过; `:app:testDebugUnitTest` 全绿 (歌词 15 例 + 手势 11 例)
- 真机 (Redmi, Android 13) 验证: 三 Tab / 播放页 / 队列覆盖层 / 歌词页 / 设置 / 手势配置 / 伪息屏排版与逐字高亮 / 息屏内滑动手势切歌 / 冷启动恢复播放与模式
