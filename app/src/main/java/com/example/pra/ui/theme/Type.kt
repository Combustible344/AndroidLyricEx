package com.example.pra.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 全站字体：直接使用系统字体。
 *
 * 之前打包了两份思源黑体 ttf（19.6 MB，占 APK 的 24%）。Android 的系统 CJK 字体
 * 本身就是 Noto Sans CJK —— 与思源黑体同源同设计，常规/粗体都由系统提供，
 * 所以改用 [FontFamily.Default] 后观感基本不变，包体直接省下 19.6 MB。
 *
 * 如果以后想换回内置字体，只需恢复：
 * ```
 * val HanSanFamily = FontFamily(
 *     Font(R.font.source_normal, FontWeight.Normal),
 *     Font(R.font.source_bold, FontWeight.Bold),
 * )
 * ```
 */
val HanSanFamily: FontFamily = FontFamily.Default

private fun hanSan(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    letterSpacing: Double = 0.0,
) = TextStyle(
    fontFamily = HanSanFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

/**
 * 排版层级。原则：
 * - 播放页标题用 display/headline，列表项用 title/body，元信息用 label。
 * - 行高给足（中文需要），字距接近 0。
 */
val Typography = Typography(
    displayLarge = hanSan(40, 48, FontWeight.Bold, -0.5),
    displayMedium = hanSan(34, 42, FontWeight.Bold, -0.5),
    displaySmall = hanSan(30, 38, FontWeight.Bold, -0.25),

    headlineLarge = hanSan(28, 36, FontWeight.Bold, -0.25),
    headlineMedium = hanSan(24, 32, FontWeight.Bold),
    headlineSmall = hanSan(21, 28, FontWeight.Bold),

    titleLarge = hanSan(20, 27, FontWeight.Bold),
    titleMedium = hanSan(17, 23, FontWeight.Bold),
    titleSmall = hanSan(15, 21, FontWeight.Bold),

    bodyLarge = hanSan(16, 24),
    bodyMedium = hanSan(14, 21),
    bodySmall = hanSan(13, 19),

    labelLarge = hanSan(13, 18, FontWeight.Bold, 0.2),
    labelMedium = hanSan(12, 16, FontWeight.Bold, 0.3),
    labelSmall = hanSan(11, 15, FontWeight.Normal, 0.4),
)
