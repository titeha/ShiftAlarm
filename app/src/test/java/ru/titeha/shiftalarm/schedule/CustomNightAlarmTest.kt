package ru.titeha.shiftalarm.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.titeha.shiftalarm.data.AlarmEntity
import java.time.LocalDate
import java.time.LocalTime

class CustomNightAlarmTest {
  private val anchor = LocalDate.of(2026, 6, 24)

  @Test
  fun `одиночная кастомная ночная смена звонит накануне`() {
    val spec = ShiftCycleCodec.encode(
      listOf(
        ShiftType.off("Выходной"),
        ShiftType("n", "Ночь", LocalTime.of(21, 0), ShiftCategory.NIGHT),
        ShiftType.off("Выходной"),
      )
    )

    val alarm = AlarmEntity(
      mode = AlarmEntity.MODE_SHIFT,
      cycleSpec = spec,
      anchorEpochDay = anchor.toEpochDay()
    )

    val base = AlarmTimes.shiftBase(alarm)!!

    assertEquals(LocalTime.of(21, 0), ShiftEngine.wakeTimeOn(anchor, base))
    assertNull(ShiftEngine.wakeTimeOn(anchor.plusDays(1), base))
    assertEquals(ShiftCategory.NIGHT, ShiftEngine.shiftOn(anchor.plusDays(1), base).category)
  }

  @Test
  fun `блок кастомных ночных смен раскладывает звонки на предыдущие дни`() {
    val spec = ShiftCycleCodec.encode(
      listOf(
        ShiftType.off("Выходной"),
        ShiftType("n1", "Ночь", LocalTime.of(21, 0), ShiftCategory.NIGHT),
        ShiftType("n2", "Ночь", LocalTime.of(21, 0), ShiftCategory.NIGHT),
        ShiftType("n3", "Ночь", LocalTime.of(21, 0), ShiftCategory.NIGHT),
        ShiftType.off("Выходной"),
      )
    )

    val alarm = AlarmEntity(
      mode = AlarmEntity.MODE_SHIFT,
      cycleSpec = spec,
      anchorEpochDay = anchor.toEpochDay()
    )

    val base = AlarmTimes.shiftBase(alarm)!!

    assertEquals(LocalTime.of(21, 0), ShiftEngine.wakeTimeOn(anchor, base))
    assertEquals(LocalTime.of(21, 0), ShiftEngine.wakeTimeOn(anchor.plusDays(1), base))
    assertEquals(LocalTime.of(21, 0), ShiftEngine.wakeTimeOn(anchor.plusDays(2), base))
    assertNull(ShiftEngine.wakeTimeOn(anchor.plusDays(3), base))

    assertEquals(ShiftCategory.NIGHT, ShiftEngine.shiftOn(anchor.plusDays(1), base).category)
    assertEquals(ShiftCategory.NIGHT, ShiftEngine.shiftOn(anchor.plusDays(2), base).category)
    assertEquals(ShiftCategory.NIGHT, ShiftEngine.shiftOn(anchor.plusDays(3), base).category)
  }
}