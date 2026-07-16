package ru.titeha.shiftalarm.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DirectBootEventBufferCodecTest {

  @Test
  fun `round trip списка`() {
    val list = listOf(
      BufferedEvent(atMillis = 1_700_000_000_000L, type = "RESCHEDULED", detail = "перевыставлены из кэша"),
      BufferedEvent(atMillis = 1_700_003_600_000L, type = "SIGNAL_DEGRADED", detail = "только вибрация")
    )
    val decoded = DirectBootEventBufferCodec.decodeOrNull(
      DirectBootEventBufferCodec.encode(list)
    )
    assertEquals(list, decoded)
  }

  @Test
  fun `пустой список`() {
    val encoded = DirectBootEventBufferCodec.encode(emptyList())
    assertEquals("v1", encoded)
    assertEquals(emptyList<BufferedEvent>(), DirectBootEventBufferCodec.decodeOrNull(encoded))
  }

  @Test
  fun `детали со спецсимволами и переводом строки сохраняются`() {
    val list = listOf(
      BufferedEvent(100L, "ERROR", "id=7 | ошибка\nвторая строка")
    )
    val decoded = DirectBootEventBufferCodec.decodeOrNull(
      DirectBootEventBufferCodec.encode(list)
    )
    assertEquals(list, decoded)
  }

  @Test
  fun `повреждённая строка не декодируется`() {
    assertNull(DirectBootEventBufferCodec.decodeOrNull("это не наш формат"))
    assertNull(DirectBootEventBufferCodec.decodeOrNull("v1\nнечисло|YQ|Yg"))
    assertNull(DirectBootEventBufferCodec.decodeOrNull("v2\n100|YQ|Yg")) // чужая версия
  }
}
