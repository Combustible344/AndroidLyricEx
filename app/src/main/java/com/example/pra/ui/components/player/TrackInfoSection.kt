package com.example.pra.ui.components.player

import androidx.compose.foundation.background
import com.example.pra.ui.components.common.FrostedGlassButton
import com.example.pra.R

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pra.ui.state.PlayerUiState
import androidx.compose.ui.graphics.Brush

@Composable
fun TrackInfoSection(
    state: PlayerUiState,
    modifier: Modifier = Modifier,
    onFavoriteClick: () -> Unit = {},
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


            // 外层容器：负责定位和点击交互

            FrostedGlassButton(
                painter = painterResource(id = R.drawable.heart_regular),
                contentDescription = "Favorite",
                onClick = onFavoriteClick, // 直接传入回调
                containerSize = 36.dp,     // 对应您原代码的 size(36.dp)
                iconSize = 20.dp,          // 对应您原代码的 size(20.dp)
                iconTint = Color.White,    // 对应您原代码的 tint
                backgroundColor = Color.White.copy(alpha = 0.2f) // 对应您原代码的 background
            )
        }
    }
}
