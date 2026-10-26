package ru.titeha.shiftalarm.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.titeha.shiftalarm.data.AlarmEntity
import java.time.LocalDateTime

/**
 * NF1: правило предварительной плашки «час до звонка» — чистая логика без Android.
 */
class PreRingPolicyTest {

  private val now = LocalDateTime.of(2026, 10, 10, 12, 0)

  @Test
  fun `звонок через 61 минуту - пред-сигнал за час`() {
    assertEquals(
      LocalDateTime.of(2026, 10, 10, 12, 1),
      PreRingPolicy.preRingAt(now.plusMinutes(61), now)
    )
  }

  @Test
  fun `звонок через 59 минут - пред-сигнал не ставится`() {
    assertNull(PreRingPolicy.preRingAt(now.plusMinutes(59), now))
  }

  @Test
  fun `звонка нет - пред-сигнала нет`() {
    assertNull(PreRingPolicy.preRingAt(null, now))
  }

  @Test
  fun `миллисекундная форма согласована с локальной`() {
    val nowMillis = 1_000_000_000_000L
    val nextMillis = nowMillis + 61 * 60_000L
    assertEquals(nextMillis - 60 * 60_000L, PreRingPolicy.preRingAtMillis(nextMillis, nowMillis))
    assertNull(PreRingPolicy.preRingAtMillis(nowMillis + 59 * 60_000L, nowMillis))
    assertNull(PreRingPolicy.preRingAtMillis(null, nowMillis))
  }

  // --- B-02: актуальность плашки после отложенной доставки/правки расписания ---

  @Test
  fun `актуальная плашка - время совпадает и звонок впереди`() {
    val now = 1_000_000L
    assertTrue(PreRingPolicy.isStillRelevant(5_000_000L, 5_000_000L, now, 60_000L))
    assertTrue(PreRingPolicy.isStillRelevant(5_000_000L, 5_030_000L, now, 60_000L))
  }

  @Test
  fun `расписание изменилось - плашка неактуальна`() {
    assertFalse(PreRingPolicy.isStillRelevant(5_000_000L, 5_200_000L, 1_000_000L, 60_000L))
  }

  @Test
  fun `нет данных или звонок в прошлом - плашка неактуальна`() {
    assertFalse(PreRingPolicy.isStillRelevant(null, 5_000_000L, 1_000_000L, 60_000L))
    assertFalse(PreRingPolicy.isStillRelevant(5_000_000L, null, 1_000_000L, 60_000L))
    assertFalse(PreRingPolicy.isStillRelevant(5_000_000L, 900_000L, 1_000_000L, 60_000L))
  }

  @Test
  fun `граница окна актуальности включительна`() {
    assertTrue(PreRingPolicy.isStillRelevant(5_000_000L, 5_060_000L, 1_000_000L, 60_000L))
    assertFalse(PreRingPolicy.isStillRelevant(5_000_000L, 5_060_001L, 1_000_000L, 60_000L))
  }

  // --- N-3: id уведомления плашки ---

  @Test
  fun `id плашки никогда не равен id уведомления звонка и положителен`() {
    for (id in 0L..2_000L) {
      assertNotEquals(AlarmService.NOTIFICATION_ID, PreRingPolicy.notificationId(id))
      assertTrue(PreRingPolicy.notificationId(id) > 0)
    }
  }

  @Test
  fun `id плашки различаются для разных будильников`() {
    val ids = (1L..50L).map { PreRingPolicy.notificationId(it) }
    assertEquals(ids.size, ids.toSet().size)
  }

  // --- AUD-9 / R-01: связка планировщик → приёмник ---

  @Test
  fun `AUD-9 план - trigger доставки, extra время звонка`() {
    val now = 1_000_000L
    val ringAt = now + 61 * 60_000L
    val plan = PreRingPolicy.plan(ringAt, now)
    assertNotNull(plan)
    // системный trigger — время ДОСТАВКИ (за час), extra — время ЗВОНКА
    assertEquals(now + 60_000L, plan!!.preAtMillis)
    assertEquals(ringAt, plan.ringAtMillis)
  }

  @Test
  fun `AUD-9 регрессия R-01 - приёмник должен видеть время звонка, а не доставки`() {
    val now = 1_000_000L
    val ringAt = now + 61 * 60_000L
    val plan = PreRingPolicy.plan(ringAt, now)!!
    val expected = ringAt // пересчитанное расписание совпадает со временем звонка
    // Раньше в extra лежало pre — isStillRelevant по нему ВСЕГДА false (плашка не показывалась).
    assertFalse(
      PreRingPolicy.isStillRelevant(plan.preAtMillis, expected, now, PreRingPolicy.RELEVANCE_WINDOW_MS)
    )
    // С исправленным extra плашка показывается.
    assertTrue(
      PreRingPolicy.isStillRelevant(plan.ringAtMillis, expected, now, PreRingPolicy.RELEVANCE_WINDOW_MS)
    )
  }

  @Test
  fun `AUD-9 задержанная доставка - в окне показываем, расхождение больше окна снимаем`() {
    val expected = 10_000_000L
    assertTrue(
      PreRingPolicy.isStillRelevant(expected, expected, expected - 30_000L, PreRingPolicy.RELEVANCE_WINDOW_MS)
    )
    assertFalse(
      PreRingPolicy.isStillRelevant(
        expected - 600_000L, expected, expected - 30_000L, PreRingPolicy.RELEVANCE_WINDOW_MS
      )
    )
  }

