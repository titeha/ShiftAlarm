package ru.titeha.shiftalarm.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ShiftEngineTest {

  private val anchor: LocalDate = LocalDate.of(2026, 1, 5)
  private val work = ShiftType("w", "Работа", LocalTime.of(7, 0))

  private fun nameOn(pattern: ShiftPattern, offset: Long) =
    ShiftEngine.shiftOn(anchor.plusDays(offset), pattern).name

  // --- 2/2 ---

  @Test
  fun rotation_2_2() {
    val p = ShiftPattern.workRest(2, 2, work, anchor)
    assertTrue(ShiftEngine.shiftOn(anchor.plusDays(0), p).isWorkDay)
    assertTrue(ShiftEngine.shiftOn(anchor.plusDays(1), p).isWorkDay)
    assertFalse(ShiftEngine.shiftOn(anchor.plusDays(2), p).isWorkDay)
    assertFalse(ShiftEngine.shiftOn(anchor.plusDays(3), p).isWorkDay)
    // перенос цикла
    assertTrue(ShiftEngine.shiftOn(anchor.plusDays(4), p).isWorkDay)
  }

  @Test
  fun rotation_2_2_before_anchor() {
    val p = ShiftPattern.workRest(2, 2, work, anchor)
    // -1 -> индекс 3 (выходной), -4 -> индекс 0 (работа)
    assertFalse(ShiftEngine.shiftOn(anchor.plusDays(-1), p).isWorkDay)
    assertFalse(ShiftEngine.shiftOn(anchor.plusDays(-2), p).isWorkDay)
    assertTrue(ShiftEngine.shiftOn(anchor.plusDays(-3), p).isWorkDay)
    assertTrue(ShiftEngine.shiftOn(anchor.plusDays(-4), p).isWorkDay)
  }

  // --- 3/3 и 3/2 ---

  @Test
  fun rotation_3_3() {
    val p = ShiftPattern.workRest(3, 3, work, anchor)
    val workDays = (0L..2L).all { ShiftEngine.shiftOn(anchor.plusDays(it), p).isWorkDay }
    val restDays = (3L..5L).none { ShiftEngine.shiftOn(anchor.plusDays(it), p).isWorkDay }
    assertTrue(workDays)
    assertTrue(restDays)
    assertTrue(ShiftEngine.shiftOn(anchor.plusDays(6), p).isWorkDay) // новый цикл
  }

  @Test
  fun rotation_3_2() {
    val p = ShiftPattern.workRest(3, 2, work, anchor) // цикл длиной 5
    assertTrue(ShiftEngine.shiftOn(anchor.plusDays(2), p).isWorkDay)
    assertFalse(ShiftEngine.shiftOn(anchor.plusDays(3), p).isWorkDay)
    assertFalse(ShiftEngine.shiftOn(anchor.plusDays(4), p).isWorkDay)
    assertTrue(ShiftEngine.shiftOn(anchor.plusDays(5), p).isWorkDay)
  }

  // --- 12-дневный: 3 утра / 3 день / 3 ночь / 3 выходных ---

  private fun twelveDayPattern(): ShiftPattern {
    val morning = ShiftType("m", "Утро", LocalTime.of(5, 0))
    val day = ShiftType("d", "День", LocalTime.of(11, 0))
    val night = ShiftType("n", "Ночь", LocalTime.of(18, 0))
    val off = ShiftType.off()
    val slots = List(3) { morning } + List(3) { day } + List(3) { night } + List(3) { off }
    return ShiftPattern(slots, anchor)
  }

  @Test
  fun rotation_morning_day_night_off_names() {
    val p = twelveDayPattern()
    assertEquals("Утро", nameOn(p, 0))
    assertEquals("Утро", nameOn(p, 2))
    assertEquals("День", nameOn(p, 3))
    assertEquals("День", nameOn(p, 5))
    assertEquals("Ночь", nameOn(p, 6))
    assertEquals("Ночь", nameOn(p, 8))
    assertEquals("Выходной", nameOn(p, 9))
    assertEquals("Выходной", nameOn(p, 11))
    assertEquals("Утро", nameOn(p, 12)) // цикл повторился
  }

  @Test
  fun rotation_morning_day_night_off_wake_times() {
    val p = twelveDayPattern()
    assertEquals(LocalTime.of(5, 0), ShiftEngine.wakeTimeOn(anchor.plusDays(0), p))
    assertEquals(LocalTime.of(11, 0), ShiftEngine.wakeTimeOn(anchor.plusDays(4), p))
    assertEquals(LocalTime.of(18, 0), ShiftEngine.wakeTimeOn(anchor.plusDays(7), p))
    assertNull(ShiftEngine.wakeTimeOn(anchor.plusDays(10), p)) // выходной — без звонка
  }

  @Test
  fun wake_time_far_in_future_wraps_correctly() {
    val p = twelveDayPattern()
    // 100 полных циклов (1200 дней) + 7 -> снова "Ночь"
    assertEquals("Ночь", nameOn(p, 1200 + 7))
  }
}
