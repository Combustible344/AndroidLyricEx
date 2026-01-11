package com.example.pra.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Intent
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
                // 连接成功后，先加载上次保存的列表
                loadSavedPlaylist()
                setupPlayerListener()
                syncPlayModeState()
                startProgressUpdateLoop()
            } catch (e: Exception) {
                Log.e("MusicPlayerVM", "Service connection failed", e)
            }
        }, MoreExecutors.directExecutor())
    }

    // --- 1. 加载逻辑升级：优先读文件 ---
    private fun loadSavedPlaylist() {
        viewModelScope.launch {
            // 尝试从本地 JSON 读取
            var songs = repository.loadPlaylistFromFile()

            // 如果是第一次安装（文件为空），则自动扫描一次系统
            if (songs.isEmpty()) {
                songs = repository.scanLocalAudio()
                // 存下来
                repository.savePlaylist(songs)
            }

            updatePlaylistAndPlayer(songs)
        }
    }

    // --- 2. 扫描并追加 ---
    fun scanAndAppend() {
        viewModelScope.launch {
            val newSongs = repository.scanLocalAudio() // 扫描系统 MediaStore
            val current = _uiState.value.playlist.toMutableList()

            // 简单去重 (ID 或 URI)
            var addedCount = 0
            newSongs.forEach { newSong ->
                if (current.none { it.id == newSong.id }) {
                    current.add(0, newSong) // 加到开头
                    addedCount++
                }
            }

            if (addedCount > 0) {
                updatePlaylistAndPlayer(current)
                repository.savePlaylist(current) // 持久化
            }
        }
    }

    // --- 3. 手动添加文件 ---
    fun addFile(uri: Uri) {
        viewModelScope.launch {
            // 解析文件信息 (Title, Artist, Duration)
            val meta = extractMetadata(uri)

            val newAudio = Audio(
                id = System.currentTimeMillis(), // 生成一个唯一ID
                uri = uri,
                title = meta.title,
                artist = meta.artist,
                duration = meta.duration,
                albumId = 0L, // 手动添加的文件通常很难获取 AlbumId 给系统查封面
                albumArtUri = null // 或者尝试从 retriever 获取图片流并存本地（这里暂且置空）
            )

            val current = _uiState.value.playlist.toMutableList()
            // 避免重复添加 (URI 查重)
            if (current.none { it.uri == uri }) {
                current.add(0, newAudio) // 加到开头
                updatePlaylistAndPlayer(current)
                repository.savePlaylist(current) // 持久化
            }
        }
    }

    // --- 4. 绑定歌词 ---
    fun bindLyric(audioId: Long, lyricUri: Uri) {
        viewModelScope.launch {
            val current = _uiState.value.playlist.toMutableList()
            val index = current.indexOfFirst { it.id == audioId }

            if (index != -1) {
                val updatedSong = current[index].copy(lyricUri = lyricUri)
                current[index] = updatedSong

                // 更新 UI
                _uiState.update { it.copy(playlist = current) }
                // 这里不需要重设 Player items，因为播放 URI 没变，只是 UI 层的歌词关联变了
                // 但要更新本地 currentPlaylist 缓存
                currentPlaylist = current
                // 持久化
                repository.savePlaylist(current)
            }
        }
    }

    // 辅助：统一更新 UI 和 ExoPlayer
    private fun updatePlaylistAndPlayer(songs: List<Audio>) {
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
            // 注意：这会重置播放列表。
            // 如果想保留当前播放进度，逻辑会复杂点（对比 diff）。
            // 这里简单处理：直接 setMediaItems。
            val wasPlaying = player.isPlaying
            val currentMediaId = player.currentMediaItem?.mediaId

            player.setMediaItems(items)

            // 尝试恢复当前播放的位置（如果在列表里还存在）
            if (currentMediaId != null) {
                val index = items.indexOfFirst { it.mediaId == currentMediaId }
                if (index != -1) {
                    player.seekToDefaultPosition(index)
                    if (wasPlaying) player.play()
                }
            } else {
                player.prepare()
                // 默认强制设为随机播放
                if (!player.shuffleModeEnabled && player.repeatMode != Player.REPEAT_MODE_ONE) {
                    player.shuffleModeEnabled = true
                    player.repeatMode = Player.REPEAT_MODE_ALL
                }
            }
        }
    }

    // 辅助：解析元数据
    private fun extractMetadata(uri: Uri): ExtractedMeta {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: "未知标题"
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "未知艺术家"
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0L
            ExtractedMeta(title, artist, duration)
        } catch (e: Exception) {
            ExtractedMeta("未知文件", "本地导入", 0L)
        } finally {
            retriever.release()
        }
    }

    data class ExtractedMeta(val title: String, val artist: String, val duration: Long)

    // --- 下面是之前的播放控制逻辑 (保持不变) ---

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

            override fun onRepeatModeChanged(repeatMode: Int) { syncPlayModeState() }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { syncPlayModeState() }
        })
    }

    fun cyclePlayMode() {
        mediaController?.let { player ->
            val currentMode = _uiState.value.playMode
            val newMode = if (currentMode == PlayMode.LoopOne) PlayMode.Shuffle else PlayMode.LoopOne

            when (newMode) {
                PlayMode.LoopOne -> {
                    player.shuffleModeEnabled = false
                    player.repeatMode = Player.REPEAT_MODE_ONE
                }
                PlayMode.Shuffle -> {
                    player.shuffleModeEnabled = true
                    player.repeatMode = Player.REPEAT_MODE_ALL
                }
            }
            _uiState.update { it.copy(playMode = newMode) }
        }
    }

    private fun syncPlayModeState() {
        mediaController?.let { player ->
            val mode = if (player.repeatMode == Player.REPEAT_MODE_ONE) PlayMode.LoopOne else PlayMode.Shuffle
            _uiState.update { it.copy(playMode = mode) }
        }
    }

    fun skipToNext() {
        mediaController?.let { player ->
            if (player.repeatMode == Player.REPEAT_MODE_ONE) {
                player.seekTo(0)
                player.play()
                return@let
            }
            if (player.hasNextMediaItem()) player.seekToNext() else player.seekToDefaultPosition(0)
        }
    }

    fun skipToPrevious() {
        mediaController?.let { player ->
            if (player.repeatMode == Player.REPEAT_MODE_ONE) {
                player.seekTo(0)
                player.play()
                return@let
            }
            player.seekToPrevious()
        }
    }

    fun togglePlayPause() { mediaController?.let { if (it.isPlaying) it.pause() else it.play() } }

    fun playSong(audioId: Long) {
        val index = currentPlaylist.indexOfFirst { it.id == audioId }
        if (index != -1) {
            mediaController?.let { player ->
                player.seekToDefaultPosition(index)
                player.play()
            }
        }
    }

    fun seekTo(progress: Float) {
        mediaController?.let { player ->
            val duration = player.duration
            val position = (duration * progress).toLong()
            player.seekTo(position)
            _uiState.update { it.copy(progress = progress, currentTime = formatTime(position)) }
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
                // --- 修改点：改为 100毫秒 (即每秒刷新10次) ---
                // 此时 UI 会收到更频繁的 updates，配合下面的动画代码，就会非常流畅
                delay(100L)
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
    fun removeSong(audio: Audio) {
        viewModelScope.launch {
            val current = _uiState.value.playlist.toMutableList()
            // 移除该歌曲
            current.removeAll { it.id == audio.id }

            // 更新 UI 和 持久化
            // 注意：如果你删除了正在播放的歌，这里暂时只是从列表移除，
            // 播放器可能会继续播完这首，但下一首就不在列表里了。
            // 这是一个比较安全的简单处理方式。
            _uiState.update { it.copy(playlist = current) }
            currentPlaylist = current
            repository.savePlaylist(current)

            // 可选：如果希望删除后立即同步给播放器内核（防止下一首播到被删的歌）
            // updatePlaylistAndPlayer(current) // 如果调用这个，可能会导致正在播放的音乐中断或重置
        }
    }
}
