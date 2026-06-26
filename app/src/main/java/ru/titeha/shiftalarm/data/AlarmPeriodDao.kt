package ru.titeha.shiftalarm.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmPeriodDao {

  /** Периоды будильника для UI-списка (по возрастанию даты начала). */
  @Query("SELECT * FROM alarm_periods WHERE alarmId = :alarmId ORDER BY fromEpochDay")
  fun observeForAlarm(alarmId: Long): Flow<List<AlarmPeriod>>

  /** Все периоды всех будильников (живой поток) — для превью «след:» в списке. */
  @Query("SELECT * FROM alarm_periods ORDER BY fromEpochDay")
  fun observeAll(): Flow<List<AlarmPeriod>>

  /** Периоды будильника — для расчёта расписания (перепланирование). */
  @Query("SELECT * FROM alarm_periods WHERE alarmId = :alarmId ORDER BY fromEpochDay")
  suspend fun forAlarm(alarmId: Long): List<AlarmPeriod>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(period: AlarmPeriod): Long

  @Delete
  suspend fun delete(period: AlarmPeriod)

  @Query("DELETE FROM alarm_periods WHERE alarmId = :alarmId")
  suspend fun deleteForAlarm(alarmId: Long)
}
