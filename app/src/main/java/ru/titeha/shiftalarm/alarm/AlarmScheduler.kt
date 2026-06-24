package ru.titeha.shiftalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import ru.titeha.shiftalarm.AlarmActivity
import ru.titeha.shiftalarm.data.AlarmState
import ru.titeha.shiftalarm.schedule.ShiftEngine
import ru.titeha.shiftalarm.schedule.ShiftPresets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Планирование будильника через системный AlarmManager.setAlarmClock —
 * «честный» будильник: переживает Doze и показывает системную иконку будущего сигнала.
 */
object AlarmScheduler {

  private const val REQUEST_FIRE = 1001
  private const val REQUEST_SHOW = 1002

  /** Запланировать/отменить будильник по сохранённому состоянию. */
  fun applyFromState(context: Context, state: AlarmState) {
    val next = if (state.enabled) nextTrigger(state) else null
    if (next == null) {
      cancel(context)
    } else {
      schedule(context, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
    }
  }

  /** Ближайший момент срабатывания для состояния (или null — нечего ставить). */
  fun nextTrigger(state: AlarmState): LocalDateTime? {
    val now = LocalDateTime.now()
    return if (state.mode == AlarmState.MODE_SHIFT) {
      val preset = ShiftPresets.byId(state.presetId) ?: return null
      val anchor = LocalDate.ofEpochDay(state.anchorEpochDay)
      ShiftEngine.nextAlarm(now, preset.build(anchor))
    } else {
      var t = now.toLocalDate().atTime(state.hour, state.minute)
      if (!t.isAfter(now)) t = t.plusDays(1)
      t
    }
  }

  /** Запланировать на конкретный момент (используется тестовой кнопкой). */
  fun schedule(context: Context, triggerAtMillis: Long) {
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    val showPending = PendingIntent.getActivity(
      context, REQUEST_SHOW,
      Intent(context, AlarmActivity::class.java),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val info = AlarmManager.AlarmClockInfo(triggerAtMillis, showPending)
    alarmManager.setAlarmClock(info, firePendingIntent(context))
  }

  fun cancel(context: Context) {
    context.getSystemService(AlarmManager::class.java).cancel(firePendingIntent(context))
  }

  private fun firePendingIntent(context: Context): PendingIntent =
    PendingIntent.getBroadcast(
      context, REQUEST_FIRE,
      Intent(context, AlarmReceiver::class.java),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
