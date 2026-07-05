package ru.titeha.shiftalarm.schedule

/**
 * Правило ТК РФ (ст. 124): больничный во время ежегодного отпуска продлевает отпуск на число
 * совпавших дней — «съеденные» болезнью дни отпуска переносятся в хвост после выздоровления.
 *
 * Чистая логика на epoch-day (без Android/Room), за швом — покрыта юнит-тестами.
 */
object VacationSick {

  /** Период без будильника: [from]..[to] включительно (epoch day), тип [kind]. */
  data class Span(val from: Long, val to: Long, val kind: PeriodKind) {
    init { require(to >= from) { "Конец периода раньше начала" } }
  }

  private fun intersects(af: Long, at: Long, bf: Long, bt: Long) = af <= bt && bf <= at

  /**
   * Наложить больничный [sick] на список периодов [periods]:
   *  - отпуск, пересечённый больничным, режется вокруг него, а «съеденные» дни отпуска
   *    добавляются в хвост ПОСЛЕ окончания болезни (продление отпуска);
   *  - прочие периоды, пересекающие больничный, заменяются им (нельзя одновременно болеть и
   *    быть в отгуле);
   *  - непересечённые периоды остаются как есть.
   * Возвращает новый список (включая сам больничный).
   */
  fun applySick(periods: List<Span>, sick: Span): List<Span> {
    val result = mutableListOf<Span>()
    for (p in periods) {
      if (!intersects(p.from, p.to, sick.from, sick.to)) {
        result += p                       // не пересекается — не трогаем
        continue
      }
      if (p.kind != PeriodKind.VACATION) {
        continue                          // прочий период под больничным — заменяется им
      }
      // Отпуск, пересечённый больничным: фронт (до болезни) + середина (после болезни в исходном
      // диапазоне) + продлённый хвост («съеденные» дни после выздоровления).
      val overlap = minOf(p.to, sick.to) - maxOf(p.from, sick.from) + 1
      val frontTo = minOf(sick.from - 1, p.to)
      if (p.from <= frontTo) result += Span(p.from, frontTo, PeriodKind.VACATION)
      val midFrom = maxOf(sick.to + 1, p.from)
      if (midFrom <= p.to) result += Span(midFrom, p.to, PeriodKind.VACATION)
      val tailFrom = maxOf(p.to, sick.to) + 1
      result += Span(tailFrom, tailFrom + overlap - 1, PeriodKind.VACATION)
    }
    result += sick
    return result
  }
}
