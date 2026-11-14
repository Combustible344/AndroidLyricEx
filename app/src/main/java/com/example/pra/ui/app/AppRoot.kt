package com.example.pra.ui.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.pra.ui.home.HomeScreen
import com.example.pra.ui.library.LibraryScreen
import com.example.pra.ui.player.NowPlayingScreen
import com.example.pra.ui.settings.GestureSettingsScreen
import com.example.pra.ui.settings.SceneEditorScreen
import com.example.pra.ui.settings.SettingsScreen
import com.example.pra.ui.theme.PraMotion
import com.example.pra.ui.theme.PraShapes
import com.example.pra.ui.theme.PraSpacing
import com.example.pra.ui.theme.praColors
import com.example.pra.ui.viewmodel.MusicPlayerViewModel
import androidx.compose.runtime.collectAsState

private enum class PraTab(val label: String, val icon: ImageVector) {
    Home("首页", Icons.Rounded.Home),
    Library("曲库", Icons.Rounded.LibraryMusic),
    Settings("设置", Icons.Rounded.Settings),
}

/**
 * 应用根节点。
 *
 * 结构：NavHost( main / player ) —— main 内部是「内容 + MiniPlayer + 底部 Tab」，
 * player 是压栈的全屏播放页（自下而上滑入）。
 *
 * 伪息屏（AOD）在外层由 MainActivity 的 IdleScreenSaver 包裹，
 * 这里只负责把「有弹层/全屏页打开」的事件上报给外层暂停 AOD。
 */
@Composable
fun AppRoot(
    viewModel: MusicPlayerViewModel,
    onUserActivity: () -> Unit = {},
    onOverlayContentVisibilityChange: (Boolean) -> Unit = {},
    onAodSuppressionChange: (Boolean) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val gestureBindings by viewModel.gestureBindings.collectAsState()
    val navController = rememberNavController()
    val colors = praColors()

    // 播放详情页 / 设置二级页 等"需要专注操作"的页面不进入伪息屏
    val backStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(backStackEntry?.destination?.route) {
        val route = backStackEntry?.destination?.route
        onAodSuppressionChange(route != null && route != "main")
    }

    // 任何触摸都算一次"用户活动"，用于 AOD 计时（Initial 阶段观察，不消费事件）
    var lastSignalAt by remember { mutableLongStateOf(0L) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        val now = System.currentTimeMillis()
                        if (now - lastSignalAt > 300) {
                            lastSignalAt = now
                            onUserActivity()
                        }
                    }
                }
            },
    ) {
        NavHost(
            navController = navController,
            startDestination = "main",
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(
                route = "player",
                enterTransition = {
                    slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(PraMotion.Normal, easing = PraMotion.Emphasized),
                    )
                },
                exitTransition = {
                    slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(PraMotion.Normal, easing = PraMotion.Emphasized),
                    )
                },
                popEnterTransition = { null },
                popExitTransition = {
                    slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(PraMotion.Normal, easing = PraMotion.Emphasized),
                    )
                },
            ) {
                NowPlayingScreen(
                    state = state,
                    onCollapse = { navController.popBackStack() },
                    onPlayPause = viewModel::togglePlayPause,
                    onNext = viewModel::skipToNext,
                    onPrevious = viewModel::skipToPrevious,
                    onSeek = viewModel::seekTo,
                    onToggleLike = viewModel::toggleLike,
                    onCycleMode = viewModel::cyclePlayMode,
                    onPlaySong = viewModel::playSong,
                    onOverlayChange = onOverlayContentVisibilityChange,
                )
            }

            composable(
                route = "gestures",
                enterTransition = {
                    slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(PraMotion.Normal, easing = PraMotion.Emphasized),
                    )
                },
                exitTransition = {
                    slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(PraMotion.Normal, easing = PraMotion.Emphasized),
                    )
                },
            ) {
                GestureSettingsScreen(
                    bindings = gestureBindings,
                    onSave = viewModel::saveGestureBindings,
                    onReset = viewModel::resetGestureBindings,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = "scenes",
                enterTransition = {
                    slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(PraMotion.Normal, easing = PraMotion.Emphasized),
                    )
                },
                exitTransition = {
                    slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(PraMotion.Normal, easing = PraMotion.Emphasized),
                    )
                },
            ) {
                SceneEditorScreen(onBack = { navController.popBackStack() })
            }

            composable("main") {
                MainScaffold(
                    viewModel = viewModel,
                    state = state,
                    onOpenPlayer = { navController.navigate("player") },
                    onOpenGestures = { navController.navigate("gestures") },
                    onOpenSceneEditor = { navController.navigate("scenes") },
                    onOverlayChange = onOverlayContentVisibilityChange,
                )
            }
        }
    }
}

