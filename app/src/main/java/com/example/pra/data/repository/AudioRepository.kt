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

/**
 * 简单的偏好数据类
 */
data class PlayerPrefs(
    val currentSongId: Long = -1L,
    val currentPosition: Long = 0L,
    val playModeName: String = "Shuffle",
    val volume: Float = 1.0f
)

class AudioRepository(private val context: Context) {

    private val playlistFile = File(context.filesDir, "playlist.json")
    private val prefsFile = File(context.filesDir, "player_prefs.json")

    /**
     * 1. 扫描系统 MediaStore
     */
    suspend fun scanLocalAudio(): List<Audio> = withContext(Dispatchers.IO) {
        val audioList = mutableListOf<Audio>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )

        // 过滤条件：必须是音乐且时长大于10秒
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

                    // 歌曲文件的实际 Uri
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    // ★★★ 修复核心：根据 Android 版本使用不同的封面 Uri 获取方式 ★★★
                    val albumArtUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        // Android 10 (API 29) 及以上：
                        // 使用 content://media/external/audio/media/{mediaId}/albumart
                        // 这种方式直接关联媒体ID，比旧版基于 albumId 的查询更稳定，Coil 可以自动处理
                        ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/media"),
                            id
                        ).buildUpon().appendPath("albumart").build()
                    } else {
                        // Android 9 及以下：
                        // 使用传统的 content://media/external/audio/albumart/{albumId}
                        ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"),
                            albumId
                        )
                    }

                    audioList.add(
                        Audio(
                            id = id,
                            uri = contentUri,
                            title = title,
                            artist = artist,
                            duration = duration,
                            albumId = albumId,
                            albumArtUri = albumArtUri,
                            lyricUri = null
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("AudioRepository", "Error scanning MediaStore", e)
        }
        return@withContext audioList
    }

    /**
     * 2. 列表持久化
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
                    put("albumArtUri", audio.albumArtUri?.toString() ?: "")
                    put("lyricUri", audio.lyricUri?.toString() ?: "")

                    put("likedCount", audio.likedCount)
                    put("isLiked", audio.isLiked)
                }
                jsonArray.put(jsonObj)
            }
            playlistFile.writeText(jsonArray.toString())
        } catch (e: Exception) {
            Log.e("AudioRepository", "Save playlist failed", e)
        }
    }
    private fun getAlbumArtUri(albumId: Long, songId: Long): Uri {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Android 10+：直接从歌曲 ID 获取封面，这样更准确
            ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/media"),
                songId
            ).buildUpon().appendPath("albumart").build()
        } else {
            // Android 9 及以下：使用旧版 Album ID 拼接
            ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                albumId
            )
        }
    }
    suspend fun loadPlaylistFromFile(): List<Audio> = withContext(Dispatchers.IO) {
        if (!playlistFile.exists()) return@withContext emptyList<Audio>()

        val list = mutableListOf<Audio>()
        try {
            val jsonStr = playlistFile.readText()
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    Audio(
                        id = obj.getLong("id"),
                        uri = Uri.parse(obj.getString("uri")),
                        title = obj.getString("title"),
                        artist = obj.getString("artist"),
                        duration = obj.getLong("duration"),
                        albumId = obj.getLong("albumId"),
                        albumArtUri = if (obj.optString("albumArtUri")
                                .isNotEmpty()
                        ) Uri.parse(obj.getString("albumArtUri")) else null,
                        lyricUri = if (obj.optString("lyricUri")
                                .isNotEmpty()
                        ) Uri.parse(obj.getString("lyricUri")) else null,
                        likedCount = obj.optInt("likedCount", 0),
                        isLiked = obj.optBoolean("isLiked", false)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("AudioRepository", "Load playlist failed", e)
        }
        return@withContext list
    }

    /**
     * 3. 偏好持久化 (进度、模式、音量)
     */
    suspend fun savePlayerPrefs(prefs: PlayerPrefs) = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("songId", prefs.currentSongId)
                put("position", prefs.currentPosition)
                put("mode", prefs.playModeName)
                put("volume", prefs.volume.toDouble())
            }
            prefsFile.writeText(json.toString())
        } catch (e: Exception) {
            Log.e("AudioRepository", "Save prefs failed", e)
        }
    }

    suspend fun loadPlayerPrefs(): PlayerPrefs = withContext(Dispatchers.IO) {
        if (!prefsFile.exists()) return@withContext PlayerPrefs()

        return@withContext try {
            val jsonStr = prefsFile.readText()
            val obj = JSONObject(jsonStr)
            PlayerPrefs(
                currentSongId = obj.optLong("songId", -1L),
                currentPosition = obj.optLong("position", 0L),
                playModeName = obj.optString("mode", "Shuffle"),
                volume = obj.optDouble("volume", 1.0).toFloat()
            )
        } catch (e: Exception) {
            PlayerPrefs()
        }
    }
}
