package ru.titeha.shiftalarm.schedule

import java.time.LocalDate

/**
 * Пользовательские правки календаря («подмены»): на диапазон [DayOverride.from]..[DayOverride.to]
 * (включительно) назначается смена [DayOverride.shift].
 *
 * Чистая логика (без Android/Room): раскладывает список правок в поля [ShiftSchedule] так, чтобы
 * сохранить приоритеты движка ([ShiftEngine]: исключение > подмена > отпуск > ротация):
 *  - правка на один день (`from == to`) → в `exceptions` (высший приоритет, точечно бьёт всё);
 *  - правка на диапазон (`from < to`)  → в `swaps` (подмена целого блока).
 *
 * Так «отдать рабочий блок» = диапазон-подмена на выходной, а «отработать свои выходные»
 * = точечные исключения (по одному) или диапазон-подмена (пакетом) на рабочую смену.
 */
object ScheduleOverrides {

  /** Одна правка: сменить [shift] на [from]..[to] включительно. */
  data class DayOverride(val from: LocalDate, val to: LocalDate, val shift: ShiftType) {
    init { require(!to.isBefore(from)) { "Конец правки раньше начала" } }

    /** Правка на один календарный день. */
    val isSingleDay: Boolean get() = from == to
  }

  /**
   * Наложить [overrides] на [schedule]: одиночные дни уходят в `exceptions`, диапазоны — в `swaps`.
   * Существующие правки расписания сохраняются; при совпадении дня в `exceptions` побеждает
   * последняя правка (порядок [overrides]).
   */
  fun apply(schedule: ShiftSchedule, overrides: List<DayOverride>): ShiftSchedule {
    val exceptions = LinkedHashMap<LocalDate, ShiftType>()
    val swaps = mutableListOf<TemporarySwap>()
    for (o in overrides) {
      if (o.isSingleDay) {
        exceptions[o.from] = o.shift
      } else {
        swaps += TemporarySwap(o.from, o.to, o.shift)
      }
    }
    return schedule.copy(
      swaps = schedule.swaps + swaps,
      exceptions = schedule.exceptions + exceptions
    )
  }
}
