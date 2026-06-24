package ru.titeha.shiftalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.runBlocking
import ru.titeha.shiftalarm.data.AlarmEntity
import ru.titeha.shiftalarm.data.AlarmRepository

/**
 * Срабатывает в назначенное время: запускает звонок, затем решает судьбу будильника:
 *  - разовый с флагом удаления → удалить;
 *  - разовый без флага → выключить;
 *  - повторяющийся (дни недели / смены) → переставить на следующее срабатывание.
 */
class AlarmReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    AlarmService.start(context)

    val id = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, AlarmScheduler.NO_ID)
    if (id == AlarmScheduler.NO_ID) return

    val pending = goAsync()
    val appContext = context.applicationContext
    try {
      runBlocking {
        val repo = AlarmRepository(appContext)
        val alarm = repo.byId(id) ?: return@runBlocking
        when {
          isOneShot(alarm) && alarm.deleteAfterFiring -> repo.delete(alarm)
          isOneShot(alarm) -> repo.update(alarm.copy(enabled = false))
          else -> AlarmScheduler.reschedule(appContext, alarm) // следующий повтор
        }
      }
    } finally {
      pending.finish()
    }
  }

  private fun isOneShot(alarm: AlarmEntity): Boolean =
    alarm.mode == AlarmEntity.MODE_WEEKLY && alarm.daysMask == 0
}
