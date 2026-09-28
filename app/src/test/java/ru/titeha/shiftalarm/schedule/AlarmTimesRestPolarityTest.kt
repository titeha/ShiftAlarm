package ru.titeha.shiftalarm.schedule

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.titeha.shiftalarm.data.AlarmEntity
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Регрессия: weekly + «Учитывать праздники» + «По выходным» должен следовать производственному
 * календарю (Сб, Вс, праздники, перенесённые выходные), а не инверсии отмеченных дней.
 *
 * Даты — июнь 2026 по встроенному календарю РФ: 12.06 (Пт) — праздник День России,
 * 22–28.06 — обычная неделя (Пн…Вс), особых дней нет.
 */
class AlarmTimesRestPolarityTest {

  @Before
  fun setUp() {
    // Другие тесты могут временно подменять источник календаря — нужен детерминированный РФ-2026.
    ProductionCalendars.source = null
  }

  @After
  fun tearDown() {
    ProductionCalendars.source = null
  }

  private val mon = LocalDate.of(2026, 6, 22)
  private val wed = LocalDate.of(2026, 6, 24)
  private val sat = LocalDate.of(2026, 6, 27)
  private val sun = LocalDate.of(2026, 6, 28)

  /** Weekly-будильник 07:00 с маской [mask], учётом праздников и полярностью «По выходным». */
  private fun restWeekly(mask: Int) = AlarmEntity(
    hour = 7,
    minute = 0,
    mode = AlarmEntity.MODE_WEEKLY,
    daysMask = mask,
    honorHolidays = true,
    polarity = AlarmEntity.POLARITY_REST
  )

  private val weekends = AlarmTimes.maskOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
  private val weekdays = AlarmTimes.maskOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
  )

  @Test
  fun `по выходным с маской Сб-Вс — ближайший звонок в субботу, а не в будни`() {
    val next = AlarmTimes.next(restWeekly(weekends), wed.atTime(8, 0))
    assertEquals(sat.atTime(7, 0), next)
  }

  @Test
  fun `по выходным с маской будней — маска не меняет результат`() {
    val next = AlarmTimes.next(restWeekly(weekdays), wed.atTime(8, 0))
    assertEquals(sat.atTime(7, 0), next)
  }

  @Test
  fun `по выходным — праздник среди недели звонит раньше выходных`() {
    val from = LocalDate.of(2026, 6, 11).atTime(8, 0) // четверг
    val next = AlarmTimes.next(restWeekly(weekends), from)
    assertEquals(LocalDate.of(2026, 6, 12).atTime(7, 0), next) // пятница — День России
  }

  @Test
  fun `по выходным — точка календаря на Сб и Вс, не на буднях`() {
    val a = restWeekly(weekends)
    val cal = requireNotNull(ProductionCalendars.merged("RU", 2026))
    assertTrue(AlarmTimes.firesOn(a, sat, cal))
    assertTrue(AlarmTimes.firesOn(a, sun, cal))
    assertFalse(AlarmTimes.firesOn(a, mon, cal))
    assertFalse(AlarmTimes.firesOn(a, wed, cal))
  }
}
