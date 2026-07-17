package ru.titeha.shiftalarm.greetings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.MonthDay

class DayGreetingResolverTest {

  private fun fixed(id: String, mmdd: MonthDay, kind: HolidayKind = HolidayKind.FUN) =
    Holiday(id, id, kind, "описание", fixed = mmdd)

  private fun ruled(id: String, rule: HolidayRule, kind: HolidayKind = HolidayKind.PROFESSIONAL) =
    Holiday(id, id, kind, "описание", rule = rule)

  private val phrases = (0..4).map { Phrase("Фраза $it") }

  @Test
  fun `фиксированный праздник выпадает только на свою дату`() {
    val h = fixed("cat", MonthDay.of(3, 1))
    val r = DayGreetingResolver(GreetingsDataset(listOf(h), phrases))
    assertEquals(listOf(h), r.forDate(LocalDate.of(2026, 3, 1)).holidays)
    assertTrue(r.forDate(LocalDate.of(2026, 3, 2)).holidays.isEmpty())
  }

  @Test
  fun `29 февраля показывается только в високосный год`() {
    val h = fixed("leap", MonthDay.of(2, 29))
    assertTrue(h.occursOn(LocalDate.of(2028, 2, 29)))   // 2028 — високосный
    assertFalse(h.occursOn(LocalDate.of(2027, 2, 28)))  // 2027 — нет 29 февраля
    assertFalse(h.occursOn(LocalDate.of(2027, 3, 1)))
  }

  @Test
  fun `правило последнее воскресенье августа = 30 августа 2026`() {
    val rule = HolidayRule(month = 8, dayOfWeek = DayOfWeek.SUNDAY, ordinal = -1)
    assertEquals(LocalDate.of(2026, 8, 30), rule.resolve(2026))
    val h = ruled("miner", rule)
    assertTrue(h.occursOn(LocalDate.of(2026, 8, 30)))
    assertFalse(h.occursOn(LocalDate.of(2026, 8, 23)))
  }

  @Test
  fun `правило первый понедельник разрешимо, пятое воскресенье — нет`() {
    assertEquals(
      LocalDate.of(2026, 6, 1), // 1 июня 2026 — понедельник
      HolidayRule(6, DayOfWeek.MONDAY, 1).resolve(2026)
    )
    // ordinal вне 1..4 и не -1 → неразрешимо (валидатор такое отсекает, резолвер не падает).
    assertNull(HolidayRule(6, DayOfWeek.SUNDAY, 5).resolve(2026))
  }

  @Test
  fun `праздники дня отсортированы по приоритету категории`() {
    val day = MonthDay.of(5, 1)
    val funH = fixed("fun", day, HolidayKind.FUN)
    val stateH = fixed("state", day, HolidayKind.STATE)
    val intlH = fixed("intl", day, HolidayKind.INTERNATIONAL)
    // В датасете порядок «перемешан» — резолвер должен упорядочить STATE > INTERNATIONAL > FUN.
    val r = DayGreetingResolver(GreetingsDataset(listOf(funH, intlH, stateH), phrases))
    val holidays = r.forDate(LocalDate.of(2026, 5, 1)).holidays
    assertEquals(listOf(stateH, intlH, funH), holidays)
  }

  @Test
  fun `фраза дня детерминирована датой (golden hashCode, список из 5)`() {
    val r = DayGreetingResolver(GreetingsDataset(emptyList(), phrases))
    // Golden: Java String.hashCode стабилен; floorMod по размеру 5.
    assertEquals(phrases[0], r.phraseFor(LocalDate.of(2026, 1, 1)))
    assertEquals(phrases[4], r.phraseFor(LocalDate.of(2026, 8, 30)))
    assertEquals(phrases[0], r.phraseFor(LocalDate.of(2026, 12, 31)))
    // Одна и та же дата — всегда одна и та же фраза.
    assertEquals(
      r.phraseFor(LocalDate.of(2026, 6, 15)),
      r.phraseFor(LocalDate.of(2026, 6, 15))
    )
  }

  @Test
  fun `пустой список фраз — фразы нет, день без праздников пуст`() {
    val r = DayGreetingResolver(GreetingsDataset(emptyList(), emptyList()))
    val g = r.forDate(LocalDate.of(2026, 4, 10))
    assertNull(g.phrase)
    assertTrue(g.holidays.isEmpty())
    assertTrue(g.isEmpty)
  }
}
