package ru.titeha.shiftalarm.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Хранилище пропущенных срабатываний (см. [SkippedAlarmOccurrence]).
 *
 * Составной ключ `(alarmId, triggerAtMillis)`; вставка идемпотентна ([OnConflictStrategy.IGNORE]),
 * чтобы повторное нажатие/повторная запись одного и того же пропуска не падали.
 */
@Dao
interface SkippedAlarmOccurrenceDao {

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insert(occurrence: SkippedAlarmOccurrence)

  @Query(
    "SELECT EXISTS(SELECT 1 FROM skipped_alarm_occurrences " +
      "WHERE alarmId = :alarmId AND triggerAtMillis = :triggerAtMillis)"
  )
  suspend fun exists(alarmId: Long, triggerAtMillis: Long): Boolean

  @Query(
    "DELETE FROM skipped_alarm_occurrences " +
      "WHERE alarmId = :alarmId AND triggerAtMillis = :triggerAtMillis"
  )
  suspend fun delete(alarmId: Long, triggerAtMillis: Long)

  /** Пропущенные срабатывания будильника по возрастанию времени. */
  @Query(
    "SELECT * FROM skipped_alarm_occurrences " +
      "WHERE alarmId = :alarmId ORDER BY triggerAtMillis"
  )
  suspend fun forAlarm(alarmId: Long): List<SkippedAlarmOccurrence>

  @Query("DELETE FROM skipped_alarm_occurrences WHERE alarmId = :alarmId")
  suspend fun deleteForAlarm(alarmId: Long)

  /** Убрать пропуски, чьё время уже прошло (записи старше [cutoffMillis] больше не нужны). */
  @Query("DELETE FROM skipped_alarm_occurrences WHERE triggerAtMillis < :cutoffMillis")
  suspend fun deleteOlderThan(cutoffMillis: Long)
}
