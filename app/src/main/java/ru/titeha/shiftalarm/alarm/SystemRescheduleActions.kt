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
   * Ранняя стадия загрузки — устройство ещё залочено (Direct Boot), Room недоступна. Обрабатывается
   * ОТДЕЛЬНО: слепое перевыставление из device-protected кэша, без полного пересчёта из базы.
   */
  const val LOCKED_BOOT_COMPLETED = "android.intent.action.LOCKED_BOOT_COMPLETED"

  /** Пользователь разблокировал устройство: Room стала доступна — можно сделать полный пересчёт. */
  const val USER_UNLOCKED = "android.intent.action.USER_UNLOCKED"

  /**
   * Изменение разрешения на точные будильники (Android 12+). При выдаче разрешения нужно
   * перепланировать: система могла отменить неточные/отклонённые срабатывания, пока его не было.
   */
  const val EXACT_ALARM_PERMISSION_CHANGED =
    "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"

  /**
   * true, если событие требует ПОЛНОГО перепланирования из базы (устройство разблокировано).
   * `LOCKED_BOOT_COMPLETED` сюда НЕ входит — он обрабатывается слепым перевыставлением ([isLockedBoot]).
   */
  fun shouldReschedule(action: String?): Boolean {
    return action == BOOT_COMPLETED ||
            action == MY_PACKAGE_REPLACED ||
            action == TIME_CHANGED ||
            action == TIMEZONE_CHANGED ||
            action == USER_UNLOCKED ||
            action == EXACT_ALARM_PERMISSION_CHANGED
  }

  /** true — ранняя загрузка залоченного устройства: перевыставить из кэша, без обращения к Room. */
  fun isLockedBoot(action: String?): Boolean = action == LOCKED_BOOT_COMPLETED

  /**
   * Человекочитаемое описание причины для логов.
   */
  fun reasonOf(action: String?): String {
    return when (action) {
      BOOT_COMPLETED -> "перезагрузка устройства"
      MY_PACKAGE_REPLACED -> "обновление приложения"
      TIME_CHANGED -> "изменение системного времени"
      TIMEZONE_CHANGED -> "изменение часового пояса"
      USER_UNLOCKED -> "разблокировка устройства"
      EXACT_ALARM_PERMISSION_CHANGED -> "изменение разрешения точных будильников"
      LOCKED_BOOT_COMPLETED -> "ранняя загрузка (устройство залочено)"
      else -> "неизвестное событие"
    }
  }
}