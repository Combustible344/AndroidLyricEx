package com.example.pra.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    viewModel: MusicPlayerViewModel = viewModel(),
    onUserActivity: () -> Unit = {},
    onPlaylistVisibilityChange: (Boolean) -> Unit = {},
    backgroundActive: Boolean = true
) {
    val state by viewModel.uiState.collectAsState()
    var showPlaylist by remember { mutableStateOf(false) }
    var tempAudioIdForLyric by remember { mutableLongStateOf(-1L) }
    val context = LocalContext.current

    // 文件选择器
    // 音频文件选择器
    val addFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { selectedUri ->
            // --- 核心：检查后缀名 ---
            var isValidAudio = false
            context.contentResolver.query(selectedUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val name = cursor.getString(nameIndex)
                        if (name != null) {
                            // 这里列出你希望支持的格式
                            val supportedExtensions = listOf(".mp3", ".flac", ".wav", ".m4a", ".aac", ".ogg")
                            if (supportedExtensions.any { name.endsWith(it, ignoreCase = true) }) {
                                isValidAudio = true
                            }
                        }
                    }
                }
            }

            if (isValidAudio) {
                context.contentResolver.takePersistableUriPermission(selectedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                viewModel.addFile(selectedUri)
            } else {
                android.widget.Toast.makeText(context, "不支持的音频格式", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val bindLyricLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { selectedUri ->
            // --- 核心：手动检查文件名后缀 ---
            var isLrc = false
            context.contentResolver.query(selectedUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val name = cursor.getString(nameIndex)
                        // 忽略大小写，检查是否以 .lrc 结尾
                        if (name != null && name.endsWith(".lrc", ignoreCase = true)) {
                            isLrc = true
                        }
                    }
                }
            }

            if (isLrc) {
                // 是 lrc 文件，继续处理
                context.contentResolver.takePersistableUriPermission(selectedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (tempAudioIdForLyric != -1L) {
                    viewModel.bindLyric(tempAudioIdForLyric, selectedUri)
                    tempAudioIdForLyric = -1L
                    // 提示成功
                    android.widget.Toast.makeText(context, "歌词绑定成功", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else {
                // 不是 lrc 文件，报错
                android.widget.Toast.makeText(context, "请选择 .lrc 格式的文件", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(showPlaylist) {
        onPlaylistVisibilityChange(showPlaylist)
    }

    AtmosphericFluidBackground(active = backgroundActive) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 注意: 不应用 systemBarsPadding —
                // 沉浸式模式下拖拽歌曲 sheet 会触发瞬时系统栏, 若响应 insets 布局会被挤压跳动
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.padding(top = 16.dp)) {
                TopBarSection(onMoreClick = {}, onMinimizeClick = {})
            }
            Spacer(modifier = Modifier.weight(2f))
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                AlbumArtSection(coverUri = state.coverUri)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                TrackInfoSection(state = state, onFavoriteClick = {viewModel.toggleLike()})
            }
            Spacer(modifier = Modifier.weight(0.5f))

            Column(modifier = Modifier.fillMaxWidth()) {
                ControlsSection(
                    state = state,
                    onPlayPauseClick = { viewModel.togglePlayPause() },
                    onPrevClick = { viewModel.skipToPrevious() },
                    onNextClick = { viewModel.skipToNext() },
                    onSeek = { viewModel.seekTo(it) },
                    onVolumeChange = { viewModel.setVolume(it) }
                )
            }
            Spacer(modifier = Modifier.weight(0.5f))

            Box(modifier = Modifier.padding(bottom = 16.dp)) {
                BottomNavSection(
                    currentMode = state.playMode,
                    onModeClick = { viewModel.cyclePlayMode() },
                    onPlaylistClick = { showPlaylist = true }
                )
            }
        }
    }

    if (showPlaylist) {
        PlaylistBottomSheet(
            playlist = state.playlist,
            currentSongId = state.currentSongId,
            onDismissRequest = { showPlaylist = false },
            onItemClick = { viewModel.playSong(it.id) },
            onScanClick = { viewModel.scanAndAppend() },
            onAddFileClick = { addFileLauncher.launch(arrayOf("audio/*")) },
            onBindLyricClick = {
                tempAudioIdForLyric = it.id
                bindLyricLauncher.launch(arrayOf("*/*"))
            },
            onRemoveClick = { viewModel.removeSong(it) },
            onUserActivity = onUserActivity
        )
    }
}
