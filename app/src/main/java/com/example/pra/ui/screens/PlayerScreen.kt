package com.example.pra.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pra.ui.components.AtmosphericFluidBackground
import com.example.pra.ui.components.player.AlbumArtSection
import com.example.pra.ui.components.player.BottomNavSection
import com.example.pra.ui.components.player.ControlsSection
import com.example.pra.ui.components.player.TopBarSection
import com.example.pra.ui.components.player.TrackInfoSection
import com.example.pra.ui.components.playlist.PlaylistBottomSheet // 引入组件
import com.example.pra.ui.viewmodel.MusicPlayerViewModel

@Composable
fun PlayerScreen(
    viewModel: MusicPlayerViewModel = viewModel()
) {
    // 1. 收集 UI 状态
    val state by viewModel.uiState.collectAsState()

    // 2. 控制播放列表显示的本地状态
    var showPlaylist by remember { mutableStateOf(false) }

    // 3. 背景层
    AtmosphericFluidBackground {

        // 4. 主内容层
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // --- A: TopBar ---
            Box(modifier = Modifier.padding(top = 16.dp)) {
                TopBarSection(
                    onMoreClick = { /* TODO */ },
                    onMinimizeClick = { /* TODO */ }
                )
            }

            Spacer(modifier = Modifier.weight(2f))

            // --- B: AlbumArt ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                AlbumArtSection(coverUri = state.coverUri)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // --- C: TrackInfo ---
            Box(modifier = Modifier.fillMaxWidth()) {
                TrackInfoSection(
                    state = state,
                    onFavoriteClick = { /* TODO */ },
                )
            }

            Spacer(modifier = Modifier.weight(0.5f))

            // --- D: Controls ---
            Column(modifier = Modifier.fillMaxWidth()) {
                ControlsSection(
                    state = state,
                    onPlayPauseClick = { viewModel.togglePlayPause() },
                    onPrevClick = { viewModel.skipToPrevious() },
                    onNextClick = { viewModel.skipToNext() },
                    onSeek = { progressValue ->
                        viewModel.seekTo(progressValue)
                    }
                )
            }

            Spacer(modifier = Modifier.weight(0.5f))

            // --- E: BottomNav ---
            Box(modifier = Modifier.padding(bottom = 16.dp)) {
                BottomNavSection(
                    currentMode = state.playMode, // 传入状态
                    onModeClick = { viewModel.cyclePlayMode() },
                    // --- 关键修改：点击列表按钮，打开 BottomSheet ---
                    onPlaylistClick = { showPlaylist = true }
                )
            }
        }
    }

    // --- 5. 播放列表弹窗 (显示时覆盖在界面之上) ---
    if (showPlaylist) {
        PlaylistBottomSheet(
            playlist = state.playlist,           // 传入数据源
            currentSongId = state.currentSongId, // 传入当前播放ID(用于高亮)
            onDismissRequest = { showPlaylist = false }, // 点击遮罩/下滑关闭
            onItemClick = { audio ->
                // 点击切歌
                viewModel.playSong(audio.id)
                // 切歌后可以选择自动关闭列表，或者保持打开
                // showPlaylist = false
            }

        )
    }
}
