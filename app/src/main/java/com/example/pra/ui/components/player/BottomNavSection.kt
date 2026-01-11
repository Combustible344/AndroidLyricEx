package com.example.pra.ui.components.player

import com.example.pra.ui.components.common.FrostedGlassButton

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.pra.R
import androidx.compose.material.icons.Icons // 需要引入
import androidx.compose.material.icons.rounded.RepeatOne // 需要引入
import androidx.compose.ui.graphics.vector.rememberVectorPainter // 需要引入
import com.example.pra.ui.state.PlayMode // 引入枚举

@Composable
fun BottomNavSection(
    currentMode: PlayMode,
    modifier: Modifier = Modifier,
    onModeClick: () -> Unit = {}, // 这里实际上是 Mode 切换
    onPlaylistClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 30.dp, top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween, // 两端对齐
        verticalAlignment = Alignment.CenterVertically
    ) {
        val iconPainter = when (currentMode) {
            PlayMode.Shuffle -> painterResource(id = R.drawable.shuffle_solid)
            // 如果你没有 repeat_one 的 xml，可以使用 Compose 自带的矢量图
            PlayMode.LoopOne -> painterResource(id=R.drawable.repeat_solid)
        }
        // 1. 左侧按钮 (循环模式) - 套上磨砂壳
        FrostedGlassButton(
            painter = iconPainter,
            contentDescription = "Mode",
            onClick = onModeClick,
            iconSize = 18.dp, // 图标大小微调
            containerSize = 40.dp // 按钮整体大小
        )

        // 2. 右侧按钮 (播放列表) - 套上磨砂壳
        FrostedGlassButton(
            painter = painterResource(id = R.drawable.list_ul_solid),
            contentDescription = "Playlist",
            onClick = onPlaylistClick,
            iconSize = 18.dp,
            containerSize = 40.dp
        )
    }
}