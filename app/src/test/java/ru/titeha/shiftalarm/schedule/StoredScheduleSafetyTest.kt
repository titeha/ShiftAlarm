package ru.titeha.shiftalarm.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import ru.titeha.shiftalarm.data.AlarmEntity
import ru.titeha.shiftalarm.data.AlarmOverride
import java.time.LocalDate
import java.time.LocalTime

class StoredScheduleSafetyTest {
  private val anchor: LocalDate = LocalDate.of(2026, 6, 24)

  @Test
  fun `decodeOrNull возвращает null для слота без разделителя`() {
    assertNull(
      ShiftCycleCodec.decodeOrNull("сломанный слот")
    )
  }

  @Test
  fun `decodeOrNull возвращает null для некорректного времени`() {
    assertNull(
      ShiftCycleCodec.decodeOrNull("M|2460|Смена")
    )
  }

  @Test
  fun `decodeOrNull возвращает пустой список для пустой строки`() {
    assertEquals(
      emptyList<ShiftType>(),
      ShiftCycleCodec.decodeOrNull("")
    )
  }

  @Test
  fun `битый cycleSpec откатывается на пресет`() {
    val alarm = AlarmEntity(
      mode = AlarmEntity.MODE_SHIFT,
      presetId = "2x2",
      cycleSpec = "сломанный слот",
      anchorEpochDay = anchor.toEpochDay()
    )

    val next = AlarmTimes.next(
      alarm = alarm,
      periods = emptyList(),
      overrides = emptyList(),
      from = anchor.atTime(6, 0)
    )

    assertEquals(anchor.atTime(7, 0), next)
  }

  @Test
  fun `битый cycleSpec без рабочего пресета не планируется`() {
    val alarm = AlarmEntity(
      mode = AlarmEntity.MODE_SHIFT,
      presetId = "нет-такого-пресета",
      cycleSpec = "сломанный слот",
      anchorEpochDay = anchor.toEpochDay()
    )

    val next = AlarmTimes.next(
      alarm = alarm,
      periods = emptyList(),
      overrides = emptyList(),
      from = anchor.atTime(6, 0)
    )

    assertNull(next)
  }

  @Test
  fun `корректная правка календаря разворачивается в доменную модель`() {
    val override = AlarmOverride(
      alarmId = 1L,
      fromEpochDay = anchor.toEpochDay(),
      toEpochDay = anchor.toEpochDay(),
      category = ShiftCategory.DAY.name,
      wakeMinutes = 8 * 60 + 30,
      name = "Отработка"
    )

    val result = override.toDayOverrideOrNull()

    assertNotNull(result)
    assertEquals(anchor, result!!.from)
    assertEquals(anchor, result.to)
    assertEquals(ShiftCategory.DAY, result.shift.category)
    assertEquals(LocalTime.of(8, 30), result.shift.wakeTime)
    assertEquals("Отработка", result.shift.name)
  }

  @Test
  fun `правка с неизвестной категорией пропускается`() {
    val override = AlarmOverride(
      alarmId = 1L,
      fromEpochDay = anchor.toEpochDay(),
      toEpochDay = anchor.toEpochDay(),
      category = "BROKEN",
      wakeMinutes = 8 * 60,
      name = "Битая правка"
    )

    assertNull(override.toDayOverrideOrNull())
  }

  @Test
  fun `правка с минутами меньше нуля пропускается`() {
    val override = AlarmOverride(
      alarmId = 1L,
      fromEpochDay = anchor.toEpochDay(),
      toEpochDay = anchor.toEpochDay(),
      category = ShiftCategory.DAY.name,
      wakeMinutes = -1,
      name = "Битая правка"
    )

    assertNull(override.toDayOverrideOrNull())
  }

  @Test
  fun `правка с минутами больше дня пропускается`() {
    val override = AlarmOverride(
      alarmId = 1L,
      fromEpochDay = anchor.toEpochDay(),
      toEpochDay = anchor.toEpochDay(),
      category = ShiftCategory.DAY.name,
      wakeMinutes = 24 * 60,
      name = "Битая правка"
    )

    assertNull(override.toDayOverrideOrNull())
  }

  @Test
  fun `правка с концом раньше начала пропускается`() {
    val override = AlarmOverride(
      alarmId = 1L,
      fromEpochDay = anchor.plusDays(1).toEpochDay(),
      toEpochDay = anchor.toEpochDay(),
      category = ShiftCategory.DAY.name,
      wakeMinutes = 8 * 60,
      name = "Битая правка"
    )

    assertNull(override.toDayOverrideOrNull())
  }
}