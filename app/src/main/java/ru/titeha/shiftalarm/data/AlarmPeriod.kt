package ru.titeha.shiftalarm.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Период без будильника (отпуск/больничный/отгул), привязанный к будильнику [alarmId].
 * Даты — epoch day, обе границы включительно. Резолв в расписании — см. `ShiftEngine`/`OffPeriod`.
 * При удалении будильника периоды удаляются каскадом (FK ON DELETE CASCADE).
 */
@Entity(
  tableName = "alarm_periods",
  foreignKeys = [ForeignKey(
    entity = AlarmEntity::class,
    parentColumns = ["id"],
    childColumns = ["alarmId"],
    onDelete = ForeignKey.CASCADE
  )],
  indices = [Index("alarmId")]
)
data class AlarmPeriod(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val alarmId: Long,
  val fromEpochDay: Long,
  val toEpochDay: Long,
  val reason: String = "Отпуск"
)
