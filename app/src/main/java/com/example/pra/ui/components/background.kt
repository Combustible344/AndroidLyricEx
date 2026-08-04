package com.example.pra.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 氛围流体背景。
 * @param active 为 false 时冻结动画 (屏保全屏覆盖时用于省电,
 *        不创建无限动画, 背景不会每帧重绘/重算模糊)
 */
@Composable
fun AtmosphericFluidBackground(
    active: Boolean = true,
    content: @Composable () -> Unit
) {
    // 动画值: active 时由 infiniteTransition 驱动, 否则固定为 0 (零开销)
    val moveAnim1: Float
    val moveAnim2: Float
    if (active) {
        val infiniteTransition = rememberInfiniteTransition(label = "AtmosphericFluid")

        // 1. 减慢动画速度：高级感通常伴随着缓慢、难以察觉的流动
        val a1 by infiniteTransition.animateFloat(
            initialValue = -60f, targetValue = 60f,
            animationSpec = infiniteRepeatable(tween(20000, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
            label = "move1"
        )
        val a2 by infiniteTransition.animateFloat(
            initialValue = 80f, targetValue = -80f,
            animationSpec = infiniteRepeatable(tween(25000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "move2"
        )
        moveAnim1 = a1
        moveAnim2 = a2
    } else {
        moveAnim1 = 0f
        moveAnim2 = 0f
    }

    // 2. 提取图片中的核心色板：深灰蓝、岩板灰、冷调白
    val baseColor = Color(0xFF2C353F)     // 基底：深岩石灰 (对应图片最暗部)
    val highlightColor = Color(0xFF5D7385) // 高光：较亮的灰蓝 (对应光源)
    val midToneColor = Color(0xFF435360)   // 中间调：过渡灰
    val shadowColor = Color(0xFF1A2126)    // 阴影：深邃黑青

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(baseColor) // 使用提取的深灰色作为底色
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    // 3. 增大模糊半径：从 120dp 增加到 180dp，消除所有边缘，形成“空气感”
                    .blur(180.dp)
            ) {
                val w = size.width
                val h = size.height

                // 绘制逻辑：模拟漫反射光线

                // 底部深色沉淀 (稳定重心)
                drawCircle(
                    color = shadowColor.copy(alpha = 0.6f),
                    radius = w * 0.9f,
                    center = Offset(w * 0.5f, h * 0.9f)
                )

                // 左上/中部 主光源 (模拟图片中柔和的亮部)
                drawCircle(
                    color = highlightColor.copy(alpha = 0.3f), // 低透明度是关键，避免过曝
                    radius = w * 0.8f,
                    center = Offset(w * 0.2f + moveAnim1, h * 0.3f + moveAnim2)
                )

                // 右侧 中间调 (丰富层次，避免单调)
                drawCircle(
                    color = midToneColor.copy(alpha = 0.4f),
                    radius = w * 0.7f,
                    center = Offset(w * 0.8f - moveAnim1, h * 0.6f - moveAnim2)
                )

                // 中心 极淡的提亮 (模拟玻璃反光)
                drawCircle(
                    color = Color.White.copy(alpha = 0.05f), // 极淡的白色
                    radius = w * 0.4f,
                    center = Offset(w * 0.5f, h * 0.4f + moveAnim1 * 0.5f)
                )
            }
        }

        // 内容层
        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}
