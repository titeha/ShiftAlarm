package ru.titeha.shiftalarm.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "alarm")

/** Состояние единственного будильника. */
data class AlarmState(
  val hour: Int = 7,
  val minute: Int = 0,
  val enabled: Boolean = false
)

/**
 * Хранилище будильника на DataStore (Preferences) — лёгкая типизированная замена SharedPreferences.
 * Когда появятся несколько будильников и графики смен — перейдём на Room.
 */
class AlarmStore(private val context: Context) {

  val state: Flow<AlarmState> = context.dataStore.data.map { p ->
    AlarmState(
      hour = p[KEY_HOUR] ?: 7,
      minute = p[KEY_MINUTE] ?: 0,
      enabled = p[KEY_ENABLED] ?: false
    )
  }

  suspend fun save(hour: Int, minute: Int, enabled: Boolean) {
    context.dataStore.edit { p ->
      p[KEY_HOUR] = hour
      p[KEY_MINUTE] = minute
      p[KEY_ENABLED] = enabled
    }
  }

  companion object {
    private val KEY_HOUR = intPreferencesKey("hour")
    private val KEY_MINUTE = intPreferencesKey("minute")
    private val KEY_ENABLED = booleanPreferencesKey("enabled")
  }
}
