package com.example.pra.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.pra.ui.theme.PraShapes
import com.example.pra.ui.theme.praColors

/**
 * 全站统一的封面加载。
 * - 统一占位底色 + 缺图图标，避免各页面各自处理 null；
 * - crossfade 让切歌时封面淡入。
 */
@Composable
fun PraArtwork(
    uri: Uri?,
    modifier: Modifier = Modifier,
    shape: Shape = PraShapes.cover,
    fallbackIconSize: Dp = 28.dp,
) {
    val colors = praColors()
    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.surfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (uri != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(uri)
                    .crossfade(220)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(fallbackIconSize),
            )
        }
    }
}

/** 圆角封面，供列表小图使用（避免每个调用点重复写 shape） */
@Composable
fun PraArtworkSmall(uri: Uri?, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    PraArtwork(
        uri = uri,
        modifier = modifier.size(size),
        shape = RoundedCornerShape(size * 0.22f),
        fallbackIconSize = size * 0.34f,
    )
}
