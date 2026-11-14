package com.example.pra.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import com.example.pra.data.model.Audio
import com.example.pra.ui.components.PraArtwork
import com.example.pra.ui.components.PraSongRow
import com.example.pra.ui.state.PlayerUiState
import com.example.pra.ui.theme.PraShapes
import com.example.pra.ui.theme.PraSpacing
import com.example.pra.ui.theme.praColors

/**
 * 首页：回答"现在听什么"。
 * 结构 = 问候 → 继续播放 → 快捷入口 → 最近添加。
 */
@Composable
fun HomeScreen(
    state: PlayerUiState,
    onPlaySong: (List<Audio>, Long) -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = praColors()
    val context = LocalContext.current
    val recent = state.playlist.take(8)
    val greeting = remember {
        when (LocalTime.now().hour) {
            in 5..8 -> "清晨好"
            in 9..11 -> "上午好"
            in 12..13 -> "中午好"
            in 14..17 -> "下午好"
            in 18..22 -> "晚上好"
            else -> "夜深了"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PraSpacing.screenH),
    ) {
        Spacer(Modifier.height(PraSpacing.xxl))

        Text(
            text = greeting,
            style = MaterialTheme.typography.displaySmall,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(PraSpacing.xs))
        Text(
            text = "共 ${state.playlist.size} 首本地歌曲",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )

        Spacer(Modifier.height(PraSpacing.xxl))

        if (state.currentSongId != -1L) {
            ContinueCard(
                state = state,
                onClick = onOpenPlayer,
            )
            Spacer(Modifier.height(PraSpacing.xxl))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(PraSpacing.md)) {
            QuickAction(
                icon = Icons.Rounded.Shuffle,
                label = "随机播放",
                modifier = Modifier.weight(1f),
                onClick = {
                    val shuffled = state.playlist.shuffled()
                    shuffled.firstOrNull()?.let { onPlaySong(shuffled, it.id) }
                },
            )
            QuickAction(
                icon = Icons.Rounded.Favorite,
                label = "我喜欢的",
                modifier = Modifier.weight(1f),
                onClick = {
                    state.playlist.firstOrNull { it.isLiked }?.let { onPlaySong(state.playlist, it.id) }
                },
            )
            QuickAction(
                icon = Icons.Rounded.Waves,
                label = "伪息屏",
                modifier = Modifier.weight(1f),
                onClick = {
                    android.widget.Toast.makeText(
                        context,
                        "5 秒无操作自动进入伪息屏，1 秒内连点 5 次退出",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        }

        Spacer(Modifier.height(PraSpacing.xxxl))

        SectionTitle(title = "最近添加", action = "全部")
        Spacer(Modifier.height(PraSpacing.sm))

        recent.forEach { song ->
            PraSongRow(
                song = song,
                isCurrent = song.id == state.currentSongId,
                isPlaying = state.isPlaying,
                onClick = { onPlaySong(recent, song.id) },
            )
        }

        if (state.playlist.isEmpty()) {
            EmptyHint()
        }

        Spacer(Modifier.height(PraSpacing.xxxl))
    }
}

@Composable
private fun ContinueCard(state: PlayerUiState, onClick: () -> Unit) {
    val colors = praColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PraShapes.card)
            .background(
                Brush.linearGradient(
                    listOf(colors.surfaceHigh, colors.surface),
                ),
            )
            .clickable(onClick = onClick)
            .padding(PraSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PraArtwork(
            uri = state.coverUri,
            modifier = Modifier.size(64.dp),
            shape = PraShapes.field,
        )
        Spacer(Modifier.size(PraSpacing.lg))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "继续播放",
                style = MaterialTheme.typography.labelMedium,
                color = colors.accent,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = state.songTitle,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = state.artistName,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(colors.accent),
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "播放",
                tint = colors.onAccent,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = praColors()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(PraShapes.card)
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(vertical = PraSpacing.xl),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = colors.accent,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(PraSpacing.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
    }
}

@Composable
fun SectionTitle(title: String, action: String? = null) {
    val colors = praColors()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
        )
        if (action != null) {
            Text(
                text = action,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
private fun EmptyHint() {
    val colors = praColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = PraSpacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "还没有歌曲",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(PraSpacing.xs))
        Text(
            text = "到「设置」里扫描本地音乐，或在播放列表里添加文件",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
        )
    }
}

/** 供其他页面复用：把 Audio 列表渲染成歌曲行 */
@Composable
fun SongList(
    songs: List<Audio>,
    currentSongId: Long,
    isPlaying: Boolean,
    onPlaySong: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        songs.forEach { song ->
            PraSongRow(
                song = song,
                isCurrent = song.id == currentSongId,
                isPlaying = isPlaying,
                onClick = { onPlaySong(song.id) },
            )
        }
    }
}
