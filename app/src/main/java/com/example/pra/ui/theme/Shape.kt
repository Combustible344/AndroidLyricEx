package com.example.pra.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 形状体系：卡片/封面用大圆角，按钮用胶囊，控件用中圆角。
 * 与 M3 Shapes 对齐，另外暴露几个项目内常用的具名形状。
 */
object PraShapes {
    val cover = RoundedCornerShape(20.dp)
    val card = RoundedCornerShape(24.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val field = RoundedCornerShape(14.dp)
    val chip = RoundedCornerShape(50)
    val miniPlayer = RoundedCornerShape(18.dp)
    val row = RoundedCornerShape(16.dp)
}

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
