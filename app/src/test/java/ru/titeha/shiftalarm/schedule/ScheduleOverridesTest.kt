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

  // --- Умная отмена ночи (Вариант Б) ---

  private val depart = ShiftType("o", "Выходной", LocalTime.of(21, 0), ShiftCategory.OFF)
  private val nightLast = ShiftType("n", "Ночь", null, ShiftCategory.NIGHT)

  /** Мини-паттерн Варианта Б: вых*🔔 / ночь🔔 / ночь— / вых / вых. Звонок ночи — на пред. дне. */
  private fun nightSchedule() = ShiftSchedule(
    ShiftPattern(listOf(depart, night, nightLast, ShiftType.off(), ShiftType.off()), anchor)
  )

  @Test
  fun cancelNight_silencesServingAlarm_keepsOutgoing() {
    val schedule = nightSchedule()
    // idx1 (anchor+1) — «Ночь» со звонком, будящим на idx2. Отменяем её.
    val cancelled = ScheduleOverrides.apply(
      schedule,
      ScheduleOverrides.cancelNight(anchor.plusDays(1)) { ShiftEngine.shiftOn(it, schedule) }
    )

    // Звонок дня idx0 (будил на отменённую ночь idx1) — снят.
    assertNull(ShiftEngine.wakeTimeOn(anchor.plusDays(0), cancelled))
    // Отменённый день покрашен как выходной.
    assertEquals(ShiftCategory.OFF, ShiftEngine.shiftOn(anchor.plusDays(1), cancelled).category)
    // Исходящий звонок idx1 сохранён — он будит на следующую ночь idx2.
    assertEquals(LocalTime.of(21, 0), ShiftEngine.wakeTimeOn(anchor.plusDays(1), cancelled))
  }

  @Test
  fun cancelNight_adjacentNights_composeCorrectly() {
    var schedule = nightSchedule()
    // Отменяем idx1, затем idx2 — считая резолв от ТЕКУЩЕГО (уже правленого) расписания.
    schedule = ScheduleOverrides.apply(
      schedule,
      ScheduleOverrides.cancelNight(anchor.plusDays(1)) { ShiftEngine.shiftOn(it, schedule) }
    )
    schedule = ScheduleOverrides.apply(
      schedule,
      ScheduleOverrides.cancelNight(anchor.plusDays(2)) { ShiftEngine.shiftOn(it, schedule) }
    )

    // Все звонки ночного блока сняты, обе ночи — выходные.
    assertNull(ShiftEngine.wakeTimeOn(anchor.plusDays(0), schedule)) // служил idx1
    assertNull(ShiftEngine.wakeTimeOn(anchor.plusDays(1), schedule)) // служил idx2 (теперь снят)
    assertEquals(ShiftCategory.OFF, ShiftEngine.shiftOn(anchor.plusDays(1), schedule).category)
    assertEquals(ShiftCategory.OFF, ShiftEngine.shiftOn(anchor.plusDays(2), schedule).category)
  }

  @Test
  fun offPeriod_onNightDay_silencesServingAlarm() {
    // Регресс: отпуск на ДЕНЬ НОЧИ должен глушить звонок предыдущего дня, обслуживающий эту ночь.
    val schedule = nightSchedule() // [depart(21), night(21), nightLast(—), off, off] от anchor
    val from = anchor.atTime(6, 0)

    // Без отпуска: первый звонок — вечер anchor (depart 21:00), будит на ночь anchor+1.
    assertEquals(anchor.atTime(21, 0), ShiftEngine.nextAlarm(from, schedule))

    // Отпуск на день ночи (anchor+1) снимает звонок anchor (он обслуживал именно эту ночь);
    // ближайший звонок — вечер anchor+1, будящий на СЛЕДУЮЩУЮ ночь anchor+2 (не в отпуске).
    val onLeave = schedule.copy(offPeriods = listOf(OffPeriod(anchor.plusDays(1), anchor.plusDays(1))))
    assertEquals(anchor.plusDays(1).atTime(21, 0), ShiftEngine.nextAlarm(from, onLeave))
  }
}
