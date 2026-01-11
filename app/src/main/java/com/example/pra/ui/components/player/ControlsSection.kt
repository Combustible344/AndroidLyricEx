package com.example.pra.ui.components.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.pra.R
import com.example.pra.ui.components.common.MaterialCustomSlider
import com.example.pra.ui.state.PlayerUiState

@Composable
fun ControlsSection(
    state: PlayerUiState,
    modifier: Modifier = Modifier,
    onPlayPauseClick: () -> Unit = {},
    onPrevClick: () -> Unit = {},
    onNextClick: () -> Unit = {},
    onSeek: (Float) -> Unit = {},
    onVolumeChange: (Float) -> Unit = {}
) {
    Column(modifier = modifier.fillMaxWidth()) {

        // 1. 进度条 (内部自带动画)
        Column(modifier = Modifier.fillMaxWidth()) {
            MaterialCustomSlider(
                value = state.progress,
                onValueChange = { onSeek(it) },
                trackHeight = 12.dp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(state.currentTime, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                Text(state.totalTime, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 2. 播放按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevClick) {
                Icon(painterResource(id = R.drawable.backward_step_solid), "Previous", tint = Color.White, modifier = Modifier.size(25.dp))
            }
            Spacer(modifier = Modifier.width(40.dp))
            IconButton(onClick = onPlayPauseClick, modifier = Modifier.size(72.dp)) {
                Icon(
                    painterResource(id = if (state.isPlaying) R.drawable.pause_solid else R.drawable.play_solid),
                    "Play/Pause", tint = Color.White, modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.width(40.dp))
            IconButton(onClick = onNextClick) {
                Icon(painterResource(id = R.drawable.forward_step_solid), "Next", tint = Color.White, modifier = Modifier.size(25.dp))
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        // 3. 音量条
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(painterResource(id = R.drawable.volume_off_solid), "Low Volume", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))

            MaterialCustomSlider(
                value = state.volume,
                onValueChange = { onVolumeChange(it) },
                trackHeight = 8.dp,
                modifier = Modifier.weight(1f)
            )

            Icon(painterResource(id = R.drawable.volume_high_solid), "High Volume", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        }
    }
}
