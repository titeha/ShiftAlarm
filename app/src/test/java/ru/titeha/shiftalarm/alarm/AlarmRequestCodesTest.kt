package ru.titeha.shiftalarm.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmRequestCodesTest {

  @Test
  fun `fire и show различны для одного id`() {
    for (id in 1L..1000L) {
      assertNotEquals("id=$id", AlarmRequestCodes.fire(id), AlarmRequestCodes.show(id))
    }
  }

  @Test
  fun `fire чётный, show нечётный — разбиение без пересечений`() {
    for (id in 1L..1000L) {
      assertEquals("fire($id) должен быть чётным", 0, AlarmRequestCodes.fire(id) % 2)
      assertEquals("show($id) должен быть нечётным", 1, Math.floorMod(AlarmRequestCodes.show(id), 2))
    }
  }

  @Test
  fun `коды различимы между разными будильниками в реальном диапазоне id`() {
    val seen = HashSet<Int>()
    for (id in 1L..100_000L) {
      assertTrue("коллизия fire для id=$id", seen.add(AlarmRequestCodes.fire(id)))
      assertTrue("коллизия show для id=$id", seen.add(AlarmRequestCodes.show(id)))
    }
  }

  @Test
  fun `код стабилен для одного id (одинаков при повторном вычислении)`() {
    assertEquals(AlarmRequestCodes.fire(42L), AlarmRequestCodes.fire(42L))
    assertEquals(AlarmRequestCodes.show(42L), AlarmRequestCodes.show(42L))
    assertEquals(AlarmRequestCodes.snooze(42L), AlarmRequestCodes.snooze(42L))
  }

  @Test
  fun `снуз в отдельном неймспейсе — не пересекается с fire и show`() {
    val used = HashSet<Int>()
    for (id in 1L..100_000L) {
      used.add(AlarmRequestCodes.fire(id))
      used.add(AlarmRequestCodes.show(id))
    }
    for (id in 1L..100_000L) {
      val snooze = AlarmRequestCodes.snooze(id)
      assertTrue("снуз($id)=$snooze должен быть отрицательным", snooze < 0)
      assertFalse("снуз($id) не должен пересекаться с fire/show", used.contains(snooze))
    }
  }

  @Test
  fun `снуз-коды различимы между будильниками`() {
    val seen = HashSet<Int>()
    for (id in 1L..100_000L) {
      assertTrue("коллизия снуза для id=$id", seen.add(AlarmRequestCodes.snooze(id)))
    }
  }
}
