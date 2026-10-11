package ru.titeha.shiftalarm.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AUD-2: device-protected план срабатываний — чистые функции, доступные в locked-режиме без Room.
 * Покрывают: построение плана (несколько записей на будильник), выбор ближайшего будущего
 * срабатывания, уборку ровно сработавшей записи, флаг разового и обратную совместимость кодека (v1).
 */
class LockedPlanTest {

  private fun cached(id: Long, at: Long, oneShot: Boolean = false) =
    CachedAlarm(alarmId = id, triggerAtMillis = at, label = "L$id", oneShot = oneShot)

  // --- planEntries ---

  @Test
  fun `план даёт по записи на каждое будущее срабатывание`() {
    val entries = planEntries(7L, "Работа", listOf(1000L, 2000L, 3000L), oneShot = false)
    assertEquals(listOf(1000L, 2000L, 3000L), entries.map { it.triggerAtMillis })
    assertTrue(entries.all { it.alarmId == 7L && it.label == "Работа" && !it.oneShot })
  }

  @Test
  fun `план разового - одна запись с флагом oneShot`() {
    val entries = planEntries(9L, "Разовый", listOf(5000L), oneShot = true)
    assertEquals(1, entries.size)
    assertTrue(entries.single().oneShot)
  }

  @Test
  fun `пустой список срабатываний - пустой план`() {
    assertEquals(emptyList<CachedAlarm>(), planEntries(1L, "x", emptyList(), oneShot = false))
  }

  // --- nextCached ---

  @Test
  fun `nextCached возвращает ближайшую будущую запись`() {
    val cache = listOf(cached(1, 1000), cached(1, 3000), cached(1, 2000))
    assertEquals(2000L, nextCached(cache, 1L, nowMillis = 1500)?.triggerAtMillis)
  }

  @Test
  fun `nextCached игнорирует прошлое и чужие будильники`() {
    val cache = listOf(cached(1, 1000), cached(2, 5000))
    assertNull(nextCached(cache, 1L, nowMillis = 1500))
  }

  @Test
  fun `nextCached без будущих записей - null`() {
    assertNull(nextCached(listOf(cached(1, 1000)), 1L, nowMillis = 999_999))
  }

  // --- removeFired ---

  @Test
  fun `removeFired убирает ровно сработавшую и хранит остальные`() {
    val cache = listOf(cached(1, 1000), cached(1, 2000), cached(1, 3000), cached(2, 2500))
    val rest = removeFired(cache, alarmId = 1L, firedAtMillis = 2000)
    assertEquals(listOf(1000L, 3000L, 2500L), rest.map { it.triggerAtMillis })
  }

  @Test
  fun `removeFired не трогает записи других будильников`() {
    val cache = listOf(cached(1, 1000), cached(2, 1000))
    assertEquals(listOf(cached(2, 1000)), removeFired(cache, 1L, 1000))
  }

  @Test
  fun `removeFired со сдвинутым временем убирает самую раннюю не позже сработавшей`() {
    // locked re-arm мог сдвинуть звонок на «сейчас» (2500) из записи 1000 → убираем именно её.
    val cache = listOf(cached(1, 1000), cached(1, 2000))
    assertEquals(listOf(cached(1, 2000)), removeFired(cache, 1L, 2500))
  }

  // --- AUD-11 (R-03): recovery-план повторяющегося после обрыва до Room-перепланирования ---

  @Test
  fun `AUD-11 после обрыва до reschedule в плане остаётся следующая запись`() {
    val day = 86_400_000L
    val plan = listOf(cached(1, 1_000), cached(1, 1_000 + day), cached(1, 1_000 + 2 * day))
    val after = removeFired(plan, alarmId = 1L, firedAtMillis = 1_000)
    // Следующий recovery entry сохранён и может быть поставлен без обращения к Room.
    assertEquals(1_000 + day, nextCached(after, 1L, nowMillis = 1_001)?.triggerAtMillis)
  }

  @Test
  fun `AUD-11 разовый после firing - в плане пусто, следующего нет`() {
    val after = removeFired(listOf(cached(9, 5_000)), alarmId = 9L, firedAtMillis = 5_000)
    assertTrue(after.isEmpty())
    assertNull(nextCached(after, 9L, nowMillis = 5_001))
  }

  // --- codec ---

  @Test
  fun `encode-decode v2 сохраняет флаг разового`() {
    val src = listOf(cached(1, 1000, oneShot = true), cached(2, 2000, oneShot = false))
    assertEquals(src, DirectBootAlarmCacheCodec.decodeOrNull(DirectBootAlarmCacheCodec.encode(src)))
  }

  @Test
  fun `decode читает старый v1-формат без флага разового`() {
    // v1: id|millis|base64url(label); base64url("A")="QQ", base64url("B")="Qg".
    val decoded = DirectBootAlarmCacheCodec.decodeOrNull("v1\n1|1000|QQ\n2|2000|Qg")
    assertEquals(
      listOf(
        CachedAlarm(1, 1000, "A"),
        CachedAlarm(2, 2000, "B"),
      ),
      decoded
    )
  }

  @Test
  fun `decode неизвестной версии возвращает null`() {
    assertNull(DirectBootAlarmCacheCodec.decodeOrNull("v9\n1|1000|QQ|0"))
  }
}
