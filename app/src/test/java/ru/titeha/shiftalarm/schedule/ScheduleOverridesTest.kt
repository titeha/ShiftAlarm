package ru.titeha.shiftalarm.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.titeha.shiftalarm.schedule.ScheduleOverrides.DayOverride
import java.time.LocalDate
import java.time.LocalTime

class ScheduleOverridesTest {

  private val anchor: LocalDate = LocalDate.of(2026, 1, 5) // понедельник
  private val work = ShiftType("w", "День", LocalTime.of(7, 0), ShiftCategory.DAY)
  private val night = ShiftType("n", "Ночь", LocalTime.of(21, 0), ShiftCategory.NIGHT)

  /** База: 2 рабочих / 2 выходных от anchor. */
  private fun base() = ShiftSchedule(ShiftPattern.workRest(2, 2, work, anchor))

  @Test
  fun singleDayOverride_goesToExceptions_andWins() {
    // anchor+2 по ротации — выходной; перекрываем на рабочую смену «Ночь».
    val d = anchor.plusDays(2)
    val schedule = ScheduleOverrides.apply(base(), listOf(DayOverride(d, d, night)))

    assertEquals(1, schedule.exceptions.size)
    assertTrue(schedule.swaps.isEmpty())
    assertEquals("Ночь", ShiftEngine.shiftOn(d, schedule).name)
    assertEquals(LocalTime.of(21, 0), ShiftEngine.wakeTimeOn(d, schedule))
  }

  @Test
  fun rangeOverride_goesToSwaps() {
    // Отдать рабочий блок anchor..anchor+1: подмена на выходной на диапазон.
    val off = ShiftType.off()
    val schedule = ScheduleOverrides.apply(
      base(), listOf(DayOverride(anchor, anchor.plusDays(1), off))
    )

    assertTrue(schedule.exceptions.isEmpty())
    assertEquals(1, schedule.swaps.size)
    assertNull(ShiftEngine.wakeTimeOn(anchor, schedule))
    assertNull(ShiftEngine.wakeTimeOn(anchor.plusDays(1), schedule))
  }

  @Test
  fun singleDayException_beatsRangeSwap_onSameDay() {
    // Диапазон-подмена на выходной, но один день внутри — точечно вернули в работу.
    val off = ShiftType.off()
    val schedule = ScheduleOverrides.apply(
      base(),
      listOf(
        DayOverride(anchor, anchor.plusDays(3), off),          // весь блок → выходной (swap)
        DayOverride(anchor.plusDays(1), anchor.plusDays(1), night) // один день → работа (exception)
      )
    )

    assertNull(ShiftEngine.wakeTimeOn(anchor, schedule))                 // покрыт только swap
    assertEquals("Ночь", ShiftEngine.shiftOn(anchor.plusDays(1), schedule).name) // exception победил
  }

  @Test
  fun apply_preservesExistingScheduleEdits() {
    val existingDay = anchor.plusDays(10)
    val start = ShiftSchedule(
      ShiftPattern.workRest(2, 2, work, anchor),
      exceptions = mapOf(existingDay to night)
    )
    val d = anchor.plusDays(2)
    val schedule = ScheduleOverrides.apply(start, listOf(DayOverride(d, d, work)))

    // Старое исключение на месте, новое добавлено.
    assertEquals("Ночь", ShiftEngine.shiftOn(existingDay, schedule).name)
    assertEquals("День", ShiftEngine.shiftOn(d, schedule).name)
  }

  @Test
  fun emptyOverrides_leaveScheduleUnchanged() {
    val schedule = ScheduleOverrides.apply(base(), emptyList())
    assertTrue(schedule.exceptions.isEmpty())
    assertTrue(schedule.swaps.isEmpty())
  }

  @Test(expected = IllegalArgumentException::class)
  fun override_rejectsInvertedRange() {
    DayOverride(anchor.plusDays(3), anchor, work)
  }
}
