package ru.titeha.shiftalarm.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.titeha.shiftalarm.data.AlarmEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class AlarmTimesTest {

  // 2026-06-24 — среда.
  private val wed = LocalDate.of(2026, 6, 24)
  private fun at(date: LocalDate, h: Int, m: Int) = date.atTime(h, m)

  @Test
  fun `разовый — сегодня, если время ещё впереди`() {
    val from = at(wed, 6, 0)
    val next = AlarmTimes.nextWeekly(7, 0, daysMask = 0, from = from)
    assertEquals(at(wed, 7, 0), next)
  }

  @Test
  fun `разовый — завтра, если время уже прошло`() {
    val from = at(wed, 8, 0)
    val next = AlarmTimes.nextWeekly(7, 0, daysMask = 0, from = from)
    assertEquals(at(wed.plusDays(1), 7, 0), next)
  }

  @Test
  fun `разовый — ровно текущее время считается прошедшим`() {
    val from = at(wed, 7, 0)
    val next = AlarmTimes.nextWeekly(7, 0, daysMask = 0, from = from)
    assertEquals(at(wed.plusDays(1), 7, 0), next)
  }

  @Test
  fun `по дням недели — сегодня среда в маске и время впереди`() {
    val mask = AlarmTimes.maskOf(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
    val next = AlarmTimes.nextWeekly(7, 0, mask, at(wed, 6, 0))
    assertEquals(at(wed, 7, 0), next)
  }

  @Test
  fun `по дням недели — сегодня в маске, но время прошло, берём следующий день маски`() {
    // Среда и пятница; в среду уже 8:00 → ближайшее в пятницу.
    val mask = AlarmTimes.maskOf(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
    val next = AlarmTimes.nextWeekly(7, 0, mask, at(wed, 8, 0))
    assertEquals(at(wed.plusDays(2), 7, 0), next) // пятница 26-го
  }

  @Test
  fun `по дням недели — только понедельник, со среды до следующего понедельника`() {
    val mask = AlarmTimes.maskOf(DayOfWeek.MONDAY)
    val next = AlarmTimes.nextWeekly(9, 30, mask, at(wed, 12, 0))
    assertEquals(at(LocalDate.of(2026, 6, 29), 9, 30), next) // ближайший понедельник
  }

  @Test
  fun `каждый день — маска из всех семи дней даёт завтра, если время прошло`() {
    val all = AlarmTimes.maskOf(*DayOfWeek.entries.toTypedArray())
    val next = AlarmTimes.nextWeekly(7, 0, all, at(wed, 9, 0))
    assertEquals(at(wed.plusDays(1), 7, 0), next)
  }

  @Test
  fun `next — режим weekly делегирует расчёт по маске`() {
    val alarm = AlarmEntity(hour = 7, minute = 0, mode = AlarmEntity.MODE_WEEKLY, daysMask = 0)
    val next = AlarmTimes.next(alarm, at(wed, 6, 0))
    assertEquals(at(wed, 7, 0), next)
  }

  @Test
  fun `next — режим shift делегирует движку смен`() {
    // Пресет 2x2 в 7:00, опорная дата = среда (рабочий день).
    val alarm = AlarmEntity(
      mode = AlarmEntity.MODE_SHIFT,
      presetId = "2x2",
      anchorEpochDay = wed.toEpochDay()
    )
    val next = AlarmTimes.next(alarm, at(wed, 6, 0))
    assertEquals(at(wed, 7, 0), next)
  }

  @Test
  fun `next — неизвестный пресет даёт null`() {
    val alarm = AlarmEntity(mode = AlarmEntity.MODE_SHIFT, presetId = "нет-такого")
    assertNull(AlarmTimes.next(alarm, at(wed, 6, 0)))
  }
}
