package com.example.pra

import com.example.pra.ui.screens.PlayerScreen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.pra.ui.theme.PraTheme

import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        enableEdgeToEdge()

        // 2. 隐藏状态栏逻辑
        hideSystemBars()

        setContent {
            PraTheme {
                PlayerScreen()


            }
        }

    }

    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

        // 配置隐藏行为：只有在边缘滑动时才显示，且过一会后自动隐藏 (BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // 隐藏状态栏 (statusBars)
        // 如果也想隐藏底部导航栏，可以使用 WindowInsetsCompat.Type.systemBars()
        windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
    }
}
