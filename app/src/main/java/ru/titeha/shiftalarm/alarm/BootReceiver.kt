package ru.titeha.shiftalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.titeha.shiftalarm.data.AlarmEventLog
import ru.titeha.shiftalarm.data.AlarmEventType
import ru.titeha.shiftalarm.data.AlarmRepository

/**
 * Перепланирует включённые будильники после системных событий.
 *
 * AlarmManager не является постоянным хранилищем расписания: после перезагрузки,
 * обновления приложения или изменения системного времени ближайшие срабатывания
 * нужно заново поставить по данным из базы.
 */
class BootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val action = intent.action

    if (!SystemRescheduleActions.shouldReschedule(action)) {
      return
    }

    val pending = goAsync()
    val appContext = context.applicationContext
    val reason = SystemRescheduleActions.reasonOf(action)

    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
      try {
        val repo = AlarmRepository(appContext)
        val alarms = repo.enabled()

        AlarmScheduler.rescheduleAll(
          context = appContext,
          repo = repo,
          alarms = alarms
        )

        AlarmEventLog(appContext).record(
          AlarmEventType.RESCHEDULED,
          "$reason, будильников: ${alarms.size}",
          System.currentTimeMillis()
        )

        Log.i(
          TAG,
          "Будильники перепланированы: $reason, включённых будильников: ${alarms.size}"
        )
      } catch (error: Exception) {
        Log.w(TAG, "Не удалось перепланировать будильники: $reason", error)
      } finally {
        pending.finish()
      }
    }
  }

  private companion object {
    const val TAG = "BootReceiver"
  }
}