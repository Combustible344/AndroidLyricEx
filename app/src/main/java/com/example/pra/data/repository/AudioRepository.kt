package com.example.pra.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.example.pra.data.model.Audio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AudioRepository(private val context: Context) {

    private val playlistFile = File(context.filesDir, "playlist.json")

    /**
     * 1. 从系统 MediaStore 扫描音频 (原本的逻辑)
     */
    suspend fun scanLocalAudio(): List<Audio> = withContext(Dispatchers.IO) {
        val audioList = mutableListOf<Audio>()

        // 定义查询列
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATE_ADDED // 加一个添加时间
        )

        // 过滤条件：必须是音乐，且大于10秒
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 10000"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
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

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

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
                            lyricUri = null // 扫描出来的歌暂时没有歌词
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("AudioRepository", "Error scanning MediaStore", e)
        }

        Log.d("AudioRepository", "扫描完成，发现 ${audioList.size} 首歌曲")
        return@withContext audioList
    }

    /**
     * 2. 保存列表到本地 JSON
     */
    suspend fun savePlaylist(playlist: List<Audio>) = withContext(Dispatchers.IO) {
        try {
            val jsonArray = JSONArray()
            playlist.forEach { audio ->
                val jsonObj = JSONObject().apply {
                    put("id", audio.id)
                    put("uri", audio.uri.toString())
                    put("title", audio.title)
                    put("artist", audio.artist)
                    put("duration", audio.duration)
                    put("albumId", audio.albumId)
                    // 如果 albumArtUri 为空，存空字符串
                    put("albumArtUri", audio.albumArtUri?.toString() ?: "")
                    put("lyricUri", audio.lyricUri?.toString() ?: "")
                }
                jsonArray.put(jsonObj)
            }
            playlistFile.writeText(jsonArray.toString())
            Log.d("AudioRepository", "列表已保存，共 ${playlist.size} 首")
        } catch (e: Exception) {
            Log.e("AudioRepository", "Save failed", e)
        }
    }

    /**
     * 3. 从本地 JSON 读取列表
     */
    suspend fun loadPlaylistFromFile(): List<Audio> = withContext(Dispatchers.IO) {
        if (!playlistFile.exists()) {
            Log.d("AudioRepository", "本地文件不存在，返回空列表")
            return@withContext emptyList<Audio>()
        }

        val list = mutableListOf<Audio>()
        try {
            val jsonStr = playlistFile.readText()
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)

                val uriStr = obj.getString("uri")
                val artStr = obj.optString("albumArtUri")
                val lrcStr = obj.optString("lyricUri")

                list.add(Audio(
                    id = obj.getLong("id"),
                    uri = Uri.parse(uriStr),
                    title = obj.getString("title"),
                    artist = obj.getString("artist"),
                    duration = obj.getLong("duration"),
                    albumId = obj.getLong("albumId"),
                    albumArtUri = if (artStr.isNotEmpty()) Uri.parse(artStr) else null,
                    lyricUri = if (lrcStr.isNotEmpty()) Uri.parse(lrcStr) else null
                ))
            }
            Log.d("AudioRepository", "从文件加载了 ${list.size} 首歌曲")
        } catch (e: Exception) {
            Log.e("AudioRepository", "Load failed", e)
        }
        return@withContext list
    }
}
