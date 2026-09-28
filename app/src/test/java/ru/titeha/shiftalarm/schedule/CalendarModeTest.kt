package ru.titeha.shiftalarm.schedule

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.titeha.shiftalarm.data.AlarmEntity
import ru.titeha.shiftalarm.data.AlarmPeriod
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Режим `calendar` (TD01, инварианты D-1…D-4): звонит в официальные нерабочие дни
 * (Сб, Вс, праздники, перенесённые выходные), маска не участвует, календарь обязателен,
 * периоды глушат звонок. Чистый слой, без Android/IO.
 *
 * Даты: 26.06.2026 — Пт, 27.06 — Сб, 28.06 — Вс; 04.11.2026 — Ср (HOLIDAY);
 * 09.03.2026 — Пн (TRANSFER_OFF); 2027 — год без встроенных данных.
 */
class CalendarModeTest {

  @Before
  fun setUp() {
    ProductionCalendars.source = null
  }

  @After
  fun tearDown() {
    ProductionCalendars.source = null
  }

  private fun calAlarm(
    mask: Int = 0,
    honorHolidays: Boolean = true,
    polarity: String = AlarmEntity.POLARITY_WORK,
  ) = AlarmEntity(
    hour = 7,
    minute = 0,
    mode = AlarmEntity.MODE_CALENDAR,
    daysMask = mask,
    honorHolidays = honorHolidays,
    polarity = polarity,
  )

  private fun periodOn(date: LocalDate) = AlarmPeriod(
    alarmId = 1,
    fromEpochDay = date.toEpochDay(),
    toEpochDay = date.toEpochDay(),
  )

