package ru.titeha.shiftalarm.data

import android.content.Context
import ru.titeha.shiftalarm.schedule.WorkWeek
import ru.titeha.shiftalarm.ui.theme.ThemeMode
import java.time.DayOfWeek

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
   * Рабочая неделя (сколько дней рабочих + с какого дня неделя начинается). Глобально: задаёт базовый
   * выходной для производственного календаря (см. [ProductionCalendars.workWeek]). По умолчанию 5/Пн.
   * Битые/старые значения тихо откатываются к дефолту.
   */
  fun workWeek(): WorkWeek = runCatching {
    val days = prefs.getInt(KEY_WORK_DAYS, WorkWeek.DEFAULT.workDays)
    val start = DayOfWeek.valueOf(
      prefs.getString(KEY_WEEK_START, WorkWeek.DEFAULT.weekStart.name)!!
    )
    WorkWeek(days, start)
  }.getOrDefault(WorkWeek.DEFAULT)

  fun setWorkWeek(week: WorkWeek) = prefs.edit()
    .putInt(KEY_WORK_DAYS, week.workDays)
    .putString(KEY_WEEK_START, week.weekStart.name)
    .apply()

  private companion object {
    const val PREFS = "app_settings"
    const val KEY_THEME = "theme_mode"
    const val KEY_DYNAMIC = "dynamic_color"
    const val KEY_NOTIF_PROMPT = "notification_prompt_done"
    const val KEY_VENDOR_SETUP_DISMISSED = "vendor_setup_dismissed"
    const val KEY_WORK_DAYS = "work_week_days"
    const val KEY_WEEK_START = "work_week_start"
  }
}
