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
  fun kindOf_derivedFromBooleanSets() {
    val holiday = LocalDate.of(2026, 6, 12)
    val workSat = LocalDate.of(2026, 7, 4)
    val cal = ProductionCalendar(holidays = setOf(holiday), workingWeekends = setOf(workSat))
    assertEquals(StateDayKind.HOLIDAY, cal.kindOf(holiday))
    assertEquals(StateDayKind.TRANSFER_WORK, cal.kindOf(workSat))
    assertEquals(StateDayKind.NONE, cal.kindOf(LocalDate.of(2026, 7, 6))) // обычный понедельник
  }

  @Test
  fun explicitKinds_takePriority_andPreHolidayIsWorking() {
    val pre = LocalDate.of(2026, 4, 30)   // сокращённый предпраздничный (Чт)
    val cal = ProductionCalendar(kinds = mapOf(pre to StateDayKind.PRE_HOLIDAY))
    assertEquals(StateDayKind.PRE_HOLIDAY, cal.kindOf(pre))
    assertTrue(cal.isWorking(pre))        // сокращённый — всё же рабочий
  }

  @Test
  fun transferOff_isNonWorking() {
    val transferOff = LocalDate.of(2026, 3, 9) // перенос выходного (Пн)
    val cal = ProductionCalendar(kinds = mapOf(transferOff to StateDayKind.TRANSFER_OFF))
    assertTrue(cal.isNonWorking(transferOff))
  }

  @Test
  fun ru2026_typedKinds() {
    val cal = ProductionCalendars.RU_2026
    assertEquals(StateDayKind.HOLIDAY, cal.kindOf(LocalDate.of(2026, 1, 1)))      // Новый год
    assertEquals(StateDayKind.HOLIDAY, cal.kindOf(LocalDate.of(2026, 6, 12)))     // День России
    assertEquals(StateDayKind.TRANSFER_OFF, cal.kindOf(LocalDate.of(2026, 3, 9))) // перенос с 8 марта
    assertEquals(StateDayKind.TRANSFER_OFF, cal.kindOf(LocalDate.of(2026, 5, 11)))// перенос с 9 мая
    assertEquals(StateDayKind.NONE, cal.kindOf(LocalDate.of(2026, 7, 6)))         // обычный день
    // Поведение (isNonWorking) прежнее.
    assertTrue(cal.isNonWorking(LocalDate.of(2026, 3, 9)))
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
    // Типы: нерабочий будень → HOLIDAY, рабочая суббота → TRANSFER_WORK.
    assertEquals(StateDayKind.HOLIDAY, cal.kindOf(LocalDate.of(2026, 1, 1)))
    assertEquals(StateDayKind.TRANSFER_WORK, cal.kindOf(LocalDate.of(2026, 1, 3)))
  }

  @Test
  fun fromIsDayOff_typesPreHoliday() {
    // Строка на 2026: всё рабочее (0), кроме одного сокращённого предпраздничного дня (код 2).
    val chars = CharArray(365) { '0' }
    val preHoliday = LocalDate.of(2026, 4, 30) // Чт
    chars[preHoliday.dayOfYear - 1] = '2'
    val cal = ProductionCalendars.fromIsDayOff(2026, String(chars))
    assertEquals(StateDayKind.PRE_HOLIDAY, cal.kindOf(preHoliday))
    assertTrue(cal.isWorking(preHoliday)) // сокращённый — рабочий
  }

  @Test(expected = IllegalArgumentException::class)
  fun fromIsDayOff_rejectsWrongLength() {
    ProductionCalendars.fromIsDayOff(2026, "010101") // не 365 цифр
  }

  @Test(expected = IllegalArgumentException::class)
  fun fromIsDayOff_rejectsInvalidChars() {
    // Правильная длина (365), но недопустимые символы (код ошибки/мусор) — отвергаем.
    ProductionCalendars.fromIsDayOff(2026, "9".repeat(365))
  }

  @Test
  fun resolve_withoutSource_usesBuiltInYear_elseNull() {
    assertSame(ProductionCalendars.RU_2026, ProductionCalendars.resolve("RU", 2026))
    assertNull(ProductionCalendars.resolve("US", 2026)) // страны нет
    assertNull(ProductionCalendars.resolve("RU", 2030)) // года нет — НЕ подставляем чужой год
  }

  @Test
  fun resolve_prefersSource_forItsYearOnly() {
    val fromSource = ProductionCalendar(holidays = setOf(LocalDate.of(2026, 7, 6)))
    ProductionCalendars.source = { country, year ->
      if (country == "RU" && year == 2026) fromSource else null
    }
    assertSame(fromSource, ProductionCalendars.resolve("RU", 2026)) // источник знает 2026
    assertNull(ProductionCalendars.resolve("RU", 2030)) // источник не знает → null (не встроенный чужой год)
  }

  @Test
  fun merged_unionsHolidaysOfBothYears() {
    // Источник даёт разные праздники на 2026 и 2027 — merged покрывает оба.
    ProductionCalendars.source = { country, year ->
      when (year) {
        2026 -> ProductionCalendar(holidays = setOf(LocalDate.of(2026, 12, 31)))
        2027 -> ProductionCalendar(holidays = setOf(LocalDate.of(2027, 1, 1)))
        else -> null
      }
    }
    val merged = ProductionCalendars.merged("RU", 2026)!!
    assertTrue(merged.isNonWorking(LocalDate.of(2026, 12, 31))) // из 2026
    assertTrue(merged.isNonWorking(LocalDate.of(2027, 1, 1)))   // из 2027 (граница года)
  }

  @Test
  fun wakeTimeOn_withCalendar_hidesRingOnHoliday() {
    val anchor = LocalDate.of(2026, 6, 10)
    val everyDay = ShiftSchedule(
      ShiftPattern(listOf(ShiftType("w", "Работа", LocalTime.of(7, 0))), anchor)
    )
    val holiday = LocalDate.of(2026, 6, 12)
    val cal = ProductionCalendar(holidays = setOf(holiday))

    // Без календаря звонок есть в любой день.
    assertEquals(LocalTime.of(7, 0), ShiftEngine.wakeTimeOn(holiday, everyDay, null))
    // С календарём в праздник звонок «скрыт» (совпадает с реальным планировщиком).
    assertNull(ShiftEngine.wakeTimeOn(holiday, everyDay, cal))
    // В обычный рабочий день звонок остаётся.
    assertEquals(LocalTime.of(7, 0), ShiftEngine.wakeTimeOn(LocalDate.of(2026, 6, 15), everyDay, cal))
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
