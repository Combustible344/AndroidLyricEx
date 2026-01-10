package com.example.pra.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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


@Composable
fun AppleMusicBrightFluidBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "AppleMusicFluid")

    // 动画：让色块移动更加柔和且幅度稍大
    val moveAnim1 by infiniteTransition.animateFloat(
        initialValue = -120f, targetValue = 120f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
        label = "move1"
    )
    val moveAnim2 by infiniteTransition.animateFloat(
        initialValue = 150f, targetValue = -150f,
        animationSpec = infiniteRepeatable(tween(15000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "move2"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 将基底从纯黑改为极深蓝色，提升整体通透度
            .background(Color(0xFF0A0F1E))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // 维持大模糊半径以保证色彩融合
                .blur(120.dp)
        ) {
            val w = size.width
            val h = size.height

            // 1. 左上角：亮青/湖蓝色 (提供主要亮度)
            drawCircle(
                color = Color(0xFF00ACC1).copy(alpha = 0.5f),
                radius = w * 0.8f,
                center = Offset(w * 0.2f + moveAnim1, h * 0.2f + moveAnim2)
            )

            // 2. 右侧中心：深靛蓝 (增加层次)
            drawCircle(
                color = Color(0xFF3949AB).copy(alpha = 0.6f),
                radius = w * 1.0f,
                center = Offset(w * 0.8f + moveAnim2, h * 0.5f + moveAnim1)
            )

            // 3. 左下角：暗紫红色 (Apple Music 标志性的冷暖对比)
            drawCircle(
                color = Color(0x6969694F).copy(alpha = 0.4f),
                radius = w * 0.9f,
                center = Offset(w * 0.1f - moveAnim2, h * 0.8f - moveAnim1)
            )

            // 4. 中心弥散块：浅灰蓝 (关键提亮步，减少死黑)
            drawCircle(
                color = Color(0xFF455A64).copy(alpha = 0.3f),
                radius = w * 0.7f,
                center = Offset(w * 0.5f, h * 0.5f)
            )
        }
    }
}
