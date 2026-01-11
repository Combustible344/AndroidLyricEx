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
import com.example.pra.ui.state.PlayMode // 确保 PlayMode 枚举已定义且只包含 LoopOne 和 Shuffle
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
                // 1. 先加载音乐数据
                loadLocalMusic()
                // 2. 设置监听器
                setupPlayerListener()
                // 3. 强制同步一次播放模式 (解决初始状态不对的问题)
                syncPlayModeState()
                // 4. 启动进度条心跳
                startProgressUpdateLoop()
            } catch (e: Exception) {
                Log.e("ViewModel", "Service connection failed", e)
            }
        }, MoreExecutors.directExecutor())
    }

    private fun loadLocalMusic() {
        viewModelScope.launch {
            val songs = repository.getLocalAudio()
            if (songs.isEmpty()) return@launch
            currentPlaylist = songs

            // 初始化 UI 数据
            _uiState.update { it.copy(playlist = songs) }

            // 转换数据给播放器
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

                    // --- 核心修复：强制初始化模式 ---
                    // 如果当前是“顺序播放”（Shuffle关，Repeat全），强制设为随机，
                    // 确保状态只在 Shuffle 和 LoopOne 之间
                    if (!player.shuffleModeEnabled && player.repeatMode != Player.REPEAT_MODE_ONE) {
                        player.shuffleModeEnabled = true
                        player.repeatMode = Player.REPEAT_MODE_ALL
                    }
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

            // 监听模式变化，确保 UI 永远同步
            override fun onRepeatModeChanged(repeatMode: Int) {
                syncPlayModeState()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                syncPlayModeState()
            }
        })
    }

    // --- 核心逻辑：只在单曲循环和随机播放之间切换 ---
    fun cyclePlayMode() {
        mediaController?.let { player ->
            val currentMode = _uiState.value.playMode

            // 1. 决定下一个模式
            // 如果是 单曲 -> 变 随机
            // 如果是 随机 -> 变 单曲
            val newMode = if (currentMode == PlayMode.LoopOne) {
                PlayMode.Shuffle
            } else {
                PlayMode.LoopOne
            }

            // 2. 将模式应用给 ExoPlayer
            when (newMode) {
                PlayMode.LoopOne -> {
                    // 单曲循环：开启 RepeatOne，关闭 Shuffle
                    player.repeatMode = Player.REPEAT_MODE_ONE
                    player.shuffleModeEnabled = false
                }
                PlayMode.Shuffle -> {
                    // 随机播放：开启 Shuffle，Repeat 设为 All (列表循环)
                    player.repeatMode = Player.REPEAT_MODE_ALL
                    player.shuffleModeEnabled = true
                }
            }

            // 3. 立即更新 UI，防止等待回调导致的延迟
            _uiState.update { it.copy(playMode = newMode) }
        }
    }

    // --- 状态同步：将播放器的实际状态映射回你的二选一枚举 ---
    private fun syncPlayModeState() {
        mediaController?.let { player ->
            // 只要 repeatMode 是 ONE，就认为是单曲循环
            // 其他情况（包括顺序播放或随机播放）都归类为 Shuffle (或者是你想默认的状态)
            val mode = if (player.repeatMode == Player.REPEAT_MODE_ONE) {
                PlayMode.LoopOne
            } else {
                // 如果当前不是单曲循环，且不是随机（比如顺序播放），这在二选一逻辑里算作异常
                // 但为了 UI 显示，我们把它归为 Shuffle
                PlayMode.Shuffle
            }
            _uiState.update { it.copy(playMode = mode) }
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
                it.copy(
                    progress = progress,
                    currentTime = formatTime(position)
                )
            }
        }
    }

    fun togglePlayPause() {
        mediaController?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun skipToNext() {
        mediaController?.seekToNext()
    }

    fun skipToPrevious() {
        mediaController?.seekToPrevious()
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
