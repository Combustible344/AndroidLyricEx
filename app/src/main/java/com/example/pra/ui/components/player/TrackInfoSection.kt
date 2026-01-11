package com.example.pra.ui.components.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pra.ui.state.PlayerUiState

@Composable
fun TrackInfoSection(
    state: PlayerUiState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // 增加一点内边距，或者直接依赖父级的 padding
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween, // 两端对齐
        verticalAlignment = Alignment.CenterVertically // 垂直居中
    ) {
        // --- 左侧：文本信息 ---
        Column(
            modifier = Modifier.weight(1f) // 占据剩余空间，防止文字过长把右边图标挤出去
        ) {
            // 歌名
            Text(
                style = MaterialTheme.typography.headlineMedium,
                text = state.songTitle,
                color = Color.White,
                fontSize = 28.sp, // 大字号
                fontWeight = FontWeight.Bold,
                maxLines = 1, // 限制一行
                overflow = TextOverflow.Ellipsis // 超出显示省略号
            )

            Spacer(modifier = Modifier.height(4.dp)) // 字间距

            // 歌手
            Text(
                style = MaterialTheme.typography.bodyLarge,
                text = state.artistName,
                color = Color.White.copy(alpha = 0.6f), // 灰色质感
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // --- 右侧：操作图标 ---
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 收藏图标
            Icon(
                imageVector = Icons.Outlined.Star,
                contentDescription = "Favorite",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .size(32.dp)
                    .clickable { /* TODO: Toggle Favorite */ }
            )

            Spacer(modifier = Modifier.width(20.dp)) // 图标间距

            // 更多图标
            Icon(
                imageVector = Icons.Default.MoreHoriz, // 三个点
                contentDescription = "More Options",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .size(32.dp)
                    .clickable { /* TODO: Show Menu */ }
            )
        }
    }
}
