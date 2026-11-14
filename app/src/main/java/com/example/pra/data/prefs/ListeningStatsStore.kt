package com.example.pra.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.statsDataStore by preferencesDataStore(name = "listening_stats")

/**
 * 收听统计：每首歌的累计播放次数 + 首页排行显示条数。
 * 存储格式极简 ("id=count;id=count"), 不引 JSON 依赖。
 */
class ListeningStatsStore(private val context: Context) {

    private val countsKey = stringPreferencesKey("play_counts")
    private val limitKey = intPreferencesKey("rank_limit")

    val counts: Flow<Map<Long, Int>> = context.statsDataStore.data.map { data ->
        decode(data[countsKey].orEmpty())
    }

    val rankLimit: Flow<Int> = context.statsDataStore.data.map { data ->
        data[limitKey] ?: DEFAULT_LIMIT
    }

    suspend fun increment(songId: Long) {
        if (songId <= 0L) return
        context.statsDataStore.edit { data ->
            val map = decode(data[countsKey].orEmpty()).toMutableMap()
            map[songId] = (map[songId] ?: 0) + 1
            data[countsKey] = encode(map)
        }
    }

    suspend fun setRankLimit(limit: Int) {
        context.statsDataStore.edit { data ->
            data[limitKey] = limit.coerceIn(MIN_LIMIT, MAX_LIMIT)
        }
    }

    private fun decode(raw: String): Map<Long, Int> {
        if (raw.isBlank()) return emptyMap()
        return raw.split(';').mapNotNull { entry ->
            val parts = entry.split('=')
            val id = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            val count = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            if (id <= 0L || count <= 0) null else id to count
        }.toMap()
    }

    private fun encode(map: Map<Long, Int>): String =
        map.entries.joinToString(";") { "${it.key}=${it.value}" }

    companion object {
        const val DEFAULT_LIMIT = 10
        const val MIN_LIMIT = 5
        const val MAX_LIMIT = 50
        val LIMIT_CHOICES = listOf(5, 10, 20, 50)
    }
}
