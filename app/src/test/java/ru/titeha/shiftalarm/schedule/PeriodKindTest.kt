package ru.titeha.shiftalarm.schedule

import org.junit.Assert.assertEquals
import org.junit.Test

class PeriodKindTest {

  @Test
  fun `известные причины распознаются, регистр не важен`() {
    assertEquals(PeriodKind.VACATION, PeriodKind.fromReason("Отпуск"))
    assertEquals(PeriodKind.SICK, PeriodKind.fromReason("Больничный"))
    assertEquals(PeriodKind.DAYOFF, PeriodKind.fromReason("отгул"))
    assertEquals(PeriodKind.UNPAID, PeriodKind.fromReason("За свой счёт"))
  }

  @Test
  fun `неизвестная или старая причина — отпуск`() {
    assertEquals(PeriodKind.VACATION, PeriodKind.fromReason("что-то своё"))
    assertEquals(PeriodKind.VACATION, PeriodKind.fromReason(""))
  }
}
