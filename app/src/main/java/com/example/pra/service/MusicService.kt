package com.example.pra.service

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.pra.data.audio.TapAudioProcessor

class MusicService : MediaSessionService() {

    // 真正的播放器实例
    private var player: ExoPlayer? = null
    // 与外界通信的会话窗口
    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // 1. 配置播放器: 在 AudioSink 前挂一个旁路处理器, 供频谱可视化读取 PCM
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessorChain(
                        DefaultAudioSink.DefaultAudioProcessorChain(TapAudioProcessor()),
                    )
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }
        }

        player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC) // 告诉系统我们在放音乐
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // 自动处理音频焦点（来电暂停）
            )
            .build()

        // 2. 创建会话
        mediaSession = MediaSession.Builder(this, player!!).build()

        // ★★★ 新增：设置通知提供者，防止服务被杀 ★★★
        setMediaNotificationProvider(DefaultMediaNotificationProvider(this))
    }

    // 当 UI 连接服务时，返回这个会话
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    // 当 App 被划掉（任务移除）时，停止服务
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = player
        // 用 isPlaying 判断真实播放状态 (playWhenReady 在加载/缓冲时也可能为 true)
        if (player != null && !player.isPlaying) {
            stopSelf()
        }
    }

    // 销毁时释放资源，防止内存泄漏 (player 只 release 一次)
    override fun onDestroy() {
        val session = mediaSession
        val p = player
        mediaSession = null
        player = null
        session?.release()
        p?.release()
        super.onDestroy()
    }
}
