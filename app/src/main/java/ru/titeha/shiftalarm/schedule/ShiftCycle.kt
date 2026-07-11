package ru.titeha.shiftalarm.schedule

import java.time.LocalTime

/** Блок редактора цикла: [count] одинаковых дней [slot] подряд. */
data class SlotRun(val slot: ShiftType, val count: Int)

/**
 * Свёртка цикла в блоки «×N» + операции над блоками. Чистая логика, без Android: покрыта
 * юнит-тестами. «Одинаковость» соседних дней — по типу/времени/названию (id игнорируется).
 *
 * Название блока по умолчанию — авто-имя по типу («Утро», «День»…). Смена типа авто-именованного
 * блока переименовывает его и подстраивает время — тогда соседние блоки одного типа склеиваются
 * (нормализация). Блок с пользовательским именем сохраняет имя и своё время.
 */
object ShiftCycle {

  /** Авто-имя блока по категории (используется как имя по умолчанию). */
  fun autoNameFor(category: ShiftCategory): String = when (category) {
    ShiftCategory.MORNING -> "Утро"
    ShiftCategory.DAY -> "День"
    ShiftCategory.NIGHT -> "Ночь"
    ShiftCategory.OFF -> "Выходной"
  }

  /** Время будильника по умолчанию для категории (звонок раньше старта смены). */
  fun defaultAlarmFor(category: ShiftCategory): LocalTime = when (category) {
    ShiftCategory.MORNING -> LocalTime.of(5, 0)
    ShiftCategory.DAY -> LocalTime.of(13, 0)
    ShiftCategory.NIGHT, ShiftCategory.OFF -> LocalTime.of(21, 0)
  }

  /** Имя блока — по умолчанию (совпадает с авто-именем своего типа), а не пользовательское. */
  fun isAutoNamed(slot: ShiftType): Boolean = slot.name == autoNameFor(slot.category)

  /**
   * Сменить тип блока. Авто-именованный блок — «стандартный»: переименовывается и получает время
   * по типу (Выходной → без звонка). Пользовательское имя сохраняется; при переключении с выходного
   * на рабочий звонок включается (время по типу), при переключении на выходной — выключается.
   */
  fun retype(slot: ShiftType, newCategory: ShiftCategory): ShiftType {
    val auto = isAutoNamed(slot)
    val name = if (auto) autoNameFor(newCategory) else slot.name
    val wake = when {
      newCategory == ShiftCategory.OFF -> null
      auto || slot.wakeTime == null -> defaultAlarmFor(newCategory)
      else -> slot.wakeTime
    }
    return slot.copy(name = name, category = newCategory, wakeTime = wake)
  }

  /** Включить/выключить будильник блока: вкл → время по типу (или прежнее), выкл → null. */
  fun setAlarm(slot: ShiftType, on: Boolean): ShiftType =
    slot.copy(wakeTime = if (on) slot.wakeTime ?: defaultAlarmFor(slot.category) else null)

  /** Новый стандартный блок заданного типа (авто-имя + время по типу; выходной — без звонка). */
  fun blockOf(category: ShiftCategory): ShiftType =
    if (category == ShiftCategory.OFF) ShiftType.off()
    else ShiftType("c", autoNameFor(category), defaultAlarmFor(category), category)

  /**
   * Тип для нового блока, отличный от [avoid] (типов соседей) — чтобы вставленный блок был виден
   * и не слился при нормализации. Предпочтение: День → Утро → Ночь → Выходной; если все заняты — День.
   */
  fun distinctCategoryFrom(avoid: Set<ShiftCategory>): ShiftCategory =
    listOf(ShiftCategory.DAY, ShiftCategory.MORNING, ShiftCategory.NIGHT, ShiftCategory.OFF)
      .firstOrNull { it !in avoid } ?: ShiftCategory.DAY

  private fun same(a: ShiftType, b: ShiftType): Boolean =
    a.name == b.name && a.category == b.category && a.wakeTime == b.wakeTime

  /**
   * Группирует подряд идущие одинаковые слоты в блоки «слот × количество» — единая точка
   * нормализации: после правок соседние идентичные (тип/время/имя) блоки склеиваются.
   */
  fun group(slots: List<ShiftType>): List<SlotRun> {
    val runs = mutableListOf<SlotRun>()
    for (s in slots) {
      val last = runs.lastOrNull()
      if (last != null && same(last.slot, s)) runs[runs.lastIndex] = last.copy(count = last.count + 1)
      else runs.add(SlotRun(s, 1))
    }
    return runs
  }

  /** Разворачивает блоки обратно в плоский список дней. */
  fun expand(runs: List<SlotRun>): List<ShiftType> =
    runs.flatMap { run -> List(run.count) { run.slot } }
}
