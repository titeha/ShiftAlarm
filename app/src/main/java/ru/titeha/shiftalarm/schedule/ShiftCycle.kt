package ru.titeha.shiftalarm.schedule

/** Запись редактора цикла: [count] одинаковых дней [slot] подряд. */
data class SlotRun(val slot: ShiftType, val count: Int)

/**
 * Свёртка цикла для компактного редактора: подряд идущие одинаковые дни — одной записью «×N».
 * Чистая логика, без Android: покрыта юнит-тестами. «Одинаковость» — по типу/времени/названию
 * (id игнорируется, он внутренний).
 */
object ShiftCycle {

  private fun same(a: ShiftType, b: ShiftType): Boolean =
    a.name == b.name && a.category == b.category && a.wakeTime == b.wakeTime

  /** Группирует подряд идущие одинаковые слоты в записи «слот × количество». */
  fun group(slots: List<ShiftType>): List<SlotRun> {
    val runs = mutableListOf<SlotRun>()
    for (s in slots) {
      val last = runs.lastOrNull()
      if (last != null && same(last.slot, s)) runs[runs.lastIndex] = last.copy(count = last.count + 1)
      else runs.add(SlotRun(s, 1))
    }
    return runs
  }

  /** Разворачивает записи обратно в плоский список дней. */
  fun expand(runs: List<SlotRun>): List<ShiftType> =
    runs.flatMap { run -> List(run.count) { run.slot } }
}
