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
  fun `выходной кодируется как off без времени`() {
    val encoded = ShiftCycleCodec.encode(listOf(ShiftType.off("Отдых")))
    assertEquals("off|Отдых", encoded)
    val decoded = ShiftCycleCodec.decode(encoded).single()
    assertEquals(null, decoded.wakeTime)
    assertEquals("Отдых", decoded.name)
  }

  @Test
  fun `рабочий слот кодирует время как HHMM`() {
    val encoded = ShiftCycleCodec.encode(listOf(ShiftType("d", "Смена", LocalTime.of(7, 5))))
    assertEquals("0705|Смена", encoded)
  }
}
