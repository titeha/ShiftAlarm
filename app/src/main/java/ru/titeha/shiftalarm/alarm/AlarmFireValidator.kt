package ru.titeha.shiftalarm.alarm

import ru.titeha.shiftalarm.data.AlarmEntity
import ru.titeha.shiftalarm.data.AlarmPeriod
import ru.titeha.shiftalarm.schedule.AlarmTimes
import ru.titeha.shiftalarm.schedule.CountryProfile
import ru.titeha.shiftalarm.schedule.ScheduleOverrides
import ru.titeha.shiftalarm.schedule.WorkWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs

/**
 * Проверяет, можно ли запускать звонок по пришедшему alarm-intent.
 *
 * AlarmManager может доставить устаревший PendingIntent: например, если пользователь
 * изменил время будильника, выключил будильник или запись уже была удалена.
 *
 * Валидатор не запускает сервис и не обращается к Android API. Это чистая логика,
 * которую можно покрывать обычными unit-тестами.
 */
object AlarmFireValidator {
  const val MISSING_TRIGGER_AT_MILLIS: Long = -1L

  /**
   * Допуск актуальности срабатывания. AUD-17 (BKL1): это же значение — окно объединения
   * совпадающих звонков в группу ([ringGroupCanJoin]), чтобы не вводить третью константу.
   */
  internal val DefaultTolerance: Duration = Duration.ofMinutes(1)

  /**
   * true — звонок соответствует текущему состоянию будильника и его можно запускать.
   *
   * [scheduledTriggerAtMillis] — время срабатывания, которое было записано в PendingIntent
   * в момент планирования. Если его нет, считаем intent старым форматом и разрешаем звонок
   * только для включённого будильника.
   *
   * [globalWeek] — глобальная рабочая неделя пользователя (TC03): та же, с которой будильник
   * планировался. По умолчанию — стандартная неделя страны (сохраняет прежнее поведение).
   */
  fun shouldStartRinging(
    alarm: AlarmEntity,
    periods: List<AlarmPeriod>,
    overrides: List<ScheduleOverrides.DayOverride>,
    scheduledTriggerAtMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
    tolerance: Duration = DefaultTolerance,
    globalWeek: WorkWeek = CountryProfile.RU.standardWeek
  ): Boolean {
    if (!alarm.enabled) {
      return false
    }

    if (scheduledTriggerAtMillis == MISSING_TRIGGER_AT_MILLIS) {
      return true
    }

    if (scheduledTriggerAtMillis <= 0L) {
      return false
    }

    val scheduledAt = LocalDateTime.ofInstant(
      Instant.ofEpochMilli(scheduledTriggerAtMillis),
      zoneId
    )

    /*
     * AlarmTimes.next ищет ближайшее срабатывание строго после from.
     * Поэтому считаем ожидаемое срабатывание от точки чуть раньше планового времени.
     */
    val expected = AlarmTimes.next(
      alarm = alarm,
      periods = periods,
      overrides = overrides,
      from = scheduledAt.minus(tolerance),
      globalWeek = globalWeek
    ) ?: return false

    val differenceMillis = abs(Duration.between(expected, scheduledAt).toMillis())

    return differenceMillis <= tolerance.toMillis()
  }
}