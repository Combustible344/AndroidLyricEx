package com.example.pra.ui.app

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pra.ui.components.PraArtwork
import com.example.pra.ui.theme.PraShapes
import com.example.pra.ui.theme.PraSpacing
import com.example.pra.ui.theme.praColors

/**
 * 常驻底部 Mini Player。设计要点：
 * - 玻璃卡片 + 顶部进度细线，不用阴影（深色下阴影脏）；
 * - 点击整卡进入全屏播放页，播放/下一首按钮单独可点；
 * - 高度克制（62dp），把屏幕留给内容。
 */
@Composable
fun PraMiniPlayer(
    title: String,
    artist: String,
    coverUri: Uri?,
    isPlaying: Boolean,
    progress: Float,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = praColors()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PraSpacing.md, vertical = PraSpacing.xs)
            .clip(PraShapes.miniPlayer)
            .background(colors.surfaceHigh)
            .border(1.dp, colors.glassSoft, PraShapes.miniPlayer)
            .clickable(onClick = onOpen)
            .padding(horizontal = PraSpacing.sm, vertical = PraSpacing.sm),
    ) {
        // 进度线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(PraShapes.chip)
                .background(colors.glassSoft),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .clip(PraShapes.chip)
                    .background(colors.accent),
            )
        }

        Spacer(Modifier.height(PraSpacing.sm))

        Row(verticalAlignment = Alignment.CenterVertically) {
            PraArtwork(
                uri = coverUri,
                modifier = Modifier.size(42.dp),
                shape = PraShapes.field,
                fallbackIconSize = 18.dp,
            )

            Spacer(Modifier.width(PraSpacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artist,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(onClick = onTogglePlay, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(26.dp),
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "下一首",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
