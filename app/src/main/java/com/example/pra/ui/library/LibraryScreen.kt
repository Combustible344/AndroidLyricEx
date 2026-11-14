package com.example.pra.ui.library

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.example.pra.data.model.Audio
import com.example.pra.ui.components.PraArtwork
import com.example.pra.ui.components.PraSongRow
import com.example.pra.ui.state.PlayerUiState
import com.example.pra.ui.theme.PraShapes
import com.example.pra.ui.theme.PraSpacing
import com.example.pra.ui.theme.praColors

private enum class SortMode(val label: String) {
    Default("默认"),
    Title("名称"),
    Duration("时长"),
}

/**
 * 曲库：搜索 / 排序 / 全量歌曲，长按歌曲可绑定歌词或移除。
 * 顶部 + 号用于手动导入音频文件（SAF）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: PlayerUiState,
    onPlaySong: (List<Audio>, Long) -> Unit,
    onAddFile: (Uri) -> Unit,
    onScan: () -> Unit,
    onBindLyric: (Long, Uri) -> Unit,
    onRemove: (Audio) -> Unit,
    onSheetVisibilityChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = praColors()
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var sortIndex by rememberSaveable { mutableIntStateOf(0) }
    val sortMode = SortMode.entries[sortIndex]

    var actionSong by remember { mutableStateOf<Audio?>(null) }
    var pendingLyricSong by remember { mutableStateOf<Audio?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 弹层打开时通知外层：暂停伪息屏 + 重新隐藏系统栏
    androidx.compose.runtime.LaunchedEffect(actionSong) {
        onSheetVisibilityChange(actionSong != null)
    }

    val addFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = queryDisplayName(context.contentResolver, uri) ?: ""
        val supported = listOf(".mp3", ".flac", ".wav", ".m4a", ".aac", ".ogg")
        if (supported.any { name.endsWith(it, ignoreCase = true) }) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            onAddFile(uri)
        } else {
            Toast.makeText(context, "不支持的音频格式", Toast.LENGTH_SHORT).show()
        }
    }

    val lyricLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val song = pendingLyricSong
        if (uri == null || song == null) return@rememberLauncherForActivityResult
        val name = queryDisplayName(context.contentResolver, uri) ?: ""
        if (name.endsWith(".lrc", ignoreCase = true)) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            onBindLyric(song.id, uri)
            Toast.makeText(context, "歌词绑定成功", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "请选择 .lrc 格式的文件", Toast.LENGTH_SHORT).show()
        }
        pendingLyricSong = null
    }

    val songs = remember(state.playlist, query, sortMode) {
        state.playlist
            .filter { song ->
                query.isBlank() ||
                    song.title.contains(query, ignoreCase = true) ||
                    song.artist.contains(query, ignoreCase = true)
            }
            .let { list ->
                when (sortMode) {
                    SortMode.Default -> list
                    SortMode.Title -> list.sortedBy { it.title }
                    SortMode.Duration -> list.sortedBy { it.duration }
                }
            }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = PraSpacing.screenH)) {
        Spacer(Modifier.height(PraSpacing.xxl))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "曲库",
                style = MaterialTheme.typography.displaySmall,
                color = colors.textPrimary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(PraShapes.chip)
                        .background(colors.surface)
                        .clickable { sortIndex = (sortIndex + 1) % SortMode.entries.size }
                        .padding(horizontal = PraSpacing.md, vertical = PraSpacing.sm),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sort,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(PraSpacing.xs))
                    Text(
                        text = sortMode.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                }
                Spacer(Modifier.size(PraSpacing.sm))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.accentContainer)
                        .clickable { addFileLauncher.launch(arrayOf("audio/*")) },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "添加音频文件",
                        tint = colors.accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(PraSpacing.lg))

        SearchField(query = query, onQueryChange = { query = it })

        Spacer(Modifier.height(PraSpacing.md))

        if (songs.isEmpty()) {
            EmptyLibrary(
                hasPlaylist = state.playlist.isNotEmpty(),
                onScan = onScan,
                onAddFile = { addFileLauncher.launch(arrayOf("audio/*")) },
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(songs, key = { it.id }) { song: Audio ->
                    PraSongRow(
                        song = song,
                        isCurrent = song.id == state.currentSongId,
                        isPlaying = state.isPlaying,
                        onClick = { onPlaySong(songs, song.id) },
                        onLongClick = { actionSong = song },
                    )
                }
                item { Spacer(Modifier.height(PraSpacing.xxxl)) }
            }
        }
    }

    actionSong?.let { song ->
        ModalBottomSheet(
            onDismissRequest = { actionSong = null },
            sheetState = sheetState,
            containerColor = colors.surface,
            shape = PraShapes.sheet,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = PraSpacing.md, bottom = PraSpacing.sm)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(colors.textTertiary.copy(alpha = 0.5f)),
                )
            },
        ) {
            Column(modifier = Modifier.padding(horizontal = PraSpacing.xl)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PraArtwork(
                        uri = song.albumArtUri,
                        modifier = Modifier.size(52.dp),
                        shape = PraShapes.field,
                    )
                    Spacer(Modifier.size(PraSpacing.md))
                    Column {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }

                Spacer(Modifier.height(PraSpacing.lg))

                ActionRow(
                    icon = Icons.Rounded.FileOpen,
                    label = if (song.lyricUri == null) "绑定 .lrc 歌词" else "更换 .lrc 歌词",
                ) {
                    pendingLyricSong = song
                    actionSong = null
                    lyricLauncher.launch(arrayOf("*/*"))
                }
                ActionRow(
                    icon = Icons.Rounded.Delete,
                    label = "从列表移除",
                    destructive = true,
                ) {
                    actionSong = null
                    onRemove(song)
                }

                Spacer(Modifier.height(PraSpacing.xxl))
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = praColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PraShapes.field)
            .clickable(onClick = onClick)
            .padding(horizontal = PraSpacing.md, vertical = PraSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (destructive) colors.warm else colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(PraSpacing.md))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (destructive) colors.warm else colors.textPrimary,
        )
    }
}

