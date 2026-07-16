package ru.titeha.shiftalarm.data

import android.content.Context
import ru.titeha.shiftalarm.alarm.DismissMode
import ru.titeha.shiftalarm.alarm.RingConfig
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

  /**
   * Показывали ли уже контекстный запрос разрешения на уведомления. Спрашиваем один раз (при
   * создании/включении будильника); дальше путь в настройки даёт баннер готовности.
   */
  fun notificationPromptDone(): Boolean = prefs.getBoolean(KEY_NOTIF_PROMPT, false)

  fun setNotificationPromptDone() = prefs.edit().putBoolean(KEY_NOTIF_PROMPT, true).apply()

  /** Скрыта ли на списке подсказка «Настроить телефон» (вендорский автозапуск). */
  fun vendorSetupDismissed(): Boolean = prefs.getBoolean(KEY_VENDOR_SETUP_DISMISSED, false)

  fun setVendorSetupDismissed() = prefs.edit().putBoolean(KEY_VENDOR_SETUP_DISMISSED, true).apply()

  /**
   * Настройки звонка (раздел «Звонок»): длительность до авто-перезвона T, интервал снуза, лимит M
   * (0 = снуз выключен), тумблер авто-перезвона. Значения зажимаются в допустимые диапазоны.
   */
  fun ringConfig(): RingConfig {
    val d = RingConfig()
    return RingConfig(
      ringDurationMinutes = prefs.getInt(KEY_RING_DURATION, d.ringDurationMinutes).coerceIn(1, 30),
      snoozeIntervalMinutes = prefs.getInt(KEY_SNOOZE_INTERVAL, d.snoozeIntervalMinutes).coerceIn(1, 30),
      maxSnoozes = prefs.getInt(KEY_MAX_SNOOZES, d.maxSnoozes).coerceIn(0, 10),
      autoRepeatEnabled = prefs.getBoolean(KEY_AUTO_REPEAT, d.autoRepeatEnabled),
    )
  }

  fun setRingConfig(config: RingConfig) = prefs.edit()
    .putInt(KEY_RING_DURATION, config.ringDurationMinutes)
    .putInt(KEY_SNOOZE_INTERVAL, config.snoozeIntervalMinutes)
    .putInt(KEY_MAX_SNOOZES, config.maxSnoozes)
    .putBoolean(KEY_AUTO_REPEAT, config.autoRepeatEnabled)
    .apply()

  /** Способ выключения звонка (жёсткий режим). По умолчанию обычное «Стоп». */
  fun dismissMode(): DismissMode = runCatching {
    DismissMode.valueOf(prefs.getString(KEY_DISMISS_MODE, DismissMode.NORMAL.name)!!)
  }.getOrDefault(DismissMode.NORMAL)

  fun setDismissMode(mode: DismissMode) =
    prefs.edit().putString(KEY_DISMISS_MODE, mode.name).apply()

  private companion object {
    const val PREFS = "app_settings"
    const val KEY_THEME = "theme_mode"
    const val KEY_DYNAMIC = "dynamic_color"
    const val KEY_NOTIF_PROMPT = "notification_prompt_done"
    const val KEY_VENDOR_SETUP_DISMISSED = "vendor_setup_dismissed"
    const val KEY_RING_DURATION = "ring_duration_minutes"
    const val KEY_SNOOZE_INTERVAL = "snooze_interval_minutes"
    const val KEY_MAX_SNOOZES = "max_snoozes"
    const val KEY_AUTO_REPEAT = "auto_repeat_enabled"
    const val KEY_DISMISS_MODE = "dismiss_mode"
  }
}
