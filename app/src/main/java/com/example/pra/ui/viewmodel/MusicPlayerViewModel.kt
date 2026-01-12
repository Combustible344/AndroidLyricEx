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
import com.example.pra.data.model.LyricLine
import com.example.pra.data.repository.AudioRepository
import com.example.pra.data.repository.PlayerPrefs
import com.example.pra.service.MusicService
import com.example.pra.ui.state.PlayMode
import com.example.pra.ui.state.PlayerUiState
import com.example.pra.utils.LyricParser
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max

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
                // 启动初始化流程
                initializePlayer()
                setupPlayerListener()
                startProgressUpdateLoop()
            } catch (e: Exception) {
                Log.e("MusicPlayerVM", "Service connection failed", e)
            }
        }, MoreExecutors.directExecutor())
    }

    // --- 初始化与恢复 ---

    private fun initializePlayer() {
        viewModelScope.launch {
            // 1. 读取数据
            var songs = repository.loadPlaylistFromFile()
            if (songs.isEmpty()) {
                songs = repository.scanLocalAudio()
                repository.savePlaylist(songs)
            }
            val prefs = repository.loadPlayerPrefs()

            Log.d("Restore", "读取到存档: SongId=${prefs.currentSongId}, Pos=${prefs.currentPosition}")

            // 2. 更新内存
            currentPlaylist = songs
            _uiState.update { it.copy(playlist = songs) }

            // 3. 构建 MediaItems
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

            // 4. 设置播放器
            mediaController?.let { player ->
                player.setMediaItems(items)

                val savedMode = try { PlayMode.valueOf(prefs.playModeName) } catch (e: Exception) { PlayMode.Shuffle }
                applyPlayModeToPlayer(player, savedMode)
                player.volume = prefs.volume

                val index = songs.indexOfFirst { it.id == prefs.currentSongId }
                if (index != -1) {
                    player.prepare()
                    player.seekTo(index, prefs.currentPosition)
                    player.pause()

                    val song = songs[index]
                    _uiState.update {
                        it.copy(
                            playMode = savedMode,
                            volume = prefs.volume,
                            songTitle = song.title,
                            artistName = song.artist,
                            coverUri = song.albumArtUri,
                            currentSongId = song.id,
                            isPlaying = false,
                            currentTime = formatTime(prefs.currentPosition),
                            totalTime = formatTime(song.duration),
                            progress = if (song.duration > 0) prefs.currentPosition.toFloat() / song.duration else 0f
                        )
                    }

                    loadLyricsForCurrentSong(prefs.currentSongId)
                    updateUiLikeState(prefs.currentSongId)
                } else {
                    player.prepare()
                    player.pause()
                }
            }
        }
    }

    // --- 保存逻辑 (安全版) ---

    private fun saveCurrentState(isDying: Boolean = false) {
        val player = mediaController ?: return
        val currentId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: -1L

        if (currentId != -1L) {
            val prefs = PlayerPrefs(
                currentSongId = currentId,
                currentPosition = player.currentPosition,
                playModeName = _uiState.value.playMode.name,
                volume = player.volume
            )

            if (isDying) {
                // APP 死亡时，使用 runBlocking 确保数据写入
                try {
                    kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                        repository.savePlayerPrefs(prefs)
                        Log.d("Restore", "退出保存(Blocking): $prefs")
                    }
                } catch (e: Exception) {
                    Log.e("Restore", "退出保存失败", e)
                }
            } else {
                viewModelScope.launch(Dispatchers.IO) {
                    repository.savePlayerPrefs(prefs)
                }
            }
        }
    }


    // --- 移除歌曲逻辑 (安全版) ---

    fun removeSong(audio: Audio) {
        viewModelScope.launch {
            val currentList = _uiState.value.playlist.toMutableList()
            val indexToRemove = currentList.indexOfFirst { it.id == audio.id }

            if (indexToRemove == -1) return@launch

            // 1. 乐观更新
            currentList.removeAt(indexToRemove)
            currentPlaylist = currentList
            repository.savePlaylist(currentList)
            _uiState.update { it.copy(playlist = currentList) }

            // 2. 处理空列表
            if (currentList.isEmpty()) {
                mediaController?.pause()
                mediaController?.clearMediaItems()
                _uiState.update {
                    it.copy(
                        isPlaying = false,
                        progress = 0f,
                        currentTime = "0:00",
                        songTitle = "请选择歌曲",
                        artistName = "",
                        coverUri = null,
                        currentSongId = -1L
                    )
                }
                return@launch
            }

            // 3. 更新播放器 (利用 Media3 自动跳过特性)
            mediaController?.let { player ->
                if (indexToRemove < player.mediaItemCount) {
                    player.removeMediaItem(indexToRemove)
                }
            }
        }
    }


    // --- 绑定歌词 (修复版：同步更新内存缓存) ---
    fun bindLyric(audioId: Long, lyricUri: Uri) {
        viewModelScope.launch {
            val current = _uiState.value.playlist.toMutableList()
            val idx = current.indexOfFirst { it.id == audioId }

            if (idx != -1) {
                // 1. 更新数据对象
                current[idx] = current[idx].copy(lyricUri = lyricUri)

                // ★★★ 核心修复：必须同步更新 ViewModel 的内部缓存 ★★★
                // 否则 loadLyricsForCurrentSong 读到的还是旧对象
                currentPlaylist = current

                // 2. 更新 UI 状态 (触发按钮高亮)
                _uiState.update { it.copy(playlist = current) }

                // 3. 保存到本地 (持久化)
                repository.savePlaylist(current)

                // 4. (优化) 不需要调用 updatePlaylistSimple(current)
                // 因为 ExoPlayer/MediaItem 并不持有歌词信息，重置列表只会增加卡顿风险

                // 5. 若为当前歌曲，立即重新加载并解析
                if (audioId == _uiState.value.currentSongId) {
                    Log.d("Lyric", "立即重新加载歌词: $lyricUri")
                    loadLyricsForCurrentSong(audioId)
                }
            }
        }
    }


    // --- 扫描与添加 (深度刷新版) ---

    fun scanAndAppend() {
        viewModelScope.launch(Dispatchers.IO) {
            val scannedSongs = repository.scanLocalAudio()
            val currentList = _uiState.value.playlist.toMutableList()
            val currentMap = currentList.mapIndexed { index, audio -> audio.id to index }.toMap()

            var hasChanges = false
            scannedSongs.forEach { newAudio ->
                val existingIndex = currentMap[newAudio.id]
                if (existingIndex != null) {
                    val existingAudio = currentList[existingIndex]
                    if (existingAudio.albumArtUri == null && newAudio.albumArtUri != null) {
                        currentList[existingIndex] = newAudio.copy(
                            isLiked = existingAudio.isLiked,
                            likedCount = existingAudio.likedCount,
                            lyricUri = existingAudio.lyricUri
                        )
                        hasChanges = true
                    }
                } else {
                    currentList.add(0, newAudio)
                    hasChanges = true
                }
            }

            if (hasChanges) {
                currentPlaylist = currentList
                repository.savePlaylist(currentList)

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(playlist = currentList) }

                    // 深度刷新播放器元数据
                    mediaController?.let { player ->
                        val currentMediaId = player.currentMediaItem?.mediaId
                        val currentPos = player.currentPosition
                        val isPlaying = player.isPlaying

                        val newItems = currentList.map { audio ->
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
                        player.setMediaItems(newItems)

                        if (currentMediaId != null) {
                            val targetIndex = newItems.indexOfFirst { it.mediaId == currentMediaId }
                            if (targetIndex != -1) {
                                player.seekTo(targetIndex, currentPos)
                                if (isPlaying) player.play()

                                // 强制同步当前播放的封面到 UI
                                val currentItem = newItems[targetIndex]
                                _uiState.update {
                                    it.copy(
                                        coverUri = currentItem.mediaMetadata.artworkUri,
                                        songTitle = currentItem.mediaMetadata.title.toString(),
                                        artistName = currentItem.mediaMetadata.artist.toString()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- 添加文件 (防止重复) ---

    fun addFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
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

            withContext(Dispatchers.Main) {
                val current = _uiState.value.playlist.toMutableList()
                if (current.none { it.uri == uri }) {
                    current.add(0, newAudio)
                    currentPlaylist = current
                    repository.savePlaylist(current)
                    _uiState.update { it.copy(playlist = current) }

                    mediaController?.let { player ->
                        val item = MediaItem.Builder()
                            .setUri(newAudio.uri)
                            .setMediaId(newAudio.id.toString())
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(newAudio.title)
                                    .setArtist(newAudio.artist)
                                    .build()
                            )
                            .build()
                        player.addMediaItem(0, item)
                        player.seekToDefaultPosition(0)
                        player.play()
                    }
                }
            }
        }
    }

    // --- 简单列表更新 (内部使用) ---
    private fun updatePlaylistSimple(songs: List<Audio>) {
        val items = songs.map { audio ->
            MediaItem.Builder()
                .setUri(audio.uri)
                .setMediaId(audio.id.toString())
                .setMediaMetadata(MediaMetadata.Builder().setTitle(audio.title).setArtist(audio.artist).build())
                .build()
        }
        mediaController?.let { player ->
            val currentMediaId = player.currentMediaItem?.mediaId
            val currentPos = player.currentPosition
            val isPlaying = player.isPlaying

            player.setMediaItems(items)

            if (currentMediaId != null) {
                val index = items.indexOfFirst { it.mediaId == currentMediaId }
                if (index != -1) {
                    player.seekTo(index, currentPos)
                    if (isPlaying) player.play()
                }
            }
        }
    }

    // --- 控制逻辑 ---

    fun cyclePlayMode() {
        mediaController?.let {
            val newMode = if (_uiState.value.playMode == PlayMode.LoopOne) PlayMode.Shuffle else PlayMode.LoopOne
            applyPlayModeToPlayer(it, newMode)
            _uiState.update { s -> s.copy(playMode = newMode) }
            saveCurrentState()
        }
    }

    fun setVolume(v: Float) {
        mediaController?.let { it.volume = v; _uiState.update { s -> s.copy(volume = v) }; saveCurrentState() }
    }

    fun skipToNext() {
        mediaController?.let {
            if (it.repeatMode == Player.REPEAT_MODE_ONE) { it.seekTo(0); it.play() }
            else if (it.hasNextMediaItem()) it.seekToNext() else it.seekToDefaultPosition(0)
        }
    }

    fun skipToPrevious() {
        mediaController?.let {
            if (it.repeatMode == Player.REPEAT_MODE_ONE) { it.seekTo(0); it.play() }
            else it.seekToPrevious()
        }
    }

    fun togglePlayPause() { mediaController?.let { if (it.isPlaying) it.pause() else it.play() } }

    fun seekTo(p: Float) {
        mediaController?.let {
            val ms = (it.duration * p).toLong()
            it.seekTo(ms)
            _uiState.update { s -> s.copy(progress = p, currentTime = formatTime(ms)) }
            updateCurrentLyricLine(ms)
        }
    }

    fun playSong(id: Long) {
        val idx = currentPlaylist.indexOfFirst { it.id == id }
        if (idx != -1) mediaController?.let { it.seekToDefaultPosition(idx); it.play() }
    }

    // --- 点赞与歌词解析 ---

    fun toggleLike() {
        val currentId = _uiState.value.currentSongId
        if (currentId == -1L) return
        viewModelScope.launch {
            val currentList = _uiState.value.playlist.toMutableList()
            val index = currentList.indexOfFirst { it.id == currentId }
            if (index != -1) {
                val old = currentList[index]
                val newIsLiked = !old.isLiked
                val newCount = if (newIsLiked) old.likedCount + 1 else max(0, old.likedCount - 1)
                currentList[index] = old.copy(isLiked = newIsLiked, likedCount = newCount)
                currentPlaylist = currentList
                _uiState.update { it.copy(playlist = currentList, isCurrentSongLiked = newIsLiked, currentSongLikedCount = newCount) }
                repository.savePlaylist(currentList)
            }
        }
    }

    private fun updateUiLikeState(audioId: Long) {
        val audio = currentPlaylist.find { it.id == audioId }
        _uiState.update { it.copy(isCurrentSongLiked = audio?.isLiked ?: false, currentSongLikedCount = audio?.likedCount ?: 0) }
    }

    private fun loadLyricsForCurrentSong(audioId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val audio = currentPlaylist.find { it.id == audioId }
            if (audio?.lyricUri == null) {
                _uiState.update { it.copy(lyrics = emptyList(), currentLyricIndex = -1, currentLyricLine = null, currentWordIndex = -1) }
                return@launch
            }
            try {
                val entry = LyricParser.parse(context.contentResolver.openInputStream(audio.lyricUri))
                _uiState.update { it.copy(lyrics = entry.lines) }
            } catch (e: Exception) {
                _uiState.update { it.copy(lyrics = emptyList()) }
            }
        }
    }

    private fun updateCurrentLyricLine(position: Long) {
        val lyrics = _uiState.value.lyrics
        if (lyrics.isEmpty()) return
        val lineIndex = lyrics.indexOfLast { it.startTime <= position }
        val activeLine = if (lineIndex != -1) lyrics[lineIndex] else null
        var wordIndex = -1
        if (activeLine != null && activeLine.words.isNotEmpty()) {
            wordIndex = activeLine.words.indexOfLast { it.startTime <= position }
        }
        if (_uiState.value.currentLyricIndex != lineIndex || _uiState.value.currentWordIndex != wordIndex) {
            _uiState.update { it.copy(currentLyricIndex = lineIndex, currentLyricLine = activeLine, currentWordIndex = wordIndex) }
        }
    }

    // --- 内部辅助 ---

    private fun applyPlayModeToPlayer(player: Player, mode: PlayMode) {
        when (mode) {
            PlayMode.LoopOne -> { player.shuffleModeEnabled = false; player.repeatMode = Player.REPEAT_MODE_ONE }
            PlayMode.Shuffle -> { player.shuffleModeEnabled = true; player.repeatMode = Player.REPEAT_MODE_ALL }
        }
    }

    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.update { it.copy(isPlaying = isPlaying) }
                if (!isPlaying) saveCurrentState()
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.let { item ->
                    val id = item.mediaId.toLongOrNull() ?: -1L
                    val meta = item.mediaMetadata
                    _uiState.update {
                        it.copy(songTitle = meta.title.toString(), artistName = meta.artist.toString(), coverUri = meta.artworkUri, currentSongId = id, totalTime = formatTime(mediaController?.duration ?: 0L))
                    }
                    saveCurrentState()
                    loadLyricsForCurrentSong(id)
                    updateUiLikeState(id)
                }
            }
            override fun onPlaybackStateChanged(s: Int) {
                if (s == Player.STATE_READY) _uiState.update { it.copy(totalTime = formatTime(mediaController?.duration ?: 0L)) }
            }
            override fun onRepeatModeChanged(r: Int) { syncMode() }
            override fun onShuffleModeEnabledChanged(s: Boolean) { syncMode() }
        })
    }

    private fun syncMode() {
        mediaController?.let {
            val mode = if (it.repeatMode == Player.REPEAT_MODE_ONE) PlayMode.LoopOne else PlayMode.Shuffle
            _uiState.update { s -> s.copy(playMode = mode) }
        }
    }

    private fun startProgressUpdateLoop() {
        viewModelScope.launch {
            while (isActive) {
                mediaController?.let {
                    if (it.isPlaying) {
                        val pos = it.currentPosition
                        val dur = it.duration.coerceAtLeast(1L)
                        _uiState.update { s -> s.copy(progress = pos.toFloat()/dur, currentTime = formatTime(pos), totalTime = formatTime(dur)) }
                        updateCurrentLyricLine(pos)
                    }
                    if (it.volume != _uiState.value.volume) _uiState.update { s -> s.copy(volume = it.volume) }
                }
                delay(100L)
            }
        }
    }

    private fun extractMetadata(uri: Uri): ExtractedMeta {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            ExtractedMeta(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: "未知", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "未知", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L)
        } catch (e: Exception) { ExtractedMeta("本地", "未知", 0L) } finally { r.release() }
    }

    private fun formatTime(ms: Long): String {
        val s = ms / 1000
        return String.format(Locale.getDefault(), "%d:%02d", s / 60, s % 60)
    }

    override fun onCleared() {
        saveCurrentState(isDying = true)
        super.onCleared()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }

    data class ExtractedMeta(val title: String, val artist: String, val duration: Long)
}
