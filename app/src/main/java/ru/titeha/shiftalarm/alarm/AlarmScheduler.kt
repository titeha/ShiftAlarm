package ru.titeha.shiftalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import ru.titeha.shiftalarm.AlarmActivity
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Планирование одного будильника через системный AlarmManager.setAlarmClock —
 * это «честный» будильник: переживает Doze и показывает системную иконку будущего сигнала.
 */
object AlarmScheduler {

  private const val REQUEST_FIRE = 1001
  private const val REQUEST_SHOW = 1002

  fun schedule(context: Context, triggerAtMillis: Long) {
    val alarmManager = context.getSystemService(AlarmManager::class.java)

    // Intent, который система откроет по тапу на иконку будильника в статусбаре.
    val showPending = PendingIntent.getActivity(
      context, REQUEST_SHOW,
      Intent(context, AlarmActivity::class.java),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    val info = AlarmManager.AlarmClockInfo(triggerAtMillis, showPending)
    alarmManager.setAlarmClock(info, firePendingIntent(context))
  }

  /** Запланировать на ближайшее время HH:MM (сегодня, иначе завтра). */
  fun scheduleAt(context: Context, hour: Int, minute: Int) {
    schedule(context, nextTriggerMillis(hour, minute))
  }

  fun cancel(context: Context) {
    context.getSystemService(AlarmManager::class.java).cancel(firePendingIntent(context))
  }

  /** Ближайший момент срабатывания для HH:MM. */
  fun nextTriggerMillis(hour: Int, minute: Int): Long {
    val now = LocalDateTime.now()
    var target = now.toLocalDate().atTime(hour, minute)
    if (!target.isAfter(now)) target = target.plusDays(1)
    return target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
  }

  /** PendingIntent, который сработает в назначенное время и разбудит AlarmReceiver. */
  private fun firePendingIntent(context: Context): PendingIntent =
    PendingIntent.getBroadcast(
      context, REQUEST_FIRE,
      Intent(context, AlarmReceiver::class.java),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
