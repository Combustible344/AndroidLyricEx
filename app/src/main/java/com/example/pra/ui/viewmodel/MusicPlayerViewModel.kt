package com.example.pra.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.media.MediaMetadataRetriever
import android.net.Uri
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
import com.example.pra.data.repository.PlayerPrefs
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
    private val context = application.applicationContext

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
                // 1. 加载数据
                loadSavedPlaylist()
                // 2. 监听器
                setupPlayerListener()
                // 3. 启动高频进度同步
                startProgressUpdateLoop()
            } catch (e: Exception) {
                Log.e("MusicPlayerVM", "Service connection failed", e)
            }
        }, MoreExecutors.directExecutor())
    }

    // --- 数据加载与恢复 ---

    private fun loadSavedPlaylist() {
        viewModelScope.launch {
            var songs = repository.loadPlaylistFromFile()
            // 第一次安装，尝试自动扫描
            if (songs.isEmpty()) {
                songs = repository.scanLocalAudio()
                repository.savePlaylist(songs)
            }
            // 放入播放器，但不自动播放
            updatePlaylistAndPlayer(songs, autoPlay = false)
            // 恢复上次的状态 (模式、进度、音量)
            restorePlayerState()
        }
    }

    private fun restorePlayerState() {
        viewModelScope.launch {
            val prefs = repository.loadPlayerPrefs()
            mediaController?.let { player ->
                // 恢复模式
                val savedMode = try { PlayMode.valueOf(prefs.playModeName) } catch (e: Exception) { PlayMode.Shuffle }
                applyPlayModeToPlayer(player, savedMode)

                // 恢复音量
                player.volume = prefs.volume

                // 恢复歌曲
                val index = currentPlaylist.indexOfFirst { it.id == prefs.currentSongId }
                if (index != -1) {
                    player.seekToDefaultPosition(index)
                    if (prefs.currentPosition > 0) player.seekTo(prefs.currentPosition)
                }

                // 更新 UI
                _uiState.update {
                    it.copy(
                        playMode = savedMode,
                        volume = prefs.volume,
                        currentTime = formatTime(prefs.currentPosition),
                        progress = if(player.duration > 0) prefs.currentPosition.toFloat()/player.duration else 0f
                    )
                }
            }
        }
    }

    private fun saveCurrentState() {
        mediaController?.let { player ->
            val currentId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: -1L
            val pos = player.currentPosition
            val mode = _uiState.value.playMode.name
            val vol = player.volume

            viewModelScope.launch {
                repository.savePlayerPrefs(PlayerPrefs(currentId, pos, mode, vol))
            }
        }
    }

    // --- 文件操作 ---

    fun scanAndAppend() {
        viewModelScope.launch {
            val newSongs = repository.scanLocalAudio()
            val current = _uiState.value.playlist.toMutableList()
            var addedCount = 0
            newSongs.forEach { newSong ->
                if (current.none { it.id == newSong.id }) {
                    current.add(0, newSong)
                    addedCount++
                }
            }
            if (addedCount > 0) {
                updatePlaylistAndPlayer(current, autoPlay = false) // 只是追加，不打断播放
                repository.savePlaylist(current)
            }
        }
    }

    fun addFile(uri: Uri) {
        viewModelScope.launch {
            val meta = extractMetadata(uri)
            val newAudio = Audio(
                id = System.currentTimeMillis(),
                uri = uri,
                title = meta.title,
                artist = meta.artist,
                duration = meta.duration,
                albumId = 0L,
                albumArtUri = null
            )
            val current = _uiState.value.playlist.toMutableList()
            if (current.none { it.uri == uri }) {
                current.add(0, newAudio)
                updatePlaylistAndPlayer(current, autoPlay = false)
                repository.savePlaylist(current)
            }
        }
    }

    fun removeSong(audio: Audio) {
        viewModelScope.launch {
            val current = _uiState.value.playlist.toMutableList()
            current.removeAll { it.id == audio.id }
            _uiState.update { it.copy(playlist = current) }
            currentPlaylist = current
            repository.savePlaylist(current)
            // 可选：如果希望删除后立即同步给播放器，可以调用 updatePlaylistAndPlayer(current, false)
        }
    }

    fun bindLyric(audioId: Long, lyricUri: Uri) {
        viewModelScope.launch {
            val current = _uiState.value.playlist.toMutableList()
            val index = current.indexOfFirst { it.id == audioId }
            if (index != -1) {
                current[index] = current[index].copy(lyricUri = lyricUri)
                _uiState.update { it.copy(playlist = current) }
                currentPlaylist = current
                repository.savePlaylist(current)
            }
        }
    }

    // --- 播放控制 ---

    fun cyclePlayMode() {
        mediaController?.let { player ->
            val currentMode = _uiState.value.playMode
            val newMode = if (currentMode == PlayMode.LoopOne) PlayMode.Shuffle else PlayMode.LoopOne

            applyPlayModeToPlayer(player, newMode)
            _uiState.update { it.copy(playMode = newMode) }
            saveCurrentState()
        }
    }

    fun setVolume(newVolume: Float) {
        mediaController?.let {
            it.volume = newVolume
            _uiState.update { s -> s.copy(volume = newVolume) }
            saveCurrentState()
        }
    }

    fun skipToNext() {
        mediaController?.let { player ->
            // 单曲循环模式强制重播，不切歌
            if (player.repeatMode == Player.REPEAT_MODE_ONE) {
                player.seekTo(0)
                player.play()
            } else {
                if (player.hasNextMediaItem()) player.seekToNext() else player.seekToDefaultPosition(0)
            }
        }
    }

    fun skipToPrevious() {
        mediaController?.let { player ->
            if (player.repeatMode == Player.REPEAT_MODE_ONE) {
                player.seekTo(0)
                player.play()
            } else {
                player.seekToPrevious()
            }
        }
    }

    fun togglePlayPause() {
        mediaController?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun seekTo(progress: Float) {
        mediaController?.let { player ->
            val pos = (player.duration * progress).toLong()
            player.seekTo(pos)
            _uiState.update { it.copy(progress = progress, currentTime = formatTime(pos)) }
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

    // --- 内部辅助 ---

    private fun updatePlaylistAndPlayer(songs: List<Audio>, autoPlay: Boolean = true) {
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
            // 如果列表变动，重新设置
            // 注意：这会重置当前播放，如果不想重置需要 diff，这里简化处理
            val currentMediaId = player.currentMediaItem?.mediaId
            val wasPlaying = player.isPlaying

            player.setMediaItems(items)

            if (currentMediaId != null) {
                val index = items.indexOfFirst { it.mediaId == currentMediaId }
                if (index != -1) {
                    player.seekToDefaultPosition(index)
                    // 如果列表变动前在播放，且 autoPlay 允许，或者本来就在播，则继续
                    if (wasPlaying && autoPlay) player.play()
                }
            } else {
                player.prepare()
            }
        }
    }

    private fun applyPlayModeToPlayer(player: Player, mode: PlayMode) {
        when (mode) {
            PlayMode.LoopOne -> {
                player.shuffleModeEnabled = false
                player.repeatMode = Player.REPEAT_MODE_ONE
            }
            PlayMode.Shuffle -> {
                player.shuffleModeEnabled = true
                player.repeatMode = Player.REPEAT_MODE_ALL
            }
        }
    }

    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.update { it.copy(isPlaying = isPlaying) }
                if (!isPlaying) saveCurrentState() // 暂停时保存
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
                    saveCurrentState() // 切歌时保存
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _uiState.update { it.copy(totalTime = formatTime(mediaController?.duration ?: 0L)) }
                }
            }
            override fun onRepeatModeChanged(repeatMode: Int) { syncPlayModeState() }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { syncPlayModeState() }
        })
    }

    private fun syncPlayModeState() {
        mediaController?.let { player ->
            val mode = if (player.repeatMode == Player.REPEAT_MODE_ONE) PlayMode.LoopOne else PlayMode.Shuffle
            _uiState.update { it.copy(playMode = mode) }
        }
    }

    private fun startProgressUpdateLoop() {
        viewModelScope.launch {
            while (isActive) {
                val player = mediaController
                if (player != null) {
                    if (player.isPlaying) {
                        val currentPos = player.currentPosition
                        val duration = player.duration.coerceAtLeast(1L)
                        val progress = currentPos.toFloat() / duration.toFloat()
                        _uiState.update {
                            it.copy(progress = progress, currentTime = formatTime(currentPos), totalTime = formatTime(duration))
                        }
                    }
                    // 同步音量 (防止物理按键修改后 UI 不同步)
                    if (player.volume != _uiState.value.volume) {
                        _uiState.update { it.copy(volume = player.volume) }
                    }
                }
                delay(100L) // 10Hz 刷新率
            }
        }
    }

    private fun extractMetadata(uri: Uri): ExtractedMeta {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: "未知标题"
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "未知艺术家"
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            ExtractedMeta(title, artist, duration)
        } catch (e: Exception) {
            ExtractedMeta("本地音频", "未知", 0L)
        } finally {
            retriever.release()
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
        saveCurrentState()
        super.onCleared()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }

    data class ExtractedMeta(val title: String, val artist: String, val duration: Long)
}
