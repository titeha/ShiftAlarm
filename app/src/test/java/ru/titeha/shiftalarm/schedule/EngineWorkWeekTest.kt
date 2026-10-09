package ru.titeha.shiftalarm.schedule

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.titeha.shiftalarm.data.AlarmEntity
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * TC02: глобальная рабочая неделя — вход движка (Q1=1а настройка, Q4=4а, Q5).
 *
 * Проверяется:
 *  - golden-тест инварианта C-1: `honorHolidays = false` → глобальная неделя не меняет результат
 *    (календарь `null`, `isWorkDay` возвращает `personal` до `transfersApply`);
 *  - переносы считаются от ГЛОБАЛЬНОЙ недели, а не от маски: стандартная неделя — переносы
 *    действуют (рабочая суббота звонит, перенос-выходной глушит масочный день); своя неделя —
 *    только чистые праздники;
 *  - режим calendar (REST): инвертируемая неделя — глобальная неделя пользователя (Q5),
 *    маска по-прежнему не участвует (D-2);
 *  - `firesOn` согласован с `next`.
 *
 * Даты — реальные встроенные данные: февраль 2027 (сб 20.02 TRANSFER_WORK, вс 21.02 и
 * пн 22.02 TRANSFER_OFF, вт 23.02 HOLIDAY — ПП РФ № 1187), январь 2027 (каникулы 1–8),
 * март 2026 (вс 08.03 HOLIDAY, пн 09.03 TRANSFER_OFF).
 */
class EngineWorkWeekTest {

  @Before fun setUp() { ProductionCalendars.source = null }
  @After fun tearDown() { ProductionCalendars.source = null }

  private val standard = CountryProfile.RU.standardWeek
  private val sixDay = workWeekOf(WorkWeekPreset.SIX_DAY_MON_SAT, 0)
  private val fourDay = workWeekOf(WorkWeekPreset.FOUR_DAY_MON_THU, 0)

