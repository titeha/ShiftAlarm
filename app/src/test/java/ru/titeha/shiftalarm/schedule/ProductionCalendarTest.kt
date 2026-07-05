package ru.titeha.shiftalarm.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ProductionCalendarTest {

  @Test
  fun weekend_isNonWorking_byDefault() {
    val cal = ProductionCalendar()
    // 2026-07-04 суббота, 2026-07-05 воскресенье, 2026-07-06 понедельник.
    assertTrue(cal.isNonWorking(LocalDate.of(2026, 7, 4)))
    assertTrue(cal.isNonWorking(LocalDate.of(2026, 7, 5)))
    assertFalse(cal.isNonWorking(LocalDate.of(2026, 7, 6)))
  }

  @Test
  fun holiday_onWeekday_isNonWorking() {
    val cal = ProductionCalendar(holidays = setOf(LocalDate.of(2026, 6, 12)))
    assertTrue(cal.isNonWorking(LocalDate.of(2026, 6, 12)))    // День России (Пт)
    assertEquals(DayStatus.NONWORKING, cal.status(LocalDate.of(2026, 6, 12)))
  }

  @Test
  fun workingWeekend_overridesSaturday() {
    val sat = LocalDate.of(2026, 7, 4)
    val cal = ProductionCalendar(workingWeekends = setOf(sat))
    assertTrue(cal.isWorking(sat))                             // перенос: рабочая суббота
    assertEquals(DayStatus.WORKING, cal.status(sat))
  }

  @Test
  fun workingWeekend_beatsHoliday_onSameDay() {
    val day = LocalDate.of(2026, 7, 4)
    val cal = ProductionCalendar(holidays = setOf(day), workingWeekends = setOf(day))
    assertTrue(cal.isWorking(day))                             // приоритет переноса в рабочий день
  }

  @Test
  fun ru2026_fixedHolidays_areNonWorking() {
    val cal = ProductionCalendars.RU_2026
    assertTrue(cal.isNonWorking(LocalDate.of(2026, 1, 1)))     // Новый год
    assertTrue(cal.isNonWorking(LocalDate.of(2026, 2, 23)))    // 23 февраля
    assertTrue(cal.isNonWorking(LocalDate.of(2026, 5, 1)))     // 1 мая
    assertTrue(cal.isNonWorking(LocalDate.of(2026, 6, 12)))    // День России
    assertTrue(cal.isNonWorking(LocalDate.of(2026, 11, 4)))    // День народного единства
    // Обычный будень — рабочий.
    assertTrue(cal.isWorking(LocalDate.of(2026, 7, 6)))        // понедельник
  }

  @Test
  fun ru2026_lookup_byCountryYear() {
    assertEquals(ProductionCalendars.RU_2026, ProductionCalendars.of("ru", 2026))
    assertNull(ProductionCalendars.of("US", 2026))
    assertNull(ProductionCalendars.of("RU", 2099))
  }

  @Test
  fun nextAlarm_withCalendar_skipsHoliday() {
    // График «звонок каждый день в 7:00» (цикл из одного рабочего слота).
    val anchor = LocalDate.of(2026, 6, 10) // среда
    val everyDay = ShiftSchedule(
      ShiftPattern(listOf(ShiftType("w", "Работа", LocalTime.of(7, 0))), anchor)
    )
    val cal = ProductionCalendar(holidays = setOf(LocalDate.of(2026, 6, 12))) // Пт — праздник
    // Отсчёт с 11 июня 8:00 (сегодняшний звонок прошёл): 12-е — праздник (пропуск),
    // 13-14 — выходные, ближайший рабочий звонок — понедельник 15 июня 7:00.
    val from = LocalDate.of(2026, 6, 11).atTime(8, 0)
    val next = ShiftEngine.nextAlarm(from, everyDay, calendar = cal)
    assertEquals(LocalDate.of(2026, 6, 15).atTime(7, 0), next)
  }

  @Test
  fun nextAlarm_withoutCalendar_unchanged() {
    val anchor = LocalDate.of(2026, 6, 10)
    val everyDay = ShiftSchedule(
      ShiftPattern(listOf(ShiftType("w", "Работа", LocalTime.of(7, 0))), anchor)
    )
    val from = LocalDate.of(2026, 6, 11).atTime(8, 0)
    // Без календаря — ближайший звонок завтра (12-го), праздники не учитываются.
    assertEquals(LocalDate.of(2026, 6, 12).atTime(7, 0), ShiftEngine.nextAlarm(from, everyDay))
  }
}
