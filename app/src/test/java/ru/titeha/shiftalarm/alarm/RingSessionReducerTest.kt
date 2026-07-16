package ru.titeha.shiftalarm.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RingSessionReducerTest {

  private val config = RingConfig(
    ringDurationMinutes = 5,
    snoozeIntervalMinutes = 5,
    maxSnoozes = 3,
    autoRepeatEnabled = true,
  )

  private val now = 1_000_000L
  private val interval = 5L * 60_000L

  private fun fresh() = RingSessionState(alarmId = 7L, scheduledTriggerAtMillis = now)

  @Test
  fun `ручной снуз ставит следующий звонок и растит счётчик`() {
    val r = RingSessionReducer.reduce(fresh(), RingEvent.SnoozePressed, config, now)
    assertEquals(1, r.state.snoozeCount)
    assertEquals(RingPhase.SNOOZING, r.state.phase)
    assertEquals(
      listOf(
        RingAction.ScheduleSnooze(now + interval),
        RingAction.Journal(RingJournalEntry.Snooze(1, now + interval)),
      ),
      r.actions,
    )
  }

  @Test
  fun `Стоп закрывает сессию и обнуляет счётчик`() {
    val snoozed = fresh().copy(snoozeCount = 2, phase = RingPhase.RINGING)
    val r = RingSessionReducer.reduce(snoozed, RingEvent.StopPressed, config, now)
    assertEquals(0, r.state.snoozeCount)
    assertEquals(RingPhase.CLOSED, r.state.phase)
    assertEquals(
      listOf(RingAction.Journal(RingJournalEntry.Stopped), RingAction.CloseSession),
      r.actions,
    )
  }

  @Test
  fun `стоп в снуз-окне закрывает сессию`() {
    // Пользователь во время снуз-паузы выключил будильник — событие Cancelled/Stop закрывает сессию.
    val snoozing = fresh().copy(snoozeCount = 1, phase = RingPhase.SNOOZING)
    val r = RingSessionReducer.reduce(snoozing, RingEvent.StopPressed, config, now)
    assertEquals(RingPhase.CLOSED, r.state.phase)
    assertTrue(r.actions.contains(RingAction.CloseSession))
  }

  @Test
  fun `лимит исчерпан — ручной снуз игнорируется`() {
    val exhausted = fresh().copy(snoozeCount = 3) // = maxSnoozes
    assertFalse(RingSessionReducer.canSnooze(exhausted, config))
    val r = RingSessionReducer.reduce(exhausted, RingEvent.SnoozePressed, config, now)
    assertEquals(exhausted, r.state)          // без изменений
    assertTrue(r.actions.isEmpty())           // и без действий (кнопку прячут, тут защита)
  }

  @Test
  fun `ручной и авто вперемешку делят общий лимит`() {
    var state = fresh()
    // Ручной снуз (n=1).
    state = RingSessionReducer.reduce(state, RingEvent.SnoozePressed, config, now).state
    assertEquals(1, state.snoozeCount)
    // Авто-перезвон по таймауту (n=2).
    var r = RingSessionReducer.reduce(state, RingEvent.RingTimeout, config, now)
    state = r.state
    assertEquals(2, state.snoozeCount)
    assertEquals(RingAction.ScheduleSnooze(now + interval), r.actions.first())
    assertTrue(r.actions.any { it is RingAction.Journal && it.entry is RingJournalEntry.AutoSnooze })
    // Ещё ручной (n=3 = лимит).
    state = RingSessionReducer.reduce(state, RingEvent.SnoozePressed, config, now).state
    assertEquals(3, state.snoozeCount)
    // Следующий таймаут — лимит исчерпан → пропущен.
    r = RingSessionReducer.reduce(state, RingEvent.RingTimeout, config, now)
    assertEquals(RingPhase.CLOSED, r.state.phase)
    assertTrue(r.actions.contains(RingAction.MarkMissed))
    assertTrue(r.actions.contains(RingAction.Journal(RingJournalEntry.Missed)))
  }

  @Test
  fun `таймаут на последней попытке — пропущен`() {
    val last = fresh().copy(snoozeCount = 3) // все использованы
    val r = RingSessionReducer.reduce(last, RingEvent.RingTimeout, config, now)
    assertEquals(RingPhase.CLOSED, r.state.phase)
    assertEquals(
      listOf(
        RingAction.Journal(RingJournalEntry.Missed),
        RingAction.MarkMissed,
        RingAction.CloseSession,
      ),
      r.actions,
    )
  }

  @Test
  fun `M=0 — снуз выключен, таймаут сразу пропущен`() {
    val cfg = config.copy(maxSnoozes = 0)
    val state = fresh()
    assertFalse(RingSessionReducer.canSnooze(state, cfg))
    // Ручной снуз игнорируется.
    val manual = RingSessionReducer.reduce(state, RingEvent.SnoozePressed, cfg, now)
    assertTrue(manual.actions.isEmpty())
    // Таймаут → пропущен без снуза.
    val timeout = RingSessionReducer.reduce(state, RingEvent.RingTimeout, cfg, now)
    assertEquals(RingPhase.CLOSED, timeout.state.phase)
    assertTrue(timeout.actions.contains(RingAction.MarkMissed))
  }

  @Test
  fun `авто-перезвон выключен — таймаут пропущен, ручной снуз ещё работает`() {
    val cfg = config.copy(autoRepeatEnabled = false)
    val state = fresh()
    // Таймаут без авто-перезвона → пропущен.
    val timeout = RingSessionReducer.reduce(state, RingEvent.RingTimeout, cfg, now)
    assertEquals(RingPhase.CLOSED, timeout.state.phase)
    assertTrue(timeout.actions.contains(RingAction.MarkMissed))
    // Но ручной снуз доступен (лимит не исчерпан).
    val manual = RingSessionReducer.reduce(state, RingEvent.SnoozePressed, cfg, now)
    assertEquals(1, manual.state.snoozeCount)
    assertTrue(manual.actions.any { it is RingAction.ScheduleSnooze })
  }

  @Test
  fun `отмена извне закрывает сессию`() {
    val snoozing = fresh().copy(snoozeCount = 2, phase = RingPhase.SNOOZING)
    val r = RingSessionReducer.reduce(snoozing, RingEvent.Cancelled, config, now)
    assertEquals(RingPhase.CLOSED, r.state.phase)
    assertEquals(0, r.state.snoozeCount)
    assertEquals(
      listOf(RingAction.Journal(RingJournalEntry.Cancelled), RingAction.CloseSession),
      r.actions,
    )
  }

  @Test
  fun `Fired переводит сессию в RINGING, счётчик сохраняется`() {
    val snoozing = fresh().copy(snoozeCount = 2, phase = RingPhase.SNOOZING)
    val r = RingSessionReducer.reduce(snoozing, RingEvent.Fired, config, now)
    assertEquals(RingPhase.RINGING, r.state.phase)
    assertEquals(2, r.state.snoozeCount)      // снуз-звонок не сбрасывает счётчик
    assertTrue(r.actions.isEmpty())
  }

  @Test
  fun `remainingSnoozes отражает остаток и не уходит в минус`() {
    assertEquals(3, RingSessionReducer.remainingSnoozes(fresh(), config))
    assertEquals(1, RingSessionReducer.remainingSnoozes(fresh().copy(snoozeCount = 2), config))
    assertEquals(0, RingSessionReducer.remainingSnoozes(fresh().copy(snoozeCount = 5), config))
  }
}
