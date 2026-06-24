package ru.titeha.shiftalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.runBlocking
import ru.titeha.shiftalarm.data.AlarmRepository

/**
 * После перезагрузки система забывает зарегистрированные будильники —
 * перерегистрируем все включённые.
 */
class BootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
    val pending = goAsync()
    val appContext = context.applicationContext
    try {
      runBlocking {
        val alarms = AlarmRepository(appContext).enabled()
        AlarmScheduler.rescheduleAll(appContext, alarms)
      }
    } finally {
      pending.finish()
    }
  }
}
