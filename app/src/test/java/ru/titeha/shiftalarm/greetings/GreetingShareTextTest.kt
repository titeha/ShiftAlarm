package ru.titeha.shiftalarm.greetings

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.MonthDay

class GreetingShareTextTest {

  private fun holiday(name: String) =
    Holiday("id", name, HolidayKind.PROFESSIONAL, "описание", fixed = MonthDay.of(8, 30))

  @Test
  fun `праздник и фраза — формат из ТЗ`() {
    val g = Greeting(
      holidays = listOf(holiday("День шахтёра")),
      phrase = Phrase("Терпение и труд всё перетрут."),
    )
    assertEquals(
      "Сегодня — День шахтёра. „Терпение и труд всё перетрут.“ — Будильник работяги",
      GreetingShareText.build(g),
    )
  }

  @Test
  fun `только фраза — без строки про праздник`() {
    val g = Greeting(holidays = emptyList(), phrase = Phrase("Утро вечера мудренее."))
    assertEquals(
      "„Утро вечера мудренее.“ — Будильник работяги",
      GreetingShareText.build(g),
    )
  }

  @Test
  fun `фраза с автором`() {
    val g = Greeting(
      holidays = emptyList(),
      phrase = Phrase("Никто не обнимет необъятного.", author = "Козьма Прутков"),
    )
    assertEquals(
      "„Никто не обнимет необъятного.“ — Козьма Прутков — Будильник работяги",
      GreetingShareText.build(g),
    )
  }

  @Test
  fun `только праздник — без фразы`() {
    val g = Greeting(holidays = listOf(holiday("День шахтёра")), phrase = null)
    assertEquals("Сегодня — День шахтёра. — Будильник работяги", GreetingShareText.build(g))
  }
}
