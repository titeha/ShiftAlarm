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
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ru.titeha.shiftalarm.MainActivity
import ru.titeha.shiftalarm.data.AlarmEntity
import ru.titeha.shiftalarm.data.AlarmEventLog
import ru.titeha.shiftalarm.data.AlarmEventType
import ru.titeha.shiftalarm.data.AlarmRepository
import ru.titeha.shiftalarm.data.SettingsStore
import ru.titeha.shiftalarm.schedule.AlarmTimes
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

  /** AUD-17: id относится к диапазону уведомлений-плашек pre-ring (для сканирования активных). */
  fun isPreRingNotificationId(id: Int): Boolean =
    id in NOTIFICATION_BASE until (NOTIFICATION_BASE + NOTIFICATION_RANGE)

  /**
   * AUD-17: кого выключает действие «Выключить будильник» из плашки — ВСЮ группу (решение владельца:
   * действие над звонком, не над сущностью). Если участников нет (старая плашка до AUD-17) — выключается
   * только сам будильник. Дубликаты id убираются; пустой список трактуется как отсутствие группы.
   */
  fun disableTargets(primaryAlarmId: Long, groupIds: List<Long>?): List<Long> =
    (groupIds?.takeIf { it.isNotEmpty() } ?: listOf(primaryAlarmId)).distinct()

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

  /** AUD-17: что делает действие «Выключить будильник» с одним участником группы. */
  enum class DisableAction { SkipCurrentOccurrence, CompleteOneShot, CancelNotificationOnly }

  /**
   * Итог действия «Выключить будильник» из плашки для одного будильника:
   *  - [DisableAction.SkipCurrentOccurrence] — повторяющийся: пропустить ТЕКУЩЕЕ срабатывание,
   *    будильник остаётся включённым;
   *  - [DisableAction.CompleteOneShot] — разовый: завершить по правилу `deleteAfterFiring`;
   *  - [DisableAction.CancelNotificationOnly] — устаревшее действие/будильник удалён: только
   *    снять плашку, будильник не трогать.
   */
  fun disableAction(alarm: AlarmEntity?, decision: DisableDecision): DisableAction = when {
    alarm == null || decision == DisableDecision.CancelNotificationOnly ->
      DisableAction.CancelNotificationOnly
    AlarmTimes.isOneShot(alarm) -> DisableAction.CompleteOneShot
    else -> DisableAction.SkipCurrentOccurrence
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
              // AUD-17: одна плашка на ВСЕ совпадающие по времени будильники.
              post(context, preRingGroup(context, repo, alarm.id, alarm.label, expected))
            } else {
              cancelPreRingNotification(context, alarmId)
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
        // AUD-17: действие выключает ВСЮ группу (решение владельца: действие над звонком).
        val groupIds = PreRingPolicy.disableTargets(
          alarmId,
          intent.getLongArrayExtra(PRE_RING_GROUP_IDS)?.toList(),
        )
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
          try {
            val repo = AlarmRepository(context)
            groupIds.forEach { id ->
              val alarm = repo.byId(id) ?: return@forEach
              val expected = if (alarm.enabled) {
                AlarmScheduler.nextTriggerAtMillis(context, repo, alarm)
              } else {
                null
              }
              // AUD-13 (R-05): устаревшее действие (будильник уже изменён/выключен) НЕ трогает
              // новый план — только снимаем старую плашку.
              val decision = PreRingPolicy.disableDecision(
                extraRingAtMillis = actionRingAt.takeIf { it > 0 },
                expectedNextMillis = expected,
                nowMillis = System.currentTimeMillis(),
                windowMs = PreRingPolicy.RELEVANCE_WINDOW_MS,
              )
              when (PreRingPolicy.disableAction(alarm, decision)) {
                PreRingPolicy.DisableAction.CancelNotificationOnly -> return@forEach

                PreRingPolicy.DisableAction.CompleteOneShot -> {
                  // Разовый: «пропустить текущий звонок» = завершить по правилу обычного срабатывания
                  // (удалить при deleteAfterFiring, иначе выключить).
                  if (alarm.deleteAfterFiring) {
                    repo.delete(alarm)
                  } else {
                    repo.update(alarm.copy(enabled = false))
                  }
                  // Снять системный сигнал (enabled=false → cancel) и пересобрать DE-кэш без этого будильника.
                  AlarmScheduler.reschedule(context, repo, alarm.copy(enabled = false))
                }

                PreRingPolicy.DisableAction.SkipCurrentOccurrence -> {
                  // DisableAlarm => expected не null (иначе isStillRelevant был бы false).
                  val ringAt = expected ?: return@forEach
                  // Повторяющийся: помечаем ТЕКУЩЕЕ срабатывание пропущенным (enabled НЕ меняется),
                  // reschedule пересчитает следующее непропущенное срабатывание.
                  repo.addSkipped(id, ringAt)
                  AlarmScheduler.reschedule(context, repo, alarm)
                }
              }
            }
            groupIds.forEach { cancelPreRingNotification(context, it) }
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

  private fun post(context: Context, members: List<RingGroupMember>) {
    val primary = members.minByOrNull { it.alarmId } ?: return
    if (!canPostNotifications(context)) {
      // B-06: на Android 13+ без POST_NOTIFICATIONS уведомление не показываем и не падаем.
      Log.w(TAG, "PreRing id=${primary.alarmId}: нет разрешения POST_NOTIFICATIONS — плашку не показываем")
      runCatching {
        AlarmEventLog(context).record(
          AlarmEventType.ERROR,
          "id=${primary.alarmId}: плашка «час до звонка» не показана (нет разрешения на уведомления)",
          System.currentTimeMillis(),
        )
      }
      return
    }

    val manager = manager(context)
    manager.createNotificationChannel(
      NotificationChannel(CHANNEL_ID, "Предстоящий звонок", NotificationManager.IMPORTANCE_DEFAULT)
        .apply {
          description = "Тихая плашка за час до звонка: будильник можно выключить заранее."
          setSound(null, null)
          enableVibration(false)
          enableLights(false)
        }
    )

    val ringAtMillis = members.minByOrNull { it.scheduledTriggerAtMillis }?.scheduledTriggerAtMillis
      ?: System.currentTimeMillis()
    val timeText = TIME_FMT.format(
      java.time.Instant.ofEpochMilli(ringAtMillis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalTime()
    )
    val names = ringGroupSummary(members, AlarmService.DEFAULT_TEXT)
    val groupIds = members.map { it.alarmId }

    val disable = PendingIntent.getBroadcast(
      context,
      AlarmRequestCodes.preRingDisable(primary.alarmId),
      Intent(context, PreRingReceiver::class.java)
        .setAction(ACTION_DISABLE)
        .putExtra(EXTRA_ALARM_ID, primary.alarmId)
        .putExtra(EXTRA_TRIGGER_AT_MILLIS, ringAtMillis)
        .putExtra(PRE_RING_GROUP_IDS, groupIds.toLongArray()),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val open = PendingIntent.getActivity(
      context,
      AlarmRequestCodes.preRingOpen(primary.alarmId),
      Intent(context, MainActivity::class.java),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    val notifExtras = Bundle().apply {
      putString(PRE_RING_GROUP_IDS, groupIds.joinToString(","))
    }
    manager.notify(
      PreRingPolicy.notificationId(primary.alarmId),
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle(timeText)
        .setContentText(names)
        .setStyle(NotificationCompat.BigTextStyle().bigText("$names. Зазвонит примерно через час."))
        .setContentIntent(open)
        .addAction(0, "Выключить будильник", disable)
        .setSilent(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        // AUD-13 (R-05): время звонка в `when` — по нему AlarmScheduler отличает устаревшую плашку.
        .setWhen(ringAtMillis)
        .setShowWhen(false)
        .setAutoCancel(true)
        .addExtras(notifExtras)
        .build()
    )
  }

  /**
   * AUD-17: все включённые будильники, у которых ближайшее срабатывание совпадает с [ringAtMillis]
   * (в пределах `RELEVANCE_WINDOW_MS`), плюс входящий. Названия берём из Room (приёмник НЕ
   * directBootAware, CE доступно).
   */
  private suspend fun preRingGroup(
    context: Context,
    repo: AlarmRepository,
    incomingId: Long,
    incomingLabel: String,
    ringAtMillis: Long,
  ): List<RingGroupMember> {
    var members = listOf(RingGroupMember(incomingId, incomingLabel, ringAtMillis))
    runCatching {
      repo.enabled().forEach { other ->
        if (other.id == incomingId) return@forEach
        val next = AlarmScheduler.nextTriggerAtMillis(context, repo, other) ?: return@forEach
        if (kotlin.math.abs(next - ringAtMillis) <= PreRingPolicy.RELEVANCE_WINDOW_MS) {
          members = ringGroupWith(members, RingGroupMember(other.id, other.label, next))
        }
      }
    }
    return members.sortedForGroup()
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

/**
 * AUD-17 (R-05): активные уведомления-плашки, относящиеся к будильнику [alarmId] — по id
 * первичного участника или по списку id группы из `extras` (плашка сгруппированная).
 */
internal fun activePreRingNotifications(context: Context, alarmId: Long): List<StatusBarNotification> =
  runCatching {
    val manager = context.getSystemService(NotificationManager::class.java)
      ?: return@runCatching emptyList()
    val wantedId = PreRingPolicy.notificationId(alarmId)
    manager.activeNotifications.filter { sbn ->
      PreRingPolicy.isPreRingNotificationId(sbn.id) &&
        (
          sbn.id == wantedId ||
            sbn.notification.extras.getString(PRE_RING_GROUP_IDS)
              ?.split(',')
              ?.any { it.trim() == alarmId.toString() } == true
          )
    }
  }.getOrDefault(emptyList())

/** AUD-17 (R-05): снять все плашки, относящиеся к будильнику (или его группе). */
internal fun cancelPreRingNotification(context: Context, alarmId: Long) {
  runCatching {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return@runCatching
    activePreRingNotifications(context, alarmId).forEach { manager.cancel(it.tag, it.id) }
  }
}

/** AUD-17 (R-05): время звонка активной плашки (или её группы) для будильника [alarmId], либо null. */
internal fun activePreRingRingAt(context: Context, alarmId: Long): Long? =
  activePreRingNotifications(context, alarmId).firstOrNull()?.notification?.`when`

/** Ключ extras плашки: список id участников группы (csv) и LongArray в интенте действия. */
internal const val PRE_RING_GROUP_IDS = "pre_ring_group_ids"
