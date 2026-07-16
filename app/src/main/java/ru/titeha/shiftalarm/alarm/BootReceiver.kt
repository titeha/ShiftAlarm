package ru.titeha.shiftalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

    if (SystemRescheduleActions.isLockedBoot(action)) {
      /*
       * Ранняя загрузка залоченного устройства (Direct Boot): Room и обычные prefs недоступны.
       * Перевыставляем будильники СЛЕПО из device-protected кэша, без обращения к базе и журналу.
       * Полный пересчёт из Room произойдёт позже — по USER_UNLOCKED / первому старту приложения.
       */
      try {
        AlarmScheduler.reArmFromCache(context.applicationContext)
        Log.i(TAG, "Locked boot: будильники перевыставлены из device-protected кэша")
      } catch (error: Exception) {
        Log.w(TAG, "Locked boot: не удалось перевыставить из кэша", error)
      }
      return
    }

    if (!SystemRescheduleActions.shouldReschedule(action)) {
      return
    }

    val pending = goAsync()
    val appContext = context.applicationContext
    val reason = SystemRescheduleActions.reasonOf(action)

    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
      try {
        val repo = AlarmRepository(appContext)
        val alarms = repo.enabled()
        val eventLog = AlarmEventLog(appContext)

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

  private companion object {
    const val TAG = "BootReceiver"
  }
}