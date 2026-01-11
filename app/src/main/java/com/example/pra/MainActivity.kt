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
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.pra.ui.components.IdleScreenSaver
import com.example.pra.ui.screens.PlayerScreen
import com.example.pra.ui.theme.PraTheme
import com.example.pra.ui.viewmodel.MusicPlayerViewModel

class MainActivity : ComponentActivity() {

    // 1. 获取 ViewModel 实例
    private val viewModel: MusicPlayerViewModel by viewModels()

    // 2. 权限请求回调
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "权限已获取，请重启APP或等待刷新", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "请授予文件权限以播放音乐", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()

        checkAndRequestPermissions()

        setContent {
            PraTheme {
                // 3. 收集 UI 状态 (StateFlow -> Compose State)
                val uiState by viewModel.uiState.collectAsState()

                // 4. 获取当前歌词文本
                // 注意：这里假设你的 LyricLine 类有一个名为 'text' 的属性。
                // 如果报错 "Unresolved reference: text"，请检查你的 LyricLine.kt 文件，
                // 看看属性名是叫 'content'、'lrc' 还是需要用 words 拼接。
                val currentLyric = uiState.currentLyricLine?.text ?: ""

                // 备选方案：如果 LyricLine 没有 text 属性，只有 words 列表，请注释上面那行，改用下面这行：
                // val currentLyric = uiState.currentLyricLine?.words?.joinToString("") { it.text } ?: ""

                // 5. 传入屏保组件
                IdleScreenSaver(
                    timeoutMillis = 5000,
                    currentLyric = currentLyric
                ) {
                    PlayerScreen()
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            // 已有权限，ViewModel 会自动加载
        } else {
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
