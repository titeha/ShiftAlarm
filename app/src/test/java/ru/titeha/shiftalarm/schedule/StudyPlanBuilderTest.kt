package ru.titeha.shiftalarm.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

class StudyPlanBuilderTest {

  private val wed = LocalDate.of(2026, 6, 24)   // среда
  private val sun = LocalDate.of(2026, 6, 28)   // воскресенье
  private val mondayThisWeek = LocalDate.of(2026, 6, 22)

  /** Слот сегодняшнего дня в собранном плане. */
  private fun todaySlot(plan: StudyPlan, today: LocalDate): ShiftType {
    val fromAnchor = ChronoUnit.DAYS.between(plan.anchorDate, today)
    val idx = Math.floorMod(fromAnchor, plan.slots.size.toLong()).toInt()
    return plan.slots[idx]
  }

  private fun sevenDay() = List<LocalTime?>(7) { if (it < 5) LocalTime.of(7, 0) else null }

  // Неделя А — подъём 08:00, неделя Б — 09:00 (Пн–Пт), Сб/Вс выходные.
  private fun fourteenDay() = List<LocalTime?>(14) { i ->
    val inWeek = i % 7
    when {
      inWeek >= 5 -> null
      i < 7 -> LocalTime.of(8, 0)
      else -> LocalTime.of(9, 0)
    }
  }

  @Test
  fun `7 дней, создание в среду — якорь понедельник этой недели, слот совпадает с сеткой`() {
    val plan = StudyPlanBuilder.build(sevenDay(), parityToday = null, today = wed)
    assertEquals(mondayThisWeek, plan.anchorDate)
    assertEquals(7, plan.slots.size)
    // Среда — индекс 2, подъём 07:00.
    assertEquals(LocalTime.of(7, 0), todaySlot(plan, wed).wakeTime)
  }

  @Test
  fun `14 дней, сейчас нечётная, среда — сегодняшний слот из недели А`() {
    val plan = StudyPlanBuilder.build(fourteenDay(), parityToday = Parity.ODD, today = wed)
    assertEquals(mondayThisWeek, plan.anchorDate)
    assertEquals(LocalTime.of(8, 0), todaySlot(plan, wed).wakeTime) // неделя А
  }

  @Test
  fun `14 дней, сейчас чётная — якорь понедельник прошлой недели, слот из недели Б`() {
    val plan = StudyPlanBuilder.build(fourteenDay(), parityToday = Parity.EVEN, today = wed)
    assertEquals(mondayThisWeek.minusWeeks(1), plan.anchorDate)
    assertEquals(LocalTime.of(9, 0), todaySlot(plan, wed).wakeTime) // неделя Б
  }

  @Test
  fun `создание в воскресенье — без сдвига на единицу`() {
    val seven = StudyPlanBuilder.build(sevenDay(), null, sun)
    assertEquals(mondayThisWeek, seven.anchorDate)
    assertEquals(DayOfWeek.SUNDAY, sun.dayOfWeek)
    // Воскресенье — индекс 6 (последний день недели А), выходной.
    assertEquals(null, todaySlot(seven, sun).wakeTime)

    val evenSun = StudyPlanBuilder.build(fourteenDay(), Parity.EVEN, sun)
    assertEquals(mondayThisWeek.minusWeeks(1), evenSun.anchorDate)
    assertEquals(null, todaySlot(evenSun, sun).wakeTime) // Вс недели Б — тоже выходной
  }

  @Test
  fun `граница года — якорь корректен`() {
    val fri = LocalDate.of(2027, 1, 1) // пятница
    val plan = StudyPlanBuilder.build(sevenDay(), null, fri)
    assertEquals(LocalDate.of(2026, 12, 28), plan.anchorDate) // понедельник предыдущего года
    assertEquals(LocalTime.of(7, 0), todaySlot(plan, fri).wakeTime) // пятница — рабочий
  }

  @Test
  fun `пустая сетка не собирается`() {
    assertThrows(IllegalArgumentException::class.java) {
      StudyPlanBuilder.build(List(7) { null }, null, wed)
    }
  }

  @Test
  fun `неверный размер сетки — ошибка`() {
    assertThrows(IllegalArgumentException::class.java) {
      StudyPlanBuilder.build(List(10) { LocalTime.of(7, 0) }, null, wed)
    }
  }

  @Test
  fun `14 дней без чётности — ошибка`() {
    assertThrows(IllegalArgumentException::class.java) {
      StudyPlanBuilder.build(fourteenDay(), parityToday = null, today = wed)
    }
  }

  @Test
  fun `выходные дают слоты-выходные`() {
    val plan = StudyPlanBuilder.build(sevenDay(), null, wed)
    assertEquals(ShiftCategory.OFF, plan.slots[5].category) // суббота
    assertEquals(ShiftCategory.DAY, plan.slots[0].category)  // понедельник — учебный «День»
  }
}
