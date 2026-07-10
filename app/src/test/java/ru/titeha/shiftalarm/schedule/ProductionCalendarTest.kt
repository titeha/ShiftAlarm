package ru.titeha.shiftalarm.schedule

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ProductionCalendarTest {

  @After
  fun resetSource() {
    ProductionCalendars.source = null // не протекать между тестами (глобальный источник)
  }

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
  fun fromIsDayOff_parsesHolidaysAndWorkingWeekends() {
    // Строка на 2026 (не високосный, 365): выходные=1, будни=0; затем правки.
    val chars = CharArray(365)
    var date = LocalDate.of(2026, 1, 1)
    for (i in 0 until 365) {
      val weekend = date.dayOfWeek == java.time.DayOfWeek.SATURDAY ||
        date.dayOfWeek == java.time.DayOfWeek.SUNDAY
      chars[i] = if (weekend) '1' else '0'
      date = date.plusDays(1)
    }
    // 1 января (Чт) — праздник (нерабочий будень).
    chars[LocalDate.of(2026, 1, 1).dayOfYear - 1] = '1'
    // 3 января (Сб) — рабочая суббота (перенос).
    chars[LocalDate.of(2026, 1, 3).dayOfYear - 1] = '0'

    val cal = ProductionCalendars.fromIsDayOff(2026, String(chars))

    assertTrue(cal.isNonWorking(LocalDate.of(2026, 1, 1)))    // праздник-будень
    assertTrue(cal.isWorking(LocalDate.of(2026, 1, 3)))       // рабочая суббота
    assertTrue(cal.isNonWorking(LocalDate.of(2026, 1, 4)))    // обычное воскресенье
    assertTrue(cal.isWorking(LocalDate.of(2026, 1, 2)))       // обычная пятница
  }

  @Test(expected = IllegalArgumentException::class)
  fun fromIsDayOff_rejectsWrongLength() {
    ProductionCalendars.fromIsDayOff(2026, "010101") // не 365 цифр
  }

  @Test
  fun resolve_withoutSource_fallsBackToBundled() {
    assertSame(ProductionCalendars.RU_2026, ProductionCalendars.resolve("RU", 2026))
    assertNull(ProductionCalendars.resolve("US", 2026)) // данных нет
  }

  @Test
  fun resolve_prefersSource_thenFallsBack() {
    val fromSource = ProductionCalendar(holidays = setOf(LocalDate.of(2026, 7, 6)))
    ProductionCalendars.source = { country, year ->
      if (country == "RU" && year == 2026) fromSource else null
    }
    // Источник знает RU/2026 → берём его.
    assertSame(fromSource, ProductionCalendars.resolve("RU", 2026))
    // Источник не знает другой год → откат на встроенные (bundled даёт RU_2026 как последний набор).
    assertSame(ProductionCalendars.RU_2026, ProductionCalendars.resolve("RU", 2030))
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
