package com.example.pra.ui.components.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.pra.data.model.Audio
import com.example.pra.ui.components.common.FrostedGlassButton

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
    onRemoveClick: (Audio) -> Unit,
    onUserActivity: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    // 定义主题色，与 PlayerScreen 保持一致
    val activeColor = Color(0xFF64B5F6)
    val surfaceColor = Color(0xFF1E2329)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = surfaceColor,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), // 更大的圆角
        modifier = Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)
                    onUserActivity()
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f) // 稍微增加一点高度
        ) {
            // --- 顶部 Header ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "播放列表",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${playlist.size} 首歌曲",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FrostedCapsuleButton(Icons.Default.Refresh, "扫描", onScanClick)
                    FrostedCapsuleButton(Icons.Default.Add, "添加", onAddFileClick)
                }
            }

            // 分割线移除，用 padding 代替，更现代

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 30.dp) // 底部留白
            ) {
                items(playlist, key = { it.id }) { audio ->
                    PlaylistItem(
                        audio = audio,
                        isPlaying = audio.id == currentSongId,
                        activeColor = activeColor,
                        onClick = { onItemClick(audio) },
                        onLyricClick = { onBindLyricClick(audio) },
                        onRemoveClick = { onRemoveClick(audio) }
                    )
                }
            }
        }
    }
}

/**
 * 自定义的胶囊形磨砂按钮 (用于顶部 "扫描" / "添加")
 * 风格模仿 FrostedGlassButton，但支持文字
 */
@Composable
fun FrostedCapsuleButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50)) // 胶囊圆角
            .background(Color.White.copy(alpha = 0.1f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun PlaylistItem(
    audio: Audio,
    isPlaying: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    onLyricClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    // 选中态背景颜色
    val backgroundColor = if (isPlaying) Color.White.copy(alpha = 0.08f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. 封面图 (如果是当前播放，显示动态波形图标或高亮边框)
        Box(contentAlignment = Alignment.Center) {
            AsyncImage(
                model = audio.albumArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(12.dp)) // 增加圆角至 12dp
                    .background(Color.DarkGray)
            )

            // 当前播放时的遮罩层
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Playing",
                        tint = activeColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // 2. 文本信息
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = audio.title,
                style = MaterialTheme.typography.bodyLarge,
                // 选中时高亮颜色
                color = if (isPlaying) activeColor else Color.White,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = audio.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f), // 统一使用 0.6 透明度
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp
            )
        }

        // 3. 操作按钮组 (使用 FrostedGlassButton 风格)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 歌词按钮
            val lyricIconColor = if (audio.lyricUri != null) activeColor else Color.White.copy(alpha = 0.5f)

            // 复用你提供的 FrostedGlassButton
            FrostedGlassButton(
                painter = rememberVectorPainter(Icons.Default.Description),
                contentDescription = "Bind Lyric",
                onClick = onLyricClick,
                containerSize = 32.dp, // 比主界面的小一点
                iconSize = 16.dp,
                iconTint = lyricIconColor,
                backgroundColor = Color.White.copy(alpha = 0.05f) // 更淡的背景
            )

            // 删除按钮
            FrostedGlassButton(
                painter = rememberVectorPainter(Icons.Default.Delete),
                contentDescription = "Remove",
                onClick = onRemoveClick,
                containerSize = 32.dp,
                iconSize = 16.dp,
                iconTint = Color.White.copy(alpha = 0.5f),
                backgroundColor = Color.White.copy(alpha = 0.05f)
            )
        }
    }
}
