package ru.titeha.shiftalarm.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.titeha.shiftalarm.schedule.ShiftCalendar.DayKind
import java.time.LocalDate
import java.time.LocalTime

class ShiftCalendarTest {

  // Опора цикла — 2026-06-01. Пресет «утро/день/ночь 3×2», 15-дневный цикл:
  // 0-2 утро(5:00) / 3-4 вых / 5-7 день(13:00) / 8-9 вых / 10-12 ночь(21:00) / 13 отсыпной / 14 вых.
  private val anchor = LocalDate.of(2026, 6, 1)
  private val mdn = ShiftPresets.byId("mdn")!!.build(anchor)

  @Test
  fun `утренние слоты — MORNING`() {
    assertEquals(DayKind.MORNING, ShiftCalendar.kindOf(anchor, mdn))
    assertEquals(DayKind.MORNING, ShiftCalendar.kindOf(anchor.plusDays(2), mdn))
  }

  @Test
  fun `дневные слоты — DAY`() {
    assertEquals(DayKind.DAY, ShiftCalendar.kindOf(anchor.plusDays(5), mdn))
    assertEquals(DayKind.DAY, ShiftCalendar.kindOf(anchor.plusDays(7), mdn))
  }

  @Test
  fun `ночные слоты — NIGHT`() {
    assertEquals(DayKind.NIGHT, ShiftCalendar.kindOf(anchor.plusDays(10), mdn))
    assertEquals(DayKind.NIGHT, ShiftCalendar.kindOf(anchor.plusDays(12), mdn))
  }

  @Test
  fun `выходные и отсыпной — OFF`() {
    assertEquals(DayKind.OFF, ShiftCalendar.kindOf(anchor.plusDays(3), mdn))  // выходной после утра
    assertEquals(DayKind.OFF, ShiftCalendar.kindOf(anchor.plusDays(13), mdn)) // отсыпной после ночи
  }

  @Test
  fun `цикл повторяется — день 15 снова MORNING`() {
    assertEquals(DayKind.MORNING, ShiftCalendar.kindOf(anchor.plusDays(15), mdn))
  }

  @Test
  fun `вариант Б — тип дня и звонок развязаны`() {
    // День 9 — выходной (вых*), но звонок 21:00 (вечером уходишь в первую ночь).
    assertEquals(DayKind.OFF, ShiftCalendar.kindOf(anchor.plusDays(9), mdn))
    assertEquals(LocalTime.of(21, 0), ShiftEngine.wakeTimeOn(anchor.plusDays(9), mdn))
    // День 12 — ночь (метка), но своего звонка нет (он был накануне).
    assertEquals(DayKind.NIGHT, ShiftCalendar.kindOf(anchor.plusDays(12), mdn))
    assertNull(ShiftEngine.wakeTimeOn(anchor.plusDays(12), mdn))
  }

  @Test
  fun `период отпуска перекрывает рабочий день — VACATION`() {
    // День 0 — рабочее утро, но накрыт отпуском → VACATION (отличаем от обычного выходного).
    val withVacation = mdn.copy(offPeriods = listOf(OffPeriod(anchor, anchor)))
    assertEquals(DayKind.VACATION, ShiftCalendar.kindOf(anchor, withVacation))
  }

  @Test
  fun `2x2 — рабочий день MORNING, выходной OFF`() {
    val s = ShiftPresets.byId("2x2")!!.build(anchor) // работа 7:00
    assertEquals(DayKind.MORNING, ShiftCalendar.kindOf(anchor, s))
    assertEquals(DayKind.OFF, ShiftCalendar.kindOf(anchor.plusDays(2), s))
  }
}