@Composable
private fun MainScaffold(
    viewModel: MusicPlayerViewModel,
    state: com.example.pra.ui.state.PlayerUiState,
    onOpenPlayer: () -> Unit,
    onOpenGestures: () -> Unit,
    onOpenSceneEditor: () -> Unit,
    onOverlayChange: (Boolean) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(tab) { onOverlayChange(false) }

    val playCounts by viewModel.playCounts.collectAsState()
    val rankLimit by viewModel.rankLimit.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when (PraTab.entries[tab]) {
                PraTab.Home -> HomeScreen(
                    state = state,
                    playCounts = playCounts,
                    rankLimit = rankLimit,
                    onPlaySong = viewModel::playFromList,
                    onOpenPlayer = onOpenPlayer,
                )

                PraTab.Library -> LibraryScreen(
                    state = state,
                    onPlaySong = viewModel::playFromList,
                    onAddFile = viewModel::addFile,
                    onScan = viewModel::scanAndAppend,
                    onBindLyric = viewModel::bindLyric,
                    onRemove = viewModel::removeSong,
                    onSheetVisibilityChange = onOverlayChange,
                )

                PraTab.Settings -> SettingsScreen(
                    onScan = viewModel::scanAndAppend,
                    onImportLyrics = viewModel::importLyricsFolder,
                    onOpenGestures = onOpenGestures,
                    onOpenSceneEditor = onOpenSceneEditor,
                    rankLimit = rankLimit,
                    onRankLimitChange = viewModel::setRankLimit,
                )
            }
        }

        if (state.currentSongId != -1L) {
            PraMiniPlayer(
                title = state.songTitle,
                artist = state.artistName,
                coverUri = state.coverUri,
                isPlaying = state.isPlaying,
                progress = state.progress,
                onOpen = onOpenPlayer,
                onTogglePlay = viewModel::togglePlayPause,
                onNext = viewModel::skipToNext,
            )
        }

        PraTabBar(
            selected = tab,
            onSelect = { tab = it },
        )
    }
}

@Composable
private fun PraTabBar(selected: Int, onSelect: (Int) -> Unit) {
    val colors = praColors()
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.glassSoft),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PraSpacing.xl, vertical = PraSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            PraTab.entries.forEachIndexed { index, tab ->
                val isSelected = selected == index
                val tint by animateColorAsState(
                    targetValue = if (isSelected) colors.accent else colors.textTertiary,
                    animationSpec = tween(PraMotion.Fast),
                    label = "tab-tint",
                )
                val pillAlpha by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0f,
                    animationSpec = tween(PraMotion.Normal, easing = PraMotion.Emphasized),
                    label = "tab-pill",
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(PraShapes.chip)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(index) }
                        .padding(horizontal = PraSpacing.lg, vertical = PraSpacing.xs),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.accentContainer.copy(alpha = 0.55f * pillAlpha))
                            .padding(horizontal = PraSpacing.md, vertical = PraSpacing.xs),
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = tint,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                    )
                }
            }
        }
    }
}
