package ru.titeha.shiftalarm.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class HolidayAlarmsTest {

  // 2026-07-06 Пн (рабочий), 07-04 Сб, 07-05 Вс, 06-12 Пт праздник.
  private val monday = LocalDate.of(2026, 7, 6)
  private val saturday = LocalDate.of(2026, 7, 4)
  private val sunday = LocalDate.of(2026, 7, 5)
  private val holidayFri = LocalDate.of(2026, 6, 12)

  private val plain = ProductionCalendar(holidays = setOf(holidayFri))

  @Test
  fun workPolarity_firesOnWorkday_notOnWeekendOrHoliday() {
    assertTrue(HolidayAlarms.firesOn(monday, AlarmPolarity.WORK, plain))
    assertFalse(HolidayAlarms.firesOn(saturday, AlarmPolarity.WORK, plain))
    assertFalse(HolidayAlarms.firesOn(holidayFri, AlarmPolarity.WORK, plain))
  }

  @Test
  fun restPolarity_firesOnWeekendAndHoliday_notOnWorkday() {
    assertTrue(HolidayAlarms.firesOn(saturday, AlarmPolarity.REST, plain))
    assertTrue(HolidayAlarms.firesOn(sunday, AlarmPolarity.REST, plain))
    assertTrue(HolidayAlarms.firesOn(holidayFri, AlarmPolarity.REST, plain))   // праздник = отдых
    assertFalse(HolidayAlarms.firesOn(monday, AlarmPolarity.REST, plain))
  }

  @Test
  fun restPolarity_transferredWorkingSaturday_isSilent() {
    // Суббота стала рабочей (перенос) → «выходной» будильник молчит.
    val cal = ProductionCalendar(workingWeekends = setOf(saturday))
    assertFalse(HolidayAlarms.firesOn(saturday, AlarmPolarity.REST, cal))
    assertTrue(HolidayAlarms.firesOn(saturday, AlarmPolarity.WORK, cal))
  }

  @Test
  fun restPolarity_transferredDayOff_onMonday_fires() {
    // Понедельник стал выходным (перенос праздника) → «выходной» будильник звонит.
    val cal = ProductionCalendar(holidays = setOf(monday))
    assertTrue(HolidayAlarms.firesOn(monday, AlarmPolarity.REST, cal))
    assertFalse(HolidayAlarms.firesOn(monday, AlarmPolarity.WORK, cal))
  }

  @Test
  fun next_restPolarity_findsWeekend() {
    // Отсчёт с пятницы 06-12 (праздник) 12:00. REST 9:00 → ближайший в субботу 06-13 9:00.
    val from = holidayFri.atTime(12, 0)
    val next = HolidayAlarms.next(from, LocalTime.of(9, 0), AlarmPolarity.REST, plain)
    assertEquals(LocalDate.of(2026, 6, 13).atTime(9, 0), next)
  }

  @Test
  fun next_workPolarity_skipsHolidayAndWeekend() {
    // С четверга 06-11 8:00: 12-е праздник, 13-14 выходные → понедельник 06-15 7:00.
    val from = LocalDate.of(2026, 6, 11).atTime(8, 0)
    val next = HolidayAlarms.next(from, LocalTime.of(7, 0), AlarmPolarity.WORK, plain)
    assertEquals(LocalDate.of(2026, 6, 15).atTime(7, 0), next)
  }
}
