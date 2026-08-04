package com.example.pra.ui.components

import android.content.Context
import android.graphics.Paint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

// ==========================================
// 1. 定义环境与接口
// ==========================================

data class ScreenSaverEnvironment(
    val context: Context,
    val size: Size,
    val center: Offset,
    val config: ScreenSaverConfig
)

data class ScreenSaverConfig(
    val timeoutMillis: Long = 5000,
    val unlockTapCount: Int = 5,
    val tapResetMillis: Long = 1000,
    val backgroundColor: Color = Color.Black
)

/**
 * 渲染器: frameNanos 由 overlay 内部帧循环提供 (60fps),
 * 只在 Canvas 绘制阶段读取, 不会触发 UI 树重组
 */
fun interface ScreenSaverRenderer<T> {
    fun onDraw(scope: DrawScope, env: ScreenSaverEnvironment, data: T, frameNanos: Long)
}

// ==========================================
// 2. 核心组件实现
// ==========================================

@Composable
fun <T> IdleScreenSaver(
    data: T,
    renderer: ScreenSaverRenderer<T>,
    config: ScreenSaverConfig = ScreenSaverConfig(),
    externalInteractionSignal: Long = 0L,
    isEnabled: Boolean = true,
    onOverlayVisibilityChange: (Boolean) -> Unit = {},
    frameRateActive: Boolean = true,
    content: @Composable () -> Unit
) {
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isOverlayVisible by remember { mutableStateOf(false) }
    val currentContext = LocalContext.current

    // 1. 监听外部信号
    LaunchedEffect(externalInteractionSignal) {
        if (externalInteractionSignal > 0) {
            lastInteractionTime = System.currentTimeMillis()
        }
    }

    // 2. 超时检测
    LaunchedEffect(lastInteractionTime, isOverlayVisible, config.timeoutMillis, isEnabled) {
        if (!isEnabled) {
            if (isOverlayVisible) {
                isOverlayVisible = false
            }
            return@LaunchedEffect
        }

        if (!isOverlayVisible) {
            val timeSinceLastInteraction = System.currentTimeMillis() - lastInteractionTime
            val remainingTime = config.timeoutMillis - timeSinceLastInteraction
            if (remainingTime > 0) {
                delay(remainingTime.milliseconds)
            }
            if (isEnabled) {
                isOverlayVisible = true
            }
        }
    }

    // 3. 可见性通知 (用于冻结背景动画等)
    LaunchedEffect(isOverlayVisible) {
        onOverlayVisibilityChange(isOverlayVisible)
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

        // ★★★ 核心修改：添加动画 ★★★
        AnimatedVisibility(
            visible = isOverlayVisible,
            enter = fadeIn(animationSpec = tween(1000)), // 1秒淡入
            exit = fadeOut(animationSpec = tween(500))   // 0.5秒淡出
        ) {
            ScreenSaverOverlay(
                data = data,
                renderer = renderer,
                config = config,
                androidContext = currentContext,
                frameRateActive = frameRateActive,
                onUnlock = {
                    isOverlayVisible = false
                    lastInteractionTime = System.currentTimeMillis()
                }
            )
        }
    }
}

@Composable
private fun <T> ScreenSaverOverlay(
    data: T,
    renderer: ScreenSaverRenderer<T>,
    config: ScreenSaverConfig,
    androidContext: Context,
    frameRateActive: Boolean,
    onUnlock: () -> Unit
) {
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    // ★★★ 自适应帧驱动: 只在 Canvas 绘制阶段读取 frameNanos, 不触发 UI 树重组。
    // frameRateActive=false (画面静止) 时只渲染一帧, 帧循环休眠, 省电
    var frameNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(frameRateActive) {
        if (frameRateActive) {
            while (true) {
                withFrameNanos { frameNanos = it }
            }
        } else {
            withFrameNanos { frameNanos = it }
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(config.backgroundColor)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastTapTime > config.tapResetMillis) {
                            tapCount = 0
                        }
                        tapCount++
                        lastTapTime = currentTime
                        if (tapCount >= config.unlockTapCount) {
                            tapCount = 0
                            onUnlock()
                        }
                    }
                )
            }
    ) {
        val env = ScreenSaverEnvironment(
            context = androidContext,
            size = size,
            center = center,
            config = config
        )
        renderer.onDraw(this, env, data, frameNanos)
    }
}

// ==========================================
// 3. 预设绘制器
// ==========================================

object ScreenSaverRenderers {
    fun createSimpleTextRenderer(
        textSize: Float = 64f,
        textColor: Int = android.graphics.Color.WHITE
    ) = ScreenSaverRenderer<String> { scope, env, text, _ ->
        if (text.isNotEmpty()) {
            scope.drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    color = textColor
                    this.textSize = textSize
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
                val xPos = env.center.x
                val yPos = env.center.y - ((paint.descent() + paint.ascent()) / 2)
                canvas.nativeCanvas.drawText(text, xPos, yPos, paint)
            }
        }
    }
}
