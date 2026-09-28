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
 * Кейсы (a)-(d) из T02-verdict: фиксация контракта «REST + непустая маска».
 * Даты — июнь 2026 (12.06 — праздник, 22-28.06 — обычная неделя) и март 2026 (09.03 — перенос).
 */
class AlarmTimesRestPolarityGuardTest {

  @Before fun setUp() { ProductionCalendars.source = null }
  @After fun tearDown() { ProductionCalendars.source = null }

  private fun restWeekly(mask: Int) = AlarmEntity(
    hour = 7, minute = 0,
    mode = AlarmEntity.MODE_CALENDAR,
    daysMask = mask,
    honorHolidays = true,
    polarity = AlarmEntity.POLARITY_REST
  )

  private fun workWeekly(mask: Int) = AlarmEntity(
    hour = 7, minute = 0,
    mode = AlarmEntity.MODE_WEEKLY,
    daysMask = mask,
    honorHolidays = true,
    polarity = AlarmEntity.POLARITY_WORK
  )

  private val allDays = AlarmTimes.maskOf(*DayOfWeek.entries.toTypedArray())
  private val weekdays = AlarmTimes.maskOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
  )

  /** (a) Маска «Все 7 дней» + REST: звонок в субботу, а не в праздник 4 ноября. */
  @Test
  fun `(a) все 7 дней + REST - суббота, а не ближайший праздник`() {
    val next = AlarmTimes.next(restWeekly(allDays), LocalDate.of(2026, 6, 24).atTime(8, 0))
    assertEquals(LocalDate.of(2026, 6, 27).atTime(7, 0), next)   // старый код давал 2026-11-04
  }

  /** (b) REST + рабочая суббота (TRANSFER_WORK): в официальном рабочий субботу НЕ звонит. */
  @Test
  fun `(b) REST и рабочая суббота - не звонит`() {
    val sat = LocalDate.of(2026, 6, 27)
    val cal = ProductionCalendar(kinds = mapOf(sat to StateDayKind.TRANSFER_WORK))
    assertFalse(AlarmTimes.firesOn(restWeekly(AlarmTimes.maskOf(DayOfWeek.SUNDAY)), sat, cal))
  }

  /** (c) REST + перенесённый выходной (TRANSFER_OFF) на масочном дне: звонит. */
  @Test
  fun `(c) REST и перенесённый выходной на масочном дне - звонит`() {
    val mon = LocalDate.of(2026, 3, 9)   // Пн, перенос выходного с 8 марта
    val cal = requireNotNull(ProductionCalendars.merged("RU", 2026))
    assertTrue(AlarmTimes.firesOn(restWeekly(AlarmTimes.maskOf(DayOfWeek.MONDAY)), mon, cal))
  }

  /** (d) WORK-guard: WORK по-прежнему строит неделю строго из маски (Сб/Вс в маске - звонит в Сб/Вс). */
  @Test
  fun `(d) WORK с маской Сб-Вс - звонит по маске, а не по стандартной неделе`() {
    val sat = LocalDate.of(2026, 6, 27)
    val cal = requireNotNull(ProductionCalendars.merged("RU", 2026))
    assertTrue(AlarmTimes.firesOn(workWeekly(AlarmTimes.maskOf(DayOfWeek.SATURDAY)), sat, cal))
    // WORK с маской будней в субботу не звонит
    assertFalse(AlarmTimes.firesOn(workWeekly(weekdays), sat, cal))
  }
}
