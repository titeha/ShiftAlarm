package ru.titeha.shiftalarm.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.titeha.shiftalarm.schedule.ShiftCategory
import java.time.LocalDate
import java.time.LocalTime

class AlarmOverrideMappingTest {

  @Test
  fun toDayOverride_reconstructsShift_withWakeTime() {
    val ovr = AlarmOverride(
      id = 1, alarmId = 5,
      fromEpochDay = 20050, toEpochDay = 20052,
      category = "NIGHT", wakeMinutes = 21 * 60, name = "Ночь"
    )

    val d = ovr.toDayOverride()

    assertEquals(LocalDate.ofEpochDay(20050), d.from)
    assertEquals(LocalDate.ofEpochDay(20052), d.to)
    assertEquals("Ночь", d.shift.name)
    assertEquals(ShiftCategory.NIGHT, d.shift.category)
    assertEquals(LocalTime.of(21, 0), d.shift.wakeTime)
  }

  @Test
  fun toDayOverride_nullWakeMinutes_meansNoAlarm() {
    val ovr = AlarmOverride(
      id = 2, alarmId = 5,
      fromEpochDay = 20060, toEpochDay = 20060,
      category = "OFF", wakeMinutes = null, name = "Выходной"
    )

    val d = ovr.toDayOverride()

    assertNull(d.shift.wakeTime)
    assertEquals(ShiftCategory.OFF, d.shift.category)
    assertEquals(true, d.isSingleDay)
  }
}
