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
import com.example.pra.data.repository.AudioRepository
import com.example.pra.service.MusicService
import com.example.pra.ui.state.PlayerUiState
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {

    // 数据源
    private val repository = AudioRepository(application)

    // UI 状态
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState = _uiState.asStateFlow()

    // 媒体控制器 (连接 Service 的遥控器)
    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

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
                // 1. 连接成功，拿到控制器
                mediaController = controllerFuture?.get()

                // 2. 加载歌曲并传给 Service
                loadMusic()

                // 3. 开始监听播放状态
                setupPlayerListener()

            } catch (e: Exception) {
                Log.e("ViewModel", "Service connection failed", e)
            }
        }, MoreExecutors.directExecutor())
    }

    private fun loadMusic() {
        viewModelScope.launch {
            // 从 Repository 拿歌
            val songs = repository.getLocalAudio()
            if (songs.isEmpty()) return@launch

            // 转换成 Media3 的 MediaItem
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

            // 塞给播放器
            mediaController?.let { player ->
                if (player.mediaItemCount == 0) {
                    player.setMediaItems(items)
                    player.prepare() // 准备播放
                }
            }
        }
    }

    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            // 监听：播放/暂停变化
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.update { it.copy(isPlaying = isPlaying) }
            }

            // 监听：切歌 (更新歌名、歌手、封面)
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaMetadata?.let { metadata ->
                    _uiState.update { state ->
                        state.copy(
                            songTitle = metadata.title.toString(),
                            artistName = metadata.artist.toString(),
                            coverUri = metadata.artworkUri
                        )
                    }
                }
            }
        })
    }

    // --- 给 UI 调用的方法 ---

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

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
