package ru.titeha.shiftalarm.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmOverrideDao {

  /** Правки будильника для UI (живой поток, по возрастанию даты начала). */
  @Query("SELECT * FROM alarm_overrides WHERE alarmId = :alarmId ORDER BY fromEpochDay")
  fun observeForAlarm(alarmId: Long): Flow<List<AlarmOverride>>

  /** Все правки всех будильников (живой поток) — для превью «след:» в списке. */
  @Query("SELECT * FROM alarm_overrides ORDER BY fromEpochDay")
  fun observeAll(): Flow<List<AlarmOverride>>

  /** Правки будильника — для расчёта расписания (перепланирование). */
  @Query("SELECT * FROM alarm_overrides WHERE alarmId = :alarmId ORDER BY fromEpochDay")
  suspend fun forAlarm(alarmId: Long): List<AlarmOverride>

  /** Все правки разом — для массового перепланирования (без N+1 запроса по каждому будильнику). */
  @Query("SELECT * FROM alarm_overrides ORDER BY fromEpochDay")
  suspend fun all(): List<AlarmOverride>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(override: AlarmOverride): Long

  @Delete
  suspend fun delete(override: AlarmOverride)

  /** Удалить все правки, покрывающие [epochDay] (для «вернуть день по графику»). */
  @Query("DELETE FROM alarm_overrides WHERE alarmId = :alarmId AND fromEpochDay <= :epochDay AND toEpochDay >= :epochDay")
  suspend fun deleteCovering(alarmId: Long, epochDay: Long)

  @Query("DELETE FROM alarm_overrides WHERE alarmId = :alarmId")
  suspend fun deleteForAlarm(alarmId: Long)
}
