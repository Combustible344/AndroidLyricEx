package com.example.pra.ui.state

import android.net.Uri
import com.example.pra.data.model.Audio

// 二选一模式
enum class PlayMode {
    LoopOne,    // 单曲循环
    Shuffle     // 随机播放
}

data class PlayerUiState(
    val songTitle: String = "未播放",
    val artistName: String = "请选择歌曲",
    val coverUri: Uri? = null,
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val currentTime: String = "0:00",
    val totalTime: String = "0:00",
    val playlist: List<Audio> = emptyList(),
    val currentSongId: Long = -1L,

    val playMode: PlayMode = PlayMode.Shuffle,
    val volume: Float = 1.0f
)
