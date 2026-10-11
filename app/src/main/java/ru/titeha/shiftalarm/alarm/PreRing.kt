package ru.titeha.shiftalarm.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ru.titeha.shiftalarm.MainActivity
import ru.titeha.shiftalarm.data.AlarmEventLog
import ru.titeha.shiftalarm.data.AlarmEventType
import ru.titeha.shiftalarm.data.AlarmRepository
import ru.titeha.shiftalarm.data.SettingsStore
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Чистое правило предварительного уведомления (NF1): за [LEAD_MINUTES] до звонка показывается
 * тихая плашка, с которой будильник можно заранее выключить. Если до звонка меньше часа
 * (или расчёта нет) — предварительный сигнал не ставится: плашка «в прошлом» бессмысленна.
 * Без Android-зависимостей — покрыто unit-тестами.
 */
object PreRingPolicy {
  const val LEAD_MINUTES = 60L

  /** Миллисекунды предварительного сигнала для звонка [nextMillis]; null — не нужен. */
  fun preRingAtMillis(nextMillis: Long?, nowMillis: Long): Long? {
    if (nextMillis == null) return null
    val pre = nextMillis - LEAD_MINUTES * 60_000L
    return if (pre > nowMillis) pre else null
  }

  /** То же в локальном времени (для тестов/читаемости). */
  fun preRingAt(next: LocalDateTime?, now: LocalDateTime): LocalDateTime? =
    next?.minusMinutes(LEAD_MINUTES)?.takeIf { it > now }

  /**
   * R-01: план пред-сигнала — ДВА разных времени, которые нельзя путать:
   *  - [preAtMillis] — системный trigger доставки (время звонка минус [LEAD_MINUTES]);
   *  - [ringAtMillis] — время ОСНОВНОГО звонка (его кладём в extra для приёмника).
   */
  data class PreRingPlan(val preAtMillis: Long, val ringAtMillis: Long)

  /**
   * R-01: построить план пред-сигнала по времени основного звонка [ringAtMillis].
   * null — плашка не нужна (нет звонка или до него меньше [LEAD_MINUTES]).
   */
  fun plan(ringAtMillis: Long?, nowMillis: Long): PreRingPlan? {
    if (ringAtMillis == null) return null
    val pre = preRingAtMillis(ringAtMillis, nowMillis) ?: return null
    return PreRingPlan(preAtMillis = pre, ringAtMillis = ringAtMillis)
  }

  /** B-02: допустимое расхождение между запланированным и пересчитанным срабатыванием (мс). */
  const val RELEVANCE_WINDOW_MS = 60_000L

  /**
   * B-02: актуальна ли плашка. true — сохранённое время срабатывания совпадает с пересчитанным
   * (в пределах [windowMs]) и звонок ещё впереди. Иначе плашку показывать нельзя (расписание
   * изменилось / доставка задержалась) — её надо снять.
   */
  fun isStillRelevant(
    scheduledTriggerAtMillis: Long?,
    expectedNextMillis: Long?,
    nowMillis: Long,
    windowMs: Long,
  ): Boolean {
    if (scheduledTriggerAtMillis == null || expectedNextMillis == null) return false
    if (expectedNextMillis <= nowMillis) return false
    return kotlin.math.abs(scheduledTriggerAtMillis - expectedNextMillis) <= windowMs
  }

  /**
   * N-3: id уведомления плашки. Вынесен в чистую функцию, чтобы инвариант «id плашки никогда не
   * равен id уведомления звонка ([AlarmService.NOTIFICATION_ID] = 1)» был проверяем тестом.
   * Отдельный диапазон от id звонка — `cancel` плашки не задевает управление звонком.
   */
  fun notificationId(alarmId: Long): Int =
    NOTIFICATION_BASE + (alarmId % NOTIFICATION_RANGE).toInt()

  /** AUD-13 (R-05): решение по действию «Выключить будильник» из плашки. */
  enum class DisableDecision { DisableAlarm, CancelNotificationOnly }

