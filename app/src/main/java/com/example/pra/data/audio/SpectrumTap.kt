package com.example.pra.data.audio

import android.util.Log
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 从播放管线内部抓 PCM 做频谱分析 (B 方案)。
 *
 * 与 Visualizer 不同：不申请录音权限、不受 ROM 限制，数据来自解码后的 PCM。
 * 数据流: TapAudioProcessor.queueInput → push() 写入环形缓冲 → 分析线程每 33ms 取 1024 点做 FFT
 *        → 聚合为 16 条柱子 + bass/mid/treble/level，供场景脚本消费。
 */
object SpectrumTap {

    const val BAR_COUNT = 16
    private const val TAG = "SpectrumTap"
    private const val FFT_SIZE = 1024
    private const val UPDATE_MS = 33L

    /** 由设置开关控制: 关闭时不拷贝、不分析, 零开销 */
    @Volatile
    var enabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) startAnalyzer() else stopAnalyzer()
        }

    @Volatile
    var bars: FloatArray = FloatArray(BAR_COUNT)
        private set

    @Volatile
    var bass: Float = 0f
        private set

    @Volatile
    var mid: Float = 0f
        private set

    @Volatile
    var treble: Float = 0f
        private set

    @Volatile
    var level: Float = 0f
        private set

    @Volatile
    var samplesReceived: Long = 0L
        private set

    @Volatile
    var lastError: String? = null
        private set

    private val lock = Any()
    private val ring = FloatArray(FFT_SIZE * 2)
    private var writePos = 0
    private var sampleRate = 44_100

    @Volatile
    private var analyzer: Thread? = null

    /** 音频线程调用: 只取左声道, 归一化后写入环形缓冲 */
    fun push(samples: FloatArray, channels: Int) {
        if (!enabled) return
        val ch = channels.coerceAtLeast(1)
        synchronized(lock) {
            var i = 0
            while (i < samples.size) {
                ring[writePos % ring.size] = samples[i]
                writePos++
                i += ch
            }
            samplesReceived += samples.size
        }
    }

    fun updateSampleRate(rate: Int) {
        if (rate > 0) sampleRate = rate
    }

    private fun startAnalyzer() {
        if (analyzer != null) return
        analyzer = Thread {
            val window = FloatArray(FFT_SIZE)
            val re = FloatArray(FFT_SIZE)
            val im = FloatArray(FFT_SIZE)
            val hann = FloatArray(FFT_SIZE) { i ->
                (0.5 - 0.5 * cos(2.0 * PI * i / (FFT_SIZE - 1))).toFloat()
            }
            var lastLog = 0L
            while (enabled) {
                synchronized(lock) {
                    val start = (writePos - FFT_SIZE).coerceAtLeast(0)
                    for (i in 0 until FFT_SIZE) {
                        val pos = start + i
                        window[i] = if (pos >= 0 && pos < writePos && (writePos - pos) <= ring.size) {
                            ring[pos % ring.size]
                        } else {
                            0f
                        }
                    }
                }
                for (i in 0 until FFT_SIZE) {
                    re[i] = window[i] * hann[i]
                    im[i] = 0f
                }
                runCatching { fft(re, im) }
                    .onFailure { lastError = it.message; Log.w(TAG, "fft failed", it) }

                val points = FFT_SIZE / 2
                val hzPerBin = sampleRate.toFloat() / FFT_SIZE
                val next = FloatArray(BAR_COUNT)
                for (b in 0 until BAR_COUNT) {
                    val lo = (points * ratio(b)).toInt().coerceIn(0, points - 1)
                    val hi = (points * ratio(b + 1)).toInt().coerceIn(lo, points - 1)
                    var peak = 0f
                    for (k in lo..hi) {
                        val mag = (sqrt(re[k] * re[k] + im[k] * im[k]) / (FFT_SIZE / 2f))
                            .coerceIn(0f, 1f)
                        if (mag > peak) peak = mag
                    }
                    next[b] = maxOf(peak * 4f, bars[b] * 0.82f).coerceIn(0f, 1f)
                }
                bars = next
                bass = bandEnergy(re, im, 20f, 250f, hzPerBin, points)
                mid = bandEnergy(re, im, 250f, 2000f, hzPerBin, points)
                treble = bandEnergy(re, im, 2000f, 16000f, hzPerBin, points)
                level = (next.average()).toFloat()

                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastLog >= 1000L) {
                    lastLog = now
                    Log.d(
                        TAG,
                        "samples=$samplesReceived level=%.3f bass=%.3f mid=%.3f treble=%.3f bars=%s"
                            .format(
                                level, bass, mid, treble,
                                next.joinToString(",", "[", "]") { "%.2f".format(it) },
                            ),
                    )
                }
                try {
                    Thread.sleep(UPDATE_MS)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }.apply {
            name = "spectrum-tap"
            isDaemon = true
            start()
        }
        Log.d(TAG, "analyzer started, rate=$sampleRate")
    }

    private fun stopAnalyzer() {
        analyzer?.interrupt()
        analyzer = null
        bars = FloatArray(BAR_COUNT)
        bass = 0f; mid = 0f; treble = 0f; level = 0f
    }

    private fun bandEnergy(
        re: FloatArray,
        im: FloatArray,
        fromHz: Float,
        toHz: Float,
        hzPerBin: Float,
        points: Int,
    ): Float {
        val lo = (fromHz / hzPerBin).toInt().coerceIn(0, points - 1)
        val hi = (toHz / hzPerBin).toInt().coerceIn(lo, points - 1)
        var sum = 0f
        for (k in lo..hi) sum += sqrt(re[k] * re[k] + im[k] * im[k]) / (FFT_SIZE / 2f)
        return (sum / (hi - lo + 1)) * 6f
    }

    private fun ratio(index: Int): Float {
        val x = index.toFloat() / BAR_COUNT
        return x * x
    }

    /** 原地迭代基-2 FFT */
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wRe = cos(angle).toFloat()
            val wIm = sin(angle).toFloat()
            var i = 0
            while (i < n) {
                var curRe = 1f
                var curIm = 0f
                for (k in 0 until len / 2) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + len / 2] * curRe - im[i + k + len / 2] * curIm
                    val vIm = re[i + k + len / 2] * curIm + im[i + k + len / 2] * curRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + len / 2] = uRe - vRe
                    im[i + k + len / 2] = uIm - vIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                i += len
            }
            len = len shl 1
        }
    }
}
