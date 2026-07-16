package ru.titeha.shiftalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import ru.titeha.shiftalarm.AlarmActivity
import ru.titeha.shiftalarm.data.AlarmEntity
import ru.titeha.shiftalarm.data.AlarmEventLog
import ru.titeha.shiftalarm.data.AlarmEventType
import ru.titeha.shiftalarm.data.AlarmPeriod
import ru.titeha.shiftalarm.data.AlarmRepository
import ru.titeha.shiftalarm.schedule.AlarmTimes
import ru.titeha.shiftalarm.schedule.ScheduleOverrides
import java.time.LocalDateTime
import ru.titeha.shiftalarm.schedule.AlarmInstant

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

  private const val TAG = "AlarmScheduler"

  /** Горизонт device-protected кэша ближайших звонков (дни). */
  private const val DIRECT_BOOT_HORIZON_DAYS = 7L

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
    refreshDirectBootCache(context, repo)
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
      val scheduled = scheduleAt(
        context = context,
        alarmId = alarm.id,
        triggerAtMillis = AlarmInstant.epochMilli(next)
      )

      if (scheduled) {
        AlarmEventLog(context).record(
          AlarmEventType.SCHEDULED,
          "id=${alarm.id} → %02d.%02d %02d:%02d".format(
            next.dayOfMonth, next.monthValue, next.hour, next.minute
          ),
          System.currentTimeMillis()
        )
      } else {
        AlarmEventLog(context).record(
          AlarmEventType.ERROR,
          "id=${alarm.id}: не удалось запланировать (нет разрешения на точные будильники)",
          System.currentTimeMillis()
        )
      }
    }
  }

  /**
   * Перепланировать набор будильников после загрузки, перезагрузки устройства
   * или массового изменения настроек.
   *
   * Ошибка одной записи не прекращает обработку остальных. Каждая ошибка
   * попадает в диагностический журнал, а вызывающий код получает сводный отчёт.
   */
  suspend fun rescheduleAll(
    context: Context,
    repo: AlarmRepository,
    alarms: List<AlarmEntity>
  ): AlarmRescheduleReport {
    val report = AlarmRescheduleBatch.run(
      alarms = alarms,
      operation = { alarm ->
        // Через не-repo перегрузку, чтобы кэш Direct Boot пересобрать один раз ниже, а не на
        // каждой записи.
        reschedule(
          context = context,
          alarm = alarm,
          periods = offPeriodsFor(repo, alarm),
          overrides = overridesFor(repo, alarm)
        )
      }
    )

    if (report.failures.isNotEmpty()) {
      val eventLog = AlarmEventLog(context)
      val now = System.currentTimeMillis()

      report.failures.forEach { failure ->
        val detail =
          "Массовое перепланирование: " +
                  "id=${failure.alarmId}, ${failure.reason}"

        /*
         * Ошибка диагностического журнала не должна снова
         * сломать уже завершённый пакет.
         */
        runCatching {
          eventLog.record(
            AlarmEventType.ERROR,
            detail,
            now
          )
        }

        Log.w(
          TAG,
          detail
        )
      }
    }

    refreshDirectBootCache(context, repo)

    return report
  }

  /**
   * Поставить системный сигнal на [triggerAtMillis]. Возвращает false, если ОС отказала из-за
   * отсутствия разрешения на точные будильники (`SecurityException`, API 31-32) — тогда планировщик
   * не роняем (готовность уже ведёт в настройки), а событие фиксирует вызывающий код (журналом,
   * который недоступен при locked boot). true — сигнал поставлен.
   */
  fun scheduleAt(context: Context, alarmId: Long, triggerAtMillis: Long): Boolean {
    val alarmManager = context.getSystemService(AlarmManager::class.java)

    val show = PendingIntent.getActivity(
      context,
      showRequest(alarmId),
      Intent(context, AlarmActivity::class.java),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    val info = AlarmManager.AlarmClockInfo(triggerAtMillis, show)

    return try {
      alarmManager.setAlarmClock(
        info,
        firePendingIntent(
          context = context,
          alarmId = alarmId,
          triggerAtMillis = triggerAtMillis
        )
      )
      true
    } catch (error: SecurityException) {
      Log.e(
        TAG,
        "Не удалось запланировать id=$alarmId: нет разрешения на точные будильники",
        error
      )
      false
    }
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

  /**
   * Пересобрать device-protected кэш ближайших звонков — чтобы после ночного ребута залоченным
   * `directBootAware`-ресивер мог перевыставить будильники, не обращаясь к Room.
   *
   * Берёт включённые будильники, считает ближайшее срабатывание каждого (тем же движком, что и
   * планирование) и кладёт в кэш те, что в пределах горизонта [DIRECT_BOOT_HORIZON_DAYS] дней.
   */
  suspend fun refreshDirectBootCache(context: Context, repo: AlarmRepository) {
    val now = LocalDateTime.now()
    val horizonMillis = AlarmInstant.epochMilli(now.plusDays(DIRECT_BOOT_HORIZON_DAYS))

    val entries = repo.enabled().mapNotNull { alarm ->
      val next = AlarmTimes.next(
        alarm,
        offPeriodsFor(repo, alarm),
        overridesFor(repo, alarm),
        now
      ) ?: return@mapNotNull null

      val millis = AlarmInstant.epochMilli(next)
      if (millis > horizonMillis) return@mapNotNull null

      CachedAlarm(alarmId = alarm.id, triggerAtMillis = millis, label = alarm.label)
    }

    DirectBootAlarmStore(context).write(entries)
  }

  /**
   * Слепо перевыставить будильники из device-protected кэша — для `LOCKED_BOOT_COMPLETED`, когда
   * Room ещё недоступна. PendingIntent собирается только из данных кэша (id + время срабатывания),
   * без единого обращения к базе.
   */
  fun reArmFromCache(context: Context) {
    DirectBootAlarmStore(context).read().forEach { entry ->
      scheduleAt(
        context = context,
        alarmId = entry.alarmId,
        triggerAtMillis = entry.triggerAtMillis
      )
    }
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