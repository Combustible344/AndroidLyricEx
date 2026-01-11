package com.example.pra.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pra.ui.components.AtmosphericFluidBackground
import com.example.pra.ui.components.player.AlbumArtSection
import com.example.pra.ui.components.player.BottomNavSection
import com.example.pra.ui.components.player.ControlsSection
import com.example.pra.ui.components.player.TopBarSection
import com.example.pra.ui.components.player.TrackInfoSection
import com.example.pra.ui.components.playlist.PlaylistBottomSheet
import com.example.pra.ui.viewmodel.MusicPlayerViewModel

@Composable
fun PlayerScreen(
    viewModel: MusicPlayerViewModel = viewModel()
) {
    // 1. 收集 UI 状态
    val state by viewModel.uiState.collectAsState()

    // 2. 控制播放列表显示的本地状态
    var showPlaylist by remember { mutableStateOf(false) }

    // 3. 临时记录：当前正在为哪首歌绑定歌词
    var tempAudioIdForLyric by remember { mutableLongStateOf(-1L) }

    val context = LocalContext.current

    // --- A. 文件选择器：添加歌曲 ---
    val addFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // 关键：申请持久化读权限
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, flags)

            viewModel.addFile(it)
        }
    }

    // --- B. 文件选择器：绑定歌词 ---
    val bindLyricLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // 关键：申请持久化读权限
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, flags)

            if (tempAudioIdForLyric != -1L) {
                viewModel.bindLyric(tempAudioIdForLyric, it)
                tempAudioIdForLyric = -1L // 重置
            }
        }
    }

    // --- 4. 背景层 ---
    AtmosphericFluidBackground {

        // 5. 主内容层
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // TopBar
            Box(modifier = Modifier.padding(top = 16.dp)) {
                TopBarSection(
                    onMoreClick = { /* TODO */ },
                    onMinimizeClick = { /* TODO */ }
                )
            }

            Spacer(modifier = Modifier.weight(2f))

            // AlbumArt
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                AlbumArtSection(coverUri = state.coverUri)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // TrackInfo
            Box(modifier = Modifier.fillMaxWidth()) {
                TrackInfoSection(
                    state = state,
                    onFavoriteClick = { /* TODO */ },
                )
            }

            Spacer(modifier = Modifier.weight(0.5f))

            // Controls
            Column(modifier = Modifier.fillMaxWidth()) {
                ControlsSection(
                    state = state,
                    onPlayPauseClick = { viewModel.togglePlayPause() },
                    onPrevClick = { viewModel.skipToPrevious() },
                    onNextClick = { viewModel.skipToNext() },
                    onSeek = { progressValue -> viewModel.seekTo(progressValue) }
                )
            }

            Spacer(modifier = Modifier.weight(0.5f))

            // BottomNav
            Box(modifier = Modifier.padding(bottom = 16.dp)) {
                BottomNavSection(
                    currentMode = state.playMode,
                    onModeClick = { viewModel.cyclePlayMode() },
                    onPlaylistClick = { showPlaylist = true }
                )
            }
        }
    }

    // --- 6. 播放列表弹窗 ---
    if (showPlaylist) {
        PlaylistBottomSheet(
            playlist = state.playlist,
            currentSongId = state.currentSongId,
            onDismissRequest = { showPlaylist = false },
            onItemClick = { audio -> viewModel.playSong(audio.id) },

            // 新增功能回调
            onScanClick = {
                viewModel.scanAndAppend()
            },
            onAddFileClick = {
                // 打开系统文件选择器，筛选音频
                addFileLauncher.launch(arrayOf("audio/*"))
            },
            onBindLyricClick = { audio ->
                tempAudioIdForLyric = audio.id
                // 打开系统文件选择器，允许所有文件 (有些 lrc 文件 mimeType 不准)
                bindLyricLauncher.launch(arrayOf("*/*"))
            },onRemoveClick = { audio ->
                viewModel.removeSong(audio)
            }

        )
    }
}
