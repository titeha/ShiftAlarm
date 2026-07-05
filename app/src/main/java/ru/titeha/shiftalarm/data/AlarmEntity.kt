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
  /**
   * Произвольный цикл смен, сериализованный [ru.titeha.shiftalarm.schedule.ShiftCycleCodec].
   * null — использовать встроенный пресет [presetId] (старое поведение); иначе цикл берётся отсюда.
   */
  val cycleSpec: String? = null,
  /** Удалить запись после срабатывания (для разовых «выстрелил и забыл»). */
  val deleteAfterFiring: Boolean = false,
  /**
   * Режим цикла смен во время периодов без будильника (отпуск/больничный/отгул):
   *  - false — цикл «крутится» по календарю (отпуск лишь глушит звонок);
   *  - true — цикл «замораживается» (отпускные дни не считаются, фаза возобновляется с места ухода).
   */
  val freezeCycleDuringOff: Boolean = false,
  /**
   * Учитывать производственный календарь (праздники/переносы). false — как раньше (календарь не
   * влияет). true — звонок сверяется с календарём по [polarity] (см. `ShiftEngine`/`HolidayAlarms`).
   */
  val honorHolidays: Boolean = false,
  /**
   * Полярность будильника относительно календаря ([ru.titeha.shiftalarm.schedule.AlarmPolarity]):
   * [POLARITY_WORK] — буди по рабочим (нерабочие глушатся); [POLARITY_REST] — буди по выходным.
   * Действует только при [honorHolidays] = true.
   */
  val polarity: String = POLARITY_WORK
) {
  companion object {
    const val MODE_WEEKLY = "weekly"
    const val MODE_SHIFT = "shift"
    const val POLARITY_WORK = "WORK"
    const val POLARITY_REST = "REST"
  }
}
