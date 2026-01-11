package com.example.pra.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialCustomSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 6.dp, // <--- 修改 1: 默认加粗到 6dp (您可以改成 8dp 或 10dp)
    // thumbSize 参数不再需要，因为我们将其隐藏了
) {
    val interactionSource = remember { MutableInteractionSource() }

    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        interactionSource = interactionSource,

        // --- 修改 2: 隐藏当前位置的原点 ---
        // 我们传入一个空的 Spacer，没有任何大小和颜色
        // 这样用户能拖动，但看不到那个球
        thumb = {
            Spacer(modifier = Modifier.size(0.dp))
        },

        // --- 自定义轨道 ---
        track = { sliderState ->
            // 轨道背景 (未激活部分 - 灰色)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight) // 使用加粗的高度
                    .background(
                        Color.White.copy(alpha = 0.3f),
                        RoundedCornerShape(trackHeight) // 圆角随高度自动调整，保持胶囊形
                    )
                    .clip(RoundedCornerShape(trackHeight))
            ) {
                // 轨道前景 (激活部分 - 白色)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = sliderState.value) // 宽度随进度变化
                        .fillMaxHeight()
                        .background(Color.White, RectangleShape)
                )
            }
        },

        // 确保没有刻度点
        steps = 0
    )
}
