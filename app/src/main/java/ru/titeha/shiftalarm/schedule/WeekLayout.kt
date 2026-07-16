package ru.titeha.shiftalarm.schedule

import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * С какого дня начинается неделя — ТОЛЬКО для отображения (порядок колонок календаря и чипов дней в
 * редакторе). В движок не проникает: расчёт звонка работает с абсолютными датами.
 */
enum class WeekStart { AUTO, MONDAY, SUNDAY, SATURDAY }

/** Семь дней недели по порядку начиная с [start]. */
fun orderedDaysOfWeek(start: DayOfWeek): List<DayOfWeek> =
    (0 until 7).map { start.plus(it.toLong()) }

/** Фактический день начала недели: [WeekStart.AUTO] берётся из локали ([WeekFields]). */
fun WeekStart.resolve(locale: Locale): DayOfWeek = when (this) {
    WeekStart.AUTO -> WeekFields.of(locale).firstDayOfWeek
    WeekStart.MONDAY -> DayOfWeek.MONDAY
    WeekStart.SUNDAY -> DayOfWeek.SUNDAY
    WeekStart.SATURDAY -> DayOfWeek.SATURDAY
}
