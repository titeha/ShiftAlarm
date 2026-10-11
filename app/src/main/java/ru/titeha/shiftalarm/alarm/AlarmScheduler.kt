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
import ru.titeha.shiftalarm.data.SettingsStore
import ru.titeha.shiftalarm.schedule.AlarmTimes
import ru.titeha.shiftalarm.schedule.ProductionCalendars
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

  /** Метка снуз-звонка: срабатывание — продолжение сессии, а не плановое (валидатор пропускаем). */
  const val EXTRA_IS_SNOOZE = "is_snooze"

  const val NO_ID = -1L
  const val NO_TRIGGER_AT_MILLIS = AlarmFireValidator.MISSING_TRIGGER_AT_MILLIS

  private const val TAG = "AlarmScheduler"

  /**
   * AUD-2: глубина device-protected плана срабатываний (дни). Открытый вопрос плана (3 против 7):
   * принято предложенное значение 3 — меняется одной константой.
   */
  private const val DE_PLAN_DAYS = 3L

  /** AUD-2: максимум записей плана на один будильник. */
  private const val MAX_PLAN_PER_ALARM = 4

  /** AUD-2: максимум записей плана суммарно — защита device-protected prefs от разрастания. */
  private const val MAX_PLAN_TOTAL = 32

  /** Насколько должно просрочиться срабатывание из кэша, чтобы счесть его пропущенным (мс). */
  private const val MISS_GRACE_MS = 90_000L

  /** Повтор «нет данных календаря» не чаще раза в сутки (мс): состояние устойчивое. */
  private const val CALENDAR_DEGRADED_REPEAT_WINDOW_MS = 24L * 60 * 60 * 1000

  /**
   * Повтор «запланирован» с тем же временем не пишется в пределах этого окна (мс): старт процесса
   * и обновление кэша планируют одно и то же дважды, системные события приходят подряд, и без
   * дедупликации журнал состоял почти только из них (N6). Действия пользователя и сменившееся время
   * звонка происходят заметно позже и в журнал попадают.
   */
  private const val SCHEDULED_REPEAT_WINDOW_MS = 10L * 60 * 1000

  /** Грейс для перевыставления просроченного за время ребута звонка при locked boot (мс). */
  private const val LOCKED_BOOT_OVERDUE_GRACE_MS = 30L * 60L * 1000L

  /**
   * Запланировать, подгрузив периоды отпуска и правки календаря из репозитория.
   */
  suspend fun reschedule(context: Context, repo: AlarmRepository, alarm: AlarmEntity) {
    // AUD-2/B-04: собрать пропуски из старого кэша ДО перезаписи в refreshDirectBootCache ниже.
    collectMissed(context)
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
      AlarmTimes.next(
        alarm,
        periods,
        overrides,
        LocalDateTime.now(),
        SettingsStore(context).globalWorkWeek()
      )
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
        // B-01: пред-сигнал ставим ТОЛЬКО при успешно поставленном основном звонке и с собственной
        // обработкой отказа (см. schedulePreRing) — вторичный сигнал не должен ломать перепланирование.
        schedulePreRing(context, alarm.id, AlarmInstant.epochMilli(next))
        AlarmEventLog(context).recordIfFresh(
          type = AlarmEventType.SCHEDULED,
          detail = "id=${alarm.id} → %02d.%02d %02d:%02d".format(
            next.dayOfMonth, next.monthValue, next.hour, next.minute
          ),
          atMillis = System.currentTimeMillis(),
          windowMillis = SCHEDULED_REPEAT_WINDOW_MS
        )
      } else {
        AlarmEventLog(context).record(
          AlarmEventType.ERROR,
          "id=${alarm.id}: не удалось запланировать (нет разрешения на точные будильники)",
          System.currentTimeMillis()
        )
      }

      // Деградация календаря — ПОСЛЕ системного планирования: сбой диагностического журнала не должен
      // оставлять будильник без сигнала. Проверяется год ЗВОНКА, а не год отсчёта: иначе невидим самый
      // частый случай «конец декабря → в следующем году данных нет» (находка F4 ревью TE1).
      recordCalendarDegradation(context, alarm, next)
    }
  }

  /**
   * Записать в журнал, что на год звонка [next] нет данных производственного календаря, а будильник
   * его учитывает ([AlarmTimes.usesProductionCalendar]).
   *
   * Состояние устойчивое (данных не будет, пока не обновится кэш), поэтому повтор не пишется, если
   * такое же событие для этого будильника и года уже есть за последние
   * [CALENDAR_DEGRADED_REPEAT_WINDOW_MS]: иначе каждый старт процесса добавлял бы запись на каждый
   * будильник и вытеснял срабатывания из кольцевого буфера на 200 записей.
   *
   * Сбой записи или проверок глушится: диагностика не должна снова сломать уже завершённое планирование.
   */
  private fun recordCalendarDegradation(context: Context, alarm: AlarmEntity, next: LocalDateTime) {
    // Внутри runCatching и проверки: hasData() дёргает внешний источник календаря, и исключение из него
    // не должно доходить до вызывающего (в suspend-перегрузке за reschedule идёт refreshDirectBootCache).
    runCatching {
      if (!AlarmTimes.usesProductionCalendar(alarm)) return@runCatching
      if (ProductionCalendars.hasData(next.year)) return@runCatching

      AlarmEventLog(context).recordIfFresh(
        type = AlarmEventType.CALENDAR_DEGRADED,
        detail = "id=${alarm.id}: нет данных календаря на ${next.year} — " +
          "нерабочие праздники не учитываются",
        atMillis = System.currentTimeMillis(),
        windowMillis = CALENDAR_DEGRADED_REPEAT_WINDOW_MS
      )
    }.onFailure {
      // Глушим, но не бесследно: до правки исключение охраны доходило до отчёта батча и давало ERROR.
      Log.w(TAG, "CALENDAR_DEGRADED: проверки или запись не удались", it)
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
    // TL02: честный детект пропусков — ДО любой перезаписи DE-кэша ниже по методу.
    // Инцидент 2026-10-10: без этого перезапись в цикле скрывала просроченные записи
    // (locked-звонок не доставлен при выключенном автозапуске MIUI — пользователь
    // не видел ни звонка, ни карточки пропуска).
    collectMissed(context)
    // Все периоды и правки грузим ДВУМЯ запросами и группируем по alarmId, а не по запросу на каждый
    // будильник (N+1). Путь массовый (boot/системное событие) — так меньше обращений к БД.
    val periodsByAlarm = repo.allPeriodsList().groupBy { it.alarmId }
    val overridesByAlarm = repo.allOverridesList()
      .groupBy { it.alarmId }
      .mapValues { (_, list) -> list.mapNotNull { it.toDayOverrideOrNull() } }

    val report = AlarmRescheduleBatch.run(
      alarms = alarms,
      operation = { alarm ->
        // Через не-repo перегрузку, чтобы кэш Direct Boot пересобрать один раз ниже, а не на каждой
        // записи. Периоды применимы всем режимам; правки (подмены смен) — только сменным.
        reschedule(
          context = context,
          alarm = alarm,
          periods = periodsByAlarm[alarm.id].orEmpty(),
          overrides = if (alarm.mode == AlarmEntity.MODE_SHIFT) {
            overridesByAlarm[alarm.id].orEmpty()
          } else {
            emptyList()
          }
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
   * Собрать пропущенные звонки из старого DE-кэша (TL02). Вызывается в начале rescheduleAll —
   * до перезаписей кэша циклом перепланирования. Безопасно вызывать повторно: после первой
   * перезаписи просроченных записей в кэше нет.
   */
  fun collectMissed(context: Context) {
    val store = DirectBootAlarmStore(context)
    val stale = staleMissed(store.read(), System.currentTimeMillis(), MISS_GRACE_MS)
    if (stale.isEmpty()) return
    // Идемпотентность: повторный вызов до перезаписи кэша не должен дублировать карточки пропусков.
    val known = store.readMissed().map { it.alarmId to it.triggerAtMillis }.toSet()
    store.addMissed(stale.filter { (it.alarmId to it.triggerAtMillis) !in known })
  }

  /**
   * AUD-2: поставить ближайшее будущее срабатывание будильника из device-protected плана — без
   * обращения к Room (locked-путь). true — сигнал поставлен; false — будущих записей нет.
   */
  fun scheduleNextFromCache(context: Context, alarmId: Long, nowMillis: Long): Boolean {
    val next = nextCached(DirectBootAlarmStore(context).read(), alarmId, nowMillis) ?: return false
    return scheduleAt(context = context, alarmId = alarmId, triggerAtMillis = next.triggerAtMillis)
  }

  /**
   * B-02: актуальное ближайшее срабатывание будильника (мс) тем же движком, что и планирование.
   * null — будильник выключен или срабатываний нет. Нужно приёмнику пред-сигнала, чтобы перед
   * показом плашки сверить сохранённое время с фактическим расписанием.
   */
  suspend fun nextTriggerAtMillis(
    context: Context,
    repo: AlarmRepository,
    alarm: AlarmEntity,
  ): Long? {
    if (!alarm.enabled) return null
    val next = AlarmTimes.next(
      alarm,
      offPeriodsFor(repo, alarm),
      overridesFor(repo, alarm),
      LocalDateTime.now(),
      SettingsStore(context).globalWorkWeek()
    ) ?: return null
    return AlarmInstant.epochMilli(next)
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
    cancelPreRing(context, alarmId)
  }

  /**
   * Тихий точный сигнал за [PreRingPolicy.LEAD_MINUTES] минут до звонка (NF1): по нему
   * `PreRingReceiver` покажет плашку «час до звонка» с действием «Выключить будильник».
   * `setExactAndAllowWhileIdle`, а не `setAlarmClock` — чтобы не жечь системный значок
   * будильника в статус-баре. Настройка выключена или до звонка меньше часа — сигнал снимается.
   */
  fun schedulePreRing(context: Context, alarmId: Long, ringAtMillis: Long) {
    val manager = context.getSystemService(AlarmManager::class.java)
    val pre = if (SettingsStore(context).preRingEnabled()) {
      PreRingPolicy.preRingAtMillis(ringAtMillis, System.currentTimeMillis())
    } else {
      null
    }
    if (pre == null) {
      manager.cancel(preRingPendingIntent(context, alarmId, NO_TRIGGER_AT_MILLIS))
      return
    }
    try {
      manager.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        pre,
        preRingPendingIntent(context, alarmId, pre)
      )
    } catch (error: SecurityException) {
      // B-01: отказ вторичного сигнала не должен ломать основной звонок и перепланирование.
      // Снимаем только pre-ring PendingIntent, основной сигнал не трогаем.
      Log.w(
        TAG,
        "PreRing id=$alarmId: нет разрешения на точные будильники — плашка не поставлена",
        error
      )
      runCatching {
        AlarmEventLog(context).record(
          AlarmEventType.ERROR,
          "id=$alarmId: пред-сигнал «час до звонка» не поставлен (нет разрешения на точные будильники)",
          System.currentTimeMillis()
        )
      }
      manager.cancel(preRingPendingIntent(context, alarmId, NO_TRIGGER_AT_MILLIS))
    }
  }

  fun cancelPreRing(context: Context, alarmId: Long) {
    context.getSystemService(AlarmManager::class.java)
      .cancel(preRingPendingIntent(context, alarmId, NO_TRIGGER_AT_MILLIS))
  }

  private fun preRingPendingIntent(
    context: Context,
    alarmId: Long,
    triggerAtMillis: Long
  ): PendingIntent {
    val intent = Intent(context, PreRingReceiver::class.java)
      .setAction(PreRingReceiver.ACTION_PRE_RING)
      .putExtra(PreRingReceiver.EXTRA_ALARM_ID, alarmId)
      .putExtra(PreRingReceiver.EXTRA_TRIGGER_AT_MILLIS, triggerAtMillis)
    return PendingIntent.getBroadcast(
      context,
      AlarmRequestCodes.preRing(alarmId),
      intent,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
  }

  /**
   * Поставить снуз-звонок будильника [alarmId] на [atMillis] — отдельным (снуз-)неймспейсом, чтобы не
   * затирать базовое расписание. Пишется в device-protected снуз-кэш, чтобы пережить ребут в снуз-окне.
   * true — сигнал поставлен.
   */
  fun scheduleSnoozeAt(context: Context, alarmId: Long, atMillis: Long, label: String): Boolean {
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    val info = AlarmManager.AlarmClockInfo(atMillis, snoozeShowPendingIntent(context, alarmId))

    return try {
      alarmManager.setAlarmClock(info, snoozeFirePendingIntent(context, alarmId, atMillis, label))
      DirectBootAlarmStore(context).putSnooze(
        CachedAlarm(alarmId = alarmId, triggerAtMillis = atMillis, label = label)
      )
      true
    } catch (error: SecurityException) {
      Log.e(TAG, "Не удалось поставить снуз id=$alarmId: нет разрешения на точные будильники", error)
      false
    }
  }

  /** Снять снуз-звонок будильника и убрать его из снуз-кэша. */
  fun cancelSnooze(context: Context, alarmId: Long) {
    context.getSystemService(AlarmManager::class.java)
      .cancel(snoozeFirePendingIntent(context, alarmId, NO_TRIGGER_AT_MILLIS, ""))
    DirectBootAlarmStore(context).removeSnooze(alarmId)
  }

  /**
   * Пересобрать device-protected кэш ближайших звонков — чтобы после ночного ребута залоченным
   * `directBootAware`-ресивер мог перевыставить будильники, не обращаясь к Room.
   *
   * Пересобрать device-protected кэш — план ближайших срабатываний, чтобы после ночного ребута
   * залоченным `directBootAware`-ресивер мог перевыставить будильники, не обращаясь к Room.
   *
   * Берёт включённые будильники, считает ближайшие срабатывания каждого (тем же движком, что и
   * планирование) и кладёт в кэш до [MAX_PLAN_PER_ALARM] записей на будильник в пределах горизонта
   * [DE_PLAN_DAYS] дней (суммарно — не более [MAX_PLAN_TOTAL]).
   */
  suspend fun refreshDirectBootCache(context: Context, repo: AlarmRepository) {
    // AUD-2/B-04: единственная точка перезаписи кэша — пропуски собираем ДО неё (идемпотентно).
    collectMissed(context)

    val store = DirectBootAlarmStore(context)

    val now = LocalDateTime.now()
    val horizonMillis = AlarmInstant.epochMilli(now.plusDays(DE_PLAN_DAYS))
    // Глобальная рабочая неделя — одна на весь кэш (настройка глобальная, Q1=1а); DE-хранилище
    // читается и до разблокировки (Q2=2а).
    val globalWeek = SettingsStore(context).globalWorkWeek()

    // AUD-2: план ближайших срабатываний — до MAX_PLAN_PER_ALARM записей на будильник, чтобы
    // залоченный телефон мог прозвонить несколько раз до разблокировки (N-2). Разовый будильник
    // получает ровно одну запись с флагом oneShot (без «завтра то же время» — N-1).
    val plan = repo.enabled().flatMap { alarm ->
      val oneShot = AlarmTimes.isOneShot(alarm)
      val periods = offPeriodsFor(repo, alarm)
      val overrides = overridesFor(repo, alarm)
      val nextMillis = buildList {
        var cursor = now
        while (size < MAX_PLAN_PER_ALARM) {
          val next = AlarmTimes.next(alarm, periods, overrides, cursor, globalWeek) ?: break
          val millis = AlarmInstant.epochMilli(next)
          if (millis > horizonMillis) break
          add(millis)
          if (oneShot) break
          cursor = next
        }
      }
      planEntries(alarmId = alarm.id, label = alarm.label, nextMillis = nextMillis, oneShot = oneShot)
    }

    store.write(plan.sortedBy { it.triggerAtMillis }.take(MAX_PLAN_TOTAL))
  }

  /**
   * Слепо перевыставить будильники из device-protected кэша — для `LOCKED_BOOT_COMPLETED`, когда
   * Room ещё недоступна. PendingIntent собирается только из данных кэша (id + время срабатывания),
   * без единого обращения к базе.
   */
  fun reArmFromCache(context: Context) {
    val now = System.currentTimeMillis()
    val store = DirectBootAlarmStore(context)

    // AUD-2: на будильник в плане несколько записей, а PendingIntent у него один — выставляем
    // ближайшую допустимую запись каждого будильника (просроченные сверх грейса пропускаем).
    store.read()
      .groupBy { it.alarmId }
      .forEach { (alarmId, entries) ->
        val candidate = entries
          .filter { it.triggerAtMillis >= now - LOCKED_BOOT_OVERDUE_GRACE_MS }
          .minByOrNull { it.triggerAtMillis }
          ?: return@forEach
        scheduleAt(
          context = context,
          alarmId = alarmId,
          triggerAtMillis = maxOf(candidate.triggerAtMillis, now)
        )
      }

    // Отложенные (снуз) звонки — тем же правилом грейса, но снуз-неймспейсом, чтобы ребут внутри
    // снуз-окна не потерял звонок и сессия продолжилась (счётчик снуза лежит в RingSessionStore).
    store.readSnoozes().forEach { snooze ->
      if (snooze.triggerAtMillis < now - LOCKED_BOOT_OVERDUE_GRACE_MS) {
        return@forEach
      }
      scheduleSnoozeAt(
        context = context,
        alarmId = snooze.alarmId,
        atMillis = maxOf(snooze.triggerAtMillis, now),
        label = snooze.label
      )
    }
  }

  private suspend fun offPeriodsFor(
    repo: AlarmRepository,
    alarm: AlarmEntity
  ): List<AlarmPeriod> {
    // Периоды без будильника (отпуск/больничный/…) применимы и к сменам, и к «по дням недели».
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

  /** PendingIntent снуз-срабатывания: тот же AlarmReceiver, но снуз-неймспейс + метка EXTRA_IS_SNOOZE. */
  private fun snoozeFirePendingIntent(
    context: Context,
    alarmId: Long,
    atMillis: Long,
    label: String
  ): PendingIntent {
    val intent = Intent(context, AlarmReceiver::class.java)
      .putExtra(EXTRA_ALARM_ID, alarmId)
      .putExtra(EXTRA_TRIGGER_AT_MILLIS, atMillis)
      .putExtra(EXTRA_IS_SNOOZE, true)
      .putExtra(AlarmService.EXTRA_LABEL, label)

    return PendingIntent.getBroadcast(
      context,
      AlarmRequestCodes.snooze(alarmId),
      intent,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
  }

  /** Show-PendingIntent для AlarmClockInfo (иконка будущего сигнала); одинаков для базы и снуза. */
  private fun snoozeShowPendingIntent(context: Context, alarmId: Long): PendingIntent =
    PendingIntent.getActivity(
      context,
      showRequest(alarmId),
      Intent(context, AlarmActivity::class.java),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

  /*
   * Разные requestCode для «сработать» и «показать экран» — уникальны по id будильника, чтобы
   * несколько будильников не затирали друг друга. Логика — в чистом [AlarmRequestCodes] (покрыта тестом).
   */
  private fun fireRequest(id: Long): Int = AlarmRequestCodes.fire(id)

  private fun showRequest(id: Long): Int = AlarmRequestCodes.show(id)
}