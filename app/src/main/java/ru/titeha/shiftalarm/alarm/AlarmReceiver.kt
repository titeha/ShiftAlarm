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
import ru.titeha.shiftalarm.data.SettingsStore
import ru.titeha.shiftalarm.schedule.AlarmTimes
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
     * Снуз-срабатывание — продолжение сессии звонка, а не плановое: валидатор актуальности пропускаем
     * (снуз-время не совпадает с расписанием), звоним по метке из extras без обращения к Room. Снуз-
     * запись в кэше «использована» — убираем, чтобы после ребута уже отзвонивший снуз не повторился.
     */
    if (intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)) {
      val label = intent.getStringExtra(AlarmService.EXTRA_LABEL).orEmpty()
      DirectBootAlarmStore(appContext).removeSnooze(id)
      Log.i(TAG, "Снуз-срабатывание id=$id — звоним без валидатора")
      AlarmService.start(appContext, id, label)
      return
    }

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
        // AUD-14 (R-07): журналирование не должно снова бросить исключение — минимальный сигнал важнее диагностики.
        runCatching {
          AlarmEventLog(appContext).record(
            AlarmEventType.ERROR,
            "срабатывание id=$id: ${error.message ?: error.javaClass.simpleName}",
            System.currentTimeMillis()
          )
        }
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

    // AUD-11 (R-03): время сработавшего firing'а — для точечного удаления записи из DE-плана
    // (fallback на «сейчас», если extra не пришёл).
    val nowMillis = System.currentTimeMillis()
    val firedAtMillis = if (scheduledTriggerAtMillis == AlarmScheduler.NO_TRIGGER_AT_MILLIS) {
      nowMillis
    } else {
      scheduledTriggerAtMillis
    }

    val periods = periodsFor(repo, alarm)
    val overrides = overridesFor(repo, alarm)

    val shouldRing = AlarmFireValidator.shouldStartRinging(
      alarm = alarm,
      periods = periods,
      overrides = overrides,
      scheduledTriggerAtMillis = scheduledTriggerAtMillis,
      globalWeek = SettingsStore(context).globalWorkWeek()
    )

    if (!shouldRing) {
      AlarmEventLog(context).record(
        AlarmEventType.SKIPPED,
        "id=$alarmId (устаревшее срабатывание или будильник выключен)",
        nowMillis
      )
      // AUD-11 (R-03): отклонённый (устаревший/выключенный) firing — убираем РОВНО его запись из
      // DE-плана ДО reschedule/collectMissed, иначе она была бы посчитана ложным пропуском.
      // Следующее здесь не ставим — reschedule пересоберёт план из Room.
      DirectBootAlarmStore(context).removeFired(alarmId, firedAtMillis)
      AlarmScheduler.reschedule(context, repo, alarm)
      return
    }

    AlarmEventLog(context).record(
      AlarmEventType.FIRED, "id=$alarmId «${alarm.label}»", nowMillis
    )
    // AUD-11 (R-03): убираем РОВНО сработавшую запись, СОХРАНЯЯ будущие записи плана. Для
    // повторяющегося будильника СРАЗУ (до AlarmService.start и до Room-перепланирования) ставим
    // следующее срабатывание из кэша — это recovery-план, если процесс/устройство оборвётся до
    // перепланирования. Раньше здесь был removeBase, стиравший весь план (crash → locked boot без
    // recovery). Для разового следующее не ставим (однократная семантика).
    val directBootStore = DirectBootAlarmStore(context)
    directBootStore.removeFired(alarmId, firedAtMillis)
    val repeating = !isOneShot(alarm)
    if (repeating) {
      AlarmScheduler.scheduleNextFromCache(context, alarmId, nowMillis)
    }

    AlarmService.start(context, alarm.id, alarm.label, firedAtMillis)

    when {
      isOneShot(alarm) && alarm.deleteAfterFiring -> {
        // Разовое срабатывание не идёт через перепланирование: DE-запись уже убрана выше,
        // осталось применить к Room окончательно.
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

    val store = DirectBootAlarmStore(context)
    val entry = store.read().firstOrNull { it.alarmId == alarmId }
    val label = entry?.label.orEmpty()

    Log.i(TAG, "Locked: звоним по RingCache id=$alarmId")
    DirectBootEventBuffer(context).add(AlarmEventType.FIRED.name, "id=$alarmId «$label» (locked)", now)
    // AUD-2: убираем ровно сработавшую запись плана и не даём ей стать ложным пропуском.
    //  - разовый: следующее срабатывание НЕ ставим (N-1 — иначе rescheduleAll на разблокировке
    //    поставил бы «завтра то же время»); отметку применим после разблокировки;
    //  - повторяющийся: сразу ставим следующее из кэша (N-2), чтобы залоченный телефон звенел
    //    и дальше без разблокировки.
    val firedAt = if (scheduledTriggerAtMillis == AlarmScheduler.NO_TRIGGER_AT_MILLIS) {
      now
    } else {
      scheduledTriggerAtMillis
    }
    store.removeFired(alarmId, firedAt)
    if (entry?.oneShot == true) {
      store.markOneShotFired(alarmId)
    } else {
      AlarmScheduler.scheduleNextFromCache(context, alarmId, now)
    }
    AlarmService.start(context, alarmId, label, scheduledTriggerAtMillis)
  }

  private suspend fun periodsFor(
    repo: AlarmRepository,
    alarm: AlarmEntity
  ): List<AlarmPeriod> {
    // Периоды без будильника (отпуск/больничный/…) применимы всем режимам, не только сменам.
    return repo.periodsList(alarm.id)
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

  /**
   * Разовый ли будильник — правило живёт в чистом слое ([AlarmTimes.isOneShot]), приёмник только
   * делегирует ему, чтобы «выключить/удалить после звонка» совпадало с тем, что посчитал движок.
   */
  private fun isOneShot(alarm: AlarmEntity): Boolean = AlarmTimes.isOneShot(alarm)

  private companion object {
    const val TAG = "AlarmReceiver"

    /** Грейс для просроченного (за время ребута) звонка при locked boot: в пределах — звоним. */
    const val LOCKED_OVERDUE_GRACE_MS = 30L * 60L * 1000L
  }
}