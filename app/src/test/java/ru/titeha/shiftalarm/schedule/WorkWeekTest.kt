package ru.titeha.shiftalarm.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class WorkWeekTest {

  @Test
  fun `дефолт — пятидневка с понедельника, выходные Сб и Вс`() {
    val week = WorkWeek.DEFAULT
    assertEquals(
      setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
      ),
      week.workingDays
    )
    assertTrue(week.isWeekend(DayOfWeek.SATURDAY))
    assertTrue(week.isWeekend(DayOfWeek.SUNDAY))
    assertFalse(week.isWeekend(DayOfWeek.MONDAY))
  }

  @Test
  fun `шестидневка с понедельника — выходное только воскресенье`() {
    val week = WorkWeek(workDays = 6, weekStart = DayOfWeek.MONDAY)
    assertFalse("суббота рабочая", week.isWeekend(DayOfWeek.SATURDAY))
    assertTrue("воскресенье выходной", week.isWeekend(DayOfWeek.SUNDAY))
  }

  @Test
  fun `четырёхдневка ВАЗ — выходные Пт Сб Вс`() {
    val week = WorkWeek(workDays = 4, weekStart = DayOfWeek.MONDAY)
    assertEquals(
      setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY),
      week.workingDays
    )
    assertTrue(week.isWeekend(DayOfWeek.FRIDAY))
    assertTrue(week.isWeekend(DayOfWeek.SATURDAY))
    assertTrue(week.isWeekend(DayOfWeek.SUNDAY))
  }

  @Test
  fun `начало недели с воскресенья — пятидневка Вс-Чт, выходные Пт и Сб`() {
    val week = WorkWeek(workDays = 5, weekStart = DayOfWeek.SUNDAY)
    assertEquals(
      setOf(
        DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY
      ),
      week.workingDays
    )
    assertTrue(week.isWeekend(DayOfWeek.FRIDAY))
    assertTrue(week.isWeekend(DayOfWeek.SATURDAY))
    assertFalse(week.isWeekend(DayOfWeek.SUNDAY))
  }

  @Test
  fun `семидневка — выходных нет`() {
    val week = WorkWeek(workDays = 7, weekStart = DayOfWeek.MONDAY)
    DayOfWeek.entries.forEach { day ->
      assertFalse("день $day должен быть рабочим", week.isWeekend(day))
    }
  }

  @Test
  fun `перенос через край недели — рабочие дни сворачиваются по модулю 7`() {
    // Начало с пятницы, 4 дня: Пт, Сб, Вс, Пн.
    val week = WorkWeek(workDays = 4, weekStart = DayOfWeek.FRIDAY)
    assertEquals(
      setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY),
      week.workingDays
    )
    assertTrue(week.isWeekend(DayOfWeek.TUESDAY))
  }

  @Test
  fun `workDays вне 1_7 — ошибка`() {
    assertThrows(IllegalArgumentException::class.java) { WorkWeek(workDays = 0) }
    assertThrows(IllegalArgumentException::class.java) { WorkWeek(workDays = 8) }
  }
}
