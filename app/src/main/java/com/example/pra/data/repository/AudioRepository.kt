package com.example.pra.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.example.pra.data.model.Audio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AudioRepository(private val context: Context) {

    /**
     * 扫描本地所有音频文件
     */
    suspend fun getLocalAudio(): List<Audio> = withContext(Dispatchers.IO) {
        val audioList = mutableListOf<Audio>()

        // 1. 定义我们要查询的列
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )

        // 2. 查询条件：只查音乐，时长大于 10秒 (过滤掉短提示音)
        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 10000"

        // 3. 排序：按添加时间倒序 (新歌在前)
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                // 获取各列的索引
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn) ?: "未知歌曲"
                    val artist = cursor.getString(artistColumn) ?: "未知歌手"
                    val duration = cursor.getLong(durationColumn)
                    val albumId = cursor.getLong(albumIdColumn)

                    // 生成播放 URI
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    // 生成封面图 URI (Android 通用专辑封面地址)
                    val albumArtUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    )

                    audioList.add(
                        Audio(
                            id = id,
                            uri = contentUri,
                            title = title,
                            artist = artist,
                            duration = duration,
                            albumId = albumId,
                            albumArtUri = albumArtUri,
                            lyricUri = null,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("AudioRepository", "Error querying audio", e)
        }

        return@withContext audioList
    }
}
