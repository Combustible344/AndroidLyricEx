package com.example.pra.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.pra.data.model.Audio
import com.example.pra.data.repository.AudioRepository
import com.example.pra.service.MusicService
import com.example.pra.ui.state.PlayMode
import com.example.pra.ui.state.PlayerUiState
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AudioRepository(application)

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState = _uiState.asStateFlow()

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var currentPlaylist: List<Audio> = emptyList()

    init {
        connectToService()
    }

    private fun connectToService() {
        val sessionToken = SessionToken(
            getApplication(),
            ComponentName(getApplication(), MusicService::class.java)
        )

        controllerFuture = MediaController.Builder(getApplication(), sessionToken)
            .buildAsync()

        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                loadLocalMusic()
                setupPlayerListener()
                syncPlayModeState() // 连接后立即同步状态
                startProgressUpdateLoop()
            } catch (e: Exception) {
                Log.e("MusicPlayerVM", "Service connection failed", e)
            }
        }, MoreExecutors.directExecutor())
    }

    private fun loadLocalMusic() {
        viewModelScope.launch {
            val songs = repository.getLocalAudio()
            if (songs.isEmpty()) return@launch
            currentPlaylist = songs
            _uiState.update { it.copy(playlist = songs) }

            val items = songs.map { audio ->
                MediaItem.Builder()
                    .setUri(audio.uri)
                    .setMediaId(audio.id.toString())
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(audio.title)
                            .setArtist(audio.artist)
                            .setArtworkUri(audio.albumArtUri)
                            .build()
                    )
                    .build()
            }

            mediaController?.let { player ->
                if (player.mediaItemCount == 0) {
                    player.setMediaItems(items)
                    player.prepare()

                    // --- 默认强制设为随机播放 ---
                    player.shuffleModeEnabled = true
                    player.repeatMode = Player.REPEAT_MODE_ALL
                }
            }
        }
    }

    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.let { item ->
                    val currentId = item.mediaId.toLongOrNull() ?: -1L
                    val metadata = item.mediaMetadata
                    _uiState.update { state ->
                        state.copy(
                            songTitle = metadata.title.toString(),
                            artistName = metadata.artist.toString(),
                            coverUri = metadata.artworkUri,
                            currentSongId = currentId,
                            totalTime = formatTime(mediaController?.duration ?: 0L)
                        )
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _uiState.update {
                        it.copy(totalTime = formatTime(mediaController?.duration ?: 0L))
                    }
                }
            }

            // 监听底层状态变化，同步 UI
            override fun onRepeatModeChanged(repeatMode: Int) {
                syncPlayModeState()
            }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                syncPlayModeState()
            }
        })
    }

    // --- 切换逻辑 ---
    fun cyclePlayMode() {
        mediaController?.let { player ->
            val currentMode = _uiState.value.playMode

            // 目标：当前是单曲 ? 变随机 : 变单曲
            val newMode = if (currentMode == PlayMode.LoopOne) PlayMode.Shuffle else PlayMode.LoopOne

            when (newMode) {
                PlayMode.LoopOne -> {
                    // 1. 先关随机
                    player.shuffleModeEnabled = false
                    // 2. 再开单曲循环
                    player.repeatMode = Player.REPEAT_MODE_ONE
                }
                PlayMode.Shuffle -> {
                    // 1. 开启随机
                    player.shuffleModeEnabled = true
                    // 2. 确保列表循环 (否则随机完一轮就停了)
                    player.repeatMode = Player.REPEAT_MODE_ALL
                }
            }

            // 乐观更新 UI
            _uiState.update { it.copy(playMode = newMode) }
        }
    }

    // --- 状态同步 ---
    private fun syncPlayModeState() {
        mediaController?.let { player ->
            val mode = if (player.repeatMode == Player.REPEAT_MODE_ONE) {
                PlayMode.LoopOne
            } else {
                PlayMode.Shuffle
            }
            _uiState.update { it.copy(playMode = mode) }
        }
    }

    // --- 核心逻辑修改：手动下一首 ---
    fun skipToNext() {
        mediaController?.let { player ->
            // 1. 如果是单曲循环模式 -> 强制重播当前歌曲，严禁切歌
            if (player.repeatMode == Player.REPEAT_MODE_ONE) {
                player.seekTo(0) // 回到 0:00
                player.play()    // 确保开始播放
                return@let       // 结束函数，不执行后面的 seekToNext
            }

            // 2. 否则 (随机模式)，交给 ExoPlayer 自动跳到下一首 (随机后的下一首)
            if (player.hasNextMediaItem()) {
                player.seekToNext()
            } else {
                player.seekToDefaultPosition(0)
            }
        }
    }

    // --- 核心逻辑修改：手动上一首 ---
    fun skipToPrevious() {
        mediaController?.let { player ->
            // 1. 如果是单曲循环模式 -> 强制重播当前歌曲
            if (player.repeatMode == Player.REPEAT_MODE_ONE) {
                player.seekTo(0)
                player.play()
                return@let
            }

            // 2. 否则正常切上一首
            player.seekToPrevious()
        }
    }

    fun togglePlayPause() {
        mediaController?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun playSong(audioId: Long) {
        val index = currentPlaylist.indexOfFirst { it.id == audioId }
        if (index != -1) {
            mediaController?.let { player ->
                player.seekToDefaultPosition(index)
                player.play()
            }
        }
    }

    private fun startProgressUpdateLoop() {
        viewModelScope.launch {
            while (isActive) {
                val player = mediaController
                if (player != null && player.isPlaying) {
                    val currentPos = player.currentPosition
                    val duration = player.duration.coerceAtLeast(1L)
                    val progress = currentPos.toFloat() / duration.toFloat()
                    _uiState.update {
                        it.copy(
                            progress = progress,
                            currentTime = formatTime(currentPos),
                            totalTime = formatTime(duration)
                        )
                    }
                }
                delay(1000L)
            }
        }
    }

    fun seekTo(progress: Float) {
        mediaController?.let { player ->
            val duration = player.duration
            val position = (duration * progress).toLong()
            player.seekTo(position)
            _uiState.update {
                it.copy(progress = progress, currentTime = formatTime(position))
            }
        }
    }

    private fun formatTime(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
