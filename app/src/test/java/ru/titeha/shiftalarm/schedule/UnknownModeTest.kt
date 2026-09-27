package ru.titeha.shiftalarm.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.titeha.shiftalarm.data.AlarmEntity
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Защитное чтение неизвестного режима: трактуется как «по дням недели» одинаково в расчёте
 * и в визуализации (K3/E7). Дата: 2026-06-24 — среда, 2026-06-27 — суббота.
 */
class UnknownModeTest {

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
      AlarmTimes.weeklyFiresOn(weekly, wed, null),
      AlarmTimes.weeklyFiresOn(unknown, wed, null)
    )
    assertTrue(AlarmTimes.weeklyFiresOn(unknown, wed, null))
    assertFalse(AlarmTimes.weeklyFiresOn(unknown, sat, null))
  }

  @Test
  fun `неизвестный режим поддерживает полярность REST как weekly`() {
    val unknown = unknownMode(weekdays, honor = true).copy(polarity = AlarmEntity.POLARITY_REST)
    assertEquals(AlarmPolarity.REST, HolidayModePolicy.effectivePolarity(unknown))
  }

  @Test
  fun `неизвестный режим учитывает праздники так же, как weekly`() {
    // Путь, где effectiveMode взаимодействует с полярностью REST и резолвером по реальному календарю:
    // в остальных тестах календарь равен null, поэтому здесь проверка отдельная.
    val calendar = requireNotNull(ProductionCalendars.calendarFor(honorHolidays = true, year = 2026))
    val unknown = unknownMode(weekdays, honor = true).copy(polarity = AlarmEntity.POLARITY_REST)
    val weekly = unknown.copy(mode = AlarmEntity.MODE_WEEKLY)

    assertEquals(AlarmTimes.next(weekly, wed.atTime(8, 0)), AlarmTimes.next(unknown, wed.atTime(8, 0)))
    assertTrue(AlarmTimes.weeklyFiresOn(unknown, sat, calendar))
    assertFalse(AlarmTimes.weeklyFiresOn(unknown, wed, calendar))
  }

  @Test
  fun `сменный режим не трактуется как weekly`() {
    val shift = AlarmEntity(
      mode = AlarmEntity.MODE_SHIFT,
      presetId = "2x2",
      anchorEpochDay = wed.toEpochDay()
    )
    assertFalse(AlarmTimes.weeklyFiresOn(shift, wed, null))
  }
}
