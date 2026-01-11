package com.example.pra.ui.screens

import com.example.pra.ui.components.player.AlbumArtSection
import com.example.pra.ui.components.player.TrackInfoSection
import com.example.pra.ui.components.player.ControlsSection
import com.example.pra.ui.components.player.BottomNavSection

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.pra.ui.components.AtmosphericFluidBackground
import com.example.pra.ui.state.PlayerUiState



// 这里的导入应该是你之前创建的组件位置
// import com.example.pra.ui.components.common.TopBarSection
// import com.example.pra.ui.components.player.AlbumArtSection
// ... 我们先把这些占位符写在同一个文件下面，方便你复制粘贴看效果

@Composable
fun PlayerScreen(
    state: PlayerUiState = PlayerUiState()
) {
    // 1. 最底层：动态模糊背景
    AtmosphericFluidBackground {

        // 2. 内容层：使用 systemBarsPadding 避开刘海屏和底部手势条
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding() // 关键：确保不被系统状态栏遮挡
                .padding(horizontal = 24.dp), // 全局左右边距，保持呼吸感
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // --- 区域 A: 顶部栏 (时间、下拉条、更多) ---
            // 高度固定，不需要 weight
            Box(modifier = Modifier.padding(top = 16.dp)) {
                //TopBarSectionPlaceholder()
            }

            // [弹簧 1]：将封面图向下推一点，但不要推太远
            Spacer(modifier = Modifier.weight(2f))

            // --- 区域 B: 专辑封面 ---
            // 这是视觉重心，给它分配最大的空间权重，或者让它自适应
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f) // 强制保持正方形
            ) {
                AlbumArtSection()
            }

            // [弹簧 2]：封面和文字之间的间距
            Spacer(modifier = Modifier.height(10.dp))

            // --- 区域 C: 歌曲信息 (歌名、歌手、收藏) ---
            Box(modifier = Modifier.fillMaxWidth()) {
                //TrackInfoSectionPlaceholder(state)
                TrackInfoSection(state)
            }

            // [弹簧 3]：歌曲信息和控制区之间的弹性空间
            // 这个 weight 可以大一点，让控制区尽量靠下，符合手指操作习惯
            Spacer(modifier = Modifier.weight(0.5f))

            // --- 区域 D: 核心控制区 (进度条、播放按钮、音量) ---
            Column(modifier = Modifier.fillMaxWidth()) {
                //ControlsSectionPlaceholder()
                ControlsSection(state)
            }

            // [弹簧 4]：控制区和底部导航之间的间距
            Spacer(modifier = Modifier.weight(0.5f))

            // --- 区域 E: 底部导航 ---
            Box(modifier = Modifier.padding(bottom = 16.dp)) {
               // BottomNavSectionPlaceholder()
                BottomNavSection()
            }
        }
    }
}

// ==========================================
// 下面是占位组件 (Placeholders)
// 稍后我们会把它们移动到单独的文件中并细化
// ==========================================

@Composable
fun TopBarSectionPlaceholder() {
    // 临时用一个半透明方块表示位置
    Surface(
        color = Color.White.copy(alpha = 0.1f),
        modifier = Modifier.fillMaxWidth().height(40.dp)
    ) { /* TODO: 填充 TopBar */ }
}

@Composable
fun AlbumArtSectionPlaceholder() {
    Surface(
        color = Color.White.copy(alpha = 0.1f),
        modifier = Modifier.fillMaxSize()
    ) { /* TODO: 填充专辑封面 */ }
}

@Composable
fun TrackInfoSectionPlaceholder(state: PlayerUiState) {
    Surface(
        color = Color.White.copy(alpha = 0.1f),
        modifier = Modifier.fillMaxWidth().height(60.dp)
    ) { /* TODO: 填充歌名歌手 */ }
}

@Composable
fun ControlsSectionPlaceholder() {
    // 包含进度条、播放按钮、音量，暂时给个总高度
    Surface(
        color = Color.White.copy(alpha = 0.1f),
        modifier = Modifier.fillMaxWidth().height(180.dp)
    ) { /* TODO: 填充控制面板 */ }
}

@Composable
fun BottomNavSectionPlaceholder() {
    Surface(
        color = Color.White.copy(alpha = 0.1f),
        modifier = Modifier.fillMaxWidth().height(50.dp)
    ) { /* TODO: 填充底部导航 */ }
}
