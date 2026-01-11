package com.example.pra.ui.state

data class PlayerUiState(
    val songTitle: String = "Skydive",
    val artistName: String = "PIKASONIC",
    val currentPosition: Float = 0.7f, // 0f to 1f
    val isPlaying: Boolean = true
)