package ru.titeha.shiftalarm.schedule

import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Тип смены. [wakeTime] — во сколько будить; null означает «выходной» (звонка нет).
 */
data class ShiftType(
  val id: String,
  val name: String,
  val wakeTime: LocalTime?
) {
  val isWorkDay: Boolean get() = wakeTime != null

  companion object {
    /** Выходной — слот без звонка. */
    fun off(name: String = "Выходной") = ShiftType("off", name, null)
  }
}

/**
 * Сменный график: упорядоченный цикл слотов, повторяющийся бесконечно и привязанный к
 * опорной дате [anchorDate] (в этот день — слот с индексом 0).
 *
 * Примеры: 2/2, 3/3, 3/2, «3 утра / 3 день / 3 ночь / 3 выходных».
 */
data class ShiftPattern(
  val slots: List<ShiftType>,
  val anchorDate: LocalDate
) {
  init {
    require(slots.isNotEmpty()) { "Цикл смен не может быть пустым" }
  }

  companion object {
    /**
     * Простая ротация: [workDays] рабочих подряд (смена [work]), затем [restDays] выходных.
     * Например 2/2: workRest(2, 2, ...), 3/3: workRest(3, 3, ...).
     */
    fun workRest(
      workDays: Int,
      restDays: Int,
      work: ShiftType,
      anchorDate: LocalDate,
      off: ShiftType = ShiftType.off()
    ): ShiftPattern {
      require(workDays >= 0 && restDays >= 0 && workDays + restDays > 0)
      return ShiftPattern(List(workDays) { work } + List(restDays) { off }, anchorDate)
    }
  }
}

/** Чистый (без UI и I/O) движок резолва смены на дату. */
object ShiftEngine {

  /** Какая смена выпадает на [date] по графику [pattern]. Работает и для дат до опорной. */
  fun shiftOn(date: LocalDate, pattern: ShiftPattern): ShiftType {
    val len = pattern.slots.size
    val daysFromAnchor = ChronoUnit.DAYS.between(pattern.anchorDate, date)
    val index = Math.floorMod(daysFromAnchor, len.toLong()).toInt()
    return pattern.slots[index]
  }

  /** Во сколько будить в [date] (или null — выходной/без звонка). */
  fun wakeTimeOn(date: LocalDate, pattern: ShiftPattern): LocalTime? =
    shiftOn(date, pattern).wakeTime
}
