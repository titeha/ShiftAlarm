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
  fun `учебные периоды — каникулы и сессия`() {
    assertEquals(PeriodKind.SCHOOL_BREAK, PeriodKind.fromReason("Каникулы"))
    assertEquals(PeriodKind.SESSION, PeriodKind.fromReason("сессия"))
    // Метки в reason (обратная совместимость: старая версия прочитает их как отпуск — тоже глушит).
    assertEquals("Каникулы", PeriodKind.SCHOOL_BREAK.label)
    assertEquals("Сессия", PeriodKind.SESSION.label)
  }

  @Test
  fun `неизвестная или старая причина — отпуск`() {
    assertEquals(PeriodKind.VACATION, PeriodKind.fromReason("что-то своё"))
    assertEquals(PeriodKind.VACATION, PeriodKind.fromReason(""))
  }
}
