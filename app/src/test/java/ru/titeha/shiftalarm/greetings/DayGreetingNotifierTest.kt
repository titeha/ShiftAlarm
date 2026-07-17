package ru.titeha.shiftalarm.greetings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.titeha.shiftalarm.alarm.AlarmService
import java.time.LocalDate
import java.time.MonthDay

class DayGreetingNotifierTest {

  private val today = LocalDate.of(2026, 1, 1)
  private val todayEpoch = today.toEpochDay()
  private val nonEmpty = Greeting(
    holidays = listOf(Holiday("h", "Праздник", HolidayKind.STATE, "описание", fixed = MonthDay.of(1, 1))),
    phrase = Phrase("Фраза"),
  )

  @Test fun `показываем — включено, сегодня ещё не показывали, есть что`() =
    assertTrue(DayGreetingNotifier.shouldPost(true, today, -1L, nonEmpty))

  @Test fun `не показываем, если выключено`() =
    assertFalse(DayGreetingNotifier.shouldPost(false, today, -1L, nonEmpty))

  @Test fun `не показываем повторно за день (первый Стоп уже был)`() =
    assertFalse(DayGreetingNotifier.shouldPost(true, today, todayEpoch, nonEmpty))

  @Test fun `не показываем пустой день`() =
    assertFalse(DayGreetingNotifier.shouldPost(true, today, -1L, Greeting(emptyList(), null)))

  @Test fun `канал Настроения дня отдельный от канала будильника`() {
    // Мьют канала «Настроение дня» не должен влиять на звонок — значит id обязан отличаться.
    assertEquals("day_greeting", DayGreetingNotifier.CHANNEL_ID)
    assertNotEquals(AlarmService.CHANNEL_ID, DayGreetingNotifier.CHANNEL_ID)
  }
}
