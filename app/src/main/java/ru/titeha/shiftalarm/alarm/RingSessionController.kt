package ru.titeha.shiftalarm.alarm

import android.content.Context
import ru.titeha.shiftalarm.data.AlarmEventLog
import ru.titeha.shiftalarm.data.AlarmEventType
import ru.titeha.shiftalarm.data.SettingsStore

/**
 * Android-исполнитель «сессии звонка»: загружает состояние и настройки, применяет чистый
 * [RingSessionReducer] и выполняет его [RingAction] (поставить снуз, закрыть сессию, записать журнал).
 *
 * Импуритет (часы, prefs, планировщик) собран здесь; логика — в редьюсере (покрыт тестами).
 * Сессия и снуз-кэш — device-protected, поэтому ручной снуз работает и до разблокировки.
 */
object RingSessionController {

    /** «Отложить» нажато. true — снуз поставлен (false — нет разрешения на точные будильники). */
    fun onSnoozePressed(context: Context, alarmId: Long, label: String): Boolean =
        applyEvent(context, alarmId, label, RingEvent.SnoozePressed)

    /** «Стоп» нажато — закрыть сессию. */
    fun onStopped(context: Context, alarmId: Long) {
        applyEvent(context, alarmId, "", RingEvent.StopPressed)
    }

    /** Внешняя отмена (выключение тумблером, удаление, сохранение правок) — закрыть сессию, если есть. */
    fun onCancelled(context: Context, alarmId: Long) {
        if (RingSessionStore(context).find(alarmId) != null) {
            applyEvent(context, alarmId, "", RingEvent.Cancelled)
        }
    }

    /** Сколько снузов ещё доступно для будильника (для подписи «осталось N» и показа кнопки). */
    fun remainingSnoozes(context: Context, alarmId: Long): Int {
        val config = SettingsStore(context).ringConfig()
        val state = RingSessionStore(context).find(alarmId) ?: RingSessionState(alarmId, 0L)
        return RingSessionReducer.remainingSnoozes(state, config)
    }

    fun snoozeIntervalMinutes(context: Context): Int =
        SettingsStore(context).ringConfig().snoozeIntervalMinutes

    private fun applyEvent(
        context: Context,
        alarmId: Long,
        label: String,
        event: RingEvent,
    ): Boolean {
        val now = System.currentTimeMillis()
        val config = SettingsStore(context).ringConfig()
        val store = RingSessionStore(context)
        val state = store.find(alarmId) ?: RingSessionState(alarmId, now)

        val result = RingSessionReducer.reduce(state, event, config, now)

        var scheduled = true
        result.actions.forEach { action ->
            when (action) {
                is RingAction.ScheduleSnooze ->
                    scheduled = AlarmScheduler.scheduleSnoozeAt(context, alarmId, action.atMillis, label)

                RingAction.MarkMissed ->
                    DirectBootAlarmStore(context).addMissed(
                        listOf(CachedAlarm(alarmId = alarmId, triggerAtMillis = now, label = label))
                    )

                RingAction.CloseSession -> {
                    AlarmScheduler.cancelSnooze(context, alarmId)
                    store.remove(alarmId)
                }

                is RingAction.Journal -> journal(context, action.entry, alarmId, now)
            }
        }

        // Сессию сохраняем, только если она не закрыта (CloseSession её уже удалил).
        if (result.state.phase != RingPhase.CLOSED) {
            store.put(result.state)
        }
        return scheduled
    }

    private fun journal(context: Context, entry: RingJournalEntry, alarmId: Long, nowMillis: Long) {
        val (type, detail) = when (entry) {
            is RingJournalEntry.Snooze ->
                AlarmEventType.SNOOZED to "id=$alarmId отложен вручную №${entry.count}"

            is RingJournalEntry.AutoSnooze ->
                AlarmEventType.SNOOZED to "id=$alarmId авто-перезвон №${entry.count}"

            RingJournalEntry.Missed ->
                AlarmEventType.MISSED to "id=$alarmId звонок не был выключен"

            // Остановлен/отменён — исходный FIRED уже в журнале, не шумим.
            RingJournalEntry.Stopped, RingJournalEntry.Cancelled -> return
        }

        // Журнал (CE) может быть недоступен до разблокировки (авто-снуз залоченным) — тогда в DPS-буфер.
        try {
            AlarmEventLog(context).record(type, detail, nowMillis)
        } catch (_: Exception) {
            try {
                DirectBootEventBuffer(context).add(type.name, detail, nowMillis)
            } catch (_: Exception) {
                // Даже DPS-буфер недоступен — звонок важнее записи, продолжаем.
            }
        }
    }
}
