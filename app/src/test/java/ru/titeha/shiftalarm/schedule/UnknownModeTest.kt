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
  fun `неизвестный режим не поддерживает полярность REST — как weekly`() {
    val unknown = unknownMode(weekdays, honor = true).copy(polarity = AlarmEntity.POLARITY_REST)
    assertEquals(AlarmPolarity.WORK, HolidayModePolicy.effectivePolarity(unknown))
  }

  @Test
  fun `неизвестный режим учитывает праздники так же, как weekly в WORK-семантике`() {
    // После TD03 неизвестный режим как weekly (WORK): праздник глушит звоночную рабочую среду.
    // REST-поведение («звонить в нерабочие») теперь только у MODE_CALENDAR — см. CalendarModeTest.
    val calendar = requireNotNull(ProductionCalendars.calendarFor(honorHolidays = true, year = 2026))
    val holiday = LocalDate.of(2026, 11, 4) // среда, праздник
    val unknown = unknownMode(weekdays, honor = true) // WORK (REST у weekly теперь не действует)
    val weekly = unknown.copy(mode = AlarmEntity.MODE_WEEKLY)

    assertEquals(AlarmTimes.next(weekly, wed.atTime(8, 0)), AlarmTimes.next(unknown, wed.atTime(8, 0)))
    // В рабочий праздник weekly (WORK) глушится — ближайший звонок сдвигается.
    assertFalse(AlarmTimes.firesOn(unknown, holiday, calendar))
    assertFalse(AlarmTimes.firesOn(weekly, holiday, calendar))
    // Без календаря (null) тот же день по маске считается рабочим — звонит.
    assertTrue(AlarmTimes.firesOn(unknown, holiday, null))
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
