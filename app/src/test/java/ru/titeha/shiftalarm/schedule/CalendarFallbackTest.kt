package ru.titeha.shiftalarm.schedule

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.titeha.shiftalarm.data.AlarmEntity
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Деградация производственного календаря: при включённом «Учитывать праздники», но отсутствии
 * данных на год, «По выходным» обязана звонить в Сб/Вс, а не превращаться в «По рабочим» (K1/E1).
 *
 * Год без данных во встроенном календаре — 2027 (есть только RU-2026). Даты: 15.01.2027 — пятница,
 * 16.01 — суббота, 18.01 — понедельник.
 */
class CalendarFallbackTest {

  @Before
  fun setUp() {
    ProductionCalendars.source = null
  }

  @After
  fun tearDown() {
    ProductionCalendars.source = null
  }

  private val from = LocalDate.of(2027, 1, 15).atTime(8, 0) // пятница
  private val sat = LocalDate.of(2027, 1, 16)
  private val mon = LocalDate.of(2027, 1, 18)

  private val weekdays = AlarmTimes.maskOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
  )

  private fun alarm(polarity: String, honor: Boolean, mask: Int) = AlarmEntity(
    hour = 7,
    minute = 0,
    mode = AlarmEntity.MODE_WEEKLY,
    daysMask = mask,
    honorHolidays = honor,
    polarity = polarity
  )

  @Test
  fun `нет данных на год — по выходным звонит в субботу, а не по маске будней`() {
    val a = alarm(AlarmEntity.POLARITY_REST, honor = true, mask = weekdays)
    assertEquals(sat.atTime(7, 0), AlarmTimes.next(a, from))
  }

  @Test
  fun `нет данных на год — по рабочим не меняется и звонит по маске`() {
    val a = alarm(AlarmEntity.POLARITY_WORK, honor = true, mask = weekdays)
    assertEquals(mon.atTime(7, 0), AlarmTimes.next(a, from))
  }

  @Test
  fun `тумблер выключен — по выходным ведёт себя как по рабочим, календарь не подставляется`() {
    val a = alarm(AlarmEntity.POLARITY_REST, honor = false, mask = weekdays)
    assertEquals(mon.atTime(7, 0), AlarmTimes.next(a, from))
  }

  @Test
  fun `разовый будильник не зависит от деградации календаря`() {
    val a = alarm(AlarmEntity.POLARITY_WORK, honor = true, mask = 0)
    assertEquals(sat.atTime(7, 0), AlarmTimes.next(a, from))
  }

  @Test
  fun `год с данными — поведение прежнее`() {
    val a = alarm(AlarmEntity.POLARITY_REST, honor = true, mask = weekdays)
    assertEquals(
      LocalDate.of(2026, 6, 27).atTime(7, 0),
      AlarmTimes.next(a, LocalDate.of(2026, 6, 24).atTime(8, 0))
    )
  }

  @Test
  fun `forAlarm — null при выключенном тумблере и непустой календарь при включённом`() {
    assertNull(ProductionCalendars.forAlarm(honorHolidays = false, year = 2027))
    assertNotNull(ProductionCalendars.forAlarm(honorHolidays = true, year = 2027))
    assertNotNull(ProductionCalendars.forAlarm(honorHolidays = true, year = 2026))
  }

  @Test
  fun `точка календаря согласована с расчётом в год без данных`() {
    val a = alarm(AlarmEntity.POLARITY_REST, honor = true, mask = weekdays)
    val cal = requireNotNull(ProductionCalendars.forAlarm(honorHolidays = true, year = 2027))
    assertTrue(AlarmTimes.weeklyFiresOn(a, sat, cal))
    assertFalse(AlarmTimes.weeklyFiresOn(a, mon, cal))
  }
}
