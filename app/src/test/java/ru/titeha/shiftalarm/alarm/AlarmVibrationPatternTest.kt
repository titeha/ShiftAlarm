package ru.titeha.shiftalarm.alarm

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmVibrationPatternTest {
  @Test
  fun `шаблон вибрации валиден`() {
    assertTrue(AlarmVibrationPattern.isValid())
  }

  @Test
  fun `шаблон вибрации начинается без задержки`() {
    assertEquals(0L, AlarmVibrationPattern.timingsMillis().first())
  }

  @Test
  fun `шаблон вибрации повторяется с начала`() {
    assertEquals(0, AlarmVibrationPattern.repeatIndex())
  }

  @Test
  fun `шаблон возвращается копией`() {
    val original = AlarmVibrationPattern.timingsMillis()
    val changed = AlarmVibrationPattern.timingsMillis()

    changed[1] = 1L

    assertArrayEquals(original, AlarmVibrationPattern.timingsMillis())
  }
}