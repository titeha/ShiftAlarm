package ru.titeha.shiftalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import ru.titeha.shiftalarm.AlarmActivity
import ru.titeha.shiftalarm.data.AlarmEntity
import ru.titeha.shiftalarm.data.AlarmEventLog
import ru.titeha.shiftalarm.data.AlarmEventType
import ru.titeha.shiftalarm.data.AlarmPeriod
import ru.titeha.shiftalarm.data.AlarmRepository
import ru.titeha.shiftalarm.schedule.AlarmTimes
import ru.titeha.shiftalarm.schedule.ScheduleOverrides
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Планирование будильников через системный AlarmManager.setAlarmClock —
 * «честный» будильник: переживает Doze и показывает системную иконку будущего сигнала.
 *
 * У каждого будильника свой PendingIntent: код запроса выводится из id записи,
 * поэтому несколько будильников не затирают друг друга.
 */
object AlarmScheduler {
  const val EXTRA_ALARM_ID = "alarm_id"
  const val EXTRA_TRIGGER_AT_MILLIS = "trigger_at_millis"

  const val NO_ID = -1L
  const val NO_TRIGGER_AT_MILLIS = AlarmFireValidator.MISSING_TRIGGER_AT_MILLIS

  /**
   * Запланировать, подгрузив периоды отпуска и правки календаря из репозитория.
   */
  suspend fun reschedule(context: Context, repo: AlarmRepository, alarm: AlarmEntity) {
    reschedule(
      context = context,
      alarm = alarm,
      periods = offPeriodsFor(repo, alarm),
      overrides = overridesFor(repo, alarm)
    )
  }

  /**
   * Запланировать или отменить будильник.
   *
   * Если будильник выключен или ближайшего срабатывания нет, системный сигнал снимается.
   */
  fun reschedule(
    context: Context,
    alarm: AlarmEntity,
    periods: List<AlarmPeriod> = emptyList(),
    overrides: List<ScheduleOverrides.DayOverride> = emptyList()
  ) {
    val next = if (alarm.enabled) {
      AlarmTimes.next(alarm, periods, overrides, LocalDateTime.now())
    } else {
      null
    }

    if (next == null) {
      cancel(context, alarm.id)
      AlarmEventLog(context).record(
        AlarmEventType.CANCELLED,
        "id=${alarm.id} (выключен или нет ближайшего срабатывания)",
        System.currentTimeMillis()
      )
    } else {
      scheduleAt(
        context = context,
        alarmId = alarm.id,
        triggerAtMillis = next
          .atZone(ZoneId.systemDefault())
          .toInstant()
          .toEpochMilli()
      )
      AlarmEventLog(context).record(
        AlarmEventType.SCHEDULED,
        "id=${alarm.id} → %02d.%02d %02d:%02d".format(
          next.dayOfMonth, next.monthValue, next.hour, next.minute
        ),
        System.currentTimeMillis()
      )
    }
  }

  /**
   * Перепланировать набор будильников после загрузки, перезагрузки устройства
   * или массового изменения настроек.
   */
  suspend fun rescheduleAll(context: Context, repo: AlarmRepository, alarms: List<AlarmEntity>) {
    alarms.forEach { alarm ->
      reschedule(context, repo, alarm)
    }
  }

  fun scheduleAt(context: Context, alarmId: Long, triggerAtMillis: Long) {
    val alarmManager = context.getSystemService(AlarmManager::class.java)

    val show = PendingIntent.getActivity(
      context,
      showRequest(alarmId),
      Intent(context, AlarmActivity::class.java),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    val info = AlarmManager.AlarmClockInfo(triggerAtMillis, show)

    alarmManager.setAlarmClock(
      info,
      firePendingIntent(
        context = context,
        alarmId = alarmId,
        triggerAtMillis = triggerAtMillis
      )
    )
  }

  fun cancel(context: Context, alarmId: Long) {
    context.getSystemService(AlarmManager::class.java).cancel(
      firePendingIntent(
        context = context,
        alarmId = alarmId,
        triggerAtMillis = NO_TRIGGER_AT_MILLIS
      )
    )
  }

  private suspend fun offPeriodsFor(
    repo: AlarmRepository,
    alarm: AlarmEntity
  ): List<AlarmPeriod> {
    return if (alarm.mode == AlarmEntity.MODE_SHIFT) {
      repo.periodsList(alarm.id)
    } else {
      emptyList()
    }
  }

  private suspend fun overridesFor(
    repo: AlarmRepository,
    alarm: AlarmEntity
  ): List<ScheduleOverrides.DayOverride> {
    return if (alarm.mode == AlarmEntity.MODE_SHIFT) {
      repo.overridesList(alarm.id).mapNotNull { it.toDayOverrideOrNull() }
    } else {
      emptyList()
    }
  }

  private fun firePendingIntent(
    context: Context,
    alarmId: Long,
    triggerAtMillis: Long
  ): PendingIntent {
    val intent = Intent(context, AlarmReceiver::class.java)
      .putExtra(EXTRA_ALARM_ID, alarmId)
      .putExtra(EXTRA_TRIGGER_AT_MILLIS, triggerAtMillis)

    return PendingIntent.getBroadcast(
      context,
      fireRequest(alarmId),
      intent,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
  }

  /*
   * Разные requestCode для «сработать» и «показать экран».
   * Они уникальны по id будильника, чтобы несколько будильников не затирали друг друга.
   */
  private fun fireRequest(id: Long): Int = (id * 2).toInt()

  private fun showRequest(id: Long): Int = (id * 2 + 1).toInt()
}