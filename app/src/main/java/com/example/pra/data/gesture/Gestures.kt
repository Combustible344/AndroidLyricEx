package com.example.pra.data.gesture

/**
 * 伪息屏手势系统。
 *
 * 设计: 用户自由组合"参数化手势"序列, 绑定到一个固定动作。
 *   左滑 ×1        → 快进 10 秒
 *   左滑 ×2        → 上一首
 *   长按 500ms     → 播放/暂停
 *   长按 800ms + 点击 → ...
 *
 * 一条绑定 = 有序的若干步, 每步包含: 手势类型 + 连续次数 + (长按)按住时长。
 * 识别时严格按顺序、按次数匹配; 不匹配就丢弃, 不做模糊猜测。
 */
enum class GesturePrimitive(val label: String) {
    Tap("点击"),
    LongPress("长按"),
    SwipeLeft("左滑"),
    SwipeRight("右滑"),
    SwipeUp("上滑"),
    SwipeDown("下滑"),
}

enum class PlayerAction(val label: String) {
    None("无操作"),
    PlayPause("播放 / 暂停"),
    Previous("上一首"),
    Next("下一首"),
    SeekForward("快进 10 秒"),
    SeekBackward("快退 10 秒"),
}

data class GestureStep(
    val primitive: GesturePrimitive,
    val repeat: Int = 1,
    val holdMs: Int = GUESTURE_DEFAULT_HOLD_MS,
) {
    val label: String
        get() = when (primitive) {
            GesturePrimitive.LongPress -> "长按 ${holdMs}ms"
            else -> if (repeat > 1) "${primitive.label} ×$repeat" else primitive.label
        }
}

data class GestureBinding(
    val steps: List<GestureStep>,
    val action: PlayerAction,
) {
    val label: String get() = steps.joinToString(" → ") { it.label }
}

/** 运行时识别到的一个手势 token (repeat 恒为 1, 连续动作会产生多个) */
data class GestureToken(
    val primitive: GesturePrimitive,
    val holdMs: Int = 0,
)

const val GUESTURE_DEFAULT_HOLD_MS = 500

object Gestures {

    /** 一条绑定最多几步 */
    const val MAX_STEPS = 3

    /** 单步最多连续几次 */
    const val MAX_REPEAT = 4

    /** 长按时长可选值 (毫秒) */
    val HOLD_CHOICES = listOf(300, 500, 800, 1000, 1500, 2000)

    /** 手势之间的最大静默间隔: 超过它就结算当前序列 */
    const val SEQUENCE_QUIET_MS = 550L

    /** 默认绑定: 常用动作开箱即用 */
    val defaults: List<GestureBinding> = listOf(
        GestureBinding(listOf(GestureStep(GesturePrimitive.Tap, repeat = 2)), PlayerAction.PlayPause),
        GestureBinding(listOf(GestureStep(GesturePrimitive.LongPress, holdMs = 500)), PlayerAction.PlayPause),
        // 单指滑动 = 拖动进度: 右滑快进, 左滑快退
        GestureBinding(listOf(GestureStep(GesturePrimitive.SwipeRight, repeat = 1)), PlayerAction.SeekForward),
        GestureBinding(listOf(GestureStep(GesturePrimitive.SwipeLeft, repeat = 1)), PlayerAction.SeekBackward),
        // 连滑两次 = 切歌: 右滑两次上一首, 左滑两次下一首
        GestureBinding(listOf(GestureStep(GesturePrimitive.SwipeRight, repeat = 2)), PlayerAction.Previous),
        GestureBinding(listOf(GestureStep(GesturePrimitive.SwipeLeft, repeat = 2)), PlayerAction.Next),
    )

