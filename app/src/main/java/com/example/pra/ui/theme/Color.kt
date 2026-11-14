package com.example.pra.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// ============================================================
// 原始色板 (Night / Nocturne)
// 冷灰中性色 + 冰蓝强调色，专为「深色音乐播放器」设计。
// 所有界面都只通过 PraColors 语义层取色，不要再硬编码。
// ============================================================

// 深色：背景由深到浅分四层，靠层级而不是阴影建立空间感
val Ink = Color(0xFF06080B)
val InkElevated = Color(0xFF0C1016)
val InkSurface = Color(0xFF121821)
val InkSurfaceHigh = Color(0xFF1A212B)
val InkSurfaceHighest = Color(0xFF232C38)
val InkOutline = Color(0xFF2A3441)

val TextPrimary = Color(0xFFEEF2F6)
val TextSecondary = Color(0xFFA6B2C0)
val TextTertiary = Color(0xFF6F7C8C)

val AccentIce = Color(0xFF8FB8FF)
val AccentIceContainer = Color(0xFF15304F)
val AccentWarm = Color(0xFFE7C67B)
val OnAccentIce = Color(0xFF05203C)

// 浅色：同一套语义，换成纸白
val Paper = Color(0xFFF7F9FC)
val PaperElevated = Color(0xFFFFFFFF)
val PaperSurface = Color(0xFFEDF1F6)
val PaperSurfaceHigh = Color(0xFFE2E8F0)
val PaperOutline = Color(0xFFD3DBE5)

val TextPrimaryLight = Color(0xFF0F1419)
val TextSecondaryLight = Color(0xFF4A5563)
val TextTertiaryLight = Color(0xFF7C8794)

val AccentBlueLight = Color(0xFF2F6FD0)
val AccentBlueLightContainer = Color(0xFFDCE8FF)

// 半透明覆盖层：磨砂/毛玻璃质感的来源
val GlassWhiteStrong = Color(0x1FFFFFFF)
val GlassWhiteSoft = Color(0x14FFFFFF)
val GlassWhiteFaint = Color(0x0AFFFFFF)
val GlassBlackSoft = Color(0x33000000)

/**
 * 语义色板。界面只依赖这个类，切换主题/取色时整棵树自动重建。
 */
@Immutable
data class PraColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val surfaceHighest: Color,
    val outline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentContainer: Color,
    val onAccent: Color,
    val warm: Color,
    val glassStrong: Color,
    val glassSoft: Color,
    val glassFaint: Color,
)

val PraDarkColors = PraColors(
    isDark = true,
    background = Ink,
    surface = InkElevated,
    surfaceHigh = InkSurface,
    surfaceHighest = InkSurfaceHighest,
    outline = InkOutline,
    textPrimary = TextPrimary,
    textSecondary = TextSecondary,
    textTertiary = TextTertiary,
    accent = AccentIce,
    accentContainer = AccentIceContainer,
    onAccent = OnAccentIce,
    warm = AccentWarm,
    glassStrong = GlassWhiteStrong,
    glassSoft = GlassWhiteSoft,
    glassFaint = GlassWhiteFaint,
)

val PraLightColors = PraColors(
    isDark = false,
    background = Paper,
    surface = PaperElevated,
    surfaceHigh = PaperSurface,
    surfaceHighest = PaperSurfaceHigh,
    outline = PaperOutline,
    textPrimary = TextPrimaryLight,
    textSecondary = TextSecondaryLight,
    textTertiary = TextTertiaryLight,
    accent = AccentBlueLight,
    accentContainer = AccentBlueLightContainer,
    onAccent = Color.White,
    warm = Color(0xFFB8862B),
    glassStrong = Color(0x14000000),
    glassSoft = Color(0x0D000000),
    glassFaint = Color(0x08000000),
)
