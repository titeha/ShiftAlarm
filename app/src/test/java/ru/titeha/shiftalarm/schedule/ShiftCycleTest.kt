package ru.titeha.shiftalarm.schedule

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class ShiftCycleTest {

  private val morning = ShiftType("m", "Утро", LocalTime.of(5, 0), ShiftCategory.MORNING)
  private val off = ShiftType.off()

  @Test
  fun `подряд идущие одинаковые сворачиваются в одну запись`() {
    val runs = ShiftCycle.group(listOf(morning, morning, morning, off, off))
    assertEquals(2, runs.size)
    assertEquals(3, runs[0].count)
    assertEquals("Утро", runs[0].slot.name)
    assertEquals(2, runs[1].count)
  }

  @Test
  fun `одинаковые, но не подряд — разные записи`() {
    val runs = ShiftCycle.group(listOf(morning, off, morning))
    assertEquals(3, runs.size)
    assertEquals(1, runs[0].count)
    assertEquals(1, runs[2].count)
  }

  @Test
  fun `различие по будильнику не сворачивается`() {
    // Две «Ночи»: со звонком и без — это разные записи (важно для Варианта Б).
    val nightAlarm = ShiftType("n", "Ночь", LocalTime.of(21, 0), ShiftCategory.NIGHT)
    val nightSilent = ShiftType("n", "Ночь", null, ShiftCategory.NIGHT)
    val runs = ShiftCycle.group(listOf(nightAlarm, nightAlarm, nightSilent))
    assertEquals(2, runs.size)
    assertEquals(2, runs[0].count)
    assertEquals(1, runs[1].count)
  }

  @Test
  fun `group и expand взаимно обратны`() {
    val slots = listOf(morning, morning, off, morning)
    assertEquals(slots, ShiftCycle.expand(ShiftCycle.group(slots)))
  }

  @Test
  fun `пустой цикл`() {
    assertEquals(emptyList<SlotRun>(), ShiftCycle.group(emptyList()))
    assertEquals(emptyList<ShiftType>(), ShiftCycle.expand(emptyList()))
  }
}
