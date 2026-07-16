package ru.titeha.shiftalarm.schedule

import java.time.LocalDate

/** Официальный статус календарного дня по производственному календарю. */
enum class DayStatus { WORKING, NONWORKING }

/**
 * Производственный календарь: официальный статус каждого дня с учётом праздников и переносов
 * выходных. Чистая логика (без Android/I-O); данные по стране/году — в [ProductionCalendars].
 *
 * Базовый статус дня недели задаёт [workWeek] (по умолчанию [WorkWeek.DEFAULT] = пятидневка с
 * понедельника → выходные Сб/Вс, как было захардкожено). [holidays] добавляет нерабочие дни
 * (праздники и перенесённые на будни выходные). [workingWeekends] делает отдельные выходные-по-неделе
 * рабочими (переносы, когда выходной «отрабатывают» в субботу). Приоритет: перенос в рабочий день >
 * праздник > базовый выходной рабочей недели.
 *
 * Модель НЕ привязана к режиму будильника: она лишь отвечает «рабочий/нерабочий день». Как это
 * влияет на звонок — задаёт «полярность» будильника (буди по рабочим / по выходным); на первом
 * этапе движок использует только «глушить рабочий звонок в нерабочие дни» ([ShiftEngine.nextAlarm]).
 */
class ProductionCalendar(
  val holidays: Set<LocalDate> = emptySet(),
  val workingWeekends: Set<LocalDate> = emptySet(),
  val workWeek: WorkWeek = WorkWeek.DEFAULT
) {

  fun isNonWorking(date: LocalDate): Boolean = when {
    date in workingWeekends -> false                 // перенос: рабочий выходной-по-неделе
    date in holidays -> true                         // праздник / перенесённый на будень выходной
    workWeek.isWeekend(date.dayOfWeek) -> true       // базовый выходной рабочей недели
    else -> false
  }

  fun isWorking(date: LocalDate): Boolean = !isNonWorking(date)

  fun status(date: LocalDate): DayStatus =
    if (isNonWorking(date)) DayStatus.NONWORKING else DayStatus.WORKING

  /** Тот же календарь (праздники/переносы), но с другой рабочей неделей (если та же — этот же объект). */
  fun withWorkWeek(workWeek: WorkWeek): ProductionCalendar =
    if (this.workWeek == workWeek) this
    else ProductionCalendar(holidays, workingWeekends, workWeek)
}