    /** 旧版默认绑定 (左右滑动含义相反), 用于自动迁移 */
    val legacyDefaults: List<GestureBinding> = listOf(
        GestureBinding(listOf(GestureStep(GesturePrimitive.Tap, repeat = 2)), PlayerAction.PlayPause),
        GestureBinding(listOf(GestureStep(GesturePrimitive.LongPress, holdMs = 500)), PlayerAction.PlayPause),
        GestureBinding(listOf(GestureStep(GesturePrimitive.SwipeLeft, repeat = 1)), PlayerAction.Next),
        GestureBinding(listOf(GestureStep(GesturePrimitive.SwipeRight, repeat = 1)), PlayerAction.Previous),
        GestureBinding(listOf(GestureStep(GesturePrimitive.SwipeLeft, repeat = 2)), PlayerAction.SeekForward),
        GestureBinding(listOf(GestureStep(GesturePrimitive.SwipeRight, repeat = 2)), PlayerAction.SeekBackward),
    )

    /**
     * 用 token 序列去匹配一条绑定。
     * 规则: 步数与次数都必须严格对齐; 长按时长必须 >= 绑定值 (按得更久算命中)。
     */
    fun matches(binding: GestureBinding, tokens: List<GestureToken>): Boolean {
        if (binding.steps.isEmpty()) return false
        var cursor = 0
        for (step in binding.steps) {
            repeat(step.repeat) {
                val token = tokens.getOrNull(cursor) ?: return false
                if (token.primitive != step.primitive) return false
                if (step.primitive == GesturePrimitive.LongPress && token.holdMs < step.holdMs) {
                    return false
                }
                cursor++
            }
        }
        return cursor == tokens.size
    }

    /** 在全部绑定里找出与 token 序列完全匹配的动作; 没有则返回 null */
    fun resolve(bindings: List<GestureBinding>, tokens: List<GestureToken>): PlayerAction? {
        if (tokens.isEmpty()) return null
        return bindings.firstOrNull { matches(it, tokens) }?.action
    }

    /**
     * 一条绑定的"手势签名"：只看手势步骤，不看动作。
     * 同一个签名只能存在一条绑定 —— 保证「一个手势不会对应两个功能」。
     */
    fun signature(binding: GestureBinding): String = binding.steps.joinToString("+") { step ->
        "${step.primitive.name},${step.repeat},${step.holdMs}"
    }

    /** 按签名去重，保留**最后一条**（编辑后的结果优先） */
    fun dedupeKeepLast(bindings: List<GestureBinding>): List<GestureBinding> {
        val seen = LinkedHashMap<String, GestureBinding>()
        bindings.forEach { seen[signature(it)] = it }
        return seen.values.toList()
    }

    /** 一条绑定展开后的 token 数量, 用于限制缓冲长度 */
    fun tokenLength(binding: GestureBinding): Int = binding.steps.sumOf { it.repeat }

    fun maxTokenLength(bindings: List<GestureBinding>): Int =
        bindings.maxOfOrNull { tokenLength(it) } ?: 0

    // ==================== 持久化 (紧凑字符串, 不引 JSON 依赖) ====================

    fun encode(bindings: List<GestureBinding>): String = bindings.joinToString(";") { binding ->
        val steps = binding.steps.joinToString("+") { step ->
            "${step.primitive.name},${step.repeat},${step.holdMs}"
        }
        "$steps>${binding.action.name}"
    }

    fun decode(raw: String): List<GestureBinding> {
        if (raw.isBlank()) return emptyList()
        return dedupeKeepLast(raw.split(';').mapNotNull { entry ->
            val halves = entry.split('>')
            if (halves.size != 2) return@mapNotNull null
            val action = runCatching { PlayerAction.valueOf(halves[1]) }.getOrNull()
                ?: return@mapNotNull null
            val steps = halves[0].split('+').mapNotNull { rawStep ->
                val fields = rawStep.split(',')
                val primitive = runCatching { GesturePrimitive.valueOf(fields[0]) }.getOrNull()
                    ?: return@mapNotNull null
                GestureStep(
                    primitive = primitive,
                    repeat = fields.getOrNull(1)?.toIntOrNull()?.coerceIn(1, MAX_REPEAT) ?: 1,
                    holdMs = fields.getOrNull(2)?.toIntOrNull() ?: GUESTURE_DEFAULT_HOLD_MS,
                )
            }
            if (steps.isEmpty()) null else GestureBinding(steps.take(MAX_STEPS), action)
        })
    }
}
