package com.example.pra.ui.state

import android.net.Uri
import com.example.pra.data.model.Audio
import com.example.pra.data.model.LyricLine

/**
 * 四种播放模式，映射到 Media3 的 repeatMode / shuffleModeEnabled：
 * - Sequential 顺序：播完最后一首停止
 * - LoopAll    列表循环：播完回到第一首
 * - LoopOne    单曲循环
 * - Shuffle    随机：打乱顺序 + 列表循环
 */
enum class PlayMode(val label: String) {
    Sequential("顺序"),
    LoopAll("列表循环"),
    LoopOne("单曲循环"),
    Shuffle("随机"),
    ;

    fun next(): PlayMode = entries[(ordinal + 1) % entries.size]
}

data class PlayerUiState(
    // --- 当前曲目 ---
    val songTitle: String = "未播放",
    val artistName: String = "请选择歌曲",
    val coverUri: Uri? = null,
    val isPlaying: Boolean = false,
    val currentSongId: Long = -1L,

    // --- 进度 ---
    /** 供 AOD / 通知使用的时间文本，低频更新 */
    val currentTime: String = "0:00",
    val totalTime: String = "0:00",
    val durationMs: Long = 0L,

    /**
     * 进度锚点：positionMs 在 positionAnchorNanos 时刻成立。
     * UI 侧用 withFrameNanos 在帧内插值，避免 10Hz 全量重组。
     * progress 仍然保留（AOD 用它同步锚点）。
     */
    val positionMs: Long = 0L,
    val positionAnchorNanos: Long = 0L,
    val progress: Float = 0f,

    // --- 数据 ---
    /** 完整曲库（MediaStore 扫描 + 手动导入） */
    val playlist: List<Audio> = emptyList(),
    /** 当前播放队列（可能等于曲库，也可能是筛选后的子集） */
    val queue: List<Audio> = emptyList(),

    val playMode: PlayMode = PlayMode.Shuffle,
    val volume: Float = 1.0f,

    // --- 歌词 ---
    val lyrics: List<LyricLine> = emptyList(),
    val currentLyricIndex: Int = -1,
    val currentLyricLine: LyricLine? = null,
    val currentWordIndex: Int = -1,

    val isCurrentSongLiked: Boolean = false,
    val currentSongLikedCount: Int = 0,
)