  private val weekdays = AlarmTimes.maskOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
  )

  private val weekends = AlarmTimes.maskOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

  private val cal2026 = requireNotNull(ProductionCalendars.merged("RU", 2026))

  private val fri = LocalDate.of(2026, 6, 26)
  private val sat = LocalDate.of(2026, 6, 27)
  private val sun = LocalDate.of(2026, 6, 28)
  private val wed = LocalDate.of(2026, 6, 24)
  private val holiday = LocalDate.of(2026, 11, 4)
  private val transferOff = LocalDate.of(2026, 3, 9)

  @Test
  fun `звонит в субботу и воскресенье`() {
    val a = calAlarm()
    assertTrue(AlarmTimes.firesOn(a, sat, cal2026))
    assertTrue(AlarmTimes.firesOn(a, sun, cal2026))
    assertEquals(sat.atTime(7, 0), AlarmTimes.next(a, fri.atTime(8, 0)))
  }

  @Test
  fun `звонит в нерабочий праздник и в перенесённый выходной`() {
    val a = calAlarm()
    assertTrue(AlarmTimes.firesOn(a, holiday, cal2026))
    assertTrue(AlarmTimes.firesOn(a, transferOff, cal2026))
    assertEquals(holiday.atTime(7, 0), AlarmTimes.next(a, holiday.minusDays(1).atTime(8, 0)))
    assertEquals(
      transferOff.atTime(7, 0),
      AlarmTimes.next(a, transferOff.minusDays(1).atTime(8, 0))
    )
  }

  @Test
  fun `молчит в обычный рабочий день`() {
    val a = calAlarm()
    assertFalse(AlarmTimes.firesOn(a, wed, cal2026))
    // Со вторника ближайший звонок — суббота: среда, четверг и пятница рабочие.
    assertEquals(sat.atTime(7, 0), AlarmTimes.next(a, LocalDate.of(2026, 6, 23).atTime(8, 0)))
  }

  @Test
  fun `маска дней в расчёте не участвует`() {
    val from = fri.atTime(8, 0)
    val aWeekdays = calAlarm(mask = weekdays)
    val aWeekends = calAlarm(mask = weekends)
    val aEmpty = calAlarm(mask = 0)
    assertEquals(AlarmTimes.next(aWeekdays, from), AlarmTimes.next(aWeekends, from))
    assertEquals(AlarmTimes.next(aWeekdays, from), AlarmTimes.next(aEmpty, from))
    assertEquals(
      AlarmTimes.firesOn(aWeekdays, sat, cal2026),
      AlarmTimes.firesOn(aWeekends, sat, cal2026)
    )
    assertFalse(AlarmTimes.firesOn(aWeekdays, wed, cal2026))
  }

  @Test
  fun `honorHolidays false не отключает календарь в этом режиме`() {
    val aOff = calAlarm(honorHolidays = false)
    val aOn = calAlarm(honorHolidays = true)
    assertTrue(AlarmTimes.usesProductionCalendar(aOff))
    assertTrue(AlarmTimes.usesProductionCalendar(aOn))
    assertEquals(AlarmTimes.next(aOn, fri.atTime(8, 0)), AlarmTimes.next(aOff, fri.atTime(8, 0)))
    assertEquals(
      holiday.atTime(7, 0),
      AlarmTimes.next(aOff, holiday.minusDays(1).atTime(8, 0))
    )
  }

  @Test
  fun `нет данных на год - звонит в субботу, а не по маске`() {
    val a = calAlarm(mask = weekdays)
    val sat2027 = LocalDate.of(2027, 1, 16)
    val mon2027 = LocalDate.of(2027, 1, 18)
    val empty = requireNotNull(ProductionCalendars.calendarFor(honorHolidays = true, year = 2027))
    assertTrue(empty.kinds.isEmpty())
    assertTrue(AlarmTimes.firesOn(a, sat2027, empty))
    assertFalse(AlarmTimes.firesOn(a, mon2027, empty))
    assertEquals(sat2027.atTime(7, 0), AlarmTimes.next(a, LocalDate.of(2027, 1, 15).atTime(8, 0)))
  }

  @Test
  fun `точки календаря согласованы с расчётом`() {
    val a = calAlarm(mask = weekdays)
    var from = LocalDate.of(2026, 11, 2).atTime(8, 0) // Пн перед праздником 04.11
    repeat(10) {
      val next = requireNotNull(AlarmTimes.next(a, from))
      assertTrue(
        "firesOn=true в день звонка $next",
        AlarmTimes.firesOn(a, next.toLocalDate(), cal2026)
      )
      var day = from.toLocalDate().plusDays(1)
      while (day.isBefore(next.toLocalDate())) {
        assertFalse(
          "firesOn=false в пропущенный день $day",
          AlarmTimes.firesOn(a, day, cal2026)
        )
        day = day.plusDays(1)
      }
      from = next
    }
  }

  @Test
  fun `период без будильника глушит звонок`() {
    val a = calAlarm()
    val periodSat = periodOn(sat)
    assertFalse(AlarmTimes.firesOn(a, sat, cal2026, periods = listOf(periodSat)))
    assertTrue(AlarmTimes.firesOn(a, sun, cal2026, periods = listOf(periodSat)))
    assertEquals(sun.atTime(7, 0), AlarmTimes.next(a, listOf(periodSat), fri.atTime(8, 0)))
    // Также праздник глушится периодом.
    assertFalse(
      AlarmTimes.firesOn(a, holiday, cal2026, periods = listOf(periodOn(holiday)))
    )
  }

  @Test
  fun `полярность фиксирована REST независимо от поля polarity`() {
    val aWork = calAlarm(polarity = AlarmEntity.POLARITY_WORK)
    val aRest = calAlarm(polarity = AlarmEntity.POLARITY_REST)
    assertEquals(AlarmPolarity.REST, HolidayModePolicy.effectivePolarity(aWork))
    assertEquals(AlarmPolarity.REST, HolidayModePolicy.effectivePolarity(aRest))
    assertEquals(
      AlarmTimes.next(aWork, fri.atTime(8, 0)),
      AlarmTimes.next(aRest, fri.atTime(8, 0))
    )
  }
}

