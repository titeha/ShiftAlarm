package ru.titeha.shiftalarm.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmEventLogCodecTest {

  @Test
  fun encodeDecode_roundTrip() {
    val events = listOf(
      AlarmEvent(1000, AlarmEventType.SCHEDULED, "id=5 at=2026-07-10 07:00"),
      AlarmEvent(2000, AlarmEventType.FIRED, "id=5"),
    )
    assertEquals(events, AlarmEventLogCodec.decode(AlarmEventLogCodec.encode(events)))
  }

  @Test
  fun decode_skipsBadLines() {
    val text = "1000\tSCHEDULED\tok\nмусор\n\n3000\tUNKNOWN\tx\n4000\tFIRED\tid=7"
    val events = AlarmEventLogCodec.decode(text)
    assertEquals(2, events.size) // строки с мусором и неизвестным типом пропущены
    assertEquals(AlarmEventType.SCHEDULED, events[0].type)
    assertEquals(AlarmEventType.FIRED, events[1].type)
  }

  @Test
  fun sanitize_detailWithSeparators_keepsFormat() {
    val e = AlarmEvent(1000, AlarmEventType.SCHEDULED, "line1\twith\ttabs\nand newline")
    val decoded = AlarmEventLogCodec.decode(AlarmEventLogCodec.encode(listOf(e)))
    assertEquals(1, decoded.size)
    assertEquals("line1 with tabs and newline", decoded[0].detail)
  }

  @Test
  fun appendCapped_keepsMostRecent() {
    var events = emptyList<AlarmEvent>()
    for (i in 1..5) {
      events = AlarmEventLogCodec.appendCapped(events, AlarmEvent(i.toLong(), AlarmEventType.FIRED, "id=$i"), max = 3)
    }
    assertEquals(3, events.size)
    assertEquals(listOf(3L, 4L, 5L), events.map { it.atMillis }) // остались самые свежие
  }
}
