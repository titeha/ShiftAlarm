package ru.titeha.shiftalarm.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AUD-17 (BKL1): чистое ядро группировки совпадающих звонков.
 */
class RingGroupTest {

  private val defaultText = "Будильник"
  private val window = 60_000L

  private fun m(id: Long, at: Long, label: String = "L$id") = RingGroupMember(id, label, at)

  // --- окно объединения ---

  @Test
  fun `два совпадающих - группа из двух, порядок по времени и id`() {
    var group = emptyList<RingGroupMember>()
    assertTrue(ringGroupCanJoin(group, m(2, 1000), window))
    group = ringGroupWith(group, m(2, 1000))
    assertTrue(ringGroupCanJoin(group, m(1, 1000), window))
    group = ringGroupWith(group, m(1, 1000))
    assertEquals(listOf(1L, 2L), group.map { it.alarmId })
  }

  @Test
  fun `три совпадающих объединяются`() {
    var group = ringGroupWith(emptyList(), m(3, 1000))
    group = ringGroupWith(group, m(1, 1000))
    group = ringGroupWith(group, m(2, 1000))
    assertEquals(listOf(1L, 2L, 3L), group.map { it.alarmId })
  }

  @Test
  fun `граница окна - ровно окно внутрь, окно плюс 1 наружу`() {
    val group = listOf(m(1, 10_000))
    assertTrue(ringGroupCanJoin(group, m(2, 10_000 + window), window))
    assertFalse(ringGroupCanJoin(group, m(2, 10_000 + window + 1), window))
    assertTrue(ringGroupCanJoin(group, m(2, 10_000 - window), window))
    assertFalse(ringGroupCanJoin(group, m(2, 10_000 - window - 1), window))
  }

  @Test
  fun `присоединение к звенящей группе не дублирует и не переупорядочивает`() {
    val group = listOf(m(1, 1000), m(3, 1000))
    val joined = ringGroupWith(group, m(2, 1000))
    assertEquals(listOf(1L, 2L, 3L), joined.map { it.alarmId })
    // повторный участник идемпотентен
    assertEquals(joined, ringGroupWith(joined, m(2, 1000)))
  }

  @Test
  fun `группа из одного - поведение как раньше`() {
    val group = listOf(m(7, 5000, label = "Работа"))
    assertEquals(listOf("Работа"), ringGroupDisplayNames(group, defaultText))
    assertEquals("Работа", ringGroupSummary(group, defaultText))
  }

  // --- схлопывание названий ---

  @Test
  fun `все без метки схлопываются в одну строку дефолтного текста`() {
    val group = listOf(m(1, 1000, ""), m(2, 1000, ""), m(3, 1000, "   "))
    assertEquals(listOf(defaultText), ringGroupDisplayNames(group, defaultText))
    assertEquals(defaultText, ringGroupSummary(group, defaultText))
  }

  @Test
  fun `часть без метки - одна строка дефолтного плюс именованные`() {
    val group = listOf(m(1, 1000, ""), m(2, 1000, "Таблетки"), m(3, 1000, ""))
    assertEquals(listOf(defaultText, "Таблетки"), ringGroupDisplayNames(group, defaultText))
  }

  @Test
  fun `два одинаковых названия - одно вхождение`() {
    val group = listOf(m(1, 1000, "Работа"), m(2, 1000, "Работа"), m(3, 1000, "Отдых"))
    assertEquals(listOf("Работа", "Отдых"), ringGroupDisplayNames(group, defaultText))
  }

  @Test
  fun `порядок названий устойчив по времени затем id`() {
    val group = listOf(m(5, 2000, "Позже"), m(2, 1000, "Раньше"), m(1, 1000, "Первее"))
    assertEquals(listOf("Первее", "Раньше", "Позже"), ringGroupDisplayNames(group, defaultText))
  }

  // --- усечение ---

  @Test
  fun `усечение при большом числе названий`() {
    val group = listOf(
      m(1, 1000, "Подъём"),
      m(2, 1000, "Таблетки"),
      m(3, 1000, "Зарядка"),
      m(4, 1000, "Кофе"),
    )
    assertEquals("Подъём, Таблетки и ещё 2", ringGroupSummary(group, defaultText, maxNames = 3))
  }

  @Test
  fun `усечение с учётом схлопывания`() {
    // 3 уникальные строки (defaultText + 2 имени) при maxNames=3 → без «и ещё»
    val group = listOf(m(1, 1000, ""), m(2, 1000, "A"), m(3, 1000, "B"), m(4, 1000, ""))
    assertEquals("$defaultText, A, B", ringGroupSummary(group, defaultText, maxNames = 3))
  }
}
