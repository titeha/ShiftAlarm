package ru.titeha.shiftalarm.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class AlarmInstantTest {

  // В США переходы DST в 2026: вперёд 8 марта 02:00→03:00, назад 1 ноября 02:00→01:00.
  private val ny = ZoneId.of("America/New_York")

  @Test
  fun `обычное время переводится по зоне`() {
    val local = LocalDateTime.of(2026, 6, 1, 7, 0) // лето, EDT −4
    val instant = AlarmInstant.of(local, ny)
    assertEquals(local.atZone(ny).toInstant(), instant)
    assertEquals("2026-06-01T11:00:00Z", instant.toString()) // 07:00 −04:00 == 11:00 UTC
  }

  @Test
  fun `пробел DST — момент сдвигается вперёд`() {
    // Локального 02:30 в этот день не существует (часы прыгнули 02:00→03:00).
    val gap = LocalDateTime.of(2026, 3, 8, 2, 30)
    val resolved = AlarmInstant.of(gap, ny).atZone(ny)
    assertEquals(LocalTime.of(3, 30), resolved.toLocalTime()) // сдвиг вперёд на длину пробела
    assertEquals(ZoneOffset.ofHours(-4), resolved.offset)     // уже новый (летний) offset
  }

  @Test
  fun `наложение DST — первое вхождение, будильник не звонит дважды`() {
    // Локальное 01:30 бывает дважды (часы откатились 02:00→01:00).
    val overlap = LocalDateTime.of(2026, 11, 1, 1, 30)
    val instant = AlarmInstant.of(overlap, ny)
    // Берётся ПЕРВОЕ вхождение — более ранний offset EDT −4, а не EST −5.
    assertEquals(ZoneOffset.ofHours(-4), instant.atZone(ny).offset)
    assertEquals(overlap.atZone(ny).withEarlierOffsetAtOverlap().toInstant(), instant)
    // Второе вхождение (на час позже) — другой момент, будильник его не использует.
    assertNotEquals(overlap.atZone(ny).withLaterOffsetAtOverlap().toInstant(), instant)
  }

  @Test
  fun `результат зависит от часового пояса`() {
    val local = LocalDateTime.of(2026, 6, 1, 7, 0)
    assertNotEquals(
      AlarmInstant.of(local, ZoneId.of("Europe/Berlin")),
      AlarmInstant.of(local, ny)
    )
  }
}
