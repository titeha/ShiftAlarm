package ru.titeha.shiftalarm.data

import android.content.Context
import ru.titeha.shiftalarm.alarm.DismissMode
import ru.titeha.shiftalarm.alarm.RingConfig
import ru.titeha.shiftalarm.schedule.WeekPairNaming
import ru.titeha.shiftalarm.schedule.WeekStart
import ru.titeha.shiftalarm.ui.theme.ThemeMode

/**
 * Пользовательские настройки приложения (SharedPreferences). Пока тема; сюда же будут добавляться
 * другие настройки (страна праздников, продление отпуска больничным и т.п.).
 */
class SettingsStore(context: Context) {

  private val app = context.applicationContext

  /*
   * CE-хранилище открываем ЛЕНИВО: до разблокировки (Direct Boot) оно недоступно и падает при открытии.
   * Путь звонка снуза конструирует SettingsStore залоченным ради alarmPrefs (DE) — если бы prefs (CE)
   * открывался в конструкторе, сервис звонка падал бы. Ленивое открытие трогает CE только для
   * тема/цвета/и т.п., которые нужны лишь после разблокировки.
   */
  private val prefs by lazy { app.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

  /**
   * Device-protected настройки, которые нужны в пути звонка ДО разблокировки (Direct Boot): параметры
   * снуза/авто-перезвона и способ выключения. CE-хранилище там недоступно и роняет сервис звонка.
   */
  private val alarmPrefs = app.createDeviceProtectedStorageContext()
    .getSharedPreferences(PREFS_ALARM_DE, Context.MODE_PRIVATE)

  fun themeMode(): ThemeMode =
    runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)!!) }
      .getOrDefault(ThemeMode.SYSTEM)

  fun setThemeMode(mode: ThemeMode) = prefs.edit().putString(KEY_THEME, mode.name).apply()

  /**
   * Масштаб шрифта ПОВЕРХ системного (для слабовидящих). 1.0 = как в системе. Множится на системный
   * fontScale, поэтому работает в дополнение к настройке Android «Размер шрифта». Диапазон 0.8..2.0.
   */
  fun fontScale(): Float = prefs.getFloat(KEY_FONT_SCALE, 1.0f).coerceIn(0.8f, 2.0f)

  fun setFontScale(scale: Float) = prefs.edit().putFloat(KEY_FONT_SCALE, scale).apply()

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
      ringDurationMinutes = alarmPrefs.getInt(KEY_RING_DURATION, d.ringDurationMinutes).coerceIn(1, 30),
      snoozeIntervalMinutes = alarmPrefs.getInt(KEY_SNOOZE_INTERVAL, d.snoozeIntervalMinutes).coerceIn(1, 30),
      maxSnoozes = alarmPrefs.getInt(KEY_MAX_SNOOZES, d.maxSnoozes).coerceIn(0, 10),
      autoRepeatEnabled = alarmPrefs.getBoolean(KEY_AUTO_REPEAT, d.autoRepeatEnabled),
    )
  }

  fun setRingConfig(config: RingConfig) = alarmPrefs.edit()
    .putInt(KEY_RING_DURATION, config.ringDurationMinutes)
    .putInt(KEY_SNOOZE_INTERVAL, config.snoozeIntervalMinutes)
    .putInt(KEY_MAX_SNOOZES, config.maxSnoozes)
    .putBoolean(KEY_AUTO_REPEAT, config.autoRepeatEnabled)
    .apply()

  /** Способ выключения звонка (жёсткий режим). По умолчанию обычное «Стоп». */
  fun dismissMode(): DismissMode = runCatching {
    DismissMode.valueOf(alarmPrefs.getString(KEY_DISMISS_MODE, DismissMode.NORMAL.name)!!)
  }.getOrDefault(DismissMode.NORMAL)

  fun setDismissMode(mode: DismissMode) =
    alarmPrefs.edit().putString(KEY_DISMISS_MODE, mode.name).apply()

  /** Начало недели (только отображение). По умолчанию авто (из локали). */
  fun weekStart(): WeekStart = runCatching {
    WeekStart.valueOf(prefs.getString(KEY_WEEK_START, WeekStart.AUTO.name)!!)
  }.getOrDefault(WeekStart.AUTO)

  fun setWeekStart(start: WeekStart) = prefs.edit().putString(KEY_WEEK_START, start.name).apply()

  /** Нейминг пары учебных недель (вуз, чёт/нечёт). По умолчанию «Нечётная/Чётная». */
  fun weekPairNaming(): WeekPairNaming = runCatching {
    WeekPairNaming.valueOf(prefs.getString(KEY_WEEK_PAIR_NAMING, WeekPairNaming.PARITY.name)!!)
  }.getOrDefault(WeekPairNaming.PARITY)

  fun setWeekPairNaming(naming: WeekPairNaming) =
    prefs.edit().putString(KEY_WEEK_PAIR_NAMING, naming.name).apply()

  // ── «Настроение дня» (праздник + фраза дня) ──
  // Флаги и сигнал в DE: сигнал dismissed пишется в момент «Стоп», который может случиться
  // залоченным (Direct Boot); флаг уведомления читается там же. Чисто информационный модуль —
  // на путь звонка не влияет.

  /** Показывать карточку «Настроение дня» после «Стоп». По умолчанию включено. */
  fun dayGreetingCardEnabled(): Boolean = alarmPrefs.getBoolean(KEY_GREETING_CARD, true)

  fun setDayGreetingCardEnabled(enabled: Boolean) =
    alarmPrefs.edit().putBoolean(KEY_GREETING_CARD, enabled).apply()

  /** Показывать уведомление «Настроение дня». По умолчанию выключено. */
  fun dayGreetingNotificationEnabled(): Boolean = alarmPrefs.getBoolean(KEY_GREETING_NOTIF, false)

  fun setDayGreetingNotificationEnabled(enabled: Boolean) =
    alarmPrefs.edit().putBoolean(KEY_GREETING_NOTIF, enabled).apply()

  /** День (epochDay) последнего выключения будильника «Стоп»; -1 если не было. Сигнал для карточки. */
  fun lastDismissedEpochDay(): Long = alarmPrefs.getLong(KEY_LAST_DISMISSED_DAY, -1L)

  /** Только для реального «Стоп» (не снуз/не пропуск): отметить день выключения будильника. */
  fun recordDismissed(epochDay: Long) =
    alarmPrefs.edit().putLong(KEY_LAST_DISMISSED_DAY, epochDay).apply()

  /** День, для которого карточку «Настроение дня» уже показали/закрыли (не навязываем повторно). */
  fun greetingCardHandledEpochDay(): Long = alarmPrefs.getLong(KEY_GREETING_CARD_HANDLED, -1L)

  fun setGreetingCardHandled(epochDay: Long) =
    alarmPrefs.edit().putLong(KEY_GREETING_CARD_HANDLED, epochDay).apply()

  private companion object {
    const val PREFS = "app_settings"
    const val PREFS_ALARM_DE = "alarm_settings_de"
    const val KEY_THEME = "theme_mode"
    const val KEY_DYNAMIC = "dynamic_color"
    const val KEY_FONT_SCALE = "font_scale"
    const val KEY_NOTIF_PROMPT = "notification_prompt_done"
    const val KEY_VENDOR_SETUP_DISMISSED = "vendor_setup_dismissed"
    const val KEY_RING_DURATION = "ring_duration_minutes"
    const val KEY_SNOOZE_INTERVAL = "snooze_interval_minutes"
    const val KEY_MAX_SNOOZES = "max_snoozes"
    const val KEY_AUTO_REPEAT = "auto_repeat_enabled"
    const val KEY_DISMISS_MODE = "dismiss_mode"
    const val KEY_WEEK_START = "week_start"
    const val KEY_WEEK_PAIR_NAMING = "week_pair_naming"
    const val KEY_GREETING_CARD = "day_greeting_card_enabled"
    const val KEY_GREETING_NOTIF = "day_greeting_notif_enabled"
    const val KEY_LAST_DISMISSED_DAY = "day_greeting_last_dismissed_day"
    const val KEY_GREETING_CARD_HANDLED = "day_greeting_card_handled_day"
  }
}
