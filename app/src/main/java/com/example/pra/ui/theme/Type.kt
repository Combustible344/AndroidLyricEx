package com.example.pra.ui.theme

import com.example.pra.R

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp


val HanSanFamily = FontFamily(
    Font(R.font.source_normal, FontWeight.Normal), // 常规体
    Font(R.font.source_bold, FontWeight.Bold)       // 粗体
)


// Set of Material typography styles to start with
val Typography = Typography(
    headlineMedium = TextStyle(
        fontFamily = HanSanFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 25.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),

    // 用于歌手 (Artist Name) - 对应之前的 body
    bodyLarge = TextStyle(
        fontFamily =HanSanFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),

    // 用于时间戳 (Timestamp) - 小号文字
    labelSmall = TextStyle(
        fontFamily = HanSanFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )

    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)