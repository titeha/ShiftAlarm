package ru.titeha.shiftalarm.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
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

  // --- Подмены и исключения ---

  private val night = ShiftType("n", "Ночь", LocalTime.of(18, 0))

  @Test
  fun exception_forces_day_off() {
    val schedule = ShiftSchedule(
      base = ShiftPattern.workRest(2, 2, work, anchor),
      exceptions = mapOf(anchor to ShiftType.off()) // в рабочий день — выходной
    )
    assertNull(ShiftEngine.wakeTimeOn(anchor, schedule))
  }

  @Test
  fun exception_forces_specific_shift_on_day_off() {
    val schedule = ShiftSchedule(
      base = ShiftPattern.workRest(2, 2, work, anchor),
      exceptions = mapOf(anchor.plusDays(2) to night) // в выходной — выйти в ночь
    )
    assertEquals(LocalTime.of(18, 0), ShiftEngine.wakeTimeOn(anchor.plusDays(2), schedule))
  }

  @Test
  fun swap_overrides_base_within_period() {
    val schedule = ShiftSchedule(
      base = ShiftPattern.workRest(2, 2, work, anchor),
      swaps = listOf(TemporarySwap(anchor.plusDays(2), anchor.plusDays(3), night))
    )
    assertEquals("Ночь", ShiftEngine.shiftOn(anchor.plusDays(2), schedule).name)
    assertEquals("Ночь", ShiftEngine.shiftOn(anchor.plusDays(3), schedule).name)
    // вне периода — базовая ротация
    assertEquals("Работа", ShiftEngine.shiftOn(anchor.plusDays(0), schedule).name)
    assertEquals("Работа", ShiftEngine.shiftOn(anchor.plusDays(4), schedule).name)
  }

  @Test
  fun exception_beats_swap() {
    val schedule = ShiftSchedule(
      base = ShiftPattern.workRest(2, 2, work, anchor),
      swaps = listOf(TemporarySwap(anchor, anchor.plusDays(5), night)),
      exceptions = mapOf(anchor.plusDays(1) to ShiftType.off())
    )
    // на день 1 действует и подмена (ночь), и исключение (выходной) — выигрывает исключение
    assertNull(ShiftEngine.wakeTimeOn(anchor.plusDays(1), schedule))
    // соседний день в периоде — подмена
    assertEquals("Ночь", ShiftEngine.shiftOn(anchor.plusDays(2), schedule).name)
  }

  // --- Ближайший звонок ---

  @Test
  fun next_alarm_today_before_wake() {
    val s = ShiftSchedule(ShiftPattern.workRest(2, 2, work, anchor))
    val from = anchor.atTime(6, 0) // рабочий день, до 7:00
    assertEquals(anchor.atTime(7, 0), ShiftEngine.nextAlarm(from, s))
  }

  @Test
  fun next_alarm_today_after_wake_goes_to_next_work_day() {
    val s = ShiftSchedule(ShiftPattern.workRest(2, 2, work, anchor))
    val from = anchor.atTime(8, 0) // рабочий день, уже после 7:00
    assertEquals(anchor.plusDays(1).atTime(7, 0), ShiftEngine.nextAlarm(from, s))
  }

  @Test
  fun next_alarm_from_day_off_skips_to_work_day() {
    val s = ShiftSchedule(ShiftPattern.workRest(2, 2, work, anchor))
    val from = anchor.plusDays(2).atTime(10, 0) // выходной (дни 2,3) -> следующий рабочий день 4
    assertEquals(anchor.plusDays(4).atTime(7, 0), ShiftEngine.nextAlarm(from, s))
  }

  @Test
  fun next_alarm_skips_today_when_exception_forces_off() {
    val s = ShiftSchedule(
      base = ShiftPattern.workRest(2, 2, work, anchor),
      exceptions = mapOf(anchor to ShiftType.off())
    )
    val from = anchor.atTime(6, 0)
    assertEquals(anchor.plusDays(1).atTime(7, 0), ShiftEngine.nextAlarm(from, s))
  }

  @Test
  fun next_alarm_null_when_schedule_all_off() {
    val s = ShiftSchedule(ShiftPattern(listOf(ShiftType.off()), anchor))
    assertNull(ShiftEngine.nextAlarm(anchor.atTime(6, 0), s))
  }

  // --- Периоды отпуска (off) ---

  /** 2/2, отпуск на дни 0..1 (это рабочие дни базовой ротации). */
  private fun scheduleWithVacation(freeze: Boolean) = ShiftSchedule(
    base = ShiftPattern.workRest(2, 2, work, anchor),
    offPeriods = listOf(OffPeriod(anchor, anchor.plusDays(1))),
    freezeCycleDuringOff = freeze
  )

  @Test
  fun off_period_silences_alarm() {
    val s = scheduleWithVacation(freeze = false)
    // дни 0,1 — рабочие по базе, но в отпуске звонка нет
    assertNull(ShiftEngine.wakeTimeOn(anchor.plusDays(0), s))
    assertNull(ShiftEngine.wakeTimeOn(anchor.plusDays(1), s))
  }

  @Test
  fun off_period_roll_keeps_calendar_phase() {
    val s = scheduleWithVacation(freeze = false)
    // цикл крутится: после отпуска фаза «по календарю» — дни 2,3 выходные базовые, день 4 — работа
    assertFalse(ShiftEngine.shiftOn(anchor.plusDays(2), s).isWorkDay)
    assertFalse(ShiftEngine.shiftOn(anchor.plusDays(3), s).isWorkDay)
    assertTrue(ShiftEngine.shiftOn(anchor.plusDays(4), s).isWorkDay)
  }

  @Test
  fun off_period_freeze_resumes_same_phase() {
    val s = scheduleWithVacation(freeze = true)
    // заморозка: 2 отпускных дня не считаются, цикл стартует заново — дни 2,3 работа, 4,5 выходные
    assertTrue(ShiftEngine.shiftOn(anchor.plusDays(2), s).isWorkDay)
    assertTrue(ShiftEngine.shiftOn(anchor.plusDays(3), s).isWorkDay)
    assertFalse(ShiftEngine.shiftOn(anchor.plusDays(4), s).isWorkDay)
    assertFalse(ShiftEngine.shiftOn(anchor.plusDays(5), s).isWorkDay)
    assertTrue(ShiftEngine.shiftOn(anchor.plusDays(6), s).isWorkDay)
  }

  @Test
  fun next_alarm_skips_off_period() {
    val s = scheduleWithVacation(freeze = false)
    // из дня 0 06:00: дни 0,1 в отпуске (молчат), 2,3 выходные базовые → первый звонок день 4
    assertEquals(anchor.plusDays(4).atTime(7, 0), ShiftEngine.nextAlarm(anchor.atTime(6, 0), s))
  }

  @Test
  fun exception_beats_off_period() {
    val s = ShiftSchedule(
      base = ShiftPattern.workRest(2, 2, work, anchor),
      offPeriods = listOf(OffPeriod(anchor, anchor.plusDays(3))),
      exceptions = mapOf(anchor.plusDays(1) to night)
    )
    // в отпускной день исключение выводит в ночь
    assertEquals(LocalTime.of(18, 0), ShiftEngine.wakeTimeOn(anchor.plusDays(1), s))
    // соседний отпускной день — без звонка
    assertNull(ShiftEngine.wakeTimeOn(anchor.plusDays(2), s))
  }

  @Test
  fun swap_beats_off_period() {
    val s = ShiftSchedule(
      base = ShiftPattern.workRest(2, 2, work, anchor),
      offPeriods = listOf(OffPeriod(anchor, anchor.plusDays(3))),
      swaps = listOf(TemporarySwap(anchor.plusDays(1), anchor.plusDays(1), night))
    )
    assertEquals("Ночь", ShiftEngine.shiftOn(anchor.plusDays(1), s).name)
    assertNull(ShiftEngine.wakeTimeOn(anchor.plusDays(2), s))
  }
}
