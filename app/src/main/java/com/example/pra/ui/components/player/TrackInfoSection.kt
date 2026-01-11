package com.example.pra.ui.components.player

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pra.R
import com.example.pra.ui.components.common.FrostedGlassButton
import com.example.pra.ui.state.PlayerUiState

@Composable
fun TrackInfoSection(
    state: PlayerUiState,
    modifier: Modifier = Modifier,
    onFavoriteClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // --- 左侧：文本信息 ---
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.songTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))

            // 歌手 + (可选)点赞数显示
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.artistName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // 如果你想显示点赞数，可以在这里加：
                /*
                if (state.currentSongLikedCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "❤ ${state.currentSongLikedCount}",
                        color = Color(0xFFE91E63).copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                }
                */
            }
        }

        // --- 右侧：操作图标 ---
        Row(verticalAlignment = Alignment.CenterVertically) {

            // 根据状态决定图标和颜色
            val iconRes = if (state.isCurrentSongLiked) R.drawable.heart_solid else R.drawable.heart_regular
            val iconColor = if (state.isCurrentSongLiked) Color.White else Color.White // 选中变红(粉)，未选中白

            FrostedGlassButton(
                painter = painterResource(id = iconRes),
                contentDescription = "Favorite",
                onClick = onFavoriteClick,
                containerSize = 36.dp,
                iconSize = 20.dp,
                iconTint = iconColor, // 动态颜色
                backgroundColor = Color.White.copy(alpha = 0.2f)
            )
        }
    }
}
