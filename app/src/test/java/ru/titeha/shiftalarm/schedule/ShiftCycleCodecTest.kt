package ru.titeha.shiftalarm.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class ShiftCycleCodecTest {

  /** Сравниваем по смыслу (имя + время), id — внутренний, при decode генерируется. */
  private fun sig(slots: List<ShiftType>) = slots.map { it.name to it.wakeTime }

  @Test
  fun `пустой список — пустая строка и обратно`() {
    assertEquals("", ShiftCycleCodec.encode(emptyList()))
    assertTrue(ShiftCycleCodec.decode("").isEmpty())
  }

  @Test
  fun `цикл 2x2 переживает round-trip`() {
    val slots = listOf(
      ShiftType("d", "Работа", LocalTime.of(7, 0)),
      ShiftType("d", "Работа", LocalTime.of(7, 0)),
      ShiftType.off(),
      ShiftType.off()
    )
    val decoded = ShiftCycleCodec.decode(ShiftCycleCodec.encode(slots))
    assertEquals(sig(slots), sig(decoded))
  }

  @Test
  fun `утро-день-ночь-отсыпной round-trip`() {
    val slots = listOf(
      ShiftType("m", "Утро", LocalTime.of(5, 0)),
      ShiftType("d", "День", LocalTime.of(11, 0)),
      ShiftType("n", "Ночь", LocalTime.of(18, 0)),
      ShiftType.off("Отсыпной")
    )
    val decoded = ShiftCycleCodec.decode(ShiftCycleCodec.encode(slots))
    assertEquals(sig(slots), sig(decoded))
  }

  @Test
  fun `имя со спецсимволами экранируется и восстанавливается`() {
    val slots = listOf(
      ShiftType("x", "Смена A|B", LocalTime.of(8, 15)),
      ShiftType("y", "ночь\\день", null),
      ShiftType("z", "две\nстроки", LocalTime.of(23, 59))
    )
    val decoded = ShiftCycleCodec.decode(ShiftCycleCodec.encode(slots))
    assertEquals(sig(slots), sig(decoded))
  }

  @Test
  fun `выходной кодируется с категорией O и пустым временем`() {
    val encoded = ShiftCycleCodec.encode(listOf(ShiftType.off("Отдых")))
    assertEquals("O||Отдых", encoded)
    val decoded = ShiftCycleCodec.decode(encoded).single()
    assertEquals(null, decoded.wakeTime)
    assertEquals("Отдых", decoded.name)
  }

  @Test
  fun `категория переживает round-trip независимо от времени`() {
    val slots = listOf(
      ShiftType("n", "Ночь", null, ShiftCategory.NIGHT),            // ночь без звонка
      ShiftType("o", "Выходной", LocalTime.of(21, 0), ShiftCategory.OFF), // выходной со звонком
      ShiftType("d", "День", LocalTime.of(17, 0), ShiftCategory.DAY)      // 17:00, но это «День», не ночь
    )
    val decoded = ShiftCycleCodec.decode(ShiftCycleCodec.encode(slots))
    assertEquals(slots.map { it.category }, decoded.map { it.category })
    assertEquals(sig(slots), sig(decoded))
  }

  @Test
  fun `старый формат читается, категория выводится из времени`() {
    // Без категорий: HHMM|name и off|name (как писала прежняя версия).
    val decoded = ShiftCycleCodec.decode("0700|Работа\noff|Выходной")
    assertEquals(LocalTime.of(7, 0), decoded[0].wakeTime)
    assertEquals(ShiftCategory.MORNING, decoded[0].category)
    assertEquals(null, decoded[1].wakeTime)
    assertEquals(ShiftCategory.OFF, decoded[1].category)
  }
}
