package com.example.pra.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AlbumArtSection(
    modifier: Modifier = Modifier
) {
    val placeholderCoverGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFFE0C3FC),
            Color(0xFF8EC5FC)
        )
    )

    // 外层容器
    Box(
        modifier = modifier.fillMaxWidth(),
        // 关键点：将默认对齐方式设为“底部居中”
        contentAlignment = Alignment.BottomCenter
    ) {
        // --- 第一层：弥散光晕 (Shadow) ---
        // 阴影也对齐底部，但额外向下偏移一点点 (offset y)，让它露出来
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f) // 宽度 50%
                .aspectRatio(1f)
                .align(Alignment.BottomCenter) // 对齐底部
                //.offset(y = 15.dp) // 向下“溢出”一点，制造悬浮感
                .blur(radius = 35.dp)
                .background(
                    brush = placeholderCoverGradient,
                    alpha = 0.6f
                )
        )

        // --- 第二层：专辑主图 (Image) ---
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f) // 宽度 60%
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(brush = placeholderCoverGradient)
                .align(Alignment.BottomCenter) // 关键：严格与父级底边对齐
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.1f))
            )
        }
    }
}
