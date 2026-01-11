package com.example.pra.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay

/**
 * 闲置屏保容器
 * @param currentLyric 当前需要显示的歌词文本
 */
@Composable
fun IdleScreenSaver(
    timeoutMillis: Long = 5000,
    currentLyric: String = "", // 新增参数：接收歌词
    content: @Composable () -> Unit
) {
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isOverlayVisible by remember { mutableStateOf(false) }

    LaunchedEffect(lastInteractionTime, isOverlayVisible) {
        if (!isOverlayVisible) {
            val timeSinceLastInteraction = System.currentTimeMillis() - lastInteractionTime
            val remainingTime = timeoutMillis - timeSinceLastInteraction
            if (remainingTime > 0) {
                delay(remainingTime)
            }
            isOverlayVisible = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(pass = PointerEventPass.Initial)
                        lastInteractionTime = System.currentTimeMillis()
                    }
                }
            }
    ) {
        content()
        if (isOverlayVisible) {
            BlackOverlayView(
                lyricText = currentLyric, // 传递歌词
                onUnlock = {
                    isOverlayVisible = false
                    lastInteractionTime = System.currentTimeMillis()
                }
            )
        }
    }
}

@Composable
private fun BlackOverlayView(
    lyricText: String,
    onUnlock: () -> Unit
) {
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastTapTime > 1000) {
                            tapCount = 0
                        }
                        tapCount++
                        lastTapTime = currentTime
                        if (tapCount >= 5) {
                            tapCount = 0
                            onUnlock()
                        }
                    }
                )
            }
    ) {
        // --- 在这里绘制当前的歌词行 ---
        if (lyricText.isNotEmpty()) {
            drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 64f // 设置字体大小
                    textAlign = Paint.Align.CENTER // 文字水平居中
                    isAntiAlias = true // 抗锯齿
                    // 可选：设置字体样式 Typeface.DEFAULT_BOLD
                }

                // 计算垂直居中的位置
                // size.height / 2 是画布中心
                // (descent + ascent) / 2 是文字本身的中心偏移量
                val xPos = size.width / 2
                val yPos = size.height / 2 - ((paint.descent() + paint.ascent()) / 2)

                canvas.nativeCanvas.drawText(
                    lyricText,
                    xPos,
                    yPos,
                    paint
                )
            }
        }
    }
}
