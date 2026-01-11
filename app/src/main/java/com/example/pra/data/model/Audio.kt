package com.example.pra.data.model

import android.net.Uri

data class Audio(
    val id: Long,
    val uri: Uri,          // 文件的播放地址 (content://...)
    val title: String,     // 歌名
    val artist: String,    // 歌手
    val duration: Long,    // 时长 (毫秒)
    val albumId: Long,     // 专辑ID (用于查找封面)
    val albumArtUri: Uri?,  // 封面图地址
    val lyricUri: Uri? = null, // <--- 新增字段，预留位置，默认为空

    // --- 新增字段 ---
    val likedCount: Int = 0,      // 总点赞数
    val isLiked: Boolean = false  // 当前是否已点赞 (变成实心)
)
