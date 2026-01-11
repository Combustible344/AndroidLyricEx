package com.example.pra.ui.components.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
    trackHeight: Dp = 6.dp,
    // 渐变色：给进度条加一点高级的视觉流动感
    activeTrackColor: Color = Color.White,
    inactiveTrackColor: Color = Color.White.copy(alpha = 0.3f)
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDragging by interactionSource.collectIsDraggedAsState()

    // --- 核心修改：使用物理弹簧动画 ---
    val animatedValue by animateFloatAsState(
        targetValue = value,
        animationSpec = spring(
            // 1. 阻尼比：NoBouncy (1.0f) 表示没有回弹。
            // 音乐进度条不需要回弹（时间不会倒流），只需要平滑追赶。
            dampingRatio = Spring.DampingRatioNoBouncy,

            // 2. 刚度：决定了追赶的速度。
            // StiffnessLow (200f) 像水流一样柔和；
            // StiffnessMedium (1500f) 比较跟手。
            // 这里我们选一个稍微自定义的数值 400f，既不拖泥带水，又足够顺滑。
            stiffness = 400f
        ),
        label = "SliderSpringAnimation"
    )

    // 拖拽时用原值，播放时用动画值
    val valueToDisplay = if (isDragging) value else animatedValue

    Slider(
        value = valueToDisplay,
        onValueChange = onValueChange,
        modifier = modifier,
        interactionSource = interactionSource,
        thumb = { Spacer(modifier = Modifier.size(0.dp)) },
        steps = 0,
        track = { sliderState ->
            // 轨道背景
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(RoundedCornerShape(trackHeight))
                    .background(inactiveTrackColor)
            ) {
                // 轨道前景 (添加一点渐变色增强视觉质感)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = sliderState.value)
                        .fillMaxHeight()
                        .background(
                            // 这是一个很淡的从“亮白”到“纯白”的渐变，会让条子看起来更有光泽
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFE0E0E0), // 稍暗的白
                                    Color.White        // 纯亮白
                                )
                            )
                        )
                )
            }
        }
    )
}
