package ru.titeha.shiftalarm.schedule

import java.time.LocalDate

/**
 * Классификация дня для наглядного календаря смен (read-only). Чистая логика, без Android.
 *
 * Тип смены на дату резолвит [ShiftEngine] (с приоритетами исключение > подмена > отпуск >
 * ротация). Цвет берётся из явной [ShiftType.category] (не из времени звонка) — поэтому ночь
 * без будильника всё равно синяя, а выходной со звонком — красный.
 */
object ShiftCalendar {

  enum class DayKind {
    MORNING,   // утренняя смена
    DAY,       // дневная смена
    NIGHT,     // ночная смена
    OFF,       // выходной/отсыпной по ротации
    VACATION   // период без будильника (отпуск/больничный/отгул)
  }

  /** Категория дня [date] по расписанию [schedule]. */
  fun kindOf(date: LocalDate, schedule: ShiftSchedule): DayKind {
    // Период отпуска отличаем от обычного выходного отдельно: ShiftEngine для обоих вернул бы off.
    if (schedule.offPeriods.any { it.covers(date) }) return DayKind.VACATION

    return when (ShiftEngine.shiftOn(date, schedule).category) {
      ShiftCategory.MORNING -> DayKind.MORNING
      ShiftCategory.DAY -> DayKind.DAY
      ShiftCategory.NIGHT -> DayKind.NIGHT
      ShiftCategory.OFF -> DayKind.OFF
    }
  }
}
