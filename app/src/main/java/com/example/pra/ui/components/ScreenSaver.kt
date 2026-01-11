package com.example.pra.ui.components

import android.content.Context
import android.graphics.Paint
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

// ==========================================
// 1. 定义环境与接口 (抽象层)
// ==========================================

/**
 * 绘图环境参数
 * 包含绘图所需的上下文信息，方便开发者调用
 */
data class ScreenSaverEnvironment(
    val context: Context,    // Android 上下文 (用于加载 Bitmap, Resources, Fonts 等)
    val size: Size,          // 画布尺寸 (Width, Height)
    val center: Offset,      // 画布中心点坐标 (x, y)
    val config: ScreenSaverConfig // 当前的配置信息
)

/**
 * 屏保配置参数
 */
data class ScreenSaverConfig(
    val timeoutMillis: Long = 5000,
    val unlockTapCount: Int = 5,
    val tapResetMillis: Long = 1000,
    val backgroundColor: Color = Color.Black
)

/**
 * 绘图策略接口
 * @param T 数据类型
 */
fun interface ScreenSaverRenderer<T> {
    /**
     * @param scope Compose 原生绘图作用域 (调用 drawCircle, drawRect 等)
     * @param env   自定义环境参数 (包含 Context, Size, Center)
     * @param data  当前数据
     */
    fun onDraw(scope: DrawScope, env: ScreenSaverEnvironment, data: T)
}

// ==========================================
// 2. 核心组件实现 (容器层)
// ==========================================

@Composable
fun <T> IdleScreenSaver(
    data: T,
    renderer: ScreenSaverRenderer<T>,
    config: ScreenSaverConfig = ScreenSaverConfig(),
    content: @Composable () -> Unit
) {
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isOverlayVisible by remember { mutableStateOf(false) }

    // 获取当前 Android Context，后续传给 Environment
    val currentContext = LocalContext.current

    // 超时检测逻辑
    LaunchedEffect(lastInteractionTime, isOverlayVisible, config.timeoutMillis) {
        if (!isOverlayVisible) {
            val timeSinceLastInteraction = System.currentTimeMillis() - lastInteractionTime
            val remainingTime = config.timeoutMillis - timeSinceLastInteraction
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
            ScreenSaverOverlay(
                data = data,
                renderer = renderer,
                config = config,
                androidContext = currentContext, // 传入 Context
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
    onUnlock: () -> Unit
) {
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

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
        // 构建环境对象
        val env = ScreenSaverEnvironment(
            context = androidContext,
            size = size,
            center = center,
            config = config
        )

        // 调用外部绘制逻辑
        renderer.onDraw(this, env, data)
    }
}

// ==========================================
// 3. 预设绘制器 (工具层)
// ==========================================

object ScreenSaverRenderers {

    /**
     * 简单的居中文字绘制器
     */
    fun createSimpleTextRenderer(
        textSize: Float = 64f,
        textColor: Int = android.graphics.Color.WHITE
    ) = ScreenSaverRenderer<String> { scope, env, text ->
        if (text.isNotEmpty()) {
            scope.drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    color = textColor
                    this.textSize = textSize
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }

                // 直接使用 env 中计算好的参数
                val xPos = env.center.x
                val yPos = env.center.y - ((paint.descent() + paint.ascent()) / 2)

                canvas.nativeCanvas.drawText(text, xPos, yPos, paint)
            }
        }
    }
}
