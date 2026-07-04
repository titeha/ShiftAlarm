package ru.titeha.shiftalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import ru.titeha.shiftalarm.AlarmActivity
import ru.titeha.shiftalarm.data.AlarmEntity
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
  const val NO_ID = -1L

  /** Запланировать, подгрузив периоды отпуска и правки календаря из репозитория (для смен). */
  suspend fun reschedule(context: Context, repo: AlarmRepository, alarm: AlarmEntity) {
    reschedule(context, alarm, offPeriodsFor(repo, alarm), overridesFor(repo, alarm))
  }

  /**
   * Запланировать (или отменить, если выключен/нечего ставить) с уже известными периодами и
   * правками календаря. Чистый I/O по AlarmManager — без обращений к БД.
   */
  fun reschedule(
    context: Context,
    alarm: AlarmEntity,
    periods: List<AlarmPeriod> = emptyList(),
    overrides: List<ScheduleOverrides.DayOverride> = emptyList()
  ) {
    val next =
      if (alarm.enabled) AlarmTimes.next(alarm, periods, overrides, LocalDateTime.now()) else null
    if (next == null) {
      cancel(context, alarm.id)
    } else {
      scheduleAt(context, alarm.id, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
    }
  }

  /** Перепланировать набор будильников (после загрузки или массового изменения). */
  suspend fun rescheduleAll(context: Context, repo: AlarmRepository, alarms: List<AlarmEntity>) {
    alarms.forEach { reschedule(context, repo, it) }
  }

  /** Периоды отпуска нужны только сменам; «по дням недели» их не использует. */
  private suspend fun offPeriodsFor(repo: AlarmRepository, alarm: AlarmEntity): List<AlarmPeriod> =
    if (alarm.mode == AlarmEntity.MODE_SHIFT) repo.periodsList(alarm.id) else emptyList()

  /** Правки календаря (подмены/исключения) нужны только сменам. */
  private suspend fun overridesFor(
    repo: AlarmRepository,
    alarm: AlarmEntity
  ): List<ScheduleOverrides.DayOverride> =
    if (alarm.mode == AlarmEntity.MODE_SHIFT)
      repo.overridesList(alarm.id).map { it.toDayOverride() }
    else emptyList()

  fun scheduleAt(context: Context, alarmId: Long, triggerAtMillis: Long) {
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    val show = PendingIntent.getActivity(
      context, showRequest(alarmId),
      Intent(context, AlarmActivity::class.java),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val info = AlarmManager.AlarmClockInfo(triggerAtMillis, show)
    alarmManager.setAlarmClock(info, firePendingIntent(context, alarmId))
  }

  fun cancel(context: Context, alarmId: Long) {
    context.getSystemService(AlarmManager::class.java).cancel(firePendingIntent(context, alarmId))
  }

  private fun firePendingIntent(context: Context, alarmId: Long): PendingIntent =
    PendingIntent.getBroadcast(
      context, fireRequest(alarmId),
      Intent(context, AlarmReceiver::class.java).putExtra(EXTRA_ALARM_ID, alarmId),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

  // Разные коды запроса для «звонка» и «показа», уникальные по id будильника.
  private fun fireRequest(id: Long): Int = (id * 2).toInt()
  private fun showRequest(id: Long): Int = (id * 2 + 1).toInt()
}
