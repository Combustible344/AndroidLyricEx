package com.example.pra.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pra.data.model.Audio
import com.example.pra.ui.theme.PraShapes
import com.example.pra.ui.theme.PraSpacing
import com.example.pra.ui.theme.praColors
import com.example.pra.ui.util.formatDuration

/**
 * 统一歌曲行。曲库、首页、队列、搜索都用这一个，保证信息密度与对齐一致。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PraSongRow(
    song: Audio,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showArtwork: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = praColors()
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(PraShapes.row)
            .then(
                if (isCurrent) Modifier.background(colors.glassFaint) else Modifier
            )
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = PraSpacing.sm, vertical = PraSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            leading != null -> leading()
            showArtwork -> PraArtworkSmall(song.albumArtUri, size = 46.dp)
        }

        Spacer(Modifier.width(PraSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) colors.accent else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(PraSpacing.md))

        if (isCurrent && isPlaying) {
            PlayingBars(color = colors.accent)
            Spacer(Modifier.width(PraSpacing.md))
        }

        trailing?.invoke() ?: Text(
            text = formatDuration(song.duration),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textTertiary,
        )
    }
}

/** 正在播放的三根跳动音柱 */
@Composable
fun PlayingBars(
    color: Color,
    modifier: Modifier = Modifier,
    barWidth: androidx.compose.ui.unit.Dp = 2.5.dp,
    height: androidx.compose.ui.unit.Dp = 14.dp,
) {
    val transition = rememberInfiniteTransition(label = "playing-bars")
    val heights = (0..2).map { index ->
        transition.animateFloat(
            initialValue = 0.28f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 420 + index * 130,
                    easing = LinearEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bar-$index",
        )
    }
    Row(
        modifier = modifier.height(height),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        heights.forEach { fraction ->
            val f by fraction
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(height * f)
                    .clip(RoundedCornerShape(barWidth))
                    .background(color),
            )
        }
    }
}
