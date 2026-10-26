package ru.titeha.shiftalarm.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Факт пропуска конкретного срабатывания будильника.
 *
 * Кнопка «Выключить будильник» в плашке «час до звонка» (PreRing) для повторяющегося будильника
 * пропускает только ближайшее срабатывание, а не выключает будильник целиком: здесь хранится
 * время пропущенного звонка [triggerAtMillis] (epoch millis). Планировщик при расчёте ближайшего
 * звонка пропускает такие записи и считает следующее время.
 *
 * Составной первичный ключ `(alarmId, triggerAtMillis)` — ровно одна запись на конкретное
 * срабатывание. При удалении будильника записи удаляются каскадом (FK ON DELETE CASCADE).
 */
@Entity(
  tableName = "skipped_alarm_occurrences",
  primaryKeys = ["alarmId", "triggerAtMillis"],
  foreignKeys = [ForeignKey(
    entity = AlarmEntity::class,
    parentColumns = ["id"],
    childColumns = ["alarmId"],
    onDelete = ForeignKey.CASCADE
  )],
  indices = [Index("alarmId")]
)
data class SkippedAlarmOccurrence(
  val alarmId: Long,
  val triggerAtMillis: Long
)
