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
  fun `нет часа — вернуть null (создавать нечего, открываем редактор)`() {
    assertNull(SetAlarmSpec.toAlarm(hour = null, minute = 0, message = "", calendarDays = emptyList()))
  }

  @Test
  fun `час без дней — разовый будильник, включён, режим по дням недели`() {
    val alarm = SetAlarmSpec.toAlarm(7, 30, "Подъём", emptyList())!!
    assertEquals(7, alarm.hour)
    assertEquals(30, alarm.minute)
    assertEquals("Подъём", alarm.label)
    assertEquals(AlarmEntity.MODE_WEEKLY, alarm.mode)
    assertEquals(0, alarm.daysMask) // разовый
    assertTrue(alarm.enabled)
  }

  @Test
  fun `дни повтора из констант Calendar кладутся в маску`() {
    val alarm = SetAlarmSpec.toAlarm(6, 0, "", listOf(Calendar.MONDAY, Calendar.WEDNESDAY))!!
    assertTrue(AlarmTimes.maskHas(alarm.daysMask, DayOfWeek.MONDAY))
    assertTrue(AlarmTimes.maskHas(alarm.daysMask, DayOfWeek.WEDNESDAY))
    assertFalse(AlarmTimes.maskHas(alarm.daysMask, DayOfWeek.TUESDAY))
    assertEquals(AlarmTimes.maskOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), alarm.daysMask)
  }

  @Test
  fun `воскресенье Calendar=1 маппится в DayOfWeek SUNDAY`() {
    val alarm = SetAlarmSpec.toAlarm(9, 0, "", listOf(Calendar.SUNDAY))!!
    assertEquals(AlarmTimes.maskOf(DayOfWeek.SUNDAY), alarm.daysMask)
  }

  @Test
  fun `все семь дней дают полную маску`() {
    val all = listOf(
      Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
      Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
    )
    val alarm = SetAlarmSpec.toAlarm(8, 0, "", all)!!
    assertEquals(AlarmTimes.maskOf(*DayOfWeek.entries.toTypedArray()), alarm.daysMask)
  }

  @Test
  fun `неизвестные значения дней игнорируются`() {
    val alarm = SetAlarmSpec.toAlarm(8, 0, "", listOf(Calendar.MONDAY, 99, -1))!!
    assertEquals(AlarmTimes.maskOf(DayOfWeek.MONDAY), alarm.daysMask)
  }
}
