package ru.titeha.shiftalarm.schedule

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.titeha.shiftalarm.data.AlarmEntity
import java.time.LocalDate

/**
 * RU_2026 по Постановлению Правительства РФ от 24.09.2025 № 1466 (E3-2026):
 * переносы сб 03.01 → пт 09.01 и вс 04.01 → чт 31.12; рабочих суббот в 2026 нет;
 * сокращённые дни (ст. 95 ТК): 30.04, 08.05, 11.06, 03.11. Итоги сверены с документом
 * КонсультантПлюс (calendar_2026.pdf): 247 рабочих / 118 нерабочих.
 */
class ProductionCalendar2026Test {

  @Before fun setUp() { ProductionCalendars.source = null }
  @After fun tearDown() { ProductionCalendars.source = null }

  private val cal = requireNotNull(ProductionCalendars.merged("RU", 2026))

  private val weekdays = AlarmTimes.maskOf(
    java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.WEDNESDAY,
    java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.FRIDAY
  )

  @Test
  fun `итоги года 247 рабочих 118 нерабочих`() {
    var work = 0
    var off = 0
    var d = LocalDate.of(2026, 1, 1)
    while (d.year == 2026) {
      if (cal.isWorking(d)) work++ else off++
      d = d.plusDays(1)
    }
    assertEquals(247, work)
    assertEquals(118, off)
  }

  @Test
  fun `переносы из постановления 9 января и 31 декабря нерабочие`() {
    assertEquals(StateDayKind.TRANSFER_OFF, cal.kindOf(LocalDate.of(2026, 1, 9)))
    assertEquals(StateDayKind.TRANSFER_OFF, cal.kindOf(LocalDate.of(2026, 12, 31)))
    assertFalse(cal.isWorking(LocalDate.of(2026, 12, 31)))
  }

  @Test
  fun `сокращённые дни 30-04 08-05 11-06 03-11`() {
    for (d in listOf(
      LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 8),
      LocalDate.of(2026, 6, 11), LocalDate.of(2026, 11, 3)
    )) {
      assertEquals(StateDayKind.PRE_HOLIDAY, cal.kindOf(d))
      assertTrue(cal.isWorking(d))
    }
  }

  @Test
  fun `в 2026 нет рабочих суббот`() {
    var d = LocalDate.of(2026, 1, 3)
    while (d.year == 2026) {
      assertFalse(cal.kindOf(d) == StateDayKind.TRANSFER_WORK)
      d = d.plusDays(7)
    }
  }

  @Test
  fun `weekly с honorHolidays молчит 9 января и 31 декабря`() {
    val a = AlarmEntity(
      hour = 7, minute = 0,
      mode = AlarmEntity.MODE_WEEKLY,
      daysMask = weekdays,
      honorHolidays = true
    )
    // Чт 08.01 08:00 → пт 09.01 глушится переносом → следующий масочный день пн 12.01.
    assertEquals(
      LocalDate.of(2026, 1, 12).atTime(7, 0),
      AlarmTimes.next(a, emptyList(), LocalDate.of(2026, 1, 8).atTime(8, 0))
    )
    // Ср 30.12 08:00 → чт 31.12 глушится переносом → праздники 2027 → пн 11.01.2027.
    assertEquals(
      LocalDate.of(2027, 1, 11).atTime(7, 0),
      AlarmTimes.next(a, emptyList(), LocalDate.of(2026, 12, 30).atTime(8, 0))
    )
  }
}
