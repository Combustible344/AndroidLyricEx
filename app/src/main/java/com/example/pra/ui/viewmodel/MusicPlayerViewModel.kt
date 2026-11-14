package com.example.pra.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.media.MediaMetadataRetriever
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
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
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.example.pra.data.gesture.GestureBinding
import com.example.pra.data.gesture.Gestures
import com.example.pra.data.gesture.PlayerAction
import com.example.pra.data.model.Audio
import com.example.pra.data.prefs.GestureSettingsStore
import com.example.pra.data.prefs.ListeningStatsStore
import com.example.pra.data.prefs.PlayerPrefsStore
import com.example.pra.data.repository.AudioRepository
import com.example.pra.data.repository.PlayerPrefs
import com.example.pra.service.MusicService
import com.example.pra.ui.state.PlayMode
import com.example.pra.ui.state.PlayerUiState
import com.example.pra.utils.LyricParser
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.max

class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AudioRepository(application)
    private val prefsStore = PlayerPrefsStore(application)
    private val gestureStore = GestureSettingsStore(application)
    private val statsStore = ListeningStatsStore(application)
    private val context = application.applicationContext

    private val _uiState = MutableStateFlow(PlayerUiState())

    /**
     * 对外状态：每次发射前都向播放器取一次**实时位置**填充 progress/positionMs。
     *
     * 为什么要这样：伪息屏（IdleScreenSaver）在**任何** uiState 变化时都会用
     * `progress * durationMs` 重新锚定帧内推演。歌词行/字变化、点赞、切模式等
     * 都会发射状态——如果 progress 还是 1 秒前的旧值，AOD 就会被拽回过去，
     * 表现为「逐字歌词不断回跳」。这里保证锚点永远与发射时刻一致。
     */
    val uiState: StateFlow<PlayerUiState> = _uiState
        .map { snapshot -> snapshot.withFreshPosition() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlayerUiState())

    /** 伪息屏手势绑定 (DataStore) */
    val gestureBindings: StateFlow<List<GestureBinding>> = gestureStore.bindings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Gestures.defaults)

    /** 每首歌的累计播放次数 */
    val playCounts: StateFlow<Map<Long, Int>> = statsStore.counts
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** 首页排行显示条数 */
    val rankLimit: StateFlow<Int> = statsStore.rankLimit
        .stateIn(viewModelScope, SharingStarted.Eagerly, ListeningStatsStore.DEFAULT_LIMIT)

    /** 音频可视化开关: 已取消, 播放即采集 */

    private fun PlayerUiState.withFreshPosition(): PlayerUiState {
        val player = mediaController ?: return this
        val dur = player.duration
        if (dur <= 0L || dur == androidx.media3.common.C.TIME_UNSET) return this
        val pos = player.currentPosition.coerceIn(0L, dur)
        return copy(
            progress = pos.toFloat() / dur,
            currentTime = formatTime(pos),
            totalTime = formatTime(dur),
            durationMs = dur,
            positionMs = pos,
            positionAnchorNanos = System.nanoTime(),
        )
    }

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    /** 完整曲库 */
    private var library: List<Audio> = emptyList()

    /** 当前播放队列（可以是曲库的子集，例如筛选后的列表） */
    private var queue: List<Audio> = emptyList()

    /** 媒体库内容变化监听（新歌/删歌自动增量刷新） */
    private var mediaObserver: ContentObserver? = null

    /** 去抖用的刷新任务 */
    private var mediaRefreshJob: Job? = null

    /** 最近一次由 UI 主动切换模式的时刻（毫秒） */
    private var programmaticModeChangeAt = 0L

    /** 初始化恢复完成前不统计播放次数 (避免冷启动把"恢复现场"算成一次播放) */
    private var countingEnabled = false

    /** 最近一次已计入统计的曲目, 防止切歌回调与主动点播重复累加 */
    private var lastCountedId = -1L

    // 可视化组件
    //private var visualizer: Visualizer? = null

    init {
        connectToService()
        startMediaObserver()
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

            // 歌词自动绑定: 给还没有歌词的歌曲匹配 lyrics/ 目录下的同名 .lrc
            val withLyrics = repository.autoBindLyrics(songs)
            if (withLyrics != songs) {
                songs = withLyrics
                repository.savePlaylist(songs)
            }

            val prefs = runCatching { prefsStore.prefs.first() }.getOrElse { PlayerPrefs() }

            Log.d("Restore", "读取到存档: SongId=${prefs.currentSongId}, Pos=${prefs.currentPosition}")

            // 2. 更新内存
            library = songs
            queue = songs
            _uiState.update { it.copy(playlist = songs, queue = songs) }

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
                programmaticModeChangeAt = System.currentTimeMillis()
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
                            positionMs = prefs.currentPosition,
                            positionAnchorNanos = System.nanoTime(),
                            progress = if (song.duration > 0) prefs.currentPosition.toFloat() / song.duration else 0f
                        )
                    }

                    loadLyricsForCurrentSong(prefs.currentSongId)
                    updateUiLikeState(prefs.currentSongId)
                } else {
                    player.prepare()
                    player.pause()
                }
                // 恢复流程结束, 之后的播放/切歌才开始计入统计
                countingEnabled = true
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
                // 以播放器实际状态为准：初始化阶段 pause() 的回调会早于 UI 状态更新，
                // 若读 _uiState 会把默认模式写回存档，导致冷启动模式回退
                playModeName = playModeOf(player).name,
                volume = player.volume
            )

            if (isDying) {
                // onCleared 时 viewModelScope 即将取消, 用 NonCancellable 保证写盘完成,
                // 但不再阻塞主线程 (原实现 runBlocking 有 ANR 风险)
                viewModelScope.launch(NonCancellable + Dispatchers.IO) {
                    prefsStore.save(prefs)
                    Log.d("Restore", "退出保存(后台): $prefs")
                }
            } else {
                viewModelScope.launch(Dispatchers.IO) {
                    prefsStore.save(prefs)
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
            library = currentList
            val newQueue = queue.filterNot { it.id == audio.id }
            queue = newQueue
            repository.savePlaylist(currentList)
            _uiState.update { it.copy(playlist = currentList, queue = newQueue) }

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

            // 3. 更新播放器 (按 mediaId 定位，避免队列与曲库索引不一致)
            mediaController?.let { player ->
                val playerIndex = (0 until player.mediaItemCount).firstOrNull { index ->
                    player.getMediaItemAt(index).mediaId == audio.id.toString()
                }
                if (playerIndex != null) {
                    player.removeMediaItem(playerIndex)
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
                library = current // 同步内部缓存

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
                library = currentList
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

    // --- 歌词文件夹导入 ---

    /**
     * 从用户选中的系统文件夹（如 Download/music）递归导入 .lrc，
     * 然后重新自动绑定并刷新当前歌曲歌词。
     */
    fun importLyricsFolder(treeUri: Uri) {
        viewModelScope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val count = repository.importLyricsFromTree(treeUri)
            val songs = repository.autoBindLyrics(_uiState.value.playlist)
            library = songs
            repository.savePlaylist(songs)
            _uiState.update { it.copy(playlist = songs) }
            loadLyricsForCurrentSong(_uiState.value.currentSongId)
            Toast.makeText(context, "已导入 $count 个歌词文件", Toast.LENGTH_SHORT).show()
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
                library = current
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
        // 队列是曲库的子集（用户从筛选列表起播）时不动播放队列，避免打断当前听歌
        if (newSongs.map { it.id } != library.map { it.id }) return

        queue = newSongs
        _uiState.update { it.copy(queue = newSongs) }

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
        mediaController?.let { player ->
            val newMode = _uiState.value.playMode.next()
            // 先打标记: 设置 repeat/shuffle 会触发 Player.Listener 回调，
            // 不能让回调把中间状态（例如 shuffle 已关、repeat 还没改）写回 UI/存档
            programmaticModeChangeAt = System.currentTimeMillis()
            applyPlayModeToPlayer(player, newMode)
            _uiState.update { s -> s.copy(playMode = newMode) }
            val prefs = PlayerPrefs(
                currentSongId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: -1L,
                currentPosition = player.currentPosition,
                playModeName = newMode.name,
                volume = player.volume,
            )
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { prefsStore.save(prefs) }
                    .onSuccess { Log.d("Prefs", "saved mode=${prefs.playModeName} pos=${prefs.currentPosition}") }
                    .onFailure { Log.e("Prefs", "save failed", it) }
            }
        }
    }

    fun setVolume(v: Float) {
        mediaController?.let {
            it.volume = v; _uiState.update { s -> s.copy(volume = v) }; saveCurrentState()
        }
    }

    // --- 伪息屏手势 ---

    fun saveGestureBindings(bindings: List<GestureBinding>) {
        viewModelScope.launch(Dispatchers.IO) { gestureStore.save(bindings) }
    }

    fun resetGestureBindings() {
        viewModelScope.launch(Dispatchers.IO) { gestureStore.reset() }
    }

    /** 伪息屏手势触发时执行对应动作 */
    fun runGestureAction(action: PlayerAction) {
        when (action) {
            PlayerAction.None -> Unit
            PlayerAction.PlayPause -> togglePlayPause()
            PlayerAction.Previous -> skipToPrevious()
            PlayerAction.Next -> skipToNext()
            PlayerAction.SeekForward -> seekBy(10_000L)
            PlayerAction.SeekBackward -> seekBy(-10_000L)
        }
    }

    private fun seekBy(deltaMs: Long) {
        mediaController?.let { player ->
            val duration = player.duration
            if (duration <= 0L) return@let
            val target = (player.currentPosition + deltaMs).coerceIn(0L, duration)
            player.seekTo(target)
            _uiState.update { state ->
                state.copy(
                    positionMs = target,
                    positionAnchorNanos = System.nanoTime(),
                    progress = target.toFloat() / duration,
                    currentTime = formatTime(target),
                )
            }
            updateCurrentLyricLine(target)
        }
    }

    fun skipToNext() {
        mediaController?.let { player ->
            val next = player.nextMediaItemIndex
            if (next != androidx.media3.common.C.INDEX_UNSET) player.seekToDefaultPosition(next)
        }
    }

    fun skipToPrevious() {
        mediaController?.let { player ->
            val previous = player.previousMediaItemIndex
            if (previous != androidx.media3.common.C.INDEX_UNSET) player.seekToDefaultPosition(previous)
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
            _uiState.update { s ->
                s.copy(
                    progress = p,
                    currentTime = formatTime(ms),
                    positionMs = ms,
                    positionAnchorNanos = System.nanoTime(),
                )
            }
            updateCurrentLyricLine(ms)
        }
    }

    fun playSong(id: Long) {
        playFromList(library, id)
    }

    /**
     * 以指定列表作为播放队列，从 songId 开始播放。
     * 曲库页会传入当前筛选/排序后的列表，这样队列与所见一致，且不影响完整曲库。
     */
    fun playFromList(songs: List<Audio>, songId: Long) {
        val player = mediaController ?: return
        if (songs.isEmpty()) return

        // 点的就是当前这首: 保留播放进度, 只恢复播放 (不允许重置到 0)
        if (player.currentMediaItem?.mediaId == songId.toString()) {
            player.play()
            return
        }

        val index = songs.indexOfFirst { it.id == songId }.coerceAtLeast(0)
        val items = songs.map { audio -> audio.toMediaItem() }
        queue = songs
        _uiState.update { it.copy(queue = songs) }
        player.setMediaItems(items, index, 0L)
        player.prepare()
        player.play()
        countPlay(songId)
    }

    // --- 收听统计 ---

    fun setRankLimit(limit: Int) {
        viewModelScope.launch(Dispatchers.IO) { statsStore.setRankLimit(limit) }
    }

    /** 同一首歌在一次起播/切歌里只计一次, 切歌回调与主动点播不会重复累加 */
    private fun countPlay(songId: Long) {
        if (!countingEnabled || songId <= 0L || songId == lastCountedId) return
        lastCountedId = songId
        viewModelScope.launch(Dispatchers.IO) { statsStore.increment(songId) }
    }

    private fun Audio.toMediaItem(): MediaItem = MediaItem.Builder()
        .setUri(uri)
        .setMediaId(id.toString())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(albumArtUri)
                .build()
        )
        .build()

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
                library = currentList
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
        val audio = library.find { it.id == audioId }
        _uiState.update {
            it.copy(
                isCurrentSongLiked = audio?.isLiked ?: false,
                currentSongLikedCount = audio?.likedCount ?: 0
            )
        }
    }

    private fun loadLyricsForCurrentSong(audioId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val audio = library.find { it.id == audioId }
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
                            durationMs = mediaController?.duration ?: 0L,
                            positionMs = 0L,
                            positionAnchorNanos = System.nanoTime(),
                            progress = 0f,
                        )
                    }
                    saveCurrentState()
                    loadLyricsForCurrentSong(id)
                    updateUiLikeState(id)
                    countPlay(id)
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

            // 模式以播放器为准: 通知栏/耳机按键改动了 shuffle 或 repeat 时,
            // 这里把状态同步回 UI, 不再强推 UI 选定的模式
            override fun onRepeatModeChanged(r: Int) { syncModeFromPlayer() }
            override fun onShuffleModeEnabledChanged(s: Boolean) { syncModeFromPlayer() }
        })
    }

    /** 播放器当前状态对应的播放模式 */
    private fun playModeOf(player: Player): PlayMode = when {
        player.repeatMode == Player.REPEAT_MODE_ONE -> PlayMode.LoopOne
        player.shuffleModeEnabled -> PlayMode.Shuffle
        player.repeatMode == Player.REPEAT_MODE_ALL -> PlayMode.LoopAll
        else -> PlayMode.Sequential
    }

    /** 从播放器的 repeat/shuffle 反推播放模式 */
    private fun syncModeFromPlayer() {
        // 程序化切换模式时的回调风暴：短时间内不回写，避免覆盖用户刚选的模式
        if (System.currentTimeMillis() - programmaticModeChangeAt < 600L) return
        mediaController?.let { player ->
            val mode = playModeOf(player)
            if (mode != _uiState.value.playMode) {
                _uiState.update { it.copy(playMode = mode) }
                saveCurrentState()
            }
        }
    }

    private fun startProgressUpdateLoop() {
        viewModelScope.launch {
            var lastUiUpdate = 0L
            var lastSave = 0L
            while (isActive) {
                mediaController?.let { player ->
                    if (player.isPlaying) {
                        val pos = player.currentPosition
                        // 歌词行/字变化才发状态，不受进度刷新频率影响
                        updateCurrentLyricLine(pos)

                        // 进度锚点 1 秒更新一次；UI 用帧插值补间，避免 10Hz 全量重组
                        val now = System.currentTimeMillis()
                        if (now - lastUiUpdate >= 1_000L) {
                            lastUiUpdate = now
                            val dur = player.duration.coerceAtLeast(1L)
                            _uiState.update { s ->
                                s.copy(
                                    progress = pos.toFloat() / dur,
                                    currentTime = formatTime(pos),
                                    totalTime = formatTime(dur),
                                    durationMs = dur,
                                    positionMs = pos,
                                    positionAnchorNanos = System.nanoTime(),
                                )
                            }
                            // 播放中每 10 秒落一次盘，避免进程被系统杀掉时丢失进度
                            if (now - lastSave >= 10_000L) {
                                lastSave = now
                                saveCurrentState()
                            }
                        }
                    }
                    if (player.volume != _uiState.value.volume) {
                        _uiState.update { s -> s.copy(volume = player.volume) }
                    }
                }
                delay(100L)
            }
        }
    }

    // --- MediaStore 增量刷新 ---

    /** 监听系统媒体库：新歌落盘/删除后自动去抖刷新，不需要用户手动点扫描 */
    private fun startMediaObserver() {
        if (mediaObserver != null) return
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                mediaRefreshJob?.cancel()
                mediaRefreshJob = viewModelScope.launch {
                    delay(1_200L)
                    scanAndAppend()
                }
            }
        }
        runCatching {
            context.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                observer,
            )
        }
        mediaObserver = observer
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
            PlayMode.Sequential -> {
                player.shuffleModeEnabled = false
                player.repeatMode = Player.REPEAT_MODE_OFF
            }

            PlayMode.LoopAll -> {
                player.shuffleModeEnabled = false
                player.repeatMode = Player.REPEAT_MODE_ALL
            }

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

    override fun onCleared() {
        saveCurrentState(isDying = true)
        mediaObserver?.let { runCatching { context.contentResolver.unregisterContentObserver(it) } }
        mediaObserver = null
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
