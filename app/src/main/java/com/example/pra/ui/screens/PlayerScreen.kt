package com.example.pra.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pra.ui.components.AtmosphericFluidBackground
import com.example.pra.ui.components.player.AlbumArtSection
import com.example.pra.ui.components.player.BottomNavSection
import com.example.pra.ui.components.player.ControlsSection
import com.example.pra.ui.components.player.TopBarSection
import com.example.pra.ui.components.player.TrackInfoSection
import com.example.pra.ui.viewmodel.MusicPlayerViewModel

@Composable
fun PlayerScreen(
    // 注入 ViewModel (自动获取，不需要手动传)
    viewModel: MusicPlayerViewModel = viewModel()
) {
    // 1. 收集 UI 状态 (StateFlow -> Compose State)
    // 只要 ViewModel 里的 _uiState 变了，这里就会自动刷新
    val state by viewModel.uiState.collectAsState()

    // 2. 最底层背景
    AtmosphericFluidBackground {

        // 3. 内容层
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding() // 避开状态栏和底部横条
                .padding(horizontal = 24.dp), // 全局边距
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // --- 区域 A: 顶部栏 ---
            Box(modifier = Modifier.padding(top = 16.dp)) {
                TopBarSection(
                    // 暂时没有功能绑定，预留着
                    onMoreClick = { /* TODO */ },
                    onMinimizeClick = { /* TODO */ }
                )
            }

            // [弹簧 1]
            Spacer(modifier = Modifier.weight(2f))

            // --- 区域 B: 专辑封面 ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f) // 保持正方形
            ) {
                // 传入真实封面 URI
                AlbumArtSection(coverUri = state.coverUri)
            }

            // [弹簧 2] (使用 height 保持固定间距，或 weight 保持弹性)
            Spacer(modifier = Modifier.height(10.dp))

            // --- 区域 C: 歌曲信息 ---
            Box(modifier = Modifier.fillMaxWidth()) {
                TrackInfoSection(
                    state = state, // 传入包含歌名歌手的 State
                    onFavoriteClick = { /* TODO: 收藏功能 */ },
                )
            }

            // [弹簧 3]
            Spacer(modifier = Modifier.weight(0.5f))

            // --- 区域 D: 核心控制区 ---
            Column(modifier = Modifier.fillMaxWidth()) {
                ControlsSection(
                    state = state,
                    // --- 关键绑定：连接 ViewModel ---
                    onPlayPauseClick = { viewModel.togglePlayPause() },
                    onPrevClick = { viewModel.skipToPrevious() },
                    onNextClick = { viewModel.skipToNext() },
                    onSeek = { /* TODO: 进度拖拽待实现 */ }
                )
            }

            // [弹簧 4]
            Spacer(modifier = Modifier.weight(0.5f))

            // --- 区域 E: 底部导航 ---
            Box(modifier = Modifier.padding(bottom = 16.dp)) {
                BottomNavSection(
                    onCommentClick = { /* TODO */ },
                    onPlaylistClick = { /* TODO */ }
                )
            }
        }
    }
}
