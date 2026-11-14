package com.example.pra.ui.player

import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.pra.data.model.LyricLine
import com.example.pra.ui.components.PraArtwork
import com.example.pra.ui.components.PraSongRow
import com.example.pra.ui.state.PlayMode
import com.example.pra.ui.state.PlayerUiState
import com.example.pra.ui.theme.PraMotion
import com.example.pra.ui.theme.PraShapes
import com.example.pra.ui.theme.PraSpacing
import com.example.pra.ui.theme.praColors
import com.example.pra.ui.util.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 全屏播放页。三块内容：
 * 1) 封面 / 歌词 双页（HorizontalPager），封面取色驱动整页渐变；
 * 2) 自绘进度条（拖动预览、松手 seek）；
 * 3) 播放控制与队列入口。
 *
 * 注意：AOD（伪息屏）由外层 IdleScreenSaver 负责，本页只上报「队列弹层是否打开」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    state: PlayerUiState,
    onCollapse: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleLike: () -> Unit,
    onCycleMode: () -> Unit,
    onPlaySong: (Long) -> Unit,
    onOverlayChange: (Boolean) -> Unit,
) {
    val colors = praColors()
    val glow = rememberCoverGlow(state.coverUri, fallback = colors.accent)
    val glowAnimated by animateColorAsState(
        targetValue = glow,
        animationSpec = tween(PraMotion.Slow),
        label = "cover-glow",
    )

    var showQueue by remember { mutableStateOf(false) }
    LaunchedEffect(showQueue) { onOverlayChange(showQueue) }

    val pagerState = rememberPagerState(pageCount = { 2 })

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to glowAnimated.copy(alpha = 0.42f),
                    0.42f to colors.background,
                    1f to colors.background,
                ),
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            NowPlayingTopBar(
                onCollapse = onCollapse,
                onOpenQueue = { showQueue = true },
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                if (page == 0) {
                    CoverPage(coverUri = state.coverUri)
                } else {
                    LyricsPage(state = state)
                }
            }

            PagerDots(current = pagerState.currentPage, count = 2)

            Spacer(Modifier.height(PraSpacing.lg))

            TrackInfo(
                state = state,
                onToggleLike = onToggleLike,
            )

            Spacer(Modifier.height(PraSpacing.md))

            SeekSection(
                state = state,
                onSeek = onSeek,
            )

            Spacer(Modifier.height(PraSpacing.lg))

            ControlRow(
                isPlaying = state.isPlaying,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
            )

            Spacer(Modifier.height(PraSpacing.lg))

            BottomActions(
                playMode = state.playMode,
                queueSize = state.queue.size,
                currentIndex = state.queue.indexOfFirst { it.id == state.currentSongId },
                onCycleMode = onCycleMode,
                onOpenQueue = { showQueue = true },
            )

            Spacer(Modifier.height(PraSpacing.xxl))
        }
    }

    // 队列面板用应用内自绘覆盖层，而不是 ModalBottomSheet：
    // 后者是独立窗口，会在 MIUI 上重新拉起系统栏，破坏沉浸式/AOD。
    androidx.compose.animation.AnimatedVisibility(
        visible = showQueue,
        enter = androidx.compose.animation.slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(PraMotion.Normal, easing = PraMotion.Emphasized),
        ) + androidx.compose.animation.fadeIn(animationSpec = tween(PraMotion.Fast)),
        exit = androidx.compose.animation.slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(PraMotion.Normal, easing = PraMotion.Emphasized),
        ) + androidx.compose.animation.fadeOut(animationSpec = tween(PraMotion.Fast)),
    ) {
        androidx.activity.compose.BackHandler(enabled = showQueue) { showQueue = false }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.52f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                ) { showQueue = false },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .clip(PraShapes.sheet)
                    .background(colors.surface)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                    ) { /* 吃掉点击，避免点面板时关闭 */ },
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = PraSpacing.md, bottom = PraSpacing.sm)
                        .align(Alignment.CenterHorizontally)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(colors.textTertiary.copy(alpha = 0.5f)),
                )
                QueueContent(
                    state = state,
                    onPlaySong = { id ->
                        onPlaySong(id)
                        showQueue = false
                    },
                )
            }
        }
    }
}

@Composable
private fun NowPlayingTopBar(onCollapse: () -> Unit, onOpenQueue: () -> Unit) {
    val colors = praColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PraSpacing.md, vertical = PraSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCollapse) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "收起",
                tint = colors.textPrimary,
                modifier = Modifier.size(28.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "正在播放",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
        }
        IconButton(onClick = onOpenQueue) {
            Icon(
                imageVector = Icons.Rounded.QueueMusic,
                contentDescription = "播放队列",
                tint = colors.textPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun CoverPage(coverUri: Uri?) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val side = minOf(maxWidth, maxHeight) * 0.92f
        PraArtwork(
            uri = coverUri,
            modifier = Modifier.size(side),
            shape = PraShapes.cover,
            fallbackIconSize = side * 0.18f,
        )
    }
}

