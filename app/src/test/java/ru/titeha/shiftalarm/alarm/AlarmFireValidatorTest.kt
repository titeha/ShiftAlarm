package ru.titeha.shiftalarm.alarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.titeha.shiftalarm.data.AlarmEntity
import java.time.LocalDateTime
import java.time.ZoneId

class AlarmFireValidatorTest {
  private val zoneId: ZoneId = ZoneId.of("UTC")

  @Test
  fun `включённый разовый будильник с актуальным временем можно запускать`() {
    val triggerAt = LocalDateTime.of(2026, 6, 24, 7, 0)
    val alarm = weeklyAlarm(hour = 7, minute = 0, enabled = true)

    val result = AlarmFireValidator.shouldStartRinging(
      alarm = alarm,
      periods = emptyList(),
      overrides = emptyList(),
      scheduledTriggerAtMillis = millis(triggerAt),
      zoneId = zoneId
    )

    assertTrue(result)
  }

  @Test
  fun `выключенный будильник нельзя запускать`() {
    val triggerAt = LocalDateTime.of(2026, 6, 24, 7, 0)
    val alarm = weeklyAlarm(hour = 7, minute = 0, enabled = false)

    val result = AlarmFireValidator.shouldStartRinging(
      alarm = alarm,
      periods = emptyList(),
      overrides = emptyList(),
      scheduledTriggerAtMillis = millis(triggerAt),
      zoneId = zoneId
    )

    assertFalse(result)
  }

  @Test
  fun `устаревшее время срабатывания нельзя запускать`() {
    val oldTriggerAt = LocalDateTime.of(2026, 6, 24, 7, 0)

    /*
     * Будильник уже изменён на 08:00, но в старом PendingIntent осталось 07:00.
     * Такой intent не должен запускать звонок.
     */
    val alarm = weeklyAlarm(hour = 8, minute = 0, enabled = true)

    val result = AlarmFireValidator.shouldStartRinging(
      alarm = alarm,
      periods = emptyList(),
      overrides = emptyList(),
      scheduledTriggerAtMillis = millis(oldTriggerAt),
      zoneId = zoneId
    )

    assertFalse(result)
  }

  @Test
  fun `intent старого формата без времени разрешён для включённого будильника`() {
    val alarm = weeklyAlarm(hour = 7, minute = 0, enabled = true)

    val result = AlarmFireValidator.shouldStartRinging(
      alarm = alarm,
      periods = emptyList(),
      overrides = emptyList(),
      scheduledTriggerAtMillis = AlarmFireValidator.MISSING_TRIGGER_AT_MILLIS,
      zoneId = zoneId
    )

    assertTrue(result)
  }

  @Test
  fun `intent старого формата без времени запрещён для выключенного будильника`() {
    val alarm = weeklyAlarm(hour = 7, minute = 0, enabled = false)

    val result = AlarmFireValidator.shouldStartRinging(
      alarm = alarm,
      periods = emptyList(),
      overrides = emptyList(),
      scheduledTriggerAtMillis = AlarmFireValidator.MISSING_TRIGGER_AT_MILLIS,
      zoneId = zoneId
    )

    assertFalse(result)
  }

  private fun weeklyAlarm(
    hour: Int,
    minute: Int,
    enabled: Boolean
  ): AlarmEntity {
    return AlarmEntity(
      id = 1L,
      enabled = enabled,
      hour = hour,
      minute = minute,
      mode = AlarmEntity.MODE_WEEKLY,
      daysMask = 0
    )
  }

  private fun millis(dateTime: LocalDateTime): Long {
    return dateTime
      .atZone(zoneId)
      .toInstant()
      .toEpochMilli()
  }
}