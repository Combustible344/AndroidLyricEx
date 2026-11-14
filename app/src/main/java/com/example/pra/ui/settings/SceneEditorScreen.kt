package com.example.pra.ui.settings

import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pra.R
import com.example.pra.ui.theme.PraShapes
import com.example.pra.ui.theme.PraSpacing
import com.example.pra.ui.theme.praColors
import kotlinx.coroutines.delay
import java.io.File

/**
 * 伪息屏场景编辑器 (v1)。
 *
 * 直接编辑设备上的 `lua_scripts/scene.lua`：
 * 保存后由 MainActivity 已有的 FileObserver(CLOSE_WRITE) 热重载，无需重启。
 * 脚本写错时 MainActivity 会回退默认场景并写 `scene_error.log`，这里读取展示。
 */
@Composable
fun SceneEditorScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = praColors()
    val context = LocalContext.current

    val sceneDir = remember { File(context.getExternalFilesDir(null), "lua_scripts") }
    val sceneFile = remember { File(sceneDir, "scene.lua") }
    val guideFile = remember { File(sceneDir, "scene_guide.md") }
    val errorFile = remember { File(sceneDir, "scene_error.log") }

    var text by remember { mutableStateOf(readText(sceneFile)) }
    var errorText by remember { mutableStateOf(readText(errorFile)) }
    var guideText by remember { mutableStateOf(readText(guideFile)) }
    var showGuide by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!sceneDir.exists()) sceneDir.mkdirs()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = PraSpacing.lg),
    ) {
        Spacer(Modifier.height(PraSpacing.md))

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = colors.textPrimary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "场景编辑器",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                )
                Text(
                    text = if (showGuide) "Lua API 手册" else "scene.lua · 保存即生效",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
            }
            Text(
                text = if (showGuide) "返回编辑" else "API 手册",
                style = MaterialTheme.typography.labelLarge,
                color = colors.accent,
                modifier = Modifier
                    .clip(PraShapes.chip)
                    .background(colors.accentContainer)
                    .clickable {
                        showGuide = !showGuide
                        if (showGuide) guideText = readText(guideFile)
                    }
                    .padding(horizontal = PraSpacing.md, vertical = PraSpacing.sm),
            )
        }

        Spacer(Modifier.height(PraSpacing.sm))

        val bodyScroll = rememberScrollState()
        if (showGuide) {
            Text(
                text = guideText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp,
                ),
                color = colors.textSecondary,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(PraShapes.field)
                    .background(colors.surface)
                    .verticalScroll(bodyScroll)
                    .padding(PraSpacing.md),
            )
        } else {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontFamily = FontFamily.Monospace,
                ),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(PraShapes.field)
                    .background(colors.surface)
                    .verticalScroll(rememberScrollState())
                    .padding(PraSpacing.md),
            )
        }

        if (errorText.isNotBlank() && !showGuide) {
            Spacer(Modifier.height(PraSpacing.sm))
            Text(
                text = "上次保存的脚本有错误（已回退默认场景）：\n" + errorText.take(300),
                style = MaterialTheme.typography.labelSmall,
                color = colors.warm,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PraShapes.field)
                    .background(colors.surfaceHigh)
                    .padding(PraSpacing.sm),
            )
        }

        Spacer(Modifier.height(PraSpacing.md))

        Row(horizontalArrangement = Arrangement.spacedBy(PraSpacing.md)) {
            Text(
                text = "恢复默认",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(PraShapes.field)
                    .background(colors.surfaceHigh)
                    .clickable {
                        val default = context.resources.openRawResource(R.raw.scene_default)
                            .bufferedReader().use { it.readText() }
                        text = default
                        writeText(sceneFile, default)
                        errorText = ""
                        Toast.makeText(context, "已恢复默认场景", Toast.LENGTH_SHORT).show()
                    }
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
                    .background(colors.accent)
                    .clickable {
                        writeText(sceneFile, text)
                        Toast.makeText(context, "已保存，息屏中立即生效", Toast.LENGTH_SHORT).show()
                        // 热重载是异步的，稍等再看有没有新的错误日志
                        errorText = ""
                    }
                    .padding(vertical = PraSpacing.md),
            )
        }

        Spacer(Modifier.height(PraSpacing.lg))
    }

    // 保存后轮询一次错误日志：脚本报错时 MainActivity 会写 scene_error.log
    LaunchedEffect(sceneFile.lastModified()) {
        delay(800)
        errorText = readText(errorFile)
    }
}

private fun readText(file: File): String =
    runCatching { if (file.exists()) file.readText() else "" }.getOrDefault("")

private fun writeText(file: File, content: String) {
    runCatching {
        file.parentFile?.mkdirs()
        file.writeText(content)
    }
}
