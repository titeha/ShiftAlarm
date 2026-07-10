package ru.titeha.shiftalarm.alarm

/**
 * Что мешает будильнику надёжно сработать — для показа пользователю. Порядок в перечислении =
 * приоритет: точные будильники критичнее уведомлений, уведомления — энергосбережения.
 */
enum class AlarmReadinessIssue {
  /** Нет разрешения на точные будильники (Android 12+): звонок может не сработать вовремя. */
  EXACT_ALARM,

  /** Нет разрешения на уведомления (Android 13+): экран/уведомление звонка может не показаться. */
  NOTIFICATIONS,

  /** Приложение под ограничением энергосбережения: система может задержать/убить звонок. */
  BATTERY,
}

/**
 * Чистая (без Android) логика: по фактическим статусам разрешений вернуть список проблем.
 * Android-обвязка (чтение реальных статусов, интенты в настройки) — в `AlarmPermissions`.
 */
object AlarmReadiness {

  /**
   * @param canScheduleExact точные будильники разрешены (на Android < 12 всегда true).
   * @param notificationsAllowed уведомления разрешены (на Android < 13 всегда true).
   * @param batteryUnrestricted нет ограничения энергосбережения; null — не проверяем.
   * @return проблемы по приоритету; пустой список — всё готово.
   */
  fun issues(
    canScheduleExact: Boolean,
    notificationsAllowed: Boolean,
    batteryUnrestricted: Boolean?,
  ): List<AlarmReadinessIssue> = buildList {
    if (!canScheduleExact) add(AlarmReadinessIssue.EXACT_ALARM)
    if (!notificationsAllowed) add(AlarmReadinessIssue.NOTIFICATIONS)
    if (batteryUnrestricted == false) add(AlarmReadinessIssue.BATTERY)
  }
}
