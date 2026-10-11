package ru.titeha.shiftalarm.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AUD-13 (R-04): выбор подмножества DE-плана — ближайшая запись каждого будильника гарантирована.
 */
class PlanSelectionTest {

  private fun e(id: Long, at: Long) = CachedAlarm(alarmId = id, triggerAtMillis = at, label = "L$id")

  @Test
  fun `поздний будильник не вытесняется ранними событиями`() {
    // A: 4 события, B: 1 — при лимите 4 ближайшая запись B обязана сохраниться.
    val a = (1..4).map { e(1, it * 1000L) }
    val b = listOf(e(2, 5000))
    val result = selectPlanEntries(listOf(a, b), maxPerAlarm = 4, maxTotal = 4)
    assertTrue("B должен остаться в плане", result.any { it.alarmId == 2L })
    assertEquals(4, result.size)
  }

  @Test
  fun `ровно 32 будильника - у каждого есть ближайшая запись`() {
    val perAlarm = (1..32).map { listOf(e(it.toLong(), it * 1000L)) }
    val result = selectPlanEntries(perAlarm, maxPerAlarm = 4, maxTotal = 32)
    assertEquals(32, result.size)
    assertEquals((1L..32L).toSet(), result.map { it.alarmId }.toSet())
  }

  @Test
  fun `больше 32 будильников - ни один не теряется (гарантия по одной записи)`() {
    // Мягкий лимит: лучше чуть больше DE-записей, чем будильник без recovery-плана.
    val perAlarm = (1..40).map { listOf(e(it.toLong(), it * 1000L)) }
    val result = selectPlanEntries(perAlarm, maxPerAlarm = 4, maxTotal = 32)
    assertEquals(40, result.size)
    assertEquals((1L..40L).toSet(), result.map { it.alarmId }.toSet())
  }

  @Test
  fun `пустой план - пусто`() {
    assertTrue(selectPlanEntries(emptyList(), maxPerAlarm = 4, maxTotal = 32).isEmpty())
  }
}
