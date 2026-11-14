package com.example.pra.ui.util

import java.util.Locale

/** 毫秒 → 3:07 / 1:02:03 */
fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

/** 0f..1f → 百分比文字，用于队列/播放页状态 */
fun formatPercent(value: Float): String = "${(value * 100).toInt()}%"
