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

  // --- Этап 2: нормализация и операции над блоками ---

  @Test
  fun `group идемпотентна`() {
    val slots = listOf(morning, morning, off, morning, off, off)
    val once = ShiftCycle.group(slots)
    val twice = ShiftCycle.group(ShiftCycle.expand(once))
    assertEquals(once, twice)
  }

  @Test
  fun `смена типа среднего блока склеивает соседей`() {
    // День, Утро, День → сменили средний Утро (авто-имя) на День → должно склеиться в День×3.
    val day = ShiftType("d", "День", LocalTime.of(13, 0), ShiftCategory.DAY)
    val slots = listOf(day, morning, day)
    val retyped = slots.toMutableList().also { it[1] = ShiftCycle.retype(it[1], ShiftCategory.DAY) }
    val runs = ShiftCycle.group(retyped)
    assertEquals(1, runs.size)
    assertEquals(3, runs[0].count)
    assertEquals("День", runs[0].slot.name)
    assertEquals(LocalTime.of(13, 0), runs[0].slot.wakeTime)
  }

  @Test
  fun `блоки с разными пользовательскими именами не сливаются`() {
    // Один тип/время, но пользовательские имена разные → отдельные блоки.
    val a = ShiftType("a", "Бригада-1", LocalTime.of(13, 0), ShiftCategory.DAY)
    val b = ShiftType("b", "Бригада-2", LocalTime.of(13, 0), ShiftCategory.DAY)
    val runs = ShiftCycle.group(listOf(a, b))
    assertEquals(2, runs.size)
  }

  @Test
  fun `retype на выходной выключает будильник, обратно — включает`() {
    val off1 = ShiftCycle.retype(morning, ShiftCategory.OFF)
    assertEquals(null, off1.wakeTime)
    assertEquals(ShiftCategory.OFF, off1.category)
    assertEquals("Выходной", off1.name)
    val back = ShiftCycle.retype(off1, ShiftCategory.DAY)
    assertEquals(LocalTime.of(13, 0), back.wakeTime)
    assertEquals("День", back.name)
  }

  @Test
  fun `distinctCategoryFrom выбирает тип, отличный от соседей`() {
    // Между Утро и Выходной новый блок не должен быть ни тем, ни другим → День (первый свободный).
    assertEquals(
      ShiftCategory.DAY,
      ShiftCycle.distinctCategoryFrom(setOf(ShiftCategory.MORNING, ShiftCategory.OFF))
    )
    // Если День и Утро заняты — следующий свободный по приоритету это Ночь.
    assertEquals(
      ShiftCategory.NIGHT,
      ShiftCycle.distinctCategoryFrom(setOf(ShiftCategory.DAY, ShiftCategory.MORNING))
    )
  }

  @Test
  fun `blockOf делает стандартный блок с будильником, выходной — без`() {
    val day = ShiftCycle.blockOf(ShiftCategory.DAY)
    assertEquals("День", day.name)
    assertEquals(LocalTime.of(13, 0), day.wakeTime)
    val off = ShiftCycle.blockOf(ShiftCategory.OFF)
    assertEquals(null, off.wakeTime)
  }

  @Test
  fun `вставка умного блока между соседями не сливается`() {
    // Утро, Выходной → вставили умный блок после Утро → он не Утро и не Выходной → 3 блока.
    val day = ShiftCycle.blockOf(
      ShiftCycle.distinctCategoryFrom(setOf(ShiftCategory.MORNING, ShiftCategory.OFF))
    )
    val runs = ShiftCycle.group(listOf(morning, day, off))
    assertEquals(3, runs.size)
  }

  @Test
  fun `retype сохраняет пользовательское имя`() {
    val custom = ShiftType("c", "Моя смена", LocalTime.of(8, 0), ShiftCategory.MORNING)
    val retyped = ShiftCycle.retype(custom, ShiftCategory.DAY)
    assertEquals("Моя смена", retyped.name)
    assertEquals(LocalTime.of(8, 0), retyped.wakeTime) // время пользователя не трогаем
  }
}
