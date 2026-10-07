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
 * Встроенные данные РФ-2027 (E3): производственный календарь на 2027 год, переносы —
 * Постановление Правительства РФ от 17.09.2026 № 1187. Итоги сверены владельцем с документом:
 * 247 рабочих / 118 нерабочих дней, сокращённые — 20.02, 30.04, 11.06, 03.11.
 */
class ProductionCalendar2027Test {

  @Before
  fun setUp() {
    ProductionCalendars.source = null
  }

  @After
  fun tearDown() {
    ProductionCalendars.source = null
  }

  private val cal = ProductionCalendars.RU_2027

  private val weekdays = AlarmTimes.maskOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
  )

  private fun weekly(mask: Int) = AlarmEntity(
    hour = 7,
    minute = 0,
    mode = AlarmEntity.MODE_WEEKLY,
    daysMask = mask,
    honorHolidays = true
  )

  private fun calendarMode() = AlarmEntity(
    hour = 7,
    minute = 0,
    mode = AlarmEntity.MODE_CALENDAR,
    honorHolidays = true
  )

  @Test
  fun `праздники и переносы соответствуют постановлению`() {
    // Новогодние каникулы и Рождество: 1–8 января.
    for (day in 1..8) {
      assertTrue(cal.isNonWorking(LocalDate.of(2027, 1, day)))
    }
    // Праздники ст. 112 ТК (будни и выходные).
    assertTrue(cal.isNonWorking(LocalDate.of(2027, 2, 23)))  // Вт
    assertTrue(cal.isNonWorking(LocalDate.of(2027, 3, 8)))   // Пн
    assertTrue(cal.isNonWorking(LocalDate.of(2027, 5, 1)))   // Сб
    assertTrue(cal.isNonWorking(LocalDate.of(2027, 5, 9)))   // Вс
    assertTrue(cal.isNonWorking(LocalDate.of(2027, 6, 12)))  // Сб
    assertTrue(cal.isNonWorking(LocalDate.of(2027, 11, 4)))  // Чт
    // Переносы: ПП № 1187 и ч. 2 ст. 112 ТК.
    assertTrue(cal.isNonWorking(LocalDate.of(2027, 2, 22)))  // Пн — с сб 20.02
    assertTrue(cal.isNonWorking(LocalDate.of(2027, 5, 3)))   // Пн — с сб 01.05
    assertTrue(cal.isNonWorking(LocalDate.of(2027, 5, 10)))  // Пн — с вс 09.05
    assertTrue(cal.isNonWorking(LocalDate.of(2027, 6, 14)))  // Пн — с сб 12.06
    assertTrue(cal.isNonWorking(LocalDate.of(2027, 11, 5)))  // Пт — с сб 02.01
    assertTrue(cal.isNonWorking(LocalDate.of(2027, 12, 31))) // Пт — с вс 03.01
    // Рабочая суббота 20.02 и сокращённые дни — РАБОЧИЕ.
    assertTrue(cal.isWorking(LocalDate.of(2027, 2, 20)))
    assertTrue(cal.isWorking(LocalDate.of(2027, 4, 30)))
    assertTrue(cal.isWorking(LocalDate.of(2027, 6, 11)))
    assertTrue(cal.isWorking(LocalDate.of(2027, 11, 3)))
  }

  @Test
  fun `итоги года соответствуют документу — 247 рабочих и 118 нерабочих дней`() {
    var nonWorking = 0
    var date = LocalDate.of(2027, 1, 1)
    while (date.year == 2027) {
      if (cal.isNonWorking(date)) nonWorking++
      date = date.plusDays(1)
    }
    assertEquals(118, nonWorking)
    assertEquals(247, 365 - nonWorking)
  }

  @Test
  fun `рабочая суббота 20 февраля — weekly звонит, режим calendar молчит`() {
    val sat = LocalDate.of(2027, 2, 20)
    // weekly со стандартной неделей: перенос применим — рабочая суббота звонит.
    assertTrue(AlarmTimes.firesOn(weekly(weekdays), sat, cal))
    // «По выходным и праздникам»: 20.02 — рабочий день, тишина.
    assertFalse(AlarmTimes.firesOn(calendarMode(), sat, cal))
    // 22.02 (Пн, отдых по переносу): weekly молчит, calendar звонит.
    val mon = LocalDate.of(2027, 2, 22)
    assertFalse(AlarmTimes.firesOn(weekly(weekdays), mon, cal))
    assertTrue(AlarmTimes.firesOn(calendarMode(), mon, cal))
  }

  @Test
  fun `режим calendar — новогодние каникулы звонят, поиск пересекает границу года`() {
    val a = calendarMode()
    // Отсчёт 31.12.2026 08:00 (Чт): звонок 07:00 уже «прошёл», а ближайший нерабочий —
    // 01.01.2027 (праздник из RU_2027). Поиск пересекает границу года: merged покрывает
    // год отсчёта и следующий.
    assertEquals(
      LocalDate.of(2027, 1, 1).atTime(7, 0),
      AlarmTimes.next(a, LocalDate.of(2026, 12, 31).atTime(8, 0))
    )
  }
}
