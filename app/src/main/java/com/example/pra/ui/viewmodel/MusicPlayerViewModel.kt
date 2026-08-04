package com.example.pra.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.audiofx.Visualizer
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.pra.data.model.Audio
import com.example.pra.data.repository.AudioRepository
import com.example.pra.data.repository.PlayerPrefs
import com.example.pra.service.MusicService
import com.example.pra.ui.state.PlayMode
import com.example.pra.ui.state.PlayerUiState
import com.example.pra.utils.LyricParser
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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

    // 可视化组件
    //private var visualizer: Visualizer? = null

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

                val savedMode = try {
                    PlayMode.valueOf(prefs.playModeName)
                } catch (e: Exception) {
                    PlayMode.Shuffle
                }
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
                            durationMs = song.duration,
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
                // onCleared 时 viewModelScope 即将取消, 用 NonCancellable 保证写盘完成,
                // 但不再阻塞主线程 (原实现 runBlocking 有 ANR 风险)
                viewModelScope.launch(NonCancellable + Dispatchers.IO) {
                    repository.savePlayerPrefs(prefs)
                    Log.d("Restore", "退出保存(后台): $prefs")
                }
            } else {
                viewModelScope.launch(Dispatchers.IO) {
                    repository.savePlayerPrefs(prefs)
                }
            }
        }
    }

    // --- 移除歌曲逻辑 ---

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

            // 3. 更新播放器
            mediaController?.let { player ->
                if (indexToRemove < player.mediaItemCount) {
                    player.removeMediaItem(indexToRemove)
                }
            }
        }
    }

    // --- 绑定歌词 ---

    fun bindLyric(audioId: Long, lyricUri: Uri) {
        viewModelScope.launch {
            val current = _uiState.value.playlist.toMutableList()
            val idx = current.indexOfFirst { it.id == audioId }

            if (idx != -1) {
                // 1. 更新数据对象
                current[idx] = current[idx].copy(lyricUri = lyricUri)
                currentPlaylist = current // 同步内部缓存

                // 2. 更新 UI 和本地存储
                _uiState.update { it.copy(playlist = current) }
                repository.savePlaylist(current)

                // 3. 若为当前歌曲，立即重载
                if (audioId == _uiState.value.currentSongId) {
                    Log.d("Lyric", "立即重新加载歌词: $lyricUri")
                    loadLyricsForCurrentSong(audioId)
                }
            }
        }
    }

    // --- 扫描与添加 (防止重复) ---

    fun scanAndAppend() {
        viewModelScope.launch(Dispatchers.IO) {
            val scannedSongs = repository.scanLocalAudio()
            val currentList = _uiState.value.playlist.toMutableList()
            val currentIdMap = currentList.map { it.id }.toHashSet()

            var hasChanges = false

            scannedSongs.forEach { newAudio ->
                // 1. 优先检查 ID
                if (currentIdMap.contains(newAudio.id)) return@forEach

                // 2. 其次检查元数据重复 (防止重复添加手动导入的歌)
                if (currentList.isDuplicate(newAudio)) return@forEach

                currentList.add(0, newAudio)
                hasChanges = true
            }

            if (hasChanges) {
                currentPlaylist = currentList
                repository.savePlaylist(currentList)

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(playlist = currentList) }

                    // 全量刷新播放器
                    mediaController?.let { player ->
                        syncPlayerPlaylist(player, currentList)
                    }
                }
            }
        }
    }

    // --- 添加文件 (防止重复 & 队尾添加) ---

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
                albumArtUri = meta.embeddedArtUri // 使用提取到的封面
            )

            withContext(Dispatchers.Main) {
                val current = _uiState.value.playlist.toMutableList()

                // 1. 检查重复
                if (current.isDuplicate(newAudio)) {
                    Toast.makeText(context, "歌曲已存在", Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                // 2. 添加到队尾
                current.add(newAudio)

                // 3. 更新缓存
                currentPlaylist = current
                repository.savePlaylist(current)
                _uiState.update { it.copy(playlist = current) }

                // 4. 同步给播放器 (不打断当前播放)
                mediaController?.let { player ->
                    syncPlayerPlaylist(player, current)
                    Toast.makeText(context, "已添加到列表末尾", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // --- 播放器同步辅助 ---
    // 封装了全量替换并恢复播放状态的逻辑
    private fun syncPlayerPlaylist(player: Player, newSongs: List<Audio>) {
        val currentMediaId = player.currentMediaItem?.mediaId
        val currentPos = player.currentPosition
        val isPlaying = player.isPlaying

        val newItems = newSongs.map { audio ->
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

                // 顺便更新一下 UI 的封面，以防元数据变了
                val currentItem = newItems[targetIndex]
                _uiState.update {
                    it.copy(
                        coverUri = currentItem.mediaMetadata.artworkUri,
                        songTitle = currentItem.mediaMetadata.title.toString(),
                        artistName = currentItem.mediaMetadata.artist.toString()
                    )
                }
            }
        } else {
            // 如果之前列表为空，准备播放第一首
            if (newItems.isNotEmpty()) {
                player.prepare()
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
        mediaController?.let {
            it.volume = v; _uiState.update { s -> s.copy(volume = v) }; saveCurrentState()
        }
    }

    fun skipToNext() {
        mediaController?.let {
            if (it.repeatMode == Player.REPEAT_MODE_ONE) {
                it.seekTo(0); it.play()
            } else if (it.hasNextMediaItem()) it.seekToNext() else it.seekToDefaultPosition(0)
        }
    }

    fun skipToPrevious() {
        mediaController?.let {
            if (it.repeatMode == Player.REPEAT_MODE_ONE) {
                it.seekTo(0); it.play()
            } else it.seekToPrevious()
        }
    }

    fun togglePlayPause() {
        mediaController?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun seekTo(p: Float) {
        mediaController?.let {
            val dur = it.duration
            // C.TIME_UNSET 或 0 时没有有效时长, 放弃 seek (避免溢出)
            if (dur <= 0 || dur == androidx.media3.common.C.TIME_UNSET) return@let
            val ms = (dur * p).toLong()
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
                _uiState.update {
                    it.copy(
                        playlist = currentList,
                        isCurrentSongLiked = newIsLiked,
                        currentSongLikedCount = newCount
                    )
                }
                repository.savePlaylist(currentList)
            }
        }
    }

    private fun updateUiLikeState(audioId: Long) {
        val audio = currentPlaylist.find { it.id == audioId }
        _uiState.update {
            it.copy(
                isCurrentSongLiked = audio?.isLiked ?: false,
                currentSongLikedCount = audio?.likedCount ?: 0
            )
        }
    }

    private fun loadLyricsForCurrentSong(audioId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val audio = currentPlaylist.find { it.id == audioId }
            if (audio?.lyricUri == null) {
                _uiState.update {
                    it.copy(
                        lyrics = emptyList(),
                        currentLyricIndex = -1,
                        currentLyricLine = null,
                        currentWordIndex = -1
                    )
                }
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
            _uiState.update {
                it.copy(
                    currentLyricIndex = lineIndex,
                    currentLyricLine = activeLine,
                    currentWordIndex = wordIndex
                )
            }
        }
    }

    // --- Player 监听器 ---

    @OptIn(UnstableApi::class)
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
                        it.copy(
                            songTitle = meta.title.toString(),
                            artistName = meta.artist.toString(),
                            coverUri = meta.artworkUri,
                            currentSongId = id,
                            totalTime = formatTime(mediaController?.duration ?: 0L),
                            durationMs = mediaController?.duration ?: 0L
                        )
                    }
                    saveCurrentState()
                    loadLyricsForCurrentSong(id)
                    updateUiLikeState(id)
                }
            }

            override fun onPlaybackStateChanged(s: Int) {
                if (s == Player.STATE_READY) _uiState.update {
                    it.copy(
                        totalTime = formatTime(mediaController?.duration ?: 0L),
                        durationMs = mediaController?.duration ?: 0L
                    )
                }
            }

            override fun onRepeatModeChanged(r: Int) { syncMode() }
            override fun onShuffleModeEnabledChanged(s: Boolean) { syncMode() }
        })
    }

    private fun syncMode() {
        mediaController?.let {
            val mode =
                if (it.repeatMode == Player.REPEAT_MODE_ONE) PlayMode.LoopOne else PlayMode.Shuffle
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
                        _uiState.update { s ->
                            s.copy(
                                progress = pos.toFloat() / dur,
                                currentTime = formatTime(pos),
                                totalTime = formatTime(dur),
                                durationMs = dur
                            )
                        }
                        updateCurrentLyricLine(pos)
                    }
                    if (it.volume != _uiState.value.volume) _uiState.update { s -> s.copy(volume = it.volume) }
                }
                delay(100L)
            }
        }
    }

    // --- 元数据提取 (包含封面) ---

    private fun extractMetadata(uri: Uri): ExtractedMeta {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)

            val title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: "未知标题"
            val artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "未知艺术家"
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L

            // 提取封面
            var artUri: Uri? = null
            val embeddedPicture = r.embeddedPicture
            if (embeddedPicture != null) {
                try {
                    val cacheDir = File(context.cacheDir, "custom_covers")
                    if (!cacheDir.exists()) cacheDir.mkdirs()
                    val coverFile = File(cacheDir, "cover_${System.currentTimeMillis()}.jpg")
                    coverFile.writeBytes(embeddedPicture)
                    artUri = android.net.Uri.fromFile(coverFile)
                } catch (e: Exception) {
                    Log.e("Metadata", "Save cover failed", e)
                }
            }

            ExtractedMeta(title, artist, duration, artUri)
        } catch (e: Exception) {
            val fileName = getFileNameFromUri(uri) ?: "本地音频"
            ExtractedMeta(fileName, "未知", 0L, null)
        } finally {
            r.release()
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) result = result?.substring(cut + 1)
        }
        return result?.substringBeforeLast(".")
    }

    private fun formatTime(ms: Long): String {
        val s = ms / 1000
        return String.format(Locale.getDefault(), "%d:%02d", s / 60, s % 60)
    }

    private fun applyPlayModeToPlayer(player: Player, mode: PlayMode) {
        when (mode) {
            PlayMode.LoopOne -> {
                player.shuffleModeEnabled = false; player.repeatMode = Player.REPEAT_MODE_ONE
            }
            PlayMode.Shuffle -> {
                player.shuffleModeEnabled = true; player.repeatMode = Player.REPEAT_MODE_ALL
            }
        }
    }

    override fun onCleared() {
        saveCurrentState(isDying = true)
        //releaseVisualizer() // 确保释放
        super.onCleared()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }

    data class ExtractedMeta(
        val title: String,
        val artist: String,
        val duration: Long,
        val embeddedArtUri: Uri? = null
    )

    // 判重逻辑
    private fun List<Audio>.isDuplicate(newSong: Audio): Boolean {
        return this.any { existing ->
            if (existing.uri == newSong.uri) return@any true
            val isSameTitle = existing.title == newSong.title
            val isSameArtist = existing.artist == newSong.artist
            val isSameDuration = kotlin.math.abs(existing.duration - newSong.duration) < 1000
            isSameTitle && isSameArtist && isSameDuration
        }
    }
}
