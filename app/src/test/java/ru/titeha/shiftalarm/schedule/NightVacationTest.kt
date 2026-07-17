package ru.titeha.shiftalarm.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * Баг «отпуск/больничный не глушит ночную смену на краю» (рассинхрон дня звонка D-1 и обслуживаемой
 * ночи D). Планировщик [ShiftEngine.nextAlarm] уже глушит по обслуживаемому дню; тут фиксируем и это,
 * и что точка-звонок в календаре ([ShiftEngine.wakeTimeOn] 3-арг) совпадает — не показывается на ночь,
 * помеченную отпуском.
 *
 * Модель Варианта Б: вечерний звонок 21:00 стоит на дне D-1 (слот «выходной со звонком»), а сама ночь
 * помечена на D (слот NIGHT без звонка).
 */
class NightVacationTest {

  private val d0 = LocalDate.of(2026, 6, 22)   // «выходной» со звонком 21:00 — служит ночи d1
  private val d1 = d0.plusDays(1)              // ночь (без своего звонка)

  private val offCarryingNight = ShiftType("o", "Выходной", LocalTime.of(21, 0), ShiftCategory.OFF)
  private val night = ShiftType("n", "Ночь", null, ShiftCategory.NIGHT)
  private val pattern = ShiftPattern(listOf(offCarryingNight, night), anchorDate = d0)

  @Test
  fun `без отпуска звонок вечером d0 служит ночи d1`() {
    val schedule = ShiftSchedule(pattern)
    assertEquals(LocalTime.of(21, 0), ShiftEngine.wakeTimeOn(d0, schedule, null))
    // Планировщик тоже звонит d0 в 21:00.
    assertEquals(d0.atTime(21, 0), ShiftEngine.nextAlarm(d0.atTime(6, 0), schedule))
  }

  @Test
  fun `отпуск на ночь d1 глушит звонок вечером d0 — и точку, и планировщик`() {
    val vacation = ShiftSchedule(pattern, offPeriods = listOf(OffPeriod(d1, d1, "отпуск")))

    // Точка-звонок в календаре на d0 скрыта: обслуживаемая ночь d1 в отпуске (был баг — показывалась).
    assertNull(ShiftEngine.wakeTimeOn(d0, vacation, null))

    // Планировщик не ставит звонок на d0 21:00 (ночь d1 в отпуске).
    assertNotEquals(d0.atTime(21, 0), ShiftEngine.nextAlarm(d0.atTime(6, 0), vacation))
  }

  @Test
  fun `отпуск на день звонка d0 не стирает звонок, служащий ночи d1`() {
    // Отпуск на D-1 (день звонка), но НЕ на обслуживаемой ночи D → звонок остаётся (служит ночи d1).
    val vacation = ShiftSchedule(pattern, offPeriods = listOf(OffPeriod(d0, d0, "отпуск")))
    assertEquals(LocalTime.of(21, 0), ShiftEngine.wakeTimeOn(d0, vacation, null))
    assertEquals(d0.atTime(21, 0), ShiftEngine.nextAlarm(d0.atTime(6, 0), vacation))
  }
}
