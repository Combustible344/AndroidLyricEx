package com.example.pra.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun FrostedGlassButton(
    painter: Painter,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 16.dp,       // 图标大小
    containerSize: Dp = 36.dp,  // 按钮整体大小
    // --- 新增参数 ---
    iconTint: Color = Color.White,  // 图标颜色 (默认纯白)
    backgroundColor: Color = Color.White.copy(alpha = 0.2f) // 背景颜色 (默认磨砂白)
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(containerSize)
            .clip(CircleShape)
            .background(backgroundColor) // 使用传入的背景色
            .clickable { onClick() }
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = iconTint, // 使用传入的图标色
            modifier = Modifier.size(iconSize)
        )
    }
}
