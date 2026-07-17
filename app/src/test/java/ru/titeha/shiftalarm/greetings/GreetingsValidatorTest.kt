package ru.titeha.shiftalarm.greetings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.MonthDay

class GreetingsValidatorTest {

  private fun ds(vararg holidays: Holiday, phrases: List<Phrase> = listOf(Phrase("Ф"))) =
    GreetingsDataset(holidays.toList(), phrases)

  private fun ok(id: String) =
    Holiday(id, "Имя", HolidayKind.FUN, "описание", fixed = MonthDay.of(1, 1))

  @Test
  fun `корректный датасет — проблем нет`() {
    val problems = GreetingsValidator.validate(
      ds(
        ok("a"),
        Holiday("b", "Б", HolidayKind.PROFESSIONAL, "описание",
          rule = HolidayRule(8, DayOfWeek.SUNDAY, -1)),
      )
    )
    assertEquals(emptyList<String>(), problems)
  }

  @Test
  fun `дублирующийся id ловится`() {
    val problems = GreetingsValidator.validate(ds(ok("x"), ok("x")))
    assertTrue(problems.any { it.contains("Дублирующийся id: x") })
  }

  @Test
  fun `пустые name и description ловятся`() {
    val h = Holiday("e", "", HolidayKind.FUN, "", fixed = MonthDay.of(2, 2))
    val problems = GreetingsValidator.validate(ds(h))
    assertTrue(problems.any { it.contains("Пустое name") })
    assertTrue(problems.any { it.contains("Пустое description") })
  }

  @Test
  fun `и date и rule одновременно — ошибка`() {
    val h = Holiday("both", "Оба", HolidayKind.FUN, "описание",
      fixed = MonthDay.of(1, 1), rule = HolidayRule(1, DayOfWeek.MONDAY, 1))
    assertTrue(GreetingsValidator.validate(ds(h)).any { it.contains("ровно одно из date/rule") })
  }

  @Test
  fun `ни date ни rule — ошибка`() {
    val h = Holiday("none", "Никак", HolidayKind.FUN, "описание")
    assertTrue(GreetingsValidator.validate(ds(h)).any { it.contains("ровно одно из date/rule") })
  }

  @Test
  fun `ordinal вне диапазона ловится`() {
    val h = Holiday("ord", "Ord", HolidayKind.FUN, "описание",
      rule = HolidayRule(6, DayOfWeek.SUNDAY, 5))
    val problems = GreetingsValidator.validate(ds(h))
    assertTrue(problems.any { it.contains("ordinal вне") })
  }

  @Test
  fun `неразрешимое правило (5-е воскресенье) ловится`() {
    // ordinal 5 отсекается проверкой диапазона; проверим неразрешимость отдельно допустимым -1
    // не выйдет (последний всегда есть), поэтому берём ordinal=5: попадает и в «вне диапазона»,
    // и в «неразрешимо в год».
    val h = Holiday("fifth", "Пятое", HolidayKind.FUN, "описание",
      rule = HolidayRule(6, DayOfWeek.SUNDAY, 5))
    val problems = GreetingsValidator.validate(ds(h))
    assertTrue(problems.any { it.contains("неразрешимо") })
  }

  @Test
  fun `пустой text у фразы ловится`() {
    val problems = GreetingsValidator.validate(ds(ok("a"), phrases = listOf(Phrase(""))))
    assertTrue(problems.any { it.contains("Пустой text") })
  }
}
