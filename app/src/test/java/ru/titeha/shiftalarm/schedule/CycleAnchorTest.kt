package ru.titeha.shiftalarm.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class CycleAnchorTest {

  private val morning = ShiftType("m", "Утро", LocalTime.of(5, 0), ShiftCategory.MORNING)
  private val day = ShiftType("d", "День", LocalTime.of(13, 0), ShiftCategory.DAY)
  private val off = ShiftType.off()

  // Цикл 5 дней: Утро, Утро, Выходной, День, Выходной.
  private val cycle = listOf(morning, morning, off, day, off)

  @Test
  fun `после ответа движок резолвит сегодня на выбранный день`() {
    val today = LocalDate.of(2026, 7, 15)
    for (idx in cycle.indices) {
      val anchor = CycleAnchor.anchorDateForToday(today, idx)
      val resolved = ShiftEngine.shiftOn(today, ShiftPattern(cycle, anchor))
      assertEquals("день цикла $idx", cycle[idx], resolved)
      assertEquals(idx, CycleAnchor.todayIndex(today, anchor, cycle.size))
    }
  }

  @Test
  fun `инвариант держится для разных циклов и дат`() {
    val dates = listOf(LocalDate.of(2026, 3, 10), LocalDate.of(2027, 11, 30))
    val cycles = listOf(
      listOf(morning, off),
      listOf(day, day, day, off, off),
      listOf(morning, morning, morning, off, off, day, day, off)
    )
    for (t in dates) for (c in cycles) for (idx in c.indices) {
      val anchor = CycleAnchor.anchorDateForToday(t, idx)
      assertEquals(c[idx], ShiftEngine.shiftOn(t, ShiftPattern(c, anchor)))
    }
  }

  @Test
  fun `переход через границу года`() {
    // Сегодня 1 января, выбран день 5 цикла длиной 7 → опорная уходит в прошлый год.
    val today = LocalDate.of(2026, 1, 1)
    val anchor = CycleAnchor.anchorDateForToday(today, 5)
    assertEquals(LocalDate.of(2025, 12, 27), anchor)
    assertEquals(5, CycleAnchor.todayIndex(today, anchor, 7))
  }

  @Test
  fun `ночной эдж — выбор «сегодня Ночь» даёт звонок накануне вечером`() {
    // Канун со звонком 21:00 обслуживает ночь следующего дня (Вариант Б). Выбираем «сегодня = ночь».
    val eve = ShiftType("e", "Выходной", LocalTime.of(21, 0), ShiftCategory.OFF)
    val nightSilent = ShiftType("n", "Ночь", null, ShiftCategory.NIGHT)
    val nightCycle = listOf(eve, nightSilent, off, off)
    val today = LocalDate.of(2026, 7, 15)
    val nightIndex = 1 // индекс ночи в цикле

    val anchor = CycleAnchor.anchorDateForToday(today, nightIndex)
    val schedule = ShiftSchedule(ShiftPattern(nightCycle, anchor))
    // Сегодня действительно ночь.
    assertEquals(ShiftCategory.NIGHT, ShiftEngine.shiftOn(today, schedule).category)

    // Первый звонок начиная с кануна (today−1) 00:00 — вечером кануна, обслуживает ночь «сегодня».
    val occ = ShiftEngine.nextAlarms(today.minusDays(1).atStartOfDay(), schedule, count = 1)
    assertEquals(1, occ.size)
    assertEquals(today.minusDays(1).atTime(21, 0), occ[0].ringAt)
    assertEquals(today, occ[0].servedDate)
    assertTrue(occ[0].eveningBefore)
  }
}
