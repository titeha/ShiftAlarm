package ru.titeha.shiftalarm.alarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AUD-12 (S-01): чистое правило остановки сигнала после попытки поставить снуз.
 * Ручной снуз и авто-перезвон используют одно правило — расхождения поведения нет.
 */
class SnoozeFailureTest {

  @Test
  fun `снуз поставлен - сигнал можно глушить`() {
    assertTrue(shouldStopRingingAfterSnooze(hadSnoozeAttempt = true, snoozeScheduled = true))
  }

  @Test
  fun `ручной снуз не поставлен - сигнал НЕ глушим`() {
    assertFalse(shouldStopRingingAfterSnooze(hadSnoozeAttempt = true, snoozeScheduled = false))
  }

  @Test
  fun `авто-перезвон не поставлен - сигнал НЕ глушим (то же правило)`() {
    // Тот же вызов, что и для ручного: поведение согласовано.
    assertFalse(shouldStopRingingAfterSnooze(hadSnoozeAttempt = true, snoozeScheduled = false))
  }

  @Test
  fun `снуз не запрашивался (Стоп-пропуск-лимит) - сигнал глушим`() {
    // Стоп/пропуск/исчерпание лимита не оставляют «висящий» сигнал: снуз не нужен.
    assertTrue(shouldStopRingingAfterSnooze(hadSnoozeAttempt = false, snoozeScheduled = false))
  }
}
