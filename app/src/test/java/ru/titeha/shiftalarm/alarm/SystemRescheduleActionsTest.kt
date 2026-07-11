package ru.titeha.shiftalarm.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemRescheduleActionsTest {
  @Test
  fun `перезагрузка устройства требует перепланирования`() {
    assertTrue(
      SystemRescheduleActions.shouldReschedule(
        SystemRescheduleActions.BOOT_COMPLETED
      )
    )
  }

  @Test
  fun `обновление приложения требует перепланирования`() {
    assertTrue(
      SystemRescheduleActions.shouldReschedule(
        SystemRescheduleActions.MY_PACKAGE_REPLACED
      )
    )
  }

  @Test
  fun `изменение времени требует перепланирования`() {
    assertTrue(
      SystemRescheduleActions.shouldReschedule(
        SystemRescheduleActions.TIME_CHANGED
      )
    )
  }

  @Test
  fun `изменение часового пояса требует перепланирования`() {
    assertTrue(
      SystemRescheduleActions.shouldReschedule(
        SystemRescheduleActions.TIMEZONE_CHANGED
      )
    )
  }

  @Test
  fun `изменение разрешения точных будильников требует перепланирования`() {
    assertTrue(
      SystemRescheduleActions.shouldReschedule(
        SystemRescheduleActions.EXACT_ALARM_PERMISSION_CHANGED
      )
    )
    assertEquals(
      "изменение разрешения точных будильников",
      SystemRescheduleActions.reasonOf(SystemRescheduleActions.EXACT_ALARM_PERMISSION_CHANGED)
    )
  }

  @Test
  fun `пустое действие не требует перепланирования`() {
    assertFalse(SystemRescheduleActions.shouldReschedule(null))
  }

  @Test
  fun `неизвестное действие не требует перепланирования`() {
    assertFalse(
      SystemRescheduleActions.shouldReschedule(
        "ru.titeha.shiftalarm.UNKNOWN_ACTION"
      )
    )
  }

  @Test
  fun `причина перезагрузки читаема для логов`() {
    assertEquals(
      "перезагрузка устройства",
      SystemRescheduleActions.reasonOf(SystemRescheduleActions.BOOT_COMPLETED)
    )
  }

  @Test
  fun `причина обновления приложения читаема для логов`() {
    assertEquals(
      "обновление приложения",
      SystemRescheduleActions.reasonOf(SystemRescheduleActions.MY_PACKAGE_REPLACED)
    )
  }

  @Test
  fun `причина изменения времени читаема для логов`() {
    assertEquals(
      "изменение системного времени",
      SystemRescheduleActions.reasonOf(SystemRescheduleActions.TIME_CHANGED)
    )
  }

  @Test
  fun `причина изменения часового пояса читаема для логов`() {
    assertEquals(
      "изменение часового пояса",
      SystemRescheduleActions.reasonOf(SystemRescheduleActions.TIMEZONE_CHANGED)
    )
  }

  @Test
  fun `неизвестная причина читаема для логов`() {
    assertEquals(
      "неизвестное событие",
      SystemRescheduleActions.reasonOf("unknown")
    )
  }
}