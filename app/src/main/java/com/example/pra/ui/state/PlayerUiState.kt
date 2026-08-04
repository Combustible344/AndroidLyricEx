package com.example.pra.ui.state

import android.net.Uri
import com.example.pra.data.model.Audio
import com.example.pra.data.model.LyricLine

// 二选一模式
enum class PlayMode {
    LoopOne,    // 单曲循环
    Shuffle     // 随机播放
}

data class PlayerUiState(
    // ... 原有基础字段 ...
    val songTitle: String = "未播放",
    val artistName: String = "请选择歌曲",
    val coverUri: Uri? = null,
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val currentTime: String = "0:00",
    val totalTime: String = "0:00",
    val durationMs: Long = 0L,           // 当前曲目总时长 (毫秒), 供渲染层做帧内进度推演
    val playlist: List<Audio> = emptyList(),
    val currentSongId: Long = -1L,
    val playMode: PlayMode = PlayMode.Shuffle,
    val volume: Float = 1.0f,

    // --- 歌词相关状态 (增强版) ---
    val lyrics: List<LyricLine> = emptyList(), // 所有歌词

    // 行级状态
    val currentLyricIndex: Int = -1,           // 当前正在播放哪一行 (List index)
    val currentLyricLine: LyricLine? = null,   // 当前行对象

    // 字级状态 (新增)
    // 如果该行支持逐字 (words不为空)，这里指示当前唱到了第几个字 (List index)
    // 如果不支持或还没开始唱，默认为 -1
    val currentWordIndex: Int = -1,

    val isCurrentSongLiked: Boolean = false,
    val currentSongLikedCount: Int = 0
)
