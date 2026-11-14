package com.example.pra.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.pra.ui.theme.PraShapes
import com.example.pra.ui.theme.PraSpacing
import com.example.pra.ui.theme.praColors

/**
 * 设置。当前阶段以「只读信息 + 扫描入口」为主，
 * 后续接入 DataStore 后再补主题切换、默认模式、AOD 参数等可写项。
 */
@Composable
fun SettingsScreen(
    onScan: () -> Unit,
    onImportLyrics: (android.net.Uri) -> Unit,
    onOpenGestures: () -> Unit,
    onOpenSceneEditor: () -> Unit,
    rankLimit: Int,
    onRankLimitChange: (Int) -> Unit,
    visualizerEnabled: Boolean,
    onToggleVisualizer: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = praColors()
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> if (uri != null) onImportLyrics(uri) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PraSpacing.screenH),
    ) {
        Spacer(Modifier.height(PraSpacing.xxl))
        Text(
            text = "设置",
            style = MaterialTheme.typography.displaySmall,
            color = colors.textPrimary,
        )

        Spacer(Modifier.height(PraSpacing.xxl))
        SettingsGroup(title = "曲库") {
            SettingsRow(
                title = "重新扫描本地音乐",
                subtitle = "从 MediaStore 增量刷新，不会清空已绑定歌词",
                onClick = onScan,
            )
            SettingsRow(
                title = "从文件夹导入歌词",
                subtitle = "选择 Download/music，自动匹配同名 .lrc 并绑定",
                onClick = { importLauncher.launch(null) },
            )
        }

        Spacer(Modifier.height(PraSpacing.xl))
        SettingsGroup(title = "首页") {
            SettingsRow(
                title = "听歌排行数量",
                subtitle = "当前显示前 $rankLimit 首，点按切换 5 / 10 / 20 / 50",
                onClick = {
                    val choices = com.example.pra.data.prefs.ListeningStatsStore.LIMIT_CHOICES
                    val next = choices[(choices.indexOf(rankLimit).coerceAtLeast(0) + 1) % choices.size]
                    onRankLimitChange(next)
                },
            )
        }

        Spacer(Modifier.height(PraSpacing.xl))
        SettingsGroup(title = "外观") {
            SettingsRow(
                title = "深色（固定）",
                subtitle = "背景与强调色跟随当前封面变化",
            )
        }

        Spacer(Modifier.height(PraSpacing.xl))
        SettingsGroup(title = "伪息屏") {
            SettingsRow(
                title = "音频可视化（实验）",
                subtitle = if (visualizerEnabled) {
                    "已开启：从播放管线采集频谱，场景脚本可读取"
                } else {
                    "关闭：开启后伪息屏可按节拍做效果（无需录音权限）"
                },
                onClick = { onToggleVisualizer(!visualizerEnabled) },
            )
            SettingsRow(
                title = "场景编辑器",
                subtitle = "编辑 scene.lua，保存即生效；附 Lua API 手册",
                onClick = onOpenSceneEditor,
            )
            SettingsRow(
                title = "自定义手势",
                subtitle = "在息屏中用点击 / 长按 / 滑动控制播放，可自由组合",
                onClick = onOpenGestures,
            )
            SettingsRow(
                title = "进入方式",
                subtitle = "5 秒无操作自动进入",
            )
            SettingsRow(
                title = "解锁方式",
                subtitle = "1 秒内连点 5 次",
            )
            SettingsRow(
                title = "场景脚本",
                subtitle = "Android/data/com.example.pra/files/lua_scripts/scene.lua，保存即热重载",
            )
        }

        Spacer(Modifier.height(PraSpacing.xl))
        SettingsGroup(title = "关于") {
            SettingsRow(title = "版本", subtitle = "1.0")
        }

        Spacer(Modifier.height(PraSpacing.xxxl))
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    val colors = praColors()
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = colors.textTertiary,
        )
        Spacer(Modifier.height(PraSpacing.sm))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(PraShapes.card)
                .background(colors.surface)
                .padding(vertical = PraSpacing.xs),
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = praColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(horizontal = PraSpacing.lg, vertical = PraSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}
