package com.example.pra.ui.state

import android.net.Uri
import com.example.pra.data.model.Audio // 引入 Audio 模型

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
    // --- 新增：播放列表 & 当前播放歌曲的ID ---
    val playlist: List<Audio> = emptyList(),
    val currentSongId: Long = -1L ,// 用于高亮当前播放的歌,
    val playMode: PlayMode = PlayMode.LoopOne
)
