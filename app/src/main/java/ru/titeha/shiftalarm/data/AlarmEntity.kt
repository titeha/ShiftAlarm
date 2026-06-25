package ru.titeha.shiftalarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Один будильник в списке.
 *
 * Режимы (mode):
 *  - [MODE_WEEKLY] — по дням недели через [daysMask]. Пустая маска (0) = разовый: сработает
 *    в ближайшее подходящее время и затем отключится (или удалится при [deleteAfterFiring]).
 *  - [MODE_SHIFT]  — по графику смен ([presetId] + [anchorEpochDay]).
 */
@Entity(tableName = "alarms")
data class AlarmEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val label: String = "",
  val enabled: Boolean = true,
  val hour: Int = 7,
  val minute: Int = 0,
  val mode: String = MODE_WEEKLY,
  /** Биты дней недели: бит 0 = Пн … бит 6 = Вс. 0 = разовый. */
  val daysMask: Int = 0,
  val presetId: String = "2x2",
  val anchorEpochDay: Long = 0L,
  /** Удалить запись после срабатывания (для разовых «выстрелил и забыл»). */
  val deleteAfterFiring: Boolean = false,
  /**
   * Режим цикла смен во время периодов без будильника (отпуск/больничный/отгул):
   *  - false — цикл «крутится» по календарю (отпуск лишь глушит звонок);
   *  - true — цикл «замораживается» (отпускные дни не считаются, фаза возобновляется с места ухода).
   */
  val freezeCycleDuringOff: Boolean = false
) {
  companion object {
    const val MODE_WEEKLY = "weekly"
    const val MODE_SHIFT = "shift"
  }
}
