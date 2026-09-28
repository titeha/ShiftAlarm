package ru.titeha.shiftalarm.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import ru.titeha.shiftalarm.data.AlarmEntity
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Правило «разовый» (TD06, решение Q5=5в): оно живёт в чистом слое [AlarmTimes.isOneShot] и
 * одинаково понимается движком и приёмником. Пустая маска дней имеет смысл только в режиме
 * «по дням недели»; сменный режим и режим calendar повторяющиеся, неизвестный режим трактуется
 * как weekly (защитное чтение [AlarmTimes.effectiveMode] в обоих слоях).
 *
 * Даты: 2026-06-24 — Ср, 2026-06-25 — Чт (рабочий), 2026-06-26 — Пт, 2026-06-27 — Сб,
 * 2026-06-28 — Вс.
 */
class OneShotRuleTest {

  // Внешний источник календаря — глобальное состояние модуля: не протекать между тестами.
  @Before
  fun setUp() {
    ProductionCalendars.source = null
  }

  @After
  fun tearDown() {
    ProductionCalendars.source = null
  }

  private val wed = LocalDate.of(2026, 6, 24)
  private val thu = LocalDate.of(2026, 6, 25)
  private val fri = LocalDate.of(2026, 6, 26)
  private val sat = LocalDate.of(2026, 6, 27)
  private val sun = LocalDate.of(2026, 6, 28)

  private val everyDay = AlarmTimes.maskOf(*DayOfWeek.entries.toTypedArray())

  private fun weekly(
    mask: Int,
    honor: Boolean = false,
    polarity: String = AlarmEntity.POLARITY_WORK,
  ) = AlarmEntity(
    hour = 7,
    minute = 0,
    mode = AlarmEntity.MODE_WEEKLY,
    daysMask = mask,
    honorHolidays = honor,
    polarity = polarity,
  )

  private fun calendar(mask: Int) = weekly(mask).copy(mode = AlarmEntity.MODE_CALENDAR)

  private fun unknown(
    mask: Int,
    honor: Boolean = false,
    polarity: String = AlarmEntity.POLARITY_WORK,
  ) = weekly(mask, honor, polarity).copy(mode = "weekly_v2")

  private fun shift(mask: Int) = weekly(mask).copy(
    mode = AlarmEntity.MODE_SHIFT,
    presetId = "2x2",
    anchorEpochDay = wed.toEpochDay(),
  )

  @Test
  fun `weekly с пустой маской — разовый, звонок ближайший`() {
    val alarm = weekly(0)
    assertTrue(AlarmTimes.isOneShot(alarm))
    // 07:00 уже прошло — ближайшее время завтра, а не «следующий день недели из маски».
    assertEquals(thu.atTime(7, 0), AlarmTimes.next(alarm, wed.atTime(8, 0)))
    // Время ещё впереди — звонок сегодня.
    assertEquals(wed.atTime(7, 0), AlarmTimes.next(alarm, wed.atTime(6, 0)))
  }

  @Test
  fun `weekly с непустой маской — не разовый`() {
    assertFalse(AlarmTimes.isOneShot(weekly(AlarmTimes.bitOf(DayOfWeek.MONDAY))))
    assertFalse(AlarmTimes.isOneShot(weekly(everyDay)))
  }

  @Test
  fun `calendar с пустой маской — не разовый и звонок повторяется`() {
    val alarm = calendar(0)
    assertFalse(AlarmTimes.isOneShot(alarm))
    // Пустая маска не превращает режим календаря в разовый: звонок — в ближайший нерабочий день,
    // после него — в следующий (инварианты D-2/D-3).
    assertEquals(sat.atTime(7, 0), AlarmTimes.next(alarm, fri.atTime(8, 0)))
    assertEquals(sun.atTime(7, 0), AlarmTimes.next(alarm, sat.atTime(8, 0)))
  }

  @Test
  fun `сменный режим с пустой маской — не разовый`() {
    assertFalse(AlarmTimes.isOneShot(shift(0)))
    assertFalse(AlarmTimes.isOneShot(shift(AlarmTimes.bitOf(DayOfWeek.MONDAY))))
  }

  @Test
  fun `неизвестный режим с пустой маской — разовый и согласован с расчётом`() {
    val alarm = unknown(0)
    // K14/F8: раньше приёмник сравнивал сырой mode и считал такую запись повторяющейся —
    // она перезванивала ежедневно и не гасилась. Теперь правило общее.
    assertTrue(AlarmTimes.isOneShot(alarm))
    assertEquals(AlarmTimes.next(weekly(0), wed.atTime(8, 0)), AlarmTimes.next(alarm, wed.atTime(8, 0)))
    // Движок отвечает ближайшее время (рабочий четверг), а не день из расписания — разовый смысл.
    assertEquals(thu.atTime(7, 0), AlarmTimes.next(alarm, wed.atTime(8, 0)))
  }

  @Test
  fun `неизвестный режим с legacy-REST и пустой маской — разовый, а не звонок по выходным`() {
    // Форма weekly_v2 + маска 0 + honorHolidays + REST: полярность вне calendar не действует (D-5),
    // поэтому движок зовёт в четверг 07:00, а не ждёт субботы → приёмник обязан погасить запись.
    val alarm = unknown(0, honor = true, polarity = AlarmEntity.POLARITY_REST)
    assertTrue(AlarmTimes.isOneShot(alarm))
    assertEquals(thu.atTime(7, 0), AlarmTimes.next(alarm, wed.atTime(8, 0)))
  }

  @Test
  fun `разовый weekly с праздниками — разовый и не показывается как повтор`() {
    val alarm = weekly(0, honor = true)
    assertTrue(AlarmTimes.isOneShot(alarm))
    assertEquals(thu.atTime(7, 0), AlarmTimes.next(alarm, wed.atTime(8, 0)))
    // Тот же мотив (маска 0 в weekly): разовый как повтор в календаре не рисуется.
    assertFalse(AlarmTimes.firesOn(alarm, thu, null))
  }

  @Test
  fun `правило не зависит от полярности legacy-записи weekly`() {
    // После TD03 полярность у weekly не действует, поэтому маска 0 разовая в обоих случаях.
    val rest = weekly(0, honor = true, polarity = AlarmEntity.POLARITY_REST)
    assertTrue(AlarmTimes.isOneShot(rest))
    assertTrue(AlarmTimes.isOneShot(weekly(0)))
    assertEquals(
      AlarmTimes.next(weekly(0), wed.atTime(8, 0)),
      AlarmTimes.next(rest, wed.atTime(8, 0)),
    )
  }

  @Test
  fun `календарный режим с непустой маской — тоже не разовый`() {
    assertFalse(AlarmTimes.isOneShot(calendar(everyDay)))
    assertFalse(AlarmTimes.isOneShot(calendar(AlarmTimes.bitOf(DayOfWeek.SATURDAY))))
  }
}
