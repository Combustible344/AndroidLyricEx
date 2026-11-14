package com.example.pra.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.pra.data.gesture.GestureBinding
import com.example.pra.data.gesture.Gestures
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.gestureDataStore by preferencesDataStore(name = "gesture_prefs")

/** 伪息屏手势绑定 (DataStore)。首次启动写入默认绑定。 */
class GestureSettingsStore(private val context: Context) {

    private val key = stringPreferencesKey("bindings")

    val bindings: Flow<List<GestureBinding>> = context.gestureDataStore.data.map { data ->
        val raw = data[key]
        if (raw.isNullOrBlank()) Gestures.defaults else Gestures.decode(raw)
    }

    suspend fun save(bindings: List<GestureBinding>) {
        val normalized = Gestures.dedupeKeepLast(bindings)
        context.gestureDataStore.edit { data ->
            data[key] = Gestures.encode(normalized)
        }
    }

    suspend fun reset() = save(Gestures.defaults)
}
