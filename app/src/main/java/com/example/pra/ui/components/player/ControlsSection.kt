package com.example.pra.ui.components.player

import com.example.pra.ui.components.common.MaterialCustomSlider

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
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.pra.ui.state.PlayerUiState

@Composable
fun ControlsSection(
    state: PlayerUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // ---------------------------------------
        // 1. 进度条区域
        // ---------------------------------------
        var sliderPosition by remember { mutableFloatStateOf(0.35f) }

        Column(modifier = Modifier.fillMaxWidth()) {

            // 进度条
            MaterialCustomSlider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it },
                // --- 修改点：这里控制粗细 ---
                trackHeight = 14.dp, // 增大到 8dp，如果不满意可以试 10dp 或 12dp
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp)) // 稍微拉开一点间距

            // 时间戳
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "1:24",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = "-2:48",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ---------------------------------------
        // 2. 播放控制按钮 (上一首 - 播放 - 下一首)
        // ---------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 上一首
            IconButton(onClick = { /* Previous */ }) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.width(40.dp))

            // 播放/暂停
            IconButton(
                onClick = { /* Toggle Play */ },
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                    contentDescription = "Play/Pause",
                    tint = Color.White,
                    modifier = Modifier.size(72.dp)
                )
            }

            Spacer(modifier = Modifier.width(40.dp))

            // 下一首
            IconButton(onClick = { /* Next */ }) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        // ---------------------------------------
        // 3. 音量控制条
        // ---------------------------------------
        var volumePosition by remember { mutableFloatStateOf(0.6f) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 音量小图标
            Icon(
                imageVector = Icons.Default.VolumeDown,
                contentDescription = "Low Volume",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )

            // 音量滑块
            MaterialCustomSlider(
                value = volumePosition, // 修正：绑定到 volumePosition
                onValueChange = { volumePosition = it },
                // --- 修改点：音量条粗细 ---
                trackHeight = 6.dp, // 稍微比进度条细一点，或者设成一样的 8dp
                modifier = Modifier.weight(1f) // 修正：必须用 weight，否则挤飞右边图标
            )

            // 音量大图标
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = "High Volume",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
