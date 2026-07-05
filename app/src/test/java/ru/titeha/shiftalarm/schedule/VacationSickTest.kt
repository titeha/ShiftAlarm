package ru.titeha.shiftalarm.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.titeha.shiftalarm.schedule.VacationSick.Span

class VacationSickTest {

  private fun vac(from: Long, to: Long) = Span(from, to, PeriodKind.VACATION)
  private fun sick(from: Long, to: Long) = Span(from, to, PeriodKind.SICK)

  /** Сумма дней отпуска в списке. */
  private fun vacationDays(spans: List<Span>): Long =
    spans.filter { it.kind == PeriodKind.VACATION }.sumOf { it.to - it.from + 1 }

  @Test
  fun sickInsideVacation_extendsTail_keepsTotalVacationDays() {
    // Отпуск 10..20 (11 дней), больничный 13..15 (3 дня) внутри.
    val result = VacationSick.applySick(listOf(vac(10, 20)), sick(13, 15))

    // Больничный на месте.
    assertTrue(result.contains(sick(13, 15)))
    // Отпуск разрезан: фронт 10..12, середина 16..20, хвост 21..23.
    assertTrue(result.contains(vac(10, 12)))
    assertTrue(result.contains(vac(16, 20)))
    assertTrue(result.contains(vac(21, 23)))
    // Итого отпускных дней столько же (11), конец сдвинулся с 20 на 23.
    assertEquals(11, vacationDays(result))
  }

  @Test
  fun sickAtVacationStart_noEmptyFront() {
    // Больничный в самом начале отпуска.
    val result = VacationSick.applySick(listOf(vac(10, 20)), sick(10, 12))

    assertTrue(result.none { it.kind == PeriodKind.VACATION && it.to < it.from })
    assertTrue(result.contains(vac(13, 20)))
    assertTrue(result.contains(vac(21, 23)))
    assertEquals(11, vacationDays(result))
  }

  @Test
  fun sickPastVacationEnd_resumesAfterSickness() {
    // Отпуск 10..20, больничный 18..25 (заходит за конец отпуска).
    val result = VacationSick.applySick(listOf(vac(10, 20)), sick(18, 25))

    assertTrue(result.contains(vac(10, 17)))          // фронт
    assertTrue(result.none { it.kind == PeriodKind.VACATION && it.from in 18..25 }) // не отпуск в болезни
    assertTrue(result.contains(vac(26, 28)))          // хвост после выздоровления (3 съеденных дня)
    assertEquals(11, vacationDays(result))
  }

  @Test
  fun sickOutsideVacation_justAddsSick() {
    val result = VacationSick.applySick(listOf(vac(10, 20)), sick(30, 32))
    assertEquals(listOf(vac(10, 20), sick(30, 32)), result)
    assertEquals(11, vacationDays(result))
  }

  @Test
  fun sickReplacesOverlappingNonVacation() {
    // Отгул 12..14 под больничным 10..16 — заменяется больничным; отпуск не при делах.
    val dayoff = Span(12, 14, PeriodKind.DAYOFF)
    val result = VacationSick.applySick(listOf(dayoff), sick(10, 16))
    assertEquals(listOf(sick(10, 16)), result)
  }

  @Test
  fun otherPeriodsUntouched() {
    val far = Span(100, 110, PeriodKind.UNPAID)
    val result = VacationSick.applySick(listOf(far), sick(10, 12))
    assertTrue(result.contains(far))
    assertTrue(result.contains(sick(10, 12)))
  }
}
