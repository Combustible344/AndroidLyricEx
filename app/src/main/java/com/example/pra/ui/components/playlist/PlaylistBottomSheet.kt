package com.example.pra.ui.components.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete // 引入删除图标
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.pra.data.model.Audio

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistBottomSheet(
    playlist: List<Audio>,
    currentSongId: Long,
    onDismissRequest: () -> Unit,
    onItemClick: (Audio) -> Unit,
    onScanClick: () -> Unit,
    onAddFileClick: () -> Unit,
    onBindLyricClick: (Audio) -> Unit,
    // --- 新增：删除回调 ---
    onRemoveClick: (Audio) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color(0xFF1E2329),
        scrimColor = Color.Black.copy(alpha = 0.5f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
        ) {
            // 1. 顶部操作栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "播放列表 (${playlist.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SmallIconButton(
                        icon = Icons.Default.Refresh,
                        text = "扫描",
                        onClick = onScanClick
                    )
                    SmallIconButton(
                        icon = Icons.Default.Add,
                        text = "添加",
                        onClick = onAddFileClick
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            // 2. 列表
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(playlist, key = { it.id }) { audio ->
                    PlaylistItem(
                        audio = audio,
                        isPlaying = audio.id == currentSongId,
                        onClick = { onItemClick(audio) },
                        onLyricClick = { onBindLyricClick(audio) },
                        // 传递删除事件
                        onRemoveClick = { onRemoveClick(audio) }
                    )
                }
            }
        }
    }
}

// 辅助组件：顶部的小按钮 (保持不变)
@Composable
fun SmallIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun PlaylistItem(
    audio: Audio,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLyricClick: () -> Unit,
    // 新增参数
    onRemoveClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 封面
        AsyncImage(
            model = audio.albumArtUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray)
        )

        Spacer(modifier = Modifier.width(16.dp))

        // 文字信息 (靠高亮颜色指示正在播放)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = audio.title,
                style = MaterialTheme.typography.bodyLarge,
                // 正在播放时显示亮蓝色，否则显示白色
                color = if (isPlaying) Color(0xFF64B5F6) else Color.White,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = audio.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // --- 右侧功能区 ---
        Row(verticalAlignment = Alignment.CenterVertically) {

            // 1. 歌词按钮
            IconButton(onClick = onLyricClick) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = "Bind Lyric",
                    tint = if (audio.lyricUri != null) Color(0xFF64B5F6) else Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // 2. 删除按钮 (替代了原来的音符图标)
            IconButton(onClick = onRemoveClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = Color.White.copy(alpha = 0.5f), // 稍微淡一点，避免太抢眼
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
