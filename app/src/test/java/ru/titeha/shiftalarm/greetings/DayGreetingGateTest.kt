package ru.titeha.shiftalarm.greetings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.MonthDay

class DayGreetingGateTest {

  private val today = LocalDate.of(2026, 1, 1)
  private val todayEpoch = today.toEpochDay()
  private val nonEmpty = Greeting(
    holidays = listOf(Holiday("h", "Праздник", HolidayKind.FUN, "описание", fixed = MonthDay.of(1, 1))),
    phrase = Phrase("Фраза"),
  )
  private val empty = Greeting(emptyList(), null)

  private fun show(
    cardEnabled: Boolean = true,
    lastDismissed: Long = todayEpoch,
    handled: Long = -1L,
    greeting: Greeting = nonEmpty,
  ) = DayGreetingGate.shouldShowCard(cardEnabled, today, lastDismissed, handled, greeting)

  @Test fun `показываем после Стоп сегодня`() = assertTrue(show())

  @Test fun `не показываем, если опция выключена`() = assertFalse(show(cardEnabled = false))

  @Test fun `не показываем без выключения будильника сегодня`() {
    assertFalse(show(lastDismissed = -1L))                 // сигнала не было
    assertFalse(show(lastDismissed = todayEpoch - 1))      // выключали вчера
  }

  @Test fun `не показываем, если карточку уже закрыли сегодня`() =
    assertFalse(show(handled = todayEpoch))

  @Test fun `не показываем пустой день`() = assertFalse(show(greeting = empty))
}
