package ru.titeha.shiftalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.titeha.shiftalarm.data.AlarmEntity
import ru.titeha.shiftalarm.data.AlarmEventLog
import ru.titeha.shiftalarm.data.AlarmEventType
import ru.titeha.shiftalarm.data.AlarmPeriod
import ru.titeha.shiftalarm.data.AlarmRepository
import ru.titeha.shiftalarm.schedule.ScheduleOverrides

/**
 * Срабатывает в назначенное время.
 *
 * Важно: звонок нельзя запускать до проверки intent.
 * Сначала проверяем id, наличие будильника в базе, включённость и актуальность
 * планового времени. Только после этого запускаем [AlarmService].
 */
class AlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val id = intent.getLongExtra(
      AlarmScheduler.EXTRA_ALARM_ID,
      AlarmScheduler.NO_ID
    )

    if (id == AlarmScheduler.NO_ID) {
      return
    }

    val scheduledTriggerAtMillis = intent.getLongExtra(
      AlarmScheduler.EXTRA_TRIGGER_AT_MILLIS,
      AlarmScheduler.NO_TRIGGER_AT_MILLIS
    )

    val appContext = context.applicationContext

    /*
     * До разблокировки (Direct Boot) credential-encrypted хранилище недоступно: Room не прочитать,
     * валидатор и репозиторий использовать нельзя. Звоним по данным из RingCache/extras, ничего не
     * трогая в базе. Полный пересчёт произойдёт по USER_UNLOCKED / первому старту приложения.
     */
    val userManager = appContext.getSystemService(UserManager::class.java)
    if (userManager != null && !userManager.isUserUnlocked) {
      handleAlarmLocked(appContext, id, scheduledTriggerAtMillis)
      return
    }

    val pending = goAsync()

    // Асинхронно, чтобы не блокировать broadcast-поток. goAsync() держит процесс живым до finish()
    // (до ~10 c) — на загрузку из БД и запуск сервиса звонка этого хватает.
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
      try {
        handleAlarm(
          context = appContext,
          alarmId = id,
          scheduledTriggerAtMillis = scheduledTriggerAtMillis
        )
      } catch (error: Exception) {
        Log.w(TAG, "Не удалось обработать срабатывание будильника", error)
        AlarmEventLog(appContext).record(
          AlarmEventType.ERROR,
          "срабатывание id=$id: ${error.message ?: error.javaClass.simpleName}",
          System.currentTimeMillis()
        )
      } finally {
        pending.finish()
      }
    }
  }

  private suspend fun handleAlarm(
    context: Context,
    alarmId: Long,
    scheduledTriggerAtMillis: Long
  ) {
    val repo = AlarmRepository(context)
    val alarm = repo.byId(alarmId) ?: return

    val periods = periodsFor(repo, alarm)
    val overrides = overridesFor(repo, alarm)

    val shouldRing = AlarmFireValidator.shouldStartRinging(
      alarm = alarm,
      periods = periods,
      overrides = overrides,
      scheduledTriggerAtMillis = scheduledTriggerAtMillis
    )

    if (!shouldRing) {
      AlarmEventLog(context).record(
        AlarmEventType.SKIPPED,
        "id=$alarmId (устаревшее срабатывание или будильник выключен)",
        System.currentTimeMillis()
      )
      AlarmScheduler.reschedule(context, repo, alarm)
      return
    }

    AlarmEventLog(context).record(
      AlarmEventType.FIRED, "id=$alarmId «${alarm.label}»", System.currentTimeMillis()
    )
    AlarmService.start(context, alarm.label)

    when {
      isOneShot(alarm) && alarm.deleteAfterFiring -> {
        repo.delete(alarm)
      }

      isOneShot(alarm) -> {
        repo.update(alarm.copy(enabled = false))
      }

      else -> {
        AlarmScheduler.reschedule(context, repo, alarm)
      }
    }
  }

  /**
   * Звонок до разблокировки устройства (Direct Boot). Метка берётся из device-protected RingCache,
   * Room и журнал (CE) НЕ трогаются. Перепланирование/удаление отложено до USER_UNLOCKED.
   */
  private fun handleAlarmLocked(
    context: Context,
    alarmId: Long,
    scheduledTriggerAtMillis: Long
  ) {
    val now = System.currentTimeMillis()

    // Просроченный сверх грейса (ребут занял слишком долго или звонок давний) — не звоним;
    // пропуск зафиксирует сверка при разблокировке / детект пропуска.
    if (scheduledTriggerAtMillis != AlarmScheduler.NO_TRIGGER_AT_MILLIS &&
      scheduledTriggerAtMillis < now - LOCKED_OVERDUE_GRACE_MS
    ) {
      Log.w(TAG, "Locked: срабатывание id=$alarmId просрочено сверх грейса — не звоним")
      DirectBootEventBuffer(context).add(
        AlarmEventType.SKIPPED.name, "id=$alarmId просрочено сверх грейса (locked)", now
      )
      return
    }

    val label = DirectBootAlarmStore(context).read()
      .firstOrNull { it.alarmId == alarmId }
      ?.label
      .orEmpty()

    Log.i(TAG, "Locked: звоним по RingCache id=$alarmId")
    DirectBootEventBuffer(context).add(AlarmEventType.FIRED.name, "id=$alarmId «$label» (locked)", now)
    AlarmService.start(context, label)
  }

  private suspend fun periodsFor(
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

  private fun isOneShot(alarm: AlarmEntity): Boolean {
    return alarm.mode == AlarmEntity.MODE_WEEKLY && alarm.daysMask == 0
  }

  private companion object {
    const val TAG = "AlarmReceiver"

    /** Грейс для просроченного (за время ребута) звонка при locked boot: в пределах — звоним. */
    const val LOCKED_OVERDUE_GRACE_MS = 30L * 60L * 1000L
  }
}