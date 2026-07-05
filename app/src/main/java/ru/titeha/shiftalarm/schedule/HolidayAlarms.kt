package ru.titeha.shiftalarm.schedule

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Полярность будильника относительно производственного календаря — на какие дни он «нацелен».
 *
 *  - [WORK] — буди по РАБОЧИМ дням (смены, 5/6-дневка): в нерабочие (праздники/выходные/переносы)
 *    звонок глушится.
 *  - [REST] — буди по ВЫХОДНЫМ (чтобы не залёживаться): звонит в нерабочие дни, включая праздники
 *    и перенесённые выходные, и молчит в рабочие (в т.ч. в перенесённую рабочую субботу).
 */
enum class AlarmPolarity { WORK, REST }

/**
 * Расчёт «простого» будильника (одно время в сутки), привязанного к производственному календарю по
 * [AlarmPolarity]. Чистая логика (без Android/I-O). Для «воскресных» будильников и 5/6-дневки,
 * которые должны следовать праздникам и переносам, а не жёстко дням недели.
 *
 * Сменные графики резолвит [ShiftEngine]; здесь — только календарь + полярность.
 */
object HolidayAlarms {

  /** Звонит ли будильник в день [date] при полярности [polarity] и календаре [calendar]. */
  fun firesOn(date: LocalDate, polarity: AlarmPolarity, calendar: ProductionCalendar): Boolean =
    when (polarity) {
      AlarmPolarity.WORK -> calendar.isWorking(date)
      AlarmPolarity.REST -> calendar.isNonWorking(date)
    }

  /**
   * Ближайший звонок времени [time] строго после [from] по полярности [polarity] и календарю
   * [calendar]. Перебирает дни до [searchDays]; null — за горизонт подходящих дней нет.
   */
  fun next(
    from: LocalDateTime,
    time: LocalTime,
    polarity: AlarmPolarity,
    calendar: ProductionCalendar,
    searchDays: Int = 366
  ): LocalDateTime? {
    var date = from.toLocalDate()
    repeat(searchDays) {
      if (firesOn(date, polarity, calendar)) {
        val candidate = date.atTime(time)
        if (candidate.isAfter(from)) return candidate
      }
      date = date.plusDays(1)
    }
    return null
  }
}
