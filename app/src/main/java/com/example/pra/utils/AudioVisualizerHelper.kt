package com.example.pra.utils

object AudioVisualizerData {
    // 原始 FFT 数据缓存 (大小通常是 128 或 1024)
    // volatile 保证多线程可见性
    @Volatile
    var rawFft: ByteArray? = null

    // 简化的能量值 (0.0 - 1.0)，用于做类似“声浪起伏”的简单效果
    @Volatile
    var audioEnergy: Float = 0f
}