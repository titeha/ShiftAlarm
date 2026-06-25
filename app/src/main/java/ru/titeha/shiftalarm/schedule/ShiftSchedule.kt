package ru.titeha.shiftalarm.schedule

import java.time.LocalDate
import java.time.LocalDateTime
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

/** Временная подмена: на период [from]..[to] (включительно) действует смена [shift]. */
data class TemporarySwap(
  val from: LocalDate,
  val to: LocalDate,
  val shift: ShiftType
) {
  init { require(!to.isBefore(from)) { "Конец периода раньше начала" } }
  fun covers(date: LocalDate): Boolean = !date.isBefore(from) && !date.isAfter(to)
}

/**
 * Период без будильника (отпуск/больничный/отгул): на [from]..[to] (включительно) звонка нет.
 * [reason] — ярлык периода (для UI). Влияние на фазу цикла после периода задаётся флагом
 * [ShiftSchedule.freezeCycleDuringOff].
 */
data class OffPeriod(
  val from: LocalDate,
  val to: LocalDate,
  val reason: String = "Отпуск"
) {
  init { require(!to.isBefore(from)) { "Конец периода раньше начала" } }
  fun covers(date: LocalDate): Boolean = !date.isBefore(from) && !date.isAfter(to)
  /** Длина периода в днях (включительно). */
  val days: Long get() = ChronoUnit.DAYS.between(from, to) + 1
}

/**
 * Полное расписание: базовая ротация + временные подмены + исключения на дату + периоды отпуска.
 *
 * Приоритет резолва: исключение на дату > подмена на период > период отпуска > базовая ротация.
 * (Учёт производственного календаря для праздников добавится отдельным слоем — см. #15.)
 *
 * [freezeCycleDuringOff] управляет фазой цикла после отпуска:
 *  - false (по умолчанию) — цикл «крутится» по календарю: отпуск лишь глушит звонок, базовая
 *    ротация под низом продолжает идти, после отпуска выходишь на смену «по календарю»;
 *  - true — цикл «замораживается»: отпускные дни не считаются, после отпуска продолжаешь с той же
 *    фазы, на которой ушёл (для индивидуального отсчёта/вахты).
 */
data class ShiftSchedule(
  val base: ShiftPattern,
  val swaps: List<TemporarySwap> = emptyList(),
  val exceptions: Map<LocalDate, ShiftType> = emptyMap(),
  val offPeriods: List<OffPeriod> = emptyList(),
  val freezeCycleDuringOff: Boolean = false
)

/** Чистый (без UI и I/O) движок резолва смены на дату. */
object ShiftEngine {

  /** Какая смена выпадает на [date] по графику [pattern]. Работает и для дат до опорной. */
  fun shiftOn(date: LocalDate, pattern: ShiftPattern): ShiftType {
    val len = pattern.slots.size
    val daysFromAnchor = ChronoUnit.DAYS.between(pattern.anchorDate, date)
    val index = Math.floorMod(daysFromAnchor, len.toLong()).toInt()
    return pattern.slots[index]
  }

  /** Смена на [date] с учётом исключений, подмен и периодов отпуска (по приоритету). */
  fun shiftOn(date: LocalDate, schedule: ShiftSchedule): ShiftType {
    schedule.exceptions[date]?.let { return it }
    schedule.swaps.firstOrNull { it.covers(date) }?.let { return it.shift }
    if (schedule.offPeriods.any { it.covers(date) }) return ShiftType.off()
    return baseShiftOn(date, schedule)
  }

  /** Базовая ротация с учётом «заморозки» цикла на время отпускных периодов. */
  private fun baseShiftOn(date: LocalDate, schedule: ShiftSchedule): ShiftType {
    val pattern = schedule.base
    val len = pattern.slots.size
    val raw = ChronoUnit.DAYS.between(pattern.anchorDate, date)
    val effective =
      if (schedule.freezeCycleDuringOff) raw - frozenDaysBefore(date, pattern.anchorDate, schedule.offPeriods)
      else raw
    val index = Math.floorMod(effective, len.toLong()).toInt()
    return pattern.slots[index]
  }

  /** Сколько отпускных дней прошло в полуинтервале [anchor, date) — на столько «отстаёт» цикл. */
  private fun frozenDaysBefore(date: LocalDate, anchor: LocalDate, offPeriods: List<OffPeriod>): Long {
    if (!date.isAfter(anchor)) return 0L
    var count = 0L
    for (p in offPeriods) {
      val lo = maxOf(p.from, anchor)
      val hi = minOf(p.to, date.minusDays(1)) // строго до date → включительно по date-1
      if (!lo.isAfter(hi)) count += ChronoUnit.DAYS.between(lo, hi) + 1
    }
    return count
  }

  /** Во сколько будить в [date] (или null — выходной/без звонка). */
  fun wakeTimeOn(date: LocalDate, pattern: ShiftPattern): LocalTime? =
    shiftOn(date, pattern).wakeTime

  fun wakeTimeOn(date: LocalDate, schedule: ShiftSchedule): LocalTime? =
    shiftOn(date, schedule).wakeTime

  /**
   * Ближайший момент звонка строго после [from] по расписанию [schedule].
   * Перебирает дни вперёд до [searchDays]; возвращает null, если за этот горизонт
   * рабочих дней нет (например, график целиком из выходных).
   */
  fun nextAlarm(
    from: LocalDateTime,
    schedule: ShiftSchedule,
    searchDays: Int = 366
  ): LocalDateTime? {
    var date = from.toLocalDate()
    repeat(searchDays) {
      val wake = wakeTimeOn(date, schedule)
      if (wake != null) {
        val candidate = date.atTime(wake)
        if (candidate.isAfter(from)) return candidate
      }
      date = date.plusDays(1)
    }
    return null
  }
}
