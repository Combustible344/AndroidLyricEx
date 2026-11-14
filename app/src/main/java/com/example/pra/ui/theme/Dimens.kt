package com.example.pra.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.dp

/** 间距刻度。禁止再出现 13.dp / 27.dp 这类随手值。 */
object PraSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val huge = 48.dp

    /** 页面左右统一边距 */
    val screenH = 20.dp
}

/**
 * 动效规范。设计意图：
 * - 小控件状态切换用 Fast + 标准缓动；
 * - 页面/面板转场用 Normal，配 emphasized 缓动（先快后缓）；
 * - 可拖拽/可中断的物理动作用 Spring，跟手。
 */
object PraMotion {
    const val Fast = 180
    const val Normal = 320
    const val Slow = 520

    /** M3 emphasized 曲线 */
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0.2f, 1f)

    fun <T> springy() = spring<T>(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMediumLow,
    )
}
