package ru.titeha.shiftalarm.data

import android.content.Context
import ru.titeha.shiftalarm.ui.theme.ThemeMode

/**
 * Пользовательские настройки приложения (SharedPreferences). Пока тема; сюда же будут добавляться
 * другие настройки (страна праздников, продление отпуска больничным и т.п.).
 */
class SettingsStore(context: Context) {

  private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun themeMode(): ThemeMode =
    runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)!!) }
      .getOrDefault(ThemeMode.SYSTEM)

  fun setThemeMode(mode: ThemeMode) = prefs.edit().putString(KEY_THEME, mode.name).apply()

  /** Динамические цвета Material You (Android 12+). По умолчанию включены. */
  fun dynamicColor(): Boolean = prefs.getBoolean(KEY_DYNAMIC, true)

  fun setDynamicColor(enabled: Boolean) = prefs.edit().putBoolean(KEY_DYNAMIC, enabled).apply()

  private companion object {
    const val PREFS = "app_settings"
    const val KEY_THEME = "theme_mode"
    const val KEY_DYNAMIC = "dynamic_color"
  }
}
