package ru.titeha.shiftalarm.alarm

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * TL02: чистый stale-детект пропущенных звонков по DE-кэшу.
 * Вызывается ДО перезаписей кэша (иначе перезапись скроет просроченные записи —
 * инцидент 2026-10-10).
 */
class StaleMissedTest {

  private fun cached(id: Long, at: Long) = CachedAlarm(alarmId = id, triggerAtMillis = at, label = "L$id")

  @Test
  fun `просроченная сверх грейса запись - пропуск`() {
    val now = 1_000_000L
    val grace = 90_000L
    val old = cached(1, now - grace - 1)
    assertEquals(listOf(old), staleMissed(listOf(old), now, grace))
  }

  @Test
  fun `запись в пределах грейса - не пропуск`() {
    val now = 1_000_000L
    val grace = 90_000L
    val fresh = cached(2, now - grace + 1)
    assertEquals(emptyList<CachedAlarm>(), staleMissed(listOf(fresh), now, grace))
  }

  @Test
  fun `будущая запись и пустой кэш - не пропуски`() {
    val now = 1_000_000L
    assertEquals(emptyList<CachedAlarm>(), staleMissed(listOf(cached(3, now + 60_000)), now, 90_000)
    )
    assertEquals(emptyList<CachedAlarm>(), staleMissed(emptyList(), now, 90_000))
  }
}
