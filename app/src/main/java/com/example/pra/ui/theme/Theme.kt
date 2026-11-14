package com.example.pra.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** 语义色板的注入点 */
val LocalPraColors = staticCompositionLocalOf { PraDarkColors }

/** 界面取色入口：`val c = praColors()` —— 避免各处再写 MaterialTheme.colorScheme.xxx */
@Composable
@ReadOnlyComposable
fun praColors(): PraColors = LocalPraColors.current

private fun schemeOf(colors: PraColors) = if (colors.isDark) {
    darkColorScheme(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        primaryContainer = colors.accentContainer,
        onPrimaryContainer = colors.textPrimary,
        secondary = colors.textSecondary,
        onSecondary = colors.background,
        background = colors.background,
        onBackground = colors.textPrimary,
        surface = colors.surface,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceHigh,
        onSurfaceVariant = colors.textSecondary,
        surfaceContainer = colors.surface,
        surfaceContainerLow = colors.surface,
        surfaceContainerHigh = colors.surfaceHigh,
        surfaceContainerHighest = colors.surfaceHighest,
        outline = colors.outline,
        outlineVariant = colors.outline,
        error = Color(0xFFFF8A80),
        onError = Color(0xFF3B0907),
        scrim = Color.Black,
    )
} else {
    lightColorScheme(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        primaryContainer = colors.accentContainer,
        onPrimaryContainer = colors.textPrimary,
        secondary = colors.textSecondary,
        onSecondary = colors.background,
        background = colors.background,
        onBackground = colors.textPrimary,
        surface = colors.surface,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceHigh,
        onSurfaceVariant = colors.textSecondary,
        surfaceContainer = colors.surface,
        surfaceContainerLow = colors.surface,
        surfaceContainerHigh = colors.surfaceHigh,
        surfaceContainerHighest = colors.surfaceHighest,
        outline = colors.outline,
        outlineVariant = colors.outline,
        error = Color(0xFFB3261E),
        onError = Color.White,
        scrim = Color.Black,
    )
}

/**
 * 全站主题。
 *
 * @param darkTheme 默认固定深色：这个播放器的主视觉是「夜里听歌」，浅色只作为可选能力保留。
 * @param dynamicColor 关闭：封面驱动的取色比壁纸取色更贴合音乐场景（见 NowPlaying 的 Palette 实现）。
 *        即使打开，也只借用系统 accent，中性色仍然保持本 App 的层级。
 */
@Composable
fun PraTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val base = if (darkTheme) PraDarkColors else PraLightColors
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        val system = if (darkTheme) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
        base.copy(accent = system.primary, accentContainer = system.primaryContainer)
    } else {
        base
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !colors.isDark
            controller.isAppearanceLightNavigationBars = !colors.isDark
        }
    }

    CompositionLocalProvider(LocalPraColors provides colors) {
        MaterialTheme(
            colorScheme = schemeOf(colors),
            typography = Typography,
            shapes = Shapes,
            content = content,
        )
    }
}