  /**
   * AUD-13 (R-05): устаревшее действие из плашки НЕ должно выключать уже изменённый будильник.
   * Выключаем, только если время звонка в extra всё ещё совпадает с текущим расписанием
   * ([isStillRelevant]); иначе — только снимаем старую плашку.
   */
  fun disableDecision(
    extraRingAtMillis: Long?,
    expectedNextMillis: Long?,
    nowMillis: Long,
    windowMs: Long,
  ): DisableDecision =
    if (isStillRelevant(extraRingAtMillis, expectedNextMillis, nowMillis, windowMs)) {
      DisableDecision.DisableAlarm
    } else {
      DisableDecision.CancelNotificationOnly
    }

  /**
   * AUD-16 (D3): обрабатывать ли действие приёмника при выключенном тумблере pre-ring.
   * Новые плашки при выключенном тумблере НЕ ставим, но действие «Выключить» и снятие уже
   * опубликованной плашки обязаны работать — иначе останется живая плашка с нерабочей кнопкой.
   */
  fun shouldHandleAction(action: String?, preRingEnabled: Boolean): Boolean =
    preRingEnabled || action == PreRingReceiver.ACTION_DISABLE

  private const val NOTIFICATION_BASE = 10_000
  private const val NOTIFICATION_RANGE = 1_000_000
}

/**
 * Приёмник предварительного сигнала: показывает тихую плашку «час до звонка» с действием
 * «Выключить будильник» и обрабатывает это действие (выключение + перепланирование).
 *
 * НЕ directBootAware: до разблокировки Room недоступен, а действие «выключить» без базы
 * невозможно; системные exact-сигналы, доставленные в залоченном состоянии, уходят в
 * отложенную доставку, поэтому на входе пересчитываем актуальное срабатывание и сверяем
 * с запланированным ([PreRingPolicy.isStillRelevant]) — устаревшую плашку снимаем.
 */
class PreRingReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val action = intent.action
    val settings = SettingsStore(context)
    // AUD-16 (D3): при выключенном тумблере новые плашки НЕ ставим, но действие «Выключить» и
    // снятие уже опубликованной плашки обязаны работать — иначе останется живая плашка с нерабочей кнопкой.
    if (!PreRingPolicy.shouldHandleAction(action, settings.preRingEnabled())) return

    val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
    if (alarmId < 0) return

    when (action) {
      ACTION_PRE_RING -> {
        val triggerAt = intent.getLongExtra(EXTRA_TRIGGER_AT_MILLIS, -1L)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
          try {
            val repo = AlarmRepository(context)
            val alarm = repo.byId(alarmId)
            val expected = if (alarm != null && alarm.enabled) {
              AlarmScheduler.nextTriggerAtMillis(context, repo, alarm)
            } else {
              null
            }
            // B-02: показываем только если пересчитанное срабатывание совпадает с запланированным;
            // иначе расписание устарело/изменилось — плашку (в т. ч. уже показанную) снимаем.
            if (alarm != null && alarm.enabled && expected != null &&
              PreRingPolicy.isStillRelevant(
                scheduledTriggerAtMillis = triggerAt.takeIf { it > 0 },
                expectedNextMillis = expected,
                nowMillis = System.currentTimeMillis(),
                windowMs = PreRingPolicy.RELEVANCE_WINDOW_MS,
              )
            ) {
              post(context, alarmId, alarm.label, alarm.hour, alarm.minute, expected)
            } else {
              manager(context).cancel(PreRingPolicy.notificationId(alarmId))
            }
          } catch (error: CancellationException) {
            throw error
          } catch (error: Exception) {
            Log.e(TAG, "PreRing: не удалось обработать плашку id=$alarmId", error)
          } finally {
            pending.finish()
          }
        }
      }

      ACTION_DISABLE -> {
        val actionRingAt = intent.getLongExtra(EXTRA_TRIGGER_AT_MILLIS, -1L)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
          try {
            val repo = AlarmRepository(context)
            val alarm = repo.byId(alarmId)
            val expected = if (alarm != null && alarm.enabled) {
              AlarmScheduler.nextTriggerAtMillis(context, repo, alarm)
            } else {
              null
            }
            // AUD-13 (R-05): устаревшее действие (будильник уже изменён/выключен) НЕ выключает
            // новый план — только снимаем старую плашку.
            val decision = PreRingPolicy.disableDecision(
              extraRingAtMillis = actionRingAt.takeIf { it > 0 },
              expectedNextMillis = expected,
              nowMillis = System.currentTimeMillis(),
              windowMs = PreRingPolicy.RELEVANCE_WINDOW_MS,
            )
            if (decision == PreRingPolicy.DisableDecision.DisableAlarm && alarm != null) {
              repo.update(alarm.copy(enabled = false))
              AlarmScheduler.reschedule(context, repo, alarm.copy(enabled = false))
            }
            manager(context).cancel(PreRingPolicy.notificationId(alarmId))
          } catch (error: CancellationException) {
            throw error
          } catch (error: Exception) {
            Log.e(TAG, "PreRing: не удалось обработать действие id=$alarmId", error)
          } finally {
            pending.finish()
          }
        }
      }
    }
  }

  private fun post(context: Context, alarmId: Long, label: String, hour: Int, minute: Int, ringAtMillis: Long) {
    if (!canPostNotifications(context)) {
      // B-06: на Android 13+ без POST_NOTIFICATIONS уведомление не показываем и не падаем.
      Log.w(TAG, "PreRing id=$alarmId: нет разрешения POST_NOTIFICATIONS — плашку не показываем")
      runCatching {
        AlarmEventLog(context).record(
          AlarmEventType.ERROR,
          "id=$alarmId: плашка «час до звонка» не показана (нет разрешения на уведомления)",
          System.currentTimeMillis(),
        )
      }
      return
    }

    val manager = manager(context)
    // B-06: канал DEFAULT + setSilent — тихая плашка без heads-up; важнось и «тихость» согласованы.
    manager.createNotificationChannel(
      NotificationChannel(CHANNEL_ID, "Предстоящий звонок", NotificationManager.IMPORTANCE_DEFAULT)
        .apply {
          description = "Тихая плашка за час до звонка: будильник можно выключить заранее."
          setSound(null, null)
          enableVibration(false)
          enableLights(false)
        }
    )

    val timeText = TIME_FMT.format(java.time.LocalTime.of(hour, minute))
    val title = if (label.isBlank()) timeText else "$timeText · $label"

    val disable = PendingIntent.getBroadcast(
      context,
      AlarmRequestCodes.preRingDisable(alarmId),
      Intent(context, PreRingReceiver::class.java)
        .setAction(ACTION_DISABLE)
        .putExtra(EXTRA_ALARM_ID, alarmId)
        .putExtra(EXTRA_TRIGGER_AT_MILLIS, ringAtMillis),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val open = PendingIntent.getActivity(
      context,
      AlarmRequestCodes.preRingOpen(alarmId),
      Intent(context, MainActivity::class.java),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    manager.notify(
      PreRingPolicy.notificationId(alarmId),
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle(title)
        .setContentText("Зазвонит примерно через час. Можно выключить заранее.")
        .setStyle(
          NotificationCompat.BigTextStyle()
            .bigText("Зазвонит примерно через час. Можно выключить заранее.")
        )
        .setContentIntent(open)
        .addAction(0, "Выключить будильник", disable)
        .setSilent(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        // AUD-13 (R-05): время звонка в `when` — по нему AlarmScheduler отличает устаревшую плашку.
        .setWhen(ringAtMillis)
        .setShowWhen(false)
        .setAutoCancel(true)
        .build()
    )
  }

  /** B-06: на Android 13+ `POST_NOTIFICATIONS` — runtime-разрешение; без него плашку не показать. */
  private fun canPostNotifications(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      true
    }

  private fun manager(context: Context): NotificationManager =
    context.getSystemService(NotificationManager::class.java)!!

  companion object {
    private const val TAG = "PreRingReceiver"
    const val ACTION_PRE_RING = "ru.titeha.shiftalarm.action.PRE_RING"
    const val ACTION_DISABLE = "ru.titeha.shiftalarm.action.PRE_RING_DISABLE"
    const val EXTRA_ALARM_ID = "pre_ring_alarm_id"
    const val EXTRA_TRIGGER_AT_MILLIS = "pre_ring_trigger_at_millis"
    private const val CHANNEL_ID = "pre_ring"
    private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
  }
}
