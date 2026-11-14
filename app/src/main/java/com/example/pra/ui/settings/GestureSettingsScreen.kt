package com.example.pra.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.pra.data.gesture.GestureBinding
import com.example.pra.data.gesture.GesturePrimitive
import com.example.pra.data.gesture.GestureStep
import com.example.pra.data.gesture.Gestures
import com.example.pra.data.gesture.PlayerAction
import com.example.pra.ui.theme.PraShapes
import com.example.pra.ui.theme.PraSpacing
import com.example.pra.ui.theme.praColors

/**
 * 伪息屏手势配置页。
 *
 * 左: 参数化手势序列 (点击/长按时长/连续次数/四向滑动, 最多 3 步)
 * 右: 固定动作 (播放暂停/上一首/下一首/快进/快退)
 */
@Composable
fun GestureSettingsScreen(
    bindings: List<GestureBinding>,
    onSave: (List<GestureBinding>) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = praColors()
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = PraSpacing.screenH),
    ) {
        Spacer(Modifier.height(PraSpacing.xl))

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = colors.textPrimary,
                )
            }
            Text(
                text = "伪息屏手势",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
            )
        }

        Spacer(Modifier.height(PraSpacing.sm))
        Text(
            text = "仅在伪息屏中生效。手势会按顺序严格匹配，匹配不上就忽略；1 秒内连点 5 次仍然是解锁。",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )

        Spacer(Modifier.height(PraSpacing.lg))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            bindings.forEachIndexed { index, binding ->
                BindingRow(
                    binding = binding,
                    onClick = { editingIndex = index },
                    onDelete = {
                        val list = bindings.toMutableList().apply { removeAt(index) }
                        onSave(list)
                    },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = PraSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(PraSpacing.md),
        ) {
            Text(
                text = "添加手势",
                style = MaterialTheme.typography.labelLarge,
                color = colors.onAccent,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(PraShapes.field)
                    .background(colors.accent)
                    .clickable { adding = true }
                    .padding(vertical = PraSpacing.md),
            )
            Text(
                text = "恢复默认",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(PraShapes.field)
                    .background(colors.surface)
                    .clickable { onReset() }
                    .padding(vertical = PraSpacing.md),
            )
        }
    }

    if (adding || editingIndex != null) {
        val initial = editingIndex?.let { bindings.getOrNull(it) }
        GestureEditorOverlay(
            initial = initial,
            onDismiss = { adding = false; editingIndex = null },
            onConfirm = { binding ->
                val list = bindings.toMutableList()
                val index = editingIndex
                if (index != null && index in list.indices) list[index] = binding else list.add(binding)
                // 同一个手势只允许存在一条绑定, 后保存的覆盖先前的
                onSave(Gestures.dedupeKeepLast(list))
                adding = false
                editingIndex = null
            },
        )
    }
}

@Composable
private fun BindingRow(
    binding: GestureBinding,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = praColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = PraSpacing.xs)
            .clip(PraShapes.row)
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = PraSpacing.md, vertical = PraSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = binding.label,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = binding.action.label,
                style = MaterialTheme.typography.bodySmall,
                color = colors.accent,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "删除",
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun GestureEditorOverlay(
    initial: GestureBinding?,
    onDismiss: () -> Unit,
    onConfirm: (GestureBinding) -> Unit,
) {
    val colors = praColors()
    var steps by remember { mutableStateOf(initial?.steps ?: emptyList()) }
    var action by remember { mutableStateOf(initial?.action ?: PlayerAction.PlayPause) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(PraShapes.sheet)
                .background(colors.surface)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                ) { }
                .padding(PraSpacing.lg)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (initial == null) "添加手势" else "编辑手势",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = colors.textSecondary)
                }
            }

            SectionLabel("手势步骤（最多 ${Gestures.MAX_STEPS} 步）")
            GesturePrimitive.entries.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    row.forEach { primitive ->
                        Chip(
                            text = primitive.label,
                            selected = false,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (steps.size < Gestures.MAX_STEPS) {
                                steps = steps + GestureStep(
                                    primitive = primitive,
                                    repeat = 1,
                                    holdMs = GUESTURE_DEFAULT_HOLD,
                                )
                            }
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            Spacer(Modifier.height(PraSpacing.sm))
            if (steps.isEmpty()) {
                Text(
                    text = "点上面的按钮添加步骤，例如：左滑 → 左滑（即连续两次）",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
            }
            steps.forEachIndexed { index, step ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(PraShapes.field)
                        .background(colors.surfaceHigh)
                        .padding(horizontal = PraSpacing.md, vertical = PraSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1}. ${step.label}",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (step.primitive == GesturePrimitive.LongPress) {
                        Text(
                            text = "${step.holdMs}ms ▸",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.accent,
                            modifier = Modifier
                                .clip(PraShapes.chip)
                                .background(colors.accentContainer)
                                .clickable {
                                    val choices = Gestures.HOLD_CHOICES
                                    val nextIndex = (choices.indexOf(step.holdMs) + 1) % choices.size
                                    steps = steps.toMutableList().also {
                                        it[index] = step.copy(holdMs = choices[nextIndex])
                                    }
                                }
                                .padding(horizontal = PraSpacing.sm, vertical = 2.dp),
                        )
                    } else {
                        Text(
                            text = "×${step.repeat} ▸",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.accent,
                            modifier = Modifier
                                .clip(PraShapes.chip)
                                .background(colors.accentContainer)
                                .clickable {
                                    steps = steps.toMutableList().also {
                                        it[index] = step.copy(repeat = step.repeat % Gestures.MAX_REPEAT + 1)
                                    }
                                }
                                .padding(horizontal = PraSpacing.sm, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.size(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "移除该步",
                        tint = colors.textTertiary,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { steps = steps.filterIndexed { i, _ -> i != index } },
                    )
                }
            }

            Spacer(Modifier.height(PraSpacing.md))
            SectionLabel("执行动作")
            PlayerAction.entries.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    row.forEach { candidate ->
                        Chip(
                            text = candidate.label,
                            selected = action == candidate,
                            modifier = Modifier.weight(1f),
                        ) { action = candidate }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(PraSpacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(PraSpacing.md)) {
                Text(
                    text = "取消",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(PraShapes.field)
                        .background(colors.surfaceHigh)
                        .clickable { onDismiss() }
                        .padding(vertical = PraSpacing.md),
                )
                Text(
                    text = "保存",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onAccent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(PraShapes.field)
                        .background(if (steps.isEmpty()) colors.surfaceHigh else colors.accent)
                        .clickable(enabled = steps.isNotEmpty()) {
                            onConfirm(GestureBinding(steps, action))
                        }
                        .padding(vertical = PraSpacing.md),
                )
            }
            Spacer(Modifier.height(PraSpacing.md))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = praColors().textTertiary,
        modifier = Modifier.padding(top = PraSpacing.md, bottom = PraSpacing.xs),
    )
}

@Composable
private fun Chip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = praColors()
    Box(
        modifier = modifier
            .clip(PraShapes.field)
            .background(if (selected) colors.accent else colors.surfaceHigh)
            .clickable(onClick = onClick)
            .padding(vertical = PraSpacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.onAccent else colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

private const val GUESTURE_DEFAULT_HOLD = 500
