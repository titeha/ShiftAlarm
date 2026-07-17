package ru.titeha.shiftalarm.greetings

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Проверка реального датасета `assets/greetings` (holidays_ru.json, phrases_ru.json): он парсится (`org.json`), проходит
 * валидатор и корректно резолвится движком. Инструментальный — нужен доступ к assets приложения.
 * Гоняется на устройстве: `./gradlew connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class GreetingsAssetsTest {

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  @Test
  fun realAssets_parse_andPassValidator() {
    val dataset = GreetingsLoader.fromAssets(context, "ru")
    assertTrue("праздники не должны быть пустыми", dataset.holidays.isNotEmpty())
    assertTrue("фразы не должны быть пустыми", dataset.phrases.isNotEmpty())

    val problems = GreetingsValidator.validate(dataset)
    assertEquals("датасет должен быть валиден, а нашли: $problems", emptyList<String>(), problems)
  }

  @Test
  fun realAssets_resolver_findsKnownHolidays() {
    val resolver = DayGreetingResolver(GreetingsLoader.fromAssets(context, "ru"))

    // Фиксированная дата: Новый год 1 января.
    assertTrue(
      resolver.forDate(LocalDate.of(2026, 1, 1)).holidays.any { it.id == "new-year" }
    )
    // Плавающая дата: последнее воскресенье августа 2026 = 30 августа (День шахтёра).
    assertTrue(
      resolver.forDate(LocalDate.of(2026, 8, 30)).holidays.any { it.id == "miner-day" }
    )
    // Обычный день без праздников из примера — праздников нет, но фраза дня есть.
    val plain = resolver.forDate(LocalDate.of(2026, 6, 17))
    assertTrue(plain.holidays.isEmpty())
    assertNotNull(plain.phrase)
  }
}
