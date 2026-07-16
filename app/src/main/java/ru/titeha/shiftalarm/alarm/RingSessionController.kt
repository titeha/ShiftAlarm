package ru.titeha.shiftalarm.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import ru.titeha.shiftalarm.MainActivity
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

    /** Прошло T минут без взаимодействия — авто-перезвон или, если лимит исчерпан, пропущен. */
    fun onRingTimeout(context: Context, alarmId: Long, label: String) {
        applyEvent(context, alarmId, label, RingEvent.RingTimeout)
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

                RingAction.MarkMissed -> postMissedNotification(context, alarmId, label)

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

    /** Ненавязчивая нотификация «будильник не был выключен» (лимит авто-перезвона исчерпан). */
    private fun postMissedNotification(context: Context, alarmId: Long, label: String) {
        try {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(
                    MISSED_CHANNEL_ID,
                    "Пропущенные будильники",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Звонок не был выключен" }
            )

            val open = PendingIntent.getActivity(
                context,
                MISSED_NOTIFICATION_BASE,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val title = AlarmService.displayText(label)
            val notification = NotificationCompat.Builder(context, MISSED_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Будильник не был выключен")
                .setContentText(title)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()

            // Уникальный id по будильнику, чтобы разные будильники не затирали нотификации друг друга.
            manager.notify(MISSED_NOTIFICATION_BASE + alarmId.toInt(), notification)
        } catch (_: Exception) {
            // Нет разрешения на уведомления / залочено — не критично, событие уже в журнале.
        }
    }

    private const val MISSED_CHANNEL_ID = "missed_channel"
    private const val MISSED_NOTIFICATION_BASE = 2000
}
