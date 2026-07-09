package ru.titeha.shiftalarm.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.titeha.shiftalarm.data.AlarmEntity
import java.time.DayOfWeek
import java.util.Calendar

class SetAlarmSpecTest {
  @Test
  fun `нет часа — вернуть null, создавать нечего`() {
    assertNull(
      SetAlarmSpec.toAlarm(
        hour = null,
        minute = 0,
        message = "",
        calendarDays = emptyList()
      )
    )
  }

  @Test
  fun `час без дней — разовый будильник, включён, режим по дням недели`() {
    val alarm = SetAlarmSpec.toAlarm(7, 30, "Подъём", emptyList())!!

    assertEquals(7, alarm.hour)
    assertEquals(30, alarm.minute)
    assertEquals("Подъём", alarm.label)
    assertEquals(AlarmEntity.MODE_WEEKLY, alarm.mode)
    assertEquals(0, alarm.daysMask)
    assertTrue(alarm.enabled)
  }

  @Test
  fun `час меньше нуля считается некорректным`() {
    assertNull(
      SetAlarmSpec.toAlarm(
        hour = -1,
        minute = 0,
        message = "",
        calendarDays = emptyList()
      )
    )
  }

  @Test
  fun `час больше двадцати трёх считается некорректным`() {
    assertNull(
      SetAlarmSpec.toAlarm(
        hour = 24,
        minute = 0,
        message = "",
        calendarDays = emptyList()
      )
    )
  }

  @Test
  fun `минуты меньше нуля считаются некорректными`() {
    assertNull(
      SetAlarmSpec.toAlarm(
        hour = 7,
        minute = -1,
        message = "",
        calendarDays = emptyList()
      )
    )
  }

  @Test
  fun `минуты больше пятидесяти девяти считаются некорректными`() {
    assertNull(
      SetAlarmSpec.toAlarm(
        hour = 7,
        minute = 60,
        message = "",
        calendarDays = emptyList()
      )
    )
  }

  @Test
  fun `пробелы вокруг подписи будильника убираются`() {
    val alarm = SetAlarmSpec.toAlarm(
      hour = 7,
      minute = 30,
      message = "  Подъём  ",
      calendarDays = emptyList()
    )!!

    assertEquals("Подъём", alarm.label)
  }

  @Test
  fun `слишком длинная подпись будильника обрезается`() {
    val alarm = SetAlarmSpec.toAlarm(
      hour = 7,
      minute = 30,
      message = "А".repeat(200),
      calendarDays = emptyList()
    )!!

    assertEquals(120, alarm.label.length)
  }

  @Test
  fun `дни повтора из констант Calendar кладутся в маску`() {
    val alarm = SetAlarmSpec.toAlarm(
      hour = 6,
      minute = 0,
      message = "",
      calendarDays = listOf(Calendar.MONDAY, Calendar.WEDNESDAY)
    )!!

    assertTrue(AlarmTimes.maskHas(alarm.daysMask, DayOfWeek.MONDAY))
    assertTrue(AlarmTimes.maskHas(alarm.daysMask, DayOfWeek.WEDNESDAY))
    assertFalse(AlarmTimes.maskHas(alarm.daysMask, DayOfWeek.TUESDAY))
    assertEquals(
      AlarmTimes.maskOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
      alarm.daysMask
    )
  }

  @Test
  fun `воскресенье Calendar=1 маппится в DayOfWeek SUNDAY`() {
    val alarm = SetAlarmSpec.toAlarm(
      hour = 9,
      minute = 0,
      message = "",
      calendarDays = listOf(Calendar.SUNDAY)
    )!!

    assertEquals(AlarmTimes.maskOf(DayOfWeek.SUNDAY), alarm.daysMask)
  }

  @Test
  fun `все семь дней дают полную маску`() {
    val all = listOf(
      Calendar.MONDAY,
      Calendar.TUESDAY,
      Calendar.WEDNESDAY,
      Calendar.THURSDAY,
      Calendar.FRIDAY,
      Calendar.SATURDAY,
      Calendar.SUNDAY
    )

    val alarm = SetAlarmSpec.toAlarm(
      hour = 8,
      minute = 0,
      message = "",
      calendarDays = all
    )!!

    assertEquals(
      AlarmTimes.maskOf(*DayOfWeek.entries.toTypedArray()),
      alarm.daysMask
    )
  }

  @Test
  fun `неизвестные значения дней игнорируются`() {
    val alarm = SetAlarmSpec.toAlarm(
      hour = 8,
      minute = 0,
      message = "",
      calendarDays = listOf(Calendar.MONDAY, 99, -1)
    )!!

    assertEquals(AlarmTimes.maskOf(DayOfWeek.MONDAY), alarm.daysMask)
  }

  @Test
  fun `только неизвестные дни дают разовый будильник`() {
    val alarm = SetAlarmSpec.toAlarm(
      hour = 8,
      minute = 0,
      message = "",
      calendarDays = listOf(99, -1, 0)
    )!!

    assertEquals(0, alarm.daysMask)
  }
}