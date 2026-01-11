package com.example.pra.data.model

data class LyricEntry(
    val lines: List<LyricLine> = emptyList()
)

data class LyricLine(
    val startTime: Long, // 该行开始时间 (毫秒)
    val endTime: Long,   // 该行结束时间 (通常是下一行的开始时间)
    val text: String,    // 完整文本
    val words: List<LyricWord> = emptyList() // 字级列表 (如果支持)
)

data class LyricWord(
    val text: String,
    val startTime: Long,
    val endTime: Long
)
