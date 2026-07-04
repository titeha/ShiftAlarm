package ru.titeha.shiftalarm.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ru.titeha.shiftalarm.schedule.ScheduleOverrides
import ru.titeha.shiftalarm.schedule.ShiftCategory
import ru.titeha.shiftalarm.schedule.ShiftType
import java.time.LocalDate
import java.time.LocalTime

/**
 * Пользовательская правка календаря («подмена»), привязанная к будильнику [alarmId].
 * На диапазон [fromEpochDay]..[toEpochDay] (обе границы включительно, epoch day) действует смена,
 * заданная полями [category]/[wakeMinutes]/[name]. Один день (`from == to`) — точечное исключение,
 * диапазон — подмена блока; раскладку по приоритетам движка делает [ScheduleOverrides].
 *
 * [wakeMinutes] — минуты от полуночи (0..1439) или null = в этот день не звонить (выходной/смена
 * без будильника). При удалении будильника правки удаляются каскадом (FK ON DELETE CASCADE).
 */
@Entity(
  tableName = "alarm_overrides",
  foreignKeys = [ForeignKey(
    entity = AlarmEntity::class,
    parentColumns = ["id"],
    childColumns = ["alarmId"],
    onDelete = ForeignKey.CASCADE
  )],
  indices = [Index("alarmId")]
)
data class AlarmOverride(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val alarmId: Long,
  val fromEpochDay: Long,
  val toEpochDay: Long,
  val category: String,
  val wakeMinutes: Int?,
  val name: String
) {
  /** Развернуть в доменную правку [ScheduleOverrides.DayOverride] для движка. */
  fun toDayOverride(): ScheduleOverrides.DayOverride {
    val shift = ShiftType(
      id = "override",
      name = name,
      wakeTime = wakeMinutes?.let { LocalTime.of(it / 60, it % 60) },
      category = ShiftCategory.valueOf(category)
    )
    return ScheduleOverrides.DayOverride(
      LocalDate.ofEpochDay(fromEpochDay),
      LocalDate.ofEpochDay(toEpochDay),
      shift
    )
  }
}
