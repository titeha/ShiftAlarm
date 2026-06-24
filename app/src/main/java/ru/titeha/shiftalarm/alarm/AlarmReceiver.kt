package ru.titeha.shiftalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ru.titeha.shiftalarm.data.AlarmStore

/** Срабатывает в назначенное время: запускает звонок и сразу планирует следующий по графику. */
class AlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    AlarmService.start(context)
    val pending = goAsync()
    try {
      val state = runBlocking { AlarmStore(context.applicationContext).state.first() }
      AlarmScheduler.applyFromState(context, state)
    } finally {
      pending.finish()
    }
  }
}
