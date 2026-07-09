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
 *
 * На диапазон [fromEpochDay]..[toEpochDay] (обе границы включительно, epoch day) действует смена,
 * заданная полями [category]/[wakeMinutes]/[name]. Один день (`from == to`) — точечное исключение,
 * диапазон — подмена блока; раскладку по приоритетам движка делает [ScheduleOverrides].
 *
 * [wakeMinutes] — минуты от полуночи (0..1439) или null = в этот день не звонить.
 * При удалении будильника правки удаляются каскадом (FK ON DELETE CASCADE).
 */
@Entity(
  tableName = "alarm_overrides",
  foreignKeys = [
    ForeignKey(
      entity = AlarmEntity::class,
      parentColumns = ["id"],
      childColumns = ["alarmId"],
      onDelete = ForeignKey.CASCADE
    )
  ],
  indices = [Index("alarmId")]
)
data class AlarmOverride(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val alarmId: Long,
  val fromEpochDay: Long,
  val toEpochDay: Long,
  val category: String,
  val wakeMinutes: Int?,
  val name: String
) {
  /**
   * Безопасно развернуть запись БД в доменную правку для движка.
   *
   * Возвращает null, если запись повреждена: некорректная категория, минуты вне диапазона,
   * невозможная дата или конец диапазона раньше начала. Такая правка пропускается, чтобы
   * не ронять планировщик и экран календаря.
   */
  fun toDayOverrideOrNull(): ScheduleOverrides.DayOverride? {
    val from = runCatching {
      LocalDate.ofEpochDay(fromEpochDay)
    }.getOrNull() ?: return null

    val to = runCatching {
      LocalDate.ofEpochDay(toEpochDay)
    }.getOrNull() ?: return null

    if (to.isBefore(from)) {
      return null
    }

    val parsedCategory = runCatching {
      ShiftCategory.valueOf(category)
    }.getOrNull() ?: return null

    val wakeTime = wakeMinutes?.let { minutes ->
      if (minutes !in 0 until MINUTES_PER_DAY) {
        return null
      }

      LocalTime.of(minutes / 60, minutes % 60)
    }

    val shift = ShiftType(
      id = "override",
      name = name,
      wakeTime = wakeTime,
      category = parsedCategory
    )

    return ScheduleOverrides.DayOverride(from, to, shift)
  }

  /**
   * Развернуть в доменную правку [ScheduleOverrides.DayOverride] для движка.
   *
   * Оставлено для мест, где некорректная запись должна считаться ошибкой программиста.
   * Для чтения из БД и пользовательских данных предпочитай [toDayOverrideOrNull].
   */
  fun toDayOverride(): ScheduleOverrides.DayOverride {
    return requireNotNull(toDayOverrideOrNull()) {
      "Некорректная правка календаря: id=$id, alarmId=$alarmId"
    }
  }

  private companion object {
    const val MINUTES_PER_DAY = 24 * 60
  }
}