package com.example.pra.ui.state

import android.net.Uri

data class PlayerUiState(
    val songTitle: String = "未播放",
    val artistName: String = "请选择歌曲",
    val coverUri: Uri? = null,    // 新增：支持动态封面
    val isPlaying: Boolean = false,
    val progress: Float = 0f,     // 0f - 1f (用于进度条)
    val currentTime: String = "0:00",
    val totalTime: String = "0:00"
)
