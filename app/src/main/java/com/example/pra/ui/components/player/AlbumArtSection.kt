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
import android.net.Uri
import androidx.compose.ui.draw.alpha
import coil.compose.AsyncImage // 引入这个
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest

@Composable
fun AlbumArtSection(
    coverUri: Uri?, // <--- 新增参数：接收封面 URI
    modifier: Modifier = Modifier
) {
    val placeholderCoverGradient = Brush.linearGradient(
        colors = listOf(Color(0xFFE0C3FC), Color(0xFF8EC5FC))
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // --- 1. 阴影层 (模糊) ---
        // 我们尝试加载同一张图做阴影，如果没有图就用渐变
        if (coverUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(coverUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .aspectRatio(1f)
                    .align(Alignment.BottomCenter)
                    .offset(y = 5.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .blur(radius = 35.dp) // 模糊
                    .alpha(0.6f)          // 半透明

            )
        } else {
            // 没有封面时的默认阴影
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .aspectRatio(1f)
                    .align(Alignment.BottomCenter)
                    .blur(radius = 35.dp)
                    .background(brush = placeholderCoverGradient, alpha = 0.6f)
            )
        }

        // --- 2. 主图层 ---
        Box(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(brush = placeholderCoverGradient) // 默认底色
                .align(Alignment.BottomCenter)
        ) {
            if (coverUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(coverUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Album Art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 微微的纹理遮罩，增加质感
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.1f))
            )
        }
    }
}