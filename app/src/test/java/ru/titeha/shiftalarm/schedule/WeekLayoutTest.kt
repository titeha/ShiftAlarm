package ru.titeha.shiftalarm.schedule

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.util.Locale

class WeekLayoutTest {

  @Test
  fun `порядок дней от понедельника`() {
    assertEquals(
      listOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
      ),
      orderedDaysOfWeek(DayOfWeek.MONDAY)
    )
  }

  @Test
  fun `порядок дней от воскресенья`() {
    assertEquals(
      listOf(
        DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
      ),
      orderedDaysOfWeek(DayOfWeek.SUNDAY)
    )
  }

  @Test
  fun `порядок дней от субботы`() {
    assertEquals(DayOfWeek.SATURDAY, orderedDaysOfWeek(DayOfWeek.SATURDAY).first())
    assertEquals(DayOfWeek.FRIDAY, orderedDaysOfWeek(DayOfWeek.SATURDAY).last())
    assertEquals(7, orderedDaysOfWeek(DayOfWeek.SATURDAY).size)
  }

  @Test
  fun `resolve — явные дни`() {
    assertEquals(DayOfWeek.MONDAY, WeekStart.MONDAY.resolve(Locale.US))
    assertEquals(DayOfWeek.SUNDAY, WeekStart.SUNDAY.resolve(Locale.forLanguageTag("ru-RU")))
    assertEquals(DayOfWeek.SATURDAY, WeekStart.SATURDAY.resolve(Locale.US))
  }

  @Test
  fun `resolve — авто из локали`() {
    // РФ: неделя с понедельника; США: с воскресенья.
    assertEquals(DayOfWeek.MONDAY, WeekStart.AUTO.resolve(Locale.forLanguageTag("ru-RU")))
    assertEquals(DayOfWeek.SUNDAY, WeekStart.AUTO.resolve(Locale.US))
  }
}
