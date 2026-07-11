package ru.titeha.shiftalarm.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.titeha.shiftalarm.data.AlarmEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Инвариантные тесты движка: проверяют ОБЩИЕ свойства расчёта, а не отдельные примеры
 * (блок 5 роадмапа). Чистые, без Android runtime.
 */
class EngineInvariantsTest {

  private val anchor: LocalDate = LocalDate.of(2026, 1, 5) // понедельник

  // --- Инвариант: ближайшее срабатывание всегда строго ПОСЛЕ from ---

  @Test
  fun next_isAlwaysStrictlyAfterFrom_weekly() {
    val masks = listOf(
      0,
      AlarmTimes.maskOf(DayOfWeek.MONDAY),
      AlarmTimes.maskOf(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
      AlarmTimes.maskOf(*DayOfWeek.entries.toTypedArray()),
    )
    for (mask in masks) {
      for (h in listOf(0, 7, 15, 23)) {
        for (m in listOf(0, 30, 59)) {
          val alarm = AlarmEntity(hour = h, minute = m, mode = AlarmEntity.MODE_WEEKLY, daysMask = mask)
          for (offset in 0..8) {
            for (fromH in listOf(0, 7, 23)) {
              val from = anchor.plusDays(offset.toLong()).atTime(fromH, 17)
              val next = AlarmTimes.next(alarm, from)
              if (next != null) {
                assertTrue("next=$next не после from=$from (mask=$mask)", next.isAfter(from))
              }
            }
          }
        }
      }
    }
  }

  @Test
  fun next_isAlwaysStrictlyAfterFrom_shift() {
    val alarm = AlarmEntity(
      mode = AlarmEntity.MODE_SHIFT, presetId = "mdn", anchorEpochDay = anchor.toEpochDay()
    )
    for (offset in -5..20) {
      for (fromH in 0..23 step 3) {
        val from = anchor.plusDays(offset.toLong()).atTime(fromH, 42)
        val next = AlarmTimes.next(alarm, from)
        if (next != null) assertTrue("next=$next не после from=$from", next.isAfter(from))
      }
    }
  }

  // --- Инвариант: график целиком из выходных → null и завершение (нет бесконечного поиска) ---

  @Test
  fun nextAlarm_allOffSchedule_returnsNull() {
    val allOff = ShiftSchedule(
      ShiftPattern(listOf(ShiftType.off(), ShiftType.off("Отсыпной")), anchor)
    )
    assertNull(ShiftEngine.nextAlarm(anchor.atTime(0, 0), allOff))
  }

  // --- Инвариант: повреждённый cycleSpec не роняет планировщик ---

  @Test
  fun corruptCycleSpec_neverThrows() {
    val garbage = listOf("мусор", "|||", "999", "cat|99:99|x", "", "\t\n", "DAY|7:00", "|7:00|Работа")
    for (spec in garbage) {
      val alarm = AlarmEntity(
        mode = AlarmEntity.MODE_SHIFT, presetId = "2x2", cycleSpec = spec,
        anchorEpochDay = anchor.toEpochDay()
      )
      // Ни резолв базы, ни расчёт следующего звонка не должны бросать исключение.
      AlarmTimes.shiftBase(alarm)
      AlarmTimes.next(alarm, anchor.atTime(6, 0))
    }
  }

  // --- Инвариант: приоритет резолва исключение > подмена > период > ротация ---

  @Test
  fun resolvePriority_exception_swap_offPeriod_rotation() {
    val day = ShiftType("d", "День", LocalTime.of(7, 0), ShiftCategory.DAY)
    val night = ShiftType("n", "Ночь", LocalTime.of(21, 0), ShiftCategory.NIGHT)
    val morning = ShiftType("m", "Утро", LocalTime.of(5, 0), ShiftCategory.MORNING)
    val d = anchor
    val everyDay = ShiftPattern(listOf(day), anchor) // каждый день — «День»

    val full = ShiftSchedule(
      base = everyDay,
      swaps = listOf(TemporarySwap(d, d, night)),
      exceptions = mapOf(d to morning),
      offPeriods = listOf(OffPeriod(d, d)),
    )
    assertEquals(ShiftCategory.MORNING, ShiftEngine.shiftOn(d, full).category)   // исключение
    assertEquals(ShiftCategory.NIGHT, ShiftEngine.shiftOn(d, full.copy(exceptions = emptyMap())).category) // подмена
    assertEquals(
      ShiftCategory.OFF,
      ShiftEngine.shiftOn(d, full.copy(exceptions = emptyMap(), swaps = emptyList())).category
    ) // период
    assertEquals(ShiftCategory.DAY, ShiftEngine.shiftOn(d, everyDaySchedule(everyDay)).category) // ротация
  }

  private fun everyDaySchedule(pattern: ShiftPattern) = ShiftSchedule(base = pattern)
}
