package com.example.pra.data.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 插在 AudioSink 前端的旁路处理器: 原样放行音频, 同时把 PCM 交给 [SpectrumTap] 做 FFT。
 * 16-bit 与 float 两种输出格式都支持; 关闭可视化时只做一次空判断, 不产生额外拷贝。
 */
class TapAudioProcessor : BaseAudioProcessor() {

    private var encoding: Int = C.ENCODING_PCM_16BIT
    private var channels: Int = 2

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        encoding = inputAudioFormat.encoding
        channels = inputAudioFormat.channelCount
        SpectrumTap.updateSampleRate(inputAudioFormat.sampleRate)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        tap(inputBuffer)

        val output = replaceOutputBuffer(remaining)
        output.put(inputBuffer)
        output.flip()
    }

    private fun tap(buffer: ByteBuffer) {
        if (!SpectrumTap.enabled) return
        val copy = buffer.duplicate().order(ByteOrder.nativeOrder())
        when (encoding) {
            C.ENCODING_PCM_FLOAT -> {
                val floats = FloatArray(copy.remaining() / 4)
                copy.asFloatBuffer().get(floats)
                SpectrumTap.push(floats, channels)
            }

            C.ENCODING_PCM_16BIT -> {
                val shorts = ShortArray(copy.remaining() / 2)
                copy.asShortBuffer().get(shorts)
                val floats = FloatArray(shorts.size) { shorts[it] / 32768f }
                SpectrumTap.push(floats, channels)
            }
        }
    }
}
