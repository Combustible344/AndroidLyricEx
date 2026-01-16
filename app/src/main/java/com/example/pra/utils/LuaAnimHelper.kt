package com.example.pra.utils

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.pow
import kotlin.math.abs

/**
 * 提供给 Lua 的数学与动画计算库 (namespace: anim)
 */
class LuaAnimHelper {

    // ===========================
    // 基础插值与数学
    // ===========================

    /**
     * 线性插值: value = start + (end - start) * fraction
     */
    fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + (end - start) * fraction
    }

    /**
     * 范围映射: 把 value 从 [inMin, inMax] 映射到 [outMin, outMax]
     */
    fun map(value: Float, inMin: Float, inMax: Float, outMin: Float, outMax: Float): Float {
        // 防止除以0
        if (abs(inMax - inMin) < 0.00001f) return outMin
        return (value - inMin) * (outMax - outMin) / (inMax - inMin) + outMin
    }

    /**
     * 限制范围 (Clamp)
     */
    fun clamp(value: Float, min: Float, max: Float): Float {
        return value.coerceIn(min, max)
    }

    // ===========================
    // 周期性动画 (呼吸/心跳)
    // ===========================

    /**
     * 正弦波呼吸
     * @param timeMs 当前时间戳 (state.position 或 env.systemTime)
     * @param periodMs 周期 (毫秒)
     * @param min 输出最小值
     * @param max 输出最大值
     */
    fun sineWave(timeMs: Double, periodMs: Double, min: Float, max: Float): Float {
        if (periodMs <= 0) return min
        val t = (timeMs % periodMs) / periodMs // 0.0 ~ 1.0
        val angle = t * 2 * PI
        // sin 结果是 -1~1，转为 0~1
        val normalizedSin = (sin(angle).toFloat() + 1) / 2
        return lerp(min, max, normalizedSin)
    }

    /**
     * 脉冲波 (就像心跳，快速弹起然后慢慢回落)
     */
    fun pulse(timeMs: Double, periodMs: Double): Float {
        if (periodMs <= 0) return 0f
        val t = (timeMs % periodMs) / periodMs
        // 使用 exp 函数模拟心跳衰减
        return if (t < 0.2) {
            (t / 0.2).toFloat() // 快速上升
        } else {
            (1 - (t - 0.2) / 0.8).toFloat() // 慢速下降
        }
    }

    // ===========================
    // 缓动函数 (Easing)
    // 输入 t (0~1)，输出 变换后的 t
    // ===========================

    fun easeInQuad(t: Float): Float = t * t
    fun easeOutQuad(t: Float): Float = t * (2 - t)

    fun easeInOutSine(t: Float): Float {
        return -(cos(PI * t) - 1).toFloat() / 2
    }

    fun easeOutBack(t: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1
        return (1 + c3 * (t - 1).pow(2) + c1 * (t - 1).pow(3))
    }
}