@Composable
private fun LyricsPage(state: PlayerUiState) {
    val colors = praColors()
    val lines = state.lyrics
    if (lines.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "暂无歌词\n可在播放列表里为该歌曲绑定 .lrc",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textTertiary,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    val listState = rememberLazyListState()
    LaunchedEffect(state.currentLyricIndex) {
        val index = state.currentLyricIndex
        if (index in lines.indices) {
            listState.animateScrollToItem(index, scrollOffset = -260)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = PraSpacing.xxl, vertical = PraSpacing.huge),
        verticalArrangement = Arrangement.spacedBy(PraSpacing.lg),
    ) {
        itemsIndexed(lines) { index, line ->
            val isCurrent = index == state.currentLyricIndex
            val isPast = index < state.currentLyricIndex
            val textColor = when {
                isCurrent -> colors.accent
                isPast -> colors.textTertiary.copy(alpha = 0.55f)
                else -> colors.textSecondary
            }
            Text(
                text = if (isCurrent) {
                    karaokeText(line, state.currentWordIndex, colors.accent, colors.textPrimary)
                } else {
                    AnnotatedString(line.text)
                },
                style = if (isCurrent) {
                    MaterialTheme.typography.headlineSmall
                } else {
                    MaterialTheme.typography.titleMedium
                },
                color = textColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 逐字歌词：已唱的字用强调色，未唱用主文本色 */
private fun karaokeText(
    line: LyricLine,
    wordIndex: Int,
    sungColor: Color,
    upcomingColor: Color,
): AnnotatedString = buildAnnotatedString {
    if (line.words.isEmpty() || wordIndex < 0) {
        withStyle(SpanStyle(color = sungColor)) { append(line.text) }
        return@buildAnnotatedString
    }
    line.words.forEachIndexed { index, word ->
        withStyle(SpanStyle(color = if (index <= wordIndex) sungColor else upcomingColor)) {
            append(word.text)
        }
    }
}

@Composable
private fun PagerDots(current: Int, count: Int) {
    val colors = praColors()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(count) { index ->
            val selected = index == current
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = if (selected) 18.dp else 6.dp, height = 6.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.textPrimary else colors.textTertiary.copy(alpha = 0.4f)),
            )
        }
    }
}

@Composable
private fun TrackInfo(state: PlayerUiState, onToggleLike: () -> Unit) {
    val colors = praColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PraSpacing.xxl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.songTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = state.artistName,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onToggleLike) {
            Icon(
                imageVector = if (state.isCurrentSongLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (state.isCurrentSongLiked) "取消收藏" else "收藏",
                tint = if (state.isCurrentSongLiked) colors.warm else colors.textSecondary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun SeekSection(
    state: PlayerUiState,
    onSeek: (Float) -> Unit,
) {
    val colors = praColors()
    val progress = rememberInterpolatedProgress(state)
    val durationMs = state.durationMs.coerceAtLeast(1L)
    val currentLabel = formatDuration((progress * durationMs).toLong())
    val totalLabel = formatDuration(state.durationMs.coerceAtLeast(0L))

    Column(modifier = Modifier.padding(horizontal = PraSpacing.xxl)) {
        PraSeekBar(
            progress = progress,
            accent = colors.accent,
            track = colors.glassStrong,
            onSeek = onSeek,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = currentLabel,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
            )
            Text(
                text = totalLabel,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
            )
        }
    }
}

/**
 * 进度锚点 + 帧插值：
 * ViewModel 每秒只发一次 (positionMs, anchorNanos)，这里用 withFrameNanos 在帧内补间，
 * 既能 60fps 跟手，又不会因为 10Hz 状态更新导致整页重组。
 */
@Composable
private fun rememberInterpolatedProgress(state: PlayerUiState): Float {
    var progress by remember {
        mutableFloatStateOf(
            if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
        )
    }
    LaunchedEffect(state.positionMs, state.positionAnchorNanos, state.isPlaying, state.durationMs) {
        val duration = state.durationMs
        if (!state.isPlaying || duration <= 0L) {
            progress = if (duration > 0L) state.positionMs.toFloat() / duration else 0f
            return@LaunchedEffect
        }
        val start = state.positionMs
        val anchor = state.positionAnchorNanos
        while (true) {
            androidx.compose.runtime.withFrameNanos { frameNanos ->
                val elapsedMs = (frameNanos - anchor) / 1_000_000L
                val pos = (start + elapsedMs).coerceIn(0L, duration)
                progress = pos.toFloat() / duration
            }
        }
    }
    return progress
}

@Composable
private fun PraSeekBar(
    progress: Float,
    accent: Color,
    track: Color,
    onSeek: (Float) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val shown = (if (dragging) dragFraction else progress).coerceIn(0f, 1f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInputSeek(
                onStart = { fraction ->
                    dragging = true
                    dragFraction = fraction
                },
                onMove = { fraction -> dragFraction = fraction },
                onFinish = {
                    dragging = false
                    onSeek(dragFraction)
                },
            ),
    ) {
        val centerY = size.height / 2f
        val trackHeight = 4.dp.toPx()
        val radius = trackHeight / 2f
        drawRoundRect(
            color = track,
            topLeft = Offset(0f, centerY - radius),
            size = Size(size.width, trackHeight),
            cornerRadius = CornerRadius(radius, radius),
        )
        drawRoundRect(
            color = accent,
            topLeft = Offset(0f, centerY - radius),
            size = Size(size.width * shown, trackHeight),
            cornerRadius = CornerRadius(radius, radius),
        )
        drawCircle(
            color = accent,
            radius = (if (dragging) 8.dp else 6.dp).toPx(),
            center = Offset(size.width * shown, centerY),
        )
    }
}

/** 一段手势：按下即预览、拖动跟随、松手提交 */
private fun Modifier.pointerInputSeek(
    onStart: (Float) -> Unit,
    onMove: (Float) -> Unit,
    onFinish: () -> Unit,
): Modifier = this.then(
    Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val width = size.width.toFloat().coerceAtLeast(1f)
            onStart((down.position.x / width).coerceIn(0f, 1f))
            down.consume()
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                onMove((change.position.x / width).coerceIn(0f, 1f))
                change.consume()
                if (!change.pressed) break
            }
            onFinish()
        }
    },
)

@Composable
private fun ControlRow(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val colors = praColors()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "上一首",
                tint = colors.textPrimary,
                modifier = Modifier.size(34.dp),
            )
        }
        Spacer(Modifier.size(PraSpacing.xxl))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(74.dp)
                .clip(CircleShape)
                .background(colors.accent)
                .clickableNoRipple(onPlayPause),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                tint = colors.onAccent,
                modifier = Modifier.size(38.dp),
            )
        }
        Spacer(Modifier.size(PraSpacing.xxl))
        IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "下一首",
                tint = colors.textPrimary,
                modifier = Modifier.size(34.dp),
            )
        }
    }
}

