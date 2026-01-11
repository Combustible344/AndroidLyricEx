package com.example.pra.service

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class MusicService : MediaSessionService() {

    // 真正的播放器实例
    private var player: ExoPlayer? = null
    // 与外界通信的会话窗口
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        // 1. 配置播放器
        player = ExoPlayer.Builder(this)
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
    }

    // 当 UI 连接服务时，返回这个会话
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    // 当 App 被划掉（任务移除）时，停止服务
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = player
        // 如果没有正在播放，就停止服务；如果正在播放，继续在后台运行
        if (player != null && !player.playWhenReady) {
            stopSelf()
        }
    }

    // 销毁时释放资源，防止内存泄漏
    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player?.release()
        player = null
        super.onDestroy()
    }
}
