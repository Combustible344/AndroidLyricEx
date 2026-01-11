package com.example.pra

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.pra.ui.screens.PlayerScreen
import com.example.pra.ui.theme.PraTheme

class MainActivity : ComponentActivity() {

    // 1. 注册权限请求回调
    // 这是一个系统契约：我们请求权限 -> 系统回调给我们 true/false
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // 权限给了！这里可以通知 ViewModel 重新加载数据
            // 但由于 ViewModel 在 init 里就会尝试加载，虽然第一次可能失败，
            // 我们可以简单地重建 UI，或者利用 ViewModel 的内部逻辑重试。
            // 简单起见，我们重启 Activity 或者就这样放着（因为下一次打开 App 肯定就好了）
            // 更好的做法是在 ViewModel 里监听权限状态，这里我们先简单 Toast 一下
            Toast.makeText(this, "权限已获取，请重启APP或等待刷新", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "请授予文件权限以播放音乐", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()

        // 2. 在这里检查并请求权限
        checkAndRequestPermissions()

        setContent {
            PraTheme {
                PlayerScreen()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        // 根据 Android 版本决定请求哪个权限
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO // Android 13+
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE // Android 12及以下
        }

        // 检查是否已经有权限
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            // 已经有权限了，啥都不用做，ViewModel 会自动工作
        } else {
            // 没有权限，发起请求
            requestPermissionLauncher.launch(permission)
        }
    }

    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
    }
}