@Composable
private fun BottomActions(
    playMode: PlayMode,
    queueSize: Int,
    currentIndex: Int,
    onCycleMode: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val colors = praColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PraSpacing.xxl),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(PraShapes.chip)
                .background(colors.glassFaint)
                .clickableNoRipple(onCycleMode)
                .padding(horizontal = PraSpacing.md, vertical = PraSpacing.sm),
        ) {
            Icon(
                imageVector = when (playMode) {
                    PlayMode.Sequential -> Icons.Rounded.PlaylistPlay
                    PlayMode.LoopAll -> Icons.Rounded.Repeat
                    PlayMode.LoopOne -> Icons.Rounded.RepeatOne
                    PlayMode.Shuffle -> Icons.Rounded.Shuffle
                },
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(PraSpacing.xs))
            Text(
                text = playMode.label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(PraShapes.chip)
                .background(colors.glassFaint)
                .clickableNoRipple(onOpenQueue)
                .padding(horizontal = PraSpacing.md, vertical = PraSpacing.sm),
        ) {
            Icon(
                imageVector = Icons.Rounded.QueueMusic,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(PraSpacing.xs))
            Text(
                text = if (currentIndex >= 0) "${currentIndex + 1} / $queueSize" else "$queueSize 首",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun QueueContent(state: PlayerUiState, onPlaySong: (Long) -> Unit) {
    val colors = praColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PraSpacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "播放队列",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            Text(
                text = "${state.queue.size} 首",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textTertiary,
            )
        }
        Spacer(Modifier.height(PraSpacing.md))
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(state.queue, key = { _, song -> song.id }) { _, song ->
                PraSongRow(
                    song = song,
                    isCurrent = song.id == state.currentSongId,
                    isPlaying = state.isPlaying,
                    onClick = { onPlaySong(song.id) },
                )
            }
            item { Spacer(Modifier.height(PraSpacing.xxl)) }
        }
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    return this.clickable(
        interactionSource = interaction,
        indication = null,
        onClick = onClick,
    )
}

/**
 * 从封面提取一个主色作为页面氛围光。
 * 失败/无封面时回退到主题强调色；过亮/过暗都会向中间压一层，保证文字对比度。
 */
@Composable
private fun rememberCoverGlow(uri: Uri?, fallback: Color): Color {
    val context = androidx.compose.ui.platform.LocalContext.current
    return produceState(initialValue = fallback, uri, fallback) {
        value = if (uri == null) {
            fallback
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val request = ImageRequest.Builder(context)
                        .data(uri)
                        .size(180)
                        .build()
                    val result = context.imageLoader.execute(request)
                    val bitmap = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
                    val palette = bitmap?.let {
                        Palette.from(it).maximumColorCount(24).generate()
                    }
                    val rgb = (palette?.vibrantSwatch
                        ?: palette?.dominantSwatch
                        ?: palette?.mutedSwatch)?.rgb
                    rgb?.let { Color(it) }
                }.getOrNull() ?: fallback
            }.let { raw ->
                // 亮度约束：太亮的颜色压暗，避免盖住白字
                if (raw.luminance() > 0.62f) raw.copy(alpha = 1f).blendWithBlack(0.35f) else raw
            }
        }
    }.value
}

private fun Color.blendWithBlack(ratio: Float): Color = Color(
    red = red * (1f - ratio),
    green = green * (1f - ratio),
    blue = blue * (1f - ratio),
    alpha = alpha,
)