@Composable
private fun EmptyLibrary(
    hasPlaylist: Boolean,
    onScan: () -> Unit,
    onAddFile: () -> Unit,
) {
    val colors = praColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PraSpacing.huge),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.LibraryMusic,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(PraSpacing.md))
        Text(
            text = if (hasPlaylist) "没有匹配的歌曲" else "曲库是空的",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(PraSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(PraSpacing.md)) {
            Text(
                text = "扫描本地音乐",
                style = MaterialTheme.typography.labelLarge,
                color = colors.accent,
                modifier = Modifier
                    .clip(PraShapes.chip)
                    .background(colors.accentContainer.copy(alpha = 0.4f))
                    .clickable(onClick = onScan)
                    .padding(horizontal = PraSpacing.lg, vertical = PraSpacing.sm),
            )
            Text(
                text = "添加文件",
                style = MaterialTheme.typography.labelLarge,
                color = colors.accent,
                modifier = Modifier
                    .clip(PraShapes.chip)
                    .background(colors.accentContainer.copy(alpha = 0.4f))
                    .clickable(onClick = onAddFile)
                    .padding(horizontal = PraSpacing.lg, vertical = PraSpacing.sm),
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val colors = praColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PraShapes.field)
            .background(colors.surface)
            .padding(horizontal = PraSpacing.md, vertical = PraSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(PraSpacing.sm))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "搜索歌曲或歌手",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textTertiary,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = colors.textPrimary,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                ),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun queryDisplayName(
    resolver: android.content.ContentResolver,
    uri: Uri,
): String? = runCatching {
    resolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index != -1) cursor.getString(index) else null
        } else {
            null
        }
    }
}.getOrNull()
