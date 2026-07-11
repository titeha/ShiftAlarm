package ru.titeha.shiftalarm.alarm

/**
 * Системные события, после которых нужно перепланировать включённые будильники.
 *
 * Константы вынесены в чистый объект, чтобы правило можно было проверить
 * обычными unit-тестами без Android runtime.
 */
object SystemRescheduleActions {
  const val BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED"
  const val MY_PACKAGE_REPLACED = "android.intent.action.MY_PACKAGE_REPLACED"
  const val TIME_CHANGED = "android.intent.action.TIME_SET"
  const val TIMEZONE_CHANGED = "android.intent.action.TIMEZONE_CHANGED"

  /**
   * Изменение разрешения на точные будильники (Android 12+). При выдаче разрешения нужно
   * перепланировать: система могла отменить неточные/отклонённые срабатывания, пока его не было.
   */
  const val EXACT_ALARM_PERMISSION_CHANGED =
    "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"

  /**
   * true, если событие требует перепланирования будильников.
   */
  fun shouldReschedule(action: String?): Boolean {
    return action == BOOT_COMPLETED ||
            action == MY_PACKAGE_REPLACED ||
            action == TIME_CHANGED ||
            action == TIMEZONE_CHANGED ||
            action == EXACT_ALARM_PERMISSION_CHANGED
  }

  /**
   * Человекочитаемое описание причины для логов.
   */
  fun reasonOf(action: String?): String {
    return when (action) {
      BOOT_COMPLETED -> "перезагрузка устройства"
      MY_PACKAGE_REPLACED -> "обновление приложения"
      TIME_CHANGED -> "изменение системного времени"
      TIMEZONE_CHANGED -> "изменение часового пояса"
      EXACT_ALARM_PERMISSION_CHANGED -> "изменение разрешения точных будильников"
      else -> "неизвестное событие"
    }
  }
}