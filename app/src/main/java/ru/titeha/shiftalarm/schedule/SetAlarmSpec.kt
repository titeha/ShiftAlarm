package ru.titeha.shiftalarm.schedule

import ru.titeha.shiftalarm.data.AlarmEntity
import java.time.DayOfWeek
import java.util.Calendar

/**
 * Чистый перевод параметров системного действия `ACTION_SET_ALARM`
 * в запись [AlarmEntity].
 *
 * Данные приходят из внешнего intent, поэтому здесь выполняется базовая валидация:
 * час, минуты, дни повтора и подпись будильника приводятся к безопасному виду.
 *
 * Android-обвязка живёт в activity-обработчике, а эта логика не зависит от Android API
 * и покрывается обычными unit-тестами.
 */
object SetAlarmSpec {
  private const val MAX_LABEL_LENGTH = 120

  /**
   * Создаёт будильник по параметрам `ACTION_SET_ALARM`.
   *
   * @param hour час срабатывания; null означает, что часа в intent нет и создавать нечего.
   * @param minute минуты срабатывания.
   * @param message подпись будильника.
   * @param calendarDays дни повтора в константах [Calendar]: ВС=1 … СБ=7.
   *
   * @return готовый включённый будильник или null, если параметры недостаточны
   * или некорректны.
   */
  fun toAlarm(
    hour: Int?,
    minute: Int,
    message: String,
    calendarDays: List<Int>
  ): AlarmEntity? {
    if (hour == null) return null
    if (hour !in 0..23) return null
    if (minute !in 0..59) return null

    val mask = calendarDays
      .mapNotNull(::dayOfWeekOf)
      .fold(0) { acc, day -> acc or AlarmTimes.bitOf(day) }

    return AlarmEntity(
      label = normalizeLabel(message),
      hour = hour,
      minute = minute,
      mode = AlarmEntity.MODE_WEEKLY,
      daysMask = mask,
      enabled = true
    )
  }

  /**
   * Приводит подпись будильника к безопасному размеру.
   *
   * Пустая строка допустима: пользователь увидит будильник без подписи.
   */
  private fun normalizeLabel(message: String): String {
    return message.trim().take(MAX_LABEL_LENGTH)
  }

  /** День недели из константы [Calendar]; null — значение вне диапазона ВС..СБ. */
  private fun dayOfWeekOf(calendarDay: Int): DayOfWeek? {
    return when (calendarDay) {
      Calendar.MONDAY -> DayOfWeek.MONDAY
      Calendar.TUESDAY -> DayOfWeek.TUESDAY
      Calendar.WEDNESDAY -> DayOfWeek.WEDNESDAY
      Calendar.THURSDAY -> DayOfWeek.THURSDAY
      Calendar.FRIDAY -> DayOfWeek.FRIDAY
      Calendar.SATURDAY -> DayOfWeek.SATURDAY
      Calendar.SUNDAY -> DayOfWeek.SUNDAY
      else -> null
    }
  }
}