package ru.titeha.shiftalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ru.titeha.shiftalarm.data.AlarmStore

/**
 * После перезагрузки система забывает зарегистрированные будильники —
 * перерегистрируем сохранённый будильник, если он включён.
 */
class BootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
    val pending = goAsync()
    try {
      val state = runBlocking { AlarmStore(context.applicationContext).state.first() }
      AlarmScheduler.applyFromState(context, state)
    } finally {
      pending.finish()
    }
  }
}
