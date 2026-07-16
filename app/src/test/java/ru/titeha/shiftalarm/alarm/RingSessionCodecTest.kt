package ru.titeha.shiftalarm.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RingSessionCodecTest {

  @Test
  fun `round trip списка`() {
    val list = listOf(
      RingSessionState(alarmId = 1L, scheduledTriggerAtMillis = 1_700_000_000_000L, snoozeCount = 0, phase = RingPhase.RINGING),
      RingSessionState(alarmId = 42L, scheduledTriggerAtMillis = 1_700_003_600_000L, snoozeCount = 2, phase = RingPhase.SNOOZING),
    )
    val decoded = RingSessionCodec.decodeOrNull(RingSessionCodec.encode(list))
    assertEquals(list, decoded)
  }

  @Test
  fun `пустой список`() {
    val encoded = RingSessionCodec.encode(emptyList())
    assertEquals("v1", encoded)
    assertEquals(emptyList<RingSessionState>(), RingSessionCodec.decodeOrNull(encoded))
  }

  @Test
  fun `повреждённая строка не декодируется`() {
    assertNull(RingSessionCodec.decodeOrNull("это не наш формат"))
    assertNull(RingSessionCodec.decodeOrNull("v1\n1|100|нечисло|RINGING"))
    assertNull(RingSessionCodec.decodeOrNull("v1\n1|100|0|НЕТ_ТАКОЙ_ФАЗЫ"))
    assertNull(RingSessionCodec.decodeOrNull("v2\n1|100|0|RINGING")) // чужая версия
  }
}