  private val weekdays = AlarmTimes.maskOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
  )
  private val weekends = AlarmTimes.maskOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
  private val allDays = AlarmTimes.maskOf(*DayOfWeek.entries.toTypedArray())
  private val wedOnly = AlarmTimes.maskOf(DayOfWeek.WEDNESDAY)

  private fun weekly(mask: Int, honor: Boolean = true) = AlarmEntity(
    hour = 7, minute = 0,
    mode = AlarmEntity.MODE_WEEKLY,
    daysMask = mask,
    honorHolidays = honor,
    polarity = AlarmEntity.POLARITY_WORK
  )

  private fun calendarMode(mask: Int = 0) = AlarmEntity(
    hour = 7, minute = 0,
    mode = AlarmEntity.MODE_CALENDAR,
    daysMask = mask,
    honorHolidays = true,
    polarity = AlarmEntity.POLARITY_REST
  )

  @Test
  fun `golden C-1 - honorHolidays выключен, глобальная неделя не влияет`() {
    // calendar == null → isWorkDay возвращает personal до transfersApply: все три недели равны.
    for (mask in listOf(allDays, weekdays, wedOnly, weekends, 0)) {
      for (from in listOf(
        LocalDate.of(2027, 2, 19).atTime(8, 0),   // неделя переносов 20.02/22.02.2027
        LocalDate.of(2027, 1, 1).atTime(0, 0),    // новогодние каникулы
        LocalDate.of(2026, 11, 4).atTime(6, 0)    // праздник
      )) {
        val a = weekly(mask, honor = false)
        val base = AlarmTimes.next(a, emptyList(), from, standard)
        assertEquals(base, AlarmTimes.next(a, emptyList(), from, sixDay))
        assertEquals(base, AlarmTimes.next(a, emptyList(), from, fourDay))
      }
    }
    // Явный контроль: маска будней, Пт 19.02.2027 08:00 → Пн 22.02 07:00 (перенос не участвует).
    assertEquals(
      LocalDate.of(2027, 2, 22).atTime(7, 0),
      AlarmTimes.next(
        weekly(weekdays, honor = false), emptyList(),
        LocalDate.of(2027, 2, 19).atTime(8, 0), sixDay
      )
    )
  }

  @Test
  fun `6-дневка в режиме calendar - суббота рабочая, звонит в воскресенье`() {
    val a = calendarMode()
    val from = LocalDate.of(2027, 1, 15).atTime(8, 0)   // Пт, обычная неделя
    assertEquals(LocalDate.of(2027, 1, 16).atTime(7, 0), AlarmTimes.next(a, emptyList(), from, standard))
    assertEquals(LocalDate.of(2027, 1, 17).atTime(7, 0), AlarmTimes.next(a, emptyList(), from, sixDay))
  }

  @Test
  fun `4-дневка в режиме calendar - пятница уже выходной`() {
    val a = calendarMode()
    val from = LocalDate.of(2027, 1, 14).atTime(8, 0)   // Чт
    assertEquals(LocalDate.of(2027, 1, 16).atTime(7, 0), AlarmTimes.next(a, emptyList(), from, standard))
    assertEquals(LocalDate.of(2027, 1, 15).atTime(7, 0), AlarmTimes.next(a, emptyList(), from, fourDay))
  }

  @Test
  fun `переносы считаются от глобальной недели - маска Пн-Пт, февраль 2027`() {
    val a = weekly(weekdays)
    val from = LocalDate.of(2027, 2, 19).atTime(8, 0)   // Пт, накануне рабочей субботы 20.02
    // Стандартная неделя: рабочая суббота (TRANSFER_WORK) звонит, хотя Сб не в маске.
    assertEquals(LocalDate.of(2027, 2, 20).atTime(7, 0), AlarmTimes.next(a, emptyList(), from, standard))
    // 6-дневка: переносы не применяются → Сб/Вс и Пн 22.02 (TRANSFER_OFF) не глушится: маска Пн — звонит.
    assertEquals(LocalDate.of(2027, 2, 22).atTime(7, 0), AlarmTimes.next(a, emptyList(), from, sixDay))
  }

  @Test
  fun `перенос-выходной глушит масочный день при стандартной неделе и не глушит при своей`() {
    val a = weekly(AlarmTimes.maskOf(DayOfWeek.MONDAY))
    val from = LocalDate.of(2026, 3, 6).atTime(8, 0)   // Пт
    // Пн 09.03.2026 — TRANSFER_OFF (перенос с Вс 08.03). Стандартная неделя: переносы
    // действуют → тишина до следующего понедельника.
    assertEquals(LocalDate.of(2026, 3, 16).atTime(7, 0), AlarmTimes.next(a, emptyList(), from, standard))
    // Своя неделя (6-дневка): переносы не применяются → масочный Пн звонит.
    assertEquals(LocalDate.of(2026, 3, 9).atTime(7, 0), AlarmTimes.next(a, emptyList(), from, sixDay))
  }

  @Test
  fun `режим calendar игнорирует маску при любой глобальной неделе - D-2`() {
    val a = calendarMode(mask = weekdays)
    val from = LocalDate.of(2027, 1, 15).atTime(8, 0)
    assertEquals(LocalDate.of(2027, 1, 17).atTime(7, 0), AlarmTimes.next(a, emptyList(), from, sixDay))
    val cal = requireNotNull(ProductionCalendars.merged("RU", 2027))
    assertFalse(AlarmTimes.firesOn(a, LocalDate.of(2027, 1, 16), cal, emptyList(), sixDay))
    assertTrue(AlarmTimes.firesOn(a, LocalDate.of(2027, 1, 17), cal, emptyList(), sixDay))
  }

  @Test
  fun `перенесённый выходной в режиме calendar - стандарт звонит, своя неделя нет, праздник звонит всем`() {
    val a = calendarMode()
    val from = LocalDate.of(2027, 2, 21).atTime(8, 0)   // Вс, вечер
    // Стандартная неделя: Пн 22.02 — TRANSFER_OFF (официальный нерабочий) → звонит.
    assertEquals(LocalDate.of(2027, 2, 22).atTime(7, 0), AlarmTimes.next(a, emptyList(), from, standard))
    // 6-дневка: переносы не применяются, Пн — личный рабочий день → тишина; звонит во Вт
    // 23.02 — чистый праздник (HOLIDAY глушит WORK и звонит в REST при любой неделе).
    assertEquals(LocalDate.of(2027, 2, 23).atTime(7, 0), AlarmTimes.next(a, emptyList(), from, sixDay))
  }

  @Test
  fun `firesOn согласован с next - рабочая суббота февраля 2027`() {
    val a = weekly(weekdays)
    val cal = requireNotNull(ProductionCalendars.merged("RU", 2027))
    val sat = LocalDate.of(2027, 2, 20)
    val mon = LocalDate.of(2027, 2, 22)
    assertTrue(AlarmTimes.firesOn(a, sat, cal, emptyList(), standard))   // TRANSFER_WORK звонит
    assertFalse(AlarmTimes.firesOn(a, mon, cal, emptyList(), standard))  // TRANSFER_OFF глушит
    assertFalse(AlarmTimes.firesOn(a, sat, cal, emptyList(), sixDay))    // переносы не действуют, Сб не в маске
    assertTrue(AlarmTimes.firesOn(a, mon, cal, emptyList(), sixDay))      // масочный Пн звонит
  }
}
