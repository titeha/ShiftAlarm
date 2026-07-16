package ru.titeha.shiftalarm.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DirectBootAlarmCacheCodecTest {

  @Test
  fun `round trip списка`() {
    val list = listOf(
      CachedAlarm(alarmId = 1L, triggerAtMillis = 1_700_000_000_000L, label = "Работа"),
      CachedAlarm(alarmId = 42L, triggerAtMillis = 1_700_003_600_000L, label = "Ночная смена")
    )
    val decoded = DirectBootAlarmCacheCodec.decodeOrNull(
      DirectBootAlarmCacheCodec.encode(list)
    )
    assertEquals(list, decoded)
  }

  @Test
  fun `пустой список`() {
    val encoded = DirectBootAlarmCacheCodec.encode(emptyList())
    assertEquals("v1", encoded)
    assertEquals(emptyList<CachedAlarm>(), DirectBootAlarmCacheCodec.decodeOrNull(encoded))
  }

  @Test
  fun `метка со спецсимволами и переводом строки сохраняется`() {
    val list = listOf(
      CachedAlarm(1L, 100L, "Участок №7 | смена\nвторая строка")
    )
    val decoded = DirectBootAlarmCacheCodec.decodeOrNull(
      DirectBootAlarmCacheCodec.encode(list)
    )
    assertEquals(list, decoded)
  }

  @Test
  fun `повреждённая строка не декодируется`() {
    assertNull(DirectBootAlarmCacheCodec.decodeOrNull("это не наш формат"))
    assertNull(DirectBootAlarmCacheCodec.decodeOrNull("v1\n1|нечисло|abc"))
    assertNull(DirectBootAlarmCacheCodec.decodeOrNull("v2\n1|100|YQ")) // чужая версия
  }
}
