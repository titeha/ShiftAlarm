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
 * Защитное чтение неизвестного режима: трактуется как «по дням недели» одинаково в расчёте
 * и в визуализации (K3/E7). Дата: 2026-06-24 — среда, 2026-06-27 — суббота.
 */
class UnknownModeTest {

  // Внешний источник календаря — глобальное состояние модуля: не протекать между тестами.
  @Before
  fun setUp() {
    ProductionCalendars.source = null
  }

  @After
  fun tearDown() {
    ProductionCalendars.source = null
  }

  private val wed = LocalDate.of(2026, 6, 24)
  private val sat = LocalDate.of(2026, 6, 27)
  private val weekdays = AlarmTimes.maskOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
  )

  private fun unknownMode(mask: Int, honor: Boolean = false) = AlarmEntity(
    hour = 7,
    minute = 0,
    mode = "weekly_v2",
    daysMask = mask,
    honorHolidays = honor,
    polarity = AlarmEntity.POLARITY_WORK
  )

  @Test
  fun `неизвестный режим считается как по дням недели`() {
    val unknown = unknownMode(weekdays)
    val weekly = unknown.copy(mode = AlarmEntity.MODE_WEEKLY)
    assertEquals(
      AlarmTimes.next(weekly, wed.atTime(8, 0)),
      AlarmTimes.next(unknown, wed.atTime(8, 0))
    )
  }

  @Test
  fun `неизвестный режим показывает точки календаря так же, как weekly`() {
    val unknown = unknownMode(weekdays)
    val weekly = unknown.copy(mode = AlarmEntity.MODE_WEEKLY)
    assertEquals(
      AlarmTimes.firesOn(weekly, wed, null),
      AlarmTimes.firesOn(unknown, wed, null)
    )
    assertTrue(AlarmTimes.firesOn(unknown, wed, null))
    assertFalse(AlarmTimes.firesOn(unknown, sat, null))
  }

  @Test
  fun `неизвестный режим поддерживает полярность REST как weekly`() {
    val unknown = unknownMode(weekdays, honor = true).copy(polarity = AlarmEntity.POLARITY_REST)
    assertEquals(AlarmPolarity.REST, HolidayModePolicy.effectivePolarity(unknown))
  }

  @Test
  fun `неизвестный режим учитывает праздники так же, как weekly`() {
    // Путь, где effectiveMode взаимодействует с полярностью REST и резолвером по реальному календарю:
    // в остальных тестах календарь равен null. 04.11.2026 — среда и нерабочий праздник в RU_2026,
    // поэтому утверждения опираются на данные календаря, а не только на дни недели.
    val calendar = requireNotNull(ProductionCalendars.calendarFor(honorHolidays = true, year = 2026))
    val holiday = LocalDate.of(2026, 11, 4)
    val unknownRest = unknownMode(weekdays, honor = true).copy(polarity = AlarmEntity.POLARITY_REST)
    val weeklyRest = unknownRest.copy(mode = AlarmEntity.MODE_WEEKLY)

    assertEquals(AlarmTimes.next(weeklyRest, wed.atTime(8, 0)), AlarmTimes.next(unknownRest, wed.atTime(8, 0)))
    assertEquals(holiday.atTime(7, 0), AlarmTimes.next(unknownRest, holiday.minusDays(1).atTime(8, 0)))
    assertTrue(AlarmTimes.firesOn(unknownRest, holiday, calendar))
    assertFalse(AlarmTimes.firesOn(unknownRest.copy(polarity = AlarmEntity.POLARITY_WORK), holiday, calendar))
    // Без данных календаря картина другая — утверждения выше опираются именно на праздник, а не на день недели.
    assertFalse(AlarmTimes.firesOn(unknownRest, holiday, ProductionCalendar()))
  }

  @Test
  fun `сменный режим не трактуется как weekly`() {
    val shift = AlarmEntity(
      mode = AlarmEntity.MODE_SHIFT,
      presetId = "2x2",
      anchorEpochDay = wed.toEpochDay()
    )
    assertFalse(AlarmTimes.firesOn(shift, wed, null))
  }
}
