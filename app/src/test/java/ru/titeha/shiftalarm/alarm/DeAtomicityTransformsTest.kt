package ru.titeha.shiftalarm.alarm

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * AUD-3: чистые трансформации DE-хранилищ (B-05/F-02). Read-modify-write вынесен из Android-обёрток
 * в чистые функции, чтобы логику можно было проверить без SharedPreferences (Robolectric в проекте нет).
 * Включая фиксированный контрпример «потери обновления» при наивном RMW.
 */
class DeAtomicityTransformsTest {

  private fun cached(id: Long, at: Long) =
    CachedAlarm(alarmId = id, triggerAtMillis = at, label = "L$id")

  private fun session(id: Long, at: Long = 0L, snooze: Int = 0) =
    RingSessionState(alarmId = id, scheduledTriggerAtMillis = at, snoozeCount = snooze)

  private fun event(at: Long, type: String = "FIRED") =
    BufferedEvent(atMillis = at, type = type, detail = "d$at")

  // --- DirectBootAlarmStore ---

  @Test
  fun `withoutBase убирает все записи будильника, чужие хранит`() {
    val cache = listOf(cached(1, 100), cached(1, 200), cached(2, 300))
    assertEquals(listOf(cached(2, 300)), withoutBase(cache, 1L))
  }

  @Test
  fun `withoutBase отсутствующего будильника не меняет список`() {
    val cache = listOf(cached(1, 100), cached(2, 200))
    assertEquals(cache, withoutBase(cache, 9L))
  }

  @Test
  fun `withSnooze обновляет существующий и добавляет новый в конец`() {
    val base = listOf(cached(1, 100), cached(2, 200))
    assertEquals(listOf(cached(1, 100), cached(2, 999)), withSnooze(base, cached(2, 999)))
    assertEquals(
      listOf(cached(1, 100), cached(2, 200), cached(3, 300)),
      withSnooze(base, cached(3, 300)),
    )
  }

  @Test
  fun `withoutSnooze убирает только свой будильник`() {
    val base = listOf(cached(1, 100), cached(2, 200))
    assertEquals(listOf(cached(2, 200)), withoutSnooze(base, 1L))
    assertEquals(base, withoutSnooze(base, 9L))
  }

  @Test
  fun `withMissed добавляет и хранит не больше max`() {
    val existing = listOf(cached(1, 100), cached(2, 200))
    val missed = listOf(cached(3, 300), cached(4, 400), cached(5, 500))
    assertEquals(
      listOf(cached(3, 300), cached(4, 400), cached(5, 500)),
      withMissed(existing, missed, max = 3),
    )
  }

  @Test
  fun `withMissed без новых записей возвращает накопленное`() {
    val existing = listOf(cached(1, 100))
    assertEquals(existing, withMissed(existing, emptyList(), max = 10))
  }

  // --- RingSessionStore ---

  @Test
  fun `withSession обновляет сессию по alarmId и хранит порядок`() {
    val base = listOf(session(1, 100), session(2, 200))
    assertEquals(
      listOf(session(1, 100), session(2, 999, snooze = 2)),
      withSession(base, session(2, 999, snooze = 2)),
    )
    assertEquals(
      listOf(session(1, 100), session(2, 200), session(3, 300)),
      withSession(base, session(3, 300)),
    )
  }

  @Test
  fun `withoutSession убирает только указанную сессию`() {
    val base = listOf(session(1, 100), session(2, 200))
    assertEquals(listOf(session(2, 200)), withoutSession(base, 1L))
    assertEquals(base, withoutSession(base, 9L))
  }

  // --- DirectBootEventBuffer ---

  @Test
  fun `withEvent добавляет событие и хранит не больше max`() {
    val base = listOf(event(1), event(2))
    assertEquals(listOf(event(1), event(2), event(3)), withEvent(base, event(3), max = 5))
    assertEquals(listOf(event(2), event(3)), withEvent(base, event(3), max = 2))
  }

  // --- контрпример «потери обновления» ---

  @Test
  fun `наивный RMW теряет параллельное обновление, трансформация от свежего состояния - нет`() {
    val snapshot = emptyList<RingSessionState>() // оба «потока» прочитали один и тот же список
    val fromA = withSession(snapshot, session(1, 100))
    val fromB = withSession(snapshot, session(2, 200)) // то же исходное snapshot
    // последняя запись затирает первую — сессия A потеряна
    assertEquals(listOf(session(2, 200)), fromB)
    // под замком вторая трансформация идёт от свежего состояния — обе сессии сохранены
    assertEquals(listOf(session(1, 100), session(2, 200)), withSession(fromA, session(2, 200)))
  }

  // --- round-trip кодеков после трансформаций ---

  @Test
  fun `кодек кэша переживает трансформации`() {
    val transformed = withSnooze(
      withoutBase(listOf(cached(1, 100), cached(2, 200)), 1L),
      cached(3, 300),
    )
    assertEquals(
      transformed,
      DirectBootAlarmCacheCodec.decodeOrNull(DirectBootAlarmCacheCodec.encode(transformed)),
    )
  }

  @Test
  fun `кодек сессий переживает трансформации`() {
    val transformed = withSession(
      withoutSession(listOf(session(1, 100), session(2, 200)), 1L),
      session(3, 300),
    )
    assertEquals(transformed, RingSessionCodec.decodeOrNull(RingSessionCodec.encode(transformed)))
  }

  @Test
  fun `кодек буфера событий переживает трансформации`() {
    val transformed = withEvent(listOf(event(1)), event(2), max = 5)
    assertEquals(
      transformed,
      DirectBootEventBufferCodec.decodeOrNull(DirectBootEventBufferCodec.encode(transformed)),
    )
  }
}
