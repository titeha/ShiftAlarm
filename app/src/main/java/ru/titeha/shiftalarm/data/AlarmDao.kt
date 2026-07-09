package ru.titeha.shiftalarm.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
  /** Все будильники, для UI-списка. */
  @Query("SELECT * FROM alarms ORDER BY hour, minute, id")
  fun observeAll(): Flow<List<AlarmEntity>>

  /** Включённые будильники — для перепланирования после загрузки и после срабатывания. */
  @Query("SELECT * FROM alarms WHERE enabled = 1")
  suspend fun enabled(): List<AlarmEntity>

  @Query("SELECT * FROM alarms WHERE id = :id")
  suspend fun byId(id: Long): AlarmEntity?

  /**
   * Вставить новый будильник.
   *
   * Для существующих будильников нельзя использовать REPLACE: SQLite при замене
   * удаляет старую строку, а это может каскадно удалить периоды и правки календаря.
   */
  @Insert
  suspend fun insert(alarm: AlarmEntity): Long

  /** Обновить существующий будильник без удаления связанных периодов и правок. */
  @Update
  suspend fun update(alarm: AlarmEntity)

  @Delete
  suspend fun delete(alarm: AlarmEntity)

  @Query("DELETE FROM alarms WHERE id = :id")
  suspend fun deleteById(id: Long)
}