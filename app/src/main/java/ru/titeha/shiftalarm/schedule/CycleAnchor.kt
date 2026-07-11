package ru.titeha.shiftalarm.schedule

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Якорь цикла смен из человеческого вопроса «какая смена сегодня».
 *
 * Пользователь не задаёт «опорную дату» (инженерное понятие) — он выбирает день цикла (индекс в
 * развёрнутом цикле), а код вычисляет опорную дату так, чтобы движок ([ShiftEngine.baseShiftOn]:
 * `index = floorMod(date − anchorDate, len)`) резолвил на сегодня именно выбранный день.
 *
 * Инвариант: `shiftOn(today, ShiftPattern(slots, anchorDateForToday(today, i))) == slots[i]`.
 */
object CycleAnchor {

  /** Опорная дата (день 0 цикла), при которой [today] попадает на день цикла [selectedDayIndex]. */
  fun anchorDateForToday(today: LocalDate, selectedDayIndex: Int): LocalDate =
    today.minusDays(selectedDayIndex.toLong())

  /** Индекс дня цикла на [today] при опорной дате [anchorDate] и длине цикла [cycleLength]. */
  fun todayIndex(today: LocalDate, anchorDate: LocalDate, cycleLength: Int): Int =
    Math.floorMod(ChronoUnit.DAYS.between(anchorDate, today), cycleLength.toLong()).toInt()
}
