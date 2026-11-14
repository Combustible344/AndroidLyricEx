package com.example.pra.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.pra.data.repository.PlayerPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.playerDataStore by preferencesDataStore(name = "player_prefs")

/**
 * 播放状态的持久化（DataStore）。替代原来的 player_prefs.json。
 * 只存"恢复现场"需要的少量标量：曲目 id、进度、模式、音量。
 */
class PlayerPrefsStore(private val context: Context) {

    private object Keys {
        val mode = stringPreferencesKey("play_mode")
        val volume = floatPreferencesKey("volume")
        val songId = longPreferencesKey("last_song_id")
        val position = longPreferencesKey("last_position")
    }

    val prefs: Flow<PlayerPrefs> = context.playerDataStore.data.map { data ->
        PlayerPrefs(
            currentSongId = data[Keys.songId] ?: -1L,
            currentPosition = data[Keys.position] ?: 0L,
            playModeName = data[Keys.mode] ?: "Shuffle",
            volume = data[Keys.volume] ?: 1.0f,
        )
    }

    suspend fun save(prefs: PlayerPrefs) {
        context.playerDataStore.edit { data ->
            data[Keys.songId] = prefs.currentSongId
            data[Keys.position] = prefs.currentPosition
            data[Keys.mode] = prefs.playModeName
            data[Keys.volume] = prefs.volume
        }
    }
}
