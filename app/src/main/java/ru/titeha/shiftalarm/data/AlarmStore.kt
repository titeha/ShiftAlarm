package ru.titeha.shiftalarm.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "alarm")

/** Состояние будильника: либо «каждый день в HH:MM», либо по графику смен (пресет + опорная дата). */
data class AlarmState(
  val enabled: Boolean = false,
  val mode: String = MODE_DAILY,
  val hour: Int = 7,
  val minute: Int = 0,
  val presetId: String = "2x2",
  val anchorEpochDay: Long = 0L
) {
  companion object {
    const val MODE_DAILY = "daily"
    const val MODE_SHIFT = "shift"
  }
}

/**
 * Хранилище будильника на DataStore (Preferences).
 * Когда появятся несколько будильников и редактор графиков — перейдём на Room.
 */
class AlarmStore(private val context: Context) {

  val state: Flow<AlarmState> = context.dataStore.data.map { p ->
    AlarmState(
      enabled = p[KEY_ENABLED] ?: false,
      mode = p[KEY_MODE] ?: AlarmState.MODE_DAILY,
      hour = p[KEY_HOUR] ?: 7,
      minute = p[KEY_MINUTE] ?: 0,
      presetId = p[KEY_PRESET] ?: "2x2",
      anchorEpochDay = p[KEY_ANCHOR] ?: 0L
    )
  }

  suspend fun save(state: AlarmState) {
    context.dataStore.edit { p ->
      p[KEY_ENABLED] = state.enabled
      p[KEY_MODE] = state.mode
      p[KEY_HOUR] = state.hour
      p[KEY_MINUTE] = state.minute
      p[KEY_PRESET] = state.presetId
      p[KEY_ANCHOR] = state.anchorEpochDay
    }
  }

  companion object {
    private val KEY_ENABLED = booleanPreferencesKey("enabled")
    private val KEY_MODE = stringPreferencesKey("mode")
    private val KEY_HOUR = intPreferencesKey("hour")
    private val KEY_MINUTE = intPreferencesKey("minute")
    private val KEY_PRESET = stringPreferencesKey("preset")
    private val KEY_ANCHOR = longPreferencesKey("anchor")
  }
}