  @Test
  fun `AUD-9 расписание изменилось - пересчёт дал другое время, снимаем`() {
    val now = 1_000_000L
    val extraRingAt = now + 61 * 60_000L
    val newNext = extraRingAt + 24 * 60 * 60_000L // сдвинули на сутки
    assertFalse(
      PreRingPolicy.isStillRelevant(extraRingAt, newNext, now, PreRingPolicy.RELEVANCE_WINDOW_MS)
    )
  }

  @Test
  fun `AUD-9 нет звонка или до него меньше часа - плана нет`() {
    val now = 1_000_000L
    assertNull(PreRingPolicy.plan(null, now))
    assertNull(PreRingPolicy.plan(now + 59 * 60_000L, now))
  }

  // --- AUD-13 (R-05): решение по действию из плашки ---

  @Test
  fun `AUD-13 действие из актуальной плашки - выключаем будильник`() {
    val now = 1_000_000L
    val ring = now + 30 * 60_000L
    assertEquals(
      PreRingPolicy.DisableDecision.DisableAlarm,
      PreRingPolicy.disableDecision(ring, ring, now, PreRingPolicy.RELEVANCE_WINDOW_MS)
    )
  }

  @Test
  fun `AUD-13 устаревшее действие из плашки - только снять плашку`() {
    val now = 1_000_000L
    val oldRing = now + 30 * 60_000L
    val changedRing = oldRing + 24 * 60 * 60_000L // будильник перенесли
    assertEquals(
      PreRingPolicy.DisableDecision.CancelNotificationOnly,
      PreRingPolicy.disableDecision(oldRing, changedRing, now, PreRingPolicy.RELEVANCE_WINDOW_MS)
    )
    // Нет времени в extra (старая версия плашки) — тоже только снять, не выключать.
    assertEquals(
      PreRingPolicy.DisableDecision.CancelNotificationOnly,
      PreRingPolicy.disableDecision(null, changedRing, now, PreRingPolicy.RELEVANCE_WINDOW_MS)
    )
  }

  // --- AUD-16 (D3): действия приёмника при выключенном тумблере ---

  @Test
  fun `AUD-16 D3 - при выключенном тумблере новые плашки не ставятся`() {
    assertFalse(PreRingPolicy.shouldHandleAction(PreRingReceiver.ACTION_PRE_RING, preRingEnabled = false))
  }

  @Test
  fun `AUD-16 D3 - при выключенном тумблере ACTION_DISABLE и снятие плашки работают`() {
    assertTrue(PreRingPolicy.shouldHandleAction(PreRingReceiver.ACTION_DISABLE, preRingEnabled = false))
  }

  @Test
  fun `AUD-16 D3 - при включённом тумблере обрабатываются оба действия`() {
    assertTrue(PreRingPolicy.shouldHandleAction(PreRingReceiver.ACTION_PRE_RING, preRingEnabled = true))
    assertTrue(PreRingPolicy.shouldHandleAction(PreRingReceiver.ACTION_DISABLE, preRingEnabled = true))
  }

  @Test
  fun `AUD-17 id плашки распознаётся по диапазону и не совпадает с id звонка`() {
    assertTrue(PreRingPolicy.isPreRingNotificationId(PreRingPolicy.notificationId(1)))
    assertTrue(PreRingPolicy.isPreRingNotificationId(PreRingPolicy.notificationId(999_999)))
    assertFalse(PreRingPolicy.isPreRingNotificationId(AlarmService.NOTIFICATION_ID))
    assertFalse(PreRingPolicy.isPreRingNotificationId(100))
  }

  @Test
  fun `AUD-17 действие выключает всю группу, а без списка — только свой будильник`() {
    assertEquals(listOf(1L, 2L, 3L), PreRingPolicy.disableTargets(1L, listOf(1L, 2L, 3L)))
    assertEquals(listOf(1L, 2L), PreRingPolicy.disableTargets(1L, listOf(1L, 2L, 1L)))
    assertEquals(listOf(2L), PreRingPolicy.disableTargets(2L, null))
    assertEquals(listOf(2L), PreRingPolicy.disableTargets(2L, emptyList()))
  }

  // --- Пропуск текущего звонка (вариант Б): решение по одному будильнику ---

  private fun recurring() = AlarmEntity(mode = AlarmEntity.MODE_WEEKLY, daysMask = 1)
  private fun oneShot(deleteAfterFiring: Boolean = false) =
    AlarmEntity(mode = AlarmEntity.MODE_WEEKLY, daysMask = 0, deleteAfterFiring = deleteAfterFiring)

  @Test
  fun `актуальное действие для повторяющегося - пропустить текущее срабатывание`() {
    assertEquals(
      PreRingPolicy.DisableAction.SkipCurrentOccurrence,
      PreRingPolicy.disableAction(recurring(), PreRingPolicy.DisableDecision.DisableAlarm)
    )
  }

  @Test
  fun `актуальное действие для разового - завершить будильник`() {
    assertEquals(
      PreRingPolicy.DisableAction.CompleteOneShot,
      PreRingPolicy.disableAction(oneShot(), PreRingPolicy.DisableDecision.DisableAlarm)
    )
  }

  @Test
  fun `устаревшее действие или удалённый будильник - только снять плашку`() {
    assertEquals(
      PreRingPolicy.DisableAction.CancelNotificationOnly,
      PreRingPolicy.disableAction(recurring(), PreRingPolicy.DisableDecision.CancelNotificationOnly)
    )
    assertEquals(
      PreRingPolicy.DisableAction.CancelNotificationOnly,
      PreRingPolicy.disableAction(null, PreRingPolicy.DisableDecision.DisableAlarm)
    )
  }
}
