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
                // 设置列表
                player.setMediaItems(items)

                // 恢复模式和音量
                val savedMode = try { PlayMode.valueOf(prefs.playModeName) } catch (e: Exception) { PlayMode.Shuffle }
                applyPlayModeToPlayer(player, savedMode)
                player.volume = prefs.volume

                // 恢复进度
                val index = songs.indexOfFirst { it.id == prefs.currentSongId }
                if (index != -1) {
                    // 关键顺序：先 prepare 让播放器就绪，再 seek
                    player.prepare()
                    player.seekTo(index, prefs.currentPosition)

                    // 强制暂停 (防止 prepare 后自动播放)
                    player.pause()

                    // ★★★ 核心修复：立即强制刷新 UI 状态 ★★★
                    // 不要等播放器回调，直接用存档数据渲染 UI，解决“进度条不显示”的问题
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
                            // 强制设置进度和时间
                            currentTime = formatTime(prefs.currentPosition),
                            totalTime = formatTime(song.duration),
                            progress = if (song.duration > 0) prefs.currentPosition.toFloat() / song.duration else 0f
                        )
                    }

                    // 加载歌词和点赞
                    loadLyricsForCurrentSong(prefs.currentSongId)
                    updateUiLikeState(prefs.currentSongId)
                } else {
                    // 存档的歌找不到了，默认重置
                    player.prepare()
                    player.pause()
                }
            }
        }
    }

    // --- 保存逻辑 ---

    @OptIn(DelicateCoroutinesApi::class)
    private fun saveCurrentState(isDying: Boolean = false) {
        mediaController?.let { player ->
            val currentId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: -1L
            // 只有当有有效歌曲时才保存
            if (currentId != -1L) {
                val pos = player.currentPosition
                val mode = _uiState.value.playMode.name
                val vol = player.volume
                val prefs = PlayerPrefs(currentId, pos, mode, vol)

                if (isDying) {
                    // APP 关闭时，使用 GlobalScope 确保写入
                    GlobalScope.launch(Dispatchers.IO) {
                        repository.savePlayerPrefs(prefs)
                        Log.d("Restore", "退出保存: $prefs")
                    }
                } else {
                    viewModelScope.launch {
                        repository.savePlayerPrefs(prefs)
                    }
                }
            }
        }
    }

    // --- 移除歌曲逻辑 (修复版) ---

    fun removeSong(audio: Audio) {
        viewModelScope.launch {
            val currentList = _uiState.value.playlist.toMutableList()
            // 1. 找到这首歌在列表中的索引
            val indexToRemove = currentList.indexOfFirst { it.id == audio.id }

            if (indexToRemove == -1) return@launch

            val wasPlayingThis = (audio.id == _uiState.value.currentSongId)

            // 2. 先更新 UI 层和数据层 (保持界面响应)
            currentList.removeAt(indexToRemove)
            currentPlaylist = currentList
            repository.savePlaylist(currentList)
            _uiState.update { it.copy(playlist = currentList) }

            // 3. 更新播放器 (使用差量更新，避免卡顿)
            mediaController?.let { player ->
                if (wasPlayingThis) {
                    // 情况A: 删除了正在播放的歌
                    // 逻辑：切到下一首，然后把刚才那首删掉
                    if (player.hasNextMediaItem()) {
                        player.seekToNextMediaItem()
                        player.removeMediaItem(indexToRemove)
                    } else if (currentList.isNotEmpty()) {
                        // 如果没有下一首了（是最后一首），但列表里还有别的歌，就回到第一首
                        player.seekToDefaultPosition(0)
                        player.removeMediaItem(indexToRemove)
                    } else {
                        // 列表空了
                        player.pause()
                        player.clearMediaItems()
                        // 重置 UI 状态
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
                    }
                } else {
                    // 情况B: 删除了其他的歌 (★★ 修复卡顿的关键 ★★)
                    // 直接移除指定索引的 Item，播放器内部会自动处理，不会影响当前播放
                    // 注意：这里的 indexToRemove 必须是删除动作发生前的索引
                    player.removeMediaItem(indexToRemove)
                }
            }
        }
    }

    // 简单的列表更新辅助方法
    private fun updatePlaylistSimple(songs: List<Audio>) {
        val items = songs.map { audio ->
            MediaItem.Builder()
                .setUri(audio.uri)
                .setMediaId(audio.id.toString())
                .setMediaMetadata(MediaMetadata.Builder().setTitle(audio.title).setArtist(audio.artist).build())
                .build()
        }
        mediaController?.let { player ->
            // 记录当前播放位置，尝试在重置列表后恢复 (如果是静默更新)
            val currentMediaId = player.currentMediaItem?.mediaId
            val currentPos = player.currentPosition
            val isPlaying = player.isPlaying

            player.setMediaItems(items)

            if (currentMediaId != null) {
                // 尝试找回刚才那首歌
                val index = items.indexOfFirst { it.mediaId == currentMediaId }
                if (index != -1) {
                    player.seekTo(index, currentPos)
                    if (isPlaying) player.play()
                }
            }
        }
    }

    // --- 其他功能保持不变 ---

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

    // 歌词逻辑
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

    // 文件操作
    fun scanAndAppend() {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. 重新扫描所有歌曲 (此时带有最新的正确封面URI)
            val scannedSongs = repository.scanLocalAudio()

            // 2. 获取当前列表的副本
            val currentList = _uiState.value.playlist.toMutableList()

            // 3. 构建一个 Map 用于快速查找现有歌曲的索引
            val currentMap = currentList.mapIndexed { index, audio -> audio.id to index }.toMap()

            var hasChanges = false

            scannedSongs.forEach { newAudio ->
                val existingIndex = currentMap[newAudio.id]

                if (existingIndex != null) {
                    // --- 情况 A: 歌曲已存在 ---
                    // 检查是否需要更新 (比如旧的数据没有封面，而新扫描的有)
                    val existingAudio = currentList[existingIndex]
                    if (existingAudio.albumArtUri == null && newAudio.albumArtUri != null) {
                        // 保留旧的点赞状态和歌词，只更新元数据
                        currentList[existingIndex] = newAudio.copy(
                            isLiked = existingAudio.isLiked,
                            likedCount = existingAudio.likedCount,
                            lyricUri = existingAudio.lyricUri
                        )
                        hasChanges = true
                    }
                } else {
                    // --- 情况 B: 纯粹的新歌 ---
                    // 加到头部
                    currentList.add(0, newAudio)
                    hasChanges = true
                }
            }

            // 4. 如果有任何变化，应用更新
            if (hasChanges) {
                // 更新内部引用
                currentPlaylist = currentList
                // 持久化
                repository.savePlaylist(currentList)

                // 切换到主线程更新 UI
                withContext(Dispatchers.Main) {
                    // 更新播放器队列 (静默更新)
                    updatePlaylistSimple(currentList)
                    // 强制刷新 UI
                    _uiState.update { it.copy(playlist = currentList) }
                }
            }
        }
    }


    fun addFile(uri: Uri) {
        viewModelScope.launch {
            val meta = extractMetadata(uri)
            val newAudio = Audio(
                id = System.currentTimeMillis(), // 简单的临时ID生成
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

                // 1. 更新内部数据
                currentPlaylist = current
                // 2. 更新播放器
                updatePlaylistSimple(current)
                // 3. 持久化
                repository.savePlaylist(current)

                // ★★★【修复点】手动触发 UI 状态更新 ★★★
                _uiState.update { it.copy(playlist = current) }

                // 可选：添加文件后直接播放这首歌
                playSong(newAudio.id)
            }
        }
    }

    fun bindLyric(audioId: Long, lyricUri: Uri) {
        viewModelScope.launch {
            val current = _uiState.value.playlist.toMutableList()
            val idx = current.indexOfFirst { it.id == audioId }
            if (idx != -1) {
                current[idx] = current[idx].copy(lyricUri = lyricUri)
                updatePlaylistSimple(current)
                repository.savePlaylist(current)
                if (audioId == _uiState.value.currentSongId) loadLyricsForCurrentSong(audioId)
            }
        }
    }

    // 控制逻辑
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

    // 内部方法
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
