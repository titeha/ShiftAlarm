package ru.titeha.shiftalarm.schedule

import ru.titeha.shiftalarm.data.AlarmEntity
import java.time.DayOfWeek
import java.util.Calendar

/**
 * Чистый (без Android) перевод параметров системного действия `ACTION_SET_ALARM`
 * (постановка будильника Ассистентом/сторонним приложением) в запись [AlarmEntity].
 *
 * Логика вынесена за шов, чтобы покрыть её юнит-тестами без Intent и БД. Android-обвязка
 * (чтение extras, сохранение, планирование) — тонкая и живёт в activity-обработчике.
 */
object SetAlarmSpec {

  /**
   * @param hour час срабатывания; null — часа в интенте нет (вызывающий открывает редактор),
   *   поэтому возвращаем null — создавать нечего.
   * @param minute минуты (0, если не заданы).
   * @param message подпись будильника.
   * @param calendarDays дни повтора в константах [java.util.Calendar] (ВС=1 … СБ=7), как их
   *   передаёт `ACTION_SET_ALARM`; пустой список — разовый будильник (маска 0). Неизвестные
   *   значения игнорируются.
   * @return готовый включённый будильник режима «по дням недели» или null, если часа нет.
   */
  fun toAlarm(hour: Int?, minute: Int, message: String, calendarDays: List<Int>): AlarmEntity? {
    if (hour == null) return null
    val mask = calendarDays.mapNotNull(::dayOfWeekOf).fold(0) { acc, d -> acc or AlarmTimes.bitOf(d) }
    return AlarmEntity(
      label = message,
      hour = hour,
      minute = minute,
      mode = AlarmEntity.MODE_WEEKLY,
      daysMask = mask,
      enabled = true
    )
  }

  /** День недели из константы [java.util.Calendar]; null — значение вне диапазона ВС..СБ. */
  private fun dayOfWeekOf(calendarDay: Int): DayOfWeek? = when (calendarDay) {
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
