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
import ru.titeha.shiftalarm.data.AlarmEventLog
import ru.titeha.shiftalarm.data.AlarmEventType
import ru.titeha.shiftalarm.data.AlarmRepository

/**
 * Перепланирует включённые будильники после системных событий.
 *
 * AlarmManager не является постоянным хранилищем расписания: после перезагрузки,
 * обновления приложения или изменения системного времени ближайшие срабатывания
 * нужно заново поставить по данным из базы.
 */
class BootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val action = intent.action
    val appContext = context.applicationContext

    val userManager = appContext.getSystemService(UserManager::class.java)
    val locked = userManager != null && !userManager.isUserUnlocked

    /*
     * До разблокировки (Direct Boot: LOCKED_BOOT_COMPLETED, а также TIME_SET/др. события, пришедшие
     * пока устройство залочено) credential-encrypted хранилище недоступно — Room не тронуть. Любое
     * такое событие обрабатываем ОДИНАКОВО: перевыставляем будильники из device-protected кэша, без
     * обращения к базе и журналу. Полный пересчёт из Room произойдёт по USER_UNLOCKED / старту после
     * разблокировки.
     */
    if (SystemRescheduleActions.isLockedBoot(action) ||
      (locked && SystemRescheduleActions.shouldReschedule(action))
    ) {
      val buffer = DirectBootEventBuffer(appContext)
      val now = System.currentTimeMillis()
      try {
        AlarmScheduler.reArmFromCache(appContext)
        buffer.add(AlarmEventType.RESCHEDULED.name, "$action: перевыставлены из кэша (до разблокировки)", now)
        Log.i(TAG, "Direct Boot ($action): будильники перевыставлены из device-protected кэша")
      } catch (error: Exception) {
        buffer.add(AlarmEventType.ERROR.name, "$action: не удалось перевыставить из кэша: ${error.javaClass.simpleName}", now)
        Log.w(TAG, "Direct Boot: не удалось перевыставить из кэша", error)
      }
      return
    }

    if (!SystemRescheduleActions.shouldReschedule(action)) {
      return
    }

    val pending = goAsync()
    val reason = SystemRescheduleActions.reasonOf(action)

    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
      try {
        val repo = AlarmRepository(appContext)
        val alarms = repo.enabled()
        val eventLog = AlarmEventLog(appContext)

        // Перелить события, накопленные до разблокировки (Direct Boot), в основной журнал с пометкой.
        flushDirectBootBuffer(appContext, eventLog)

        val report = AlarmScheduler.rescheduleAll(
          context = appContext,
          repo = repo,
          alarms = alarms
        )

        val failedIds = report.failedIds()

        val detail = buildString {
          append(reason)
          append(", успешно: ")
          append(report.succeededCount)
          append(", ошибок: ")
          append(report.failedCount)

          if (failedIds.isNotBlank()) {
            append(", id: ")
            append(failedIds)
          }
        }

        eventLog.record(
          AlarmEventType.RESCHEDULED,
          detail,
          System.currentTimeMillis()
        )

        if (report.allSucceeded) {
          Log.i(
            TAG,
            "Будильники перепланированы: $detail"
          )
        } else {
          Log.w(
            TAG,
            "Перепланирование завершено частично: $detail"
          )
        }
      } catch (error: Exception) {
        val detail =
          "Не удалось начать массовое перепланирование: " +
                  "$reason, ${error.javaClass.simpleName}"

        runCatching {
          AlarmEventLog(appContext).record(
            AlarmEventType.ERROR,
            detail,
            System.currentTimeMillis()
          )
        }

        Log.w(
          TAG,
          detail,
          error
        )
      } finally {
        pending.finish()
      }
    }
  }

  /**
   * Переливает буфер событий Direct Boot (накопленных до разблокировки, когда CE-журнал недоступен)
   * в основной диагностический журнал с пометкой `direct_boot`, затем очищает буфер.
   */
  private fun flushDirectBootBuffer(context: Context, eventLog: AlarmEventLog) {
    runCatching {
      DirectBootEventBuffer(context).drain().forEach { event ->
        val type = runCatching { AlarmEventType.valueOf(event.type) }
          .getOrDefault(AlarmEventType.RESCHEDULED)
        eventLog.record(type, "[direct_boot] ${event.detail}", event.atMillis)
      }
    }
  }

  private companion object {
    const val TAG = "BootReceiver"
  }
}