package ru.titeha.shiftalarm.data

import android.content.Context
import androidx.room.withTransaction
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
  /**
   * Сохраняет будильник без использования SQLite REPLACE.
   *
   * Для новой записи выполняется INSERT.
   * Для существующей записи выполняется UPDATE, чтобы не удалить связанные периоды
   * и правки календаря через каскадное удаление.
   *
   * Название upsert оставлено для совместимости с текущими вызовами в UI и планировщике.
   */
  suspend fun upsert(alarm: AlarmEntity): Long {
    return if (alarm.id == 0L) {
      dao.insert(alarm)
    } else {
      dao.update(alarm)
      alarm.id
    }
  }
  suspend fun update(alarm: AlarmEntity) = dao.update(alarm)
  suspend fun delete(alarm: AlarmEntity) = dao.delete(alarm)
  suspend fun deleteById(id: Long) = dao.deleteById(id)

  /**
   * Сохранить будильник вместе с его периодами и правками календаря ОДНОЙ транзакцией.
   *
   * Раньше UI делал это последовательностью upsert → delete → insert → delete → insert; краш или
   * убийство процесса между delete и insert терял периоды/правки (тот же класс риска, что старый
   * REPLACE). Транзакция гарантирует «всё или ничего». Возвращает id будильника.
   */
  suspend fun saveWithChildren(
    alarm: AlarmEntity,
    periods: List<AlarmPeriod>,
    overrides: List<AlarmOverride>,
  ): Long = db.withTransaction {
    val id = upsert(alarm)
    periodDao.deleteForAlarm(id)
    periods.forEach { periodDao.upsert(it.copy(id = 0, alarmId = id)) }
    overrideDao.deleteForAlarm(id)
    overrides.forEach { overrideDao.upsert(it.copy(id = 0, alarmId = id)) }
    id
  }

  // --- Периоды без будильника (отпуск/больничный/отгул) ---

  /** Периоды будильника для UI (живой поток). */
  fun periods(alarmId: Long): Flow<List<AlarmPeriod>> = periodDao.observeForAlarm(alarmId)

  /** Все периоды всех будильников (живой поток) — для превью «след:» в списке. */
  val allPeriods: Flow<List<AlarmPeriod>> = periodDao.observeAll()

  /** Периоды будильника — для расчёта расписания. */
  suspend fun periodsList(alarmId: Long): List<AlarmPeriod> = periodDao.forAlarm(alarmId)

  /** Все периоды разом (одноразовый список) — для массового перепланирования без N+1. */
  suspend fun allPeriodsList(): List<AlarmPeriod> = periodDao.all()

  suspend fun upsertPeriod(period: AlarmPeriod): Long = periodDao.upsert(period)
  suspend fun deletePeriod(period: AlarmPeriod) = periodDao.delete(period)
  suspend fun deletePeriodsForAlarm(alarmId: Long) = periodDao.deleteForAlarm(alarmId)

  // --- Пер-дневные правки календаря (подмены/исключения) ---

  /** Правки будильника для UI (живой поток). */
  fun overrides(alarmId: Long): Flow<List<AlarmOverride>> = overrideDao.observeForAlarm(alarmId)

  /** Все правки всех будильников (живой поток) — для превью «след:» в списке. */
  val allOverrides: Flow<List<AlarmOverride>> = overrideDao.observeAll()

  /** Правки будильника — для расчёта расписания. */
  suspend fun overridesList(alarmId: Long): List<AlarmOverride> = overrideDao.forAlarm(alarmId)

  /** Все правки разом (одноразовый список) — для массового перепланирования без N+1. */
  suspend fun allOverridesList(): List<AlarmOverride> = overrideDao.all()

  suspend fun upsertOverride(override: AlarmOverride): Long = overrideDao.upsert(override)
  suspend fun deleteOverride(override: AlarmOverride) = overrideDao.delete(override)

  /** Снять все правки, покрывающие день (для «вернуть день по графику»). */
  suspend fun clearOverrideOn(alarmId: Long, epochDay: Long) =
    overrideDao.deleteCovering(alarmId, epochDay)

  suspend fun deleteOverridesForAlarm(alarmId: Long) = overrideDao.deleteForAlarm(alarmId)
}
