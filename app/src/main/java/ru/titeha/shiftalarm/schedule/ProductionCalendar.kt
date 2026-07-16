package ru.titeha.shiftalarm.schedule

import java.time.DayOfWeek
import java.time.LocalDate

/** Официальный статус календарного дня по производственному календарю. */
enum class DayStatus { WORKING, NONWORKING }

/**
 * Производственный календарь: официальный статус каждого дня с учётом праздников и переносов
 * выходных. Чистая логика (без Android/I-O); данные по стране/году — в [ProductionCalendars].
 *
 * По умолчанию суббота и воскресенье — нерабочие. [holidays] добавляет нерабочие дни (праздники и
 * перенесённые на будни выходные). [workingWeekends] делает отдельные Сб/Вс рабочими (переносы,
 * когда выходной «отрабатывают» в субботу). Приоритет: перенос в рабочую субботу > праздник >
 * обычные Сб/Вс.
 *
 * Модель НЕ привязана к режиму будильника: она лишь отвечает «рабочий/нерабочий день». Как это
 * влияет на звонок — задаёт «полярность» будильника (буди по рабочим / по выходным); на первом
 * этапе движок использует только «глушить рабочий звонок в нерабочие дни» ([ShiftEngine.nextAlarm]).
 */
class ProductionCalendar(
  val holidays: Set<LocalDate> = emptySet(),
  val workingWeekends: Set<LocalDate> = emptySet()
) {

  fun isNonWorking(date: LocalDate): Boolean = when {
    date in workingWeekends -> false                 // перенос: рабочая суббота/воскресенье
    date in holidays -> true                         // праздник / перенесённый на будень выходной
    date.dayOfWeek == DayOfWeek.SATURDAY -> true
    date.dayOfWeek == DayOfWeek.SUNDAY -> true
    else -> false
  }

  fun isWorking(date: LocalDate): Boolean = !isNonWorking(date)

  fun status(date: LocalDate): DayStatus =
    if (isNonWorking(date)) DayStatus.NONWORKING else DayStatus.WORKING
}
