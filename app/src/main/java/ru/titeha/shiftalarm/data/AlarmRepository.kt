package ru.titeha.shiftalarm.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

/** Тонкая обёртка над DAO — единая точка доступа к будильникам. */
class AlarmRepository(context: Context) {

  private val dao = AppDatabase.get(context).alarmDao()

  val all: Flow<List<AlarmEntity>> = dao.observeAll()

  suspend fun enabled(): List<AlarmEntity> = dao.enabled()
  suspend fun byId(id: Long): AlarmEntity? = dao.byId(id)
  suspend fun upsert(alarm: AlarmEntity): Long = dao.upsert(alarm)
  suspend fun update(alarm: AlarmEntity) = dao.update(alarm)
  suspend fun delete(alarm: AlarmEntity) = dao.delete(alarm)
  suspend fun deleteById(id: Long) = dao.deleteById(id)
}
