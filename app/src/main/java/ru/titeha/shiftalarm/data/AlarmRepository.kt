package ru.titeha.shiftalarm.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

/** Тонкая обёртка над DAO — единая точка доступа к будильникам. */
class AlarmRepository(context: Context) {

  private val db = AppDatabase.get(context)
  private val dao = db.alarmDao()
  private val periodDao = db.alarmPeriodDao()
  private val overrideDao = db.alarmOverrideDao()

  val all: Flow<List<AlarmEntity>> = dao.observeAll()

  suspend fun enabled(): List<AlarmEntity> = dao.enabled()
  suspend fun byId(id: Long): AlarmEntity? = dao.byId(id)
  suspend fun upsert(alarm: AlarmEntity): Long = dao.upsert(alarm)
  suspend fun update(alarm: AlarmEntity) = dao.update(alarm)
  suspend fun delete(alarm: AlarmEntity) = dao.delete(alarm)
  suspend fun deleteById(id: Long) = dao.deleteById(id)

  // --- Периоды без будильника (отпуск/больничный/отгул) ---

  /** Периоды будильника для UI (живой поток). */
  fun periods(alarmId: Long): Flow<List<AlarmPeriod>> = periodDao.observeForAlarm(alarmId)

  /** Все периоды всех будильников (живой поток) — для превью «след:» в списке. */
  val allPeriods: Flow<List<AlarmPeriod>> = periodDao.observeAll()

  /** Периоды будильника — для расчёта расписания. */
  suspend fun periodsList(alarmId: Long): List<AlarmPeriod> = periodDao.forAlarm(alarmId)

  suspend fun upsertPeriod(period: AlarmPeriod): Long = periodDao.upsert(period)
  suspend fun deletePeriod(period: AlarmPeriod) = periodDao.delete(period)
  suspend fun deletePeriodsForAlarm(alarmId: Long) = periodDao.deleteForAlarm(alarmId)

  // --- Пер-дневные правки календаря (подмены/исключения) ---

  /** Правки будильника для UI (живой поток). */
  fun overrides(alarmId: Long): Flow<List<AlarmOverride>> = overrideDao.observeForAlarm(alarmId)

  /** Правки будильника — для расчёта расписания. */
  suspend fun overridesList(alarmId: Long): List<AlarmOverride> = overrideDao.forAlarm(alarmId)

  suspend fun upsertOverride(override: AlarmOverride): Long = overrideDao.upsert(override)
  suspend fun deleteOverride(override: AlarmOverride) = overrideDao.delete(override)

  /** Снять все правки, покрывающие день (для «вернуть день по графику»). */
  suspend fun clearOverrideOn(alarmId: Long, epochDay: Long) =
    overrideDao.deleteCovering(alarmId, epochDay)

  suspend fun deleteOverridesForAlarm(alarmId: Long) = overrideDao.deleteForAlarm(alarmId)
}
