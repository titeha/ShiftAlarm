package ru.titeha.shiftalarm.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Golden-тесты матрицы «личная неделя × госкалендарь» (§2/§5 docs/WORKWEEK_AND_HOLIDAYS.md) на
 * реальной неделе 2026-06-22 (Пн) … 2026-06-28 (Вс).
 *
 * После TC02 (Q4=4a) правило переносов считается от ГЛОБАЛЬНОЙ рабочей недели пользователя:
 * сценарии «своя неделя» передают её в конструктор резолвера явно вторым аргументом
 * [work]/[rest]; переносы применяются только когда глобальная неделя равна стандартной.
 */
class PersonalDayResolverTest {

  private val mon = LocalDate.of(2026, 6, 22)
  private val tue = LocalDate.of(2026, 6, 23)
  private val wed = LocalDate.of(2026, 6, 24)
  private val thu = LocalDate.of(2026, 6, 25)
  private val fri = LocalDate.of(2026, 6, 26)
  private val sat = LocalDate.of(2026, 6, 27)
  private val sun = LocalDate.of(2026, 6, 28)

  private fun week(vararg days: DayOfWeek) = WorkWeek(days.toSet())

  private fun cal(vararg pairs: Pair<LocalDate, StateDayKind>) =
    ProductionCalendar(kinds = mapOf(*pairs))

  private fun work(
    calendar: ProductionCalendar?,
    globalWeek: WorkWeek = CountryProfile.RU.standardWeek,
  ) = PersonalDayResolver(CountryProfile.RU, calendar, AlarmPolarity.WORK, globalWeek)

  private fun rest(
    calendar: ProductionCalendar?,
    globalWeek: WorkWeek = CountryProfile.RU.standardWeek,
  ) = PersonalDayResolver(CountryProfile.RU, calendar, AlarmPolarity.REST, globalWeek)

  private val fiveDay = week(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
  )
  private val sixDay = week(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
  )
  private val fourDay = week(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY
  )

  @Test
  fun `1 пятидневка — рабочая суббота звонит, перенос-выходной глушит`() {
    val r = work(cal(sat to StateDayKind.TRANSFER_WORK, wed to StateDayKind.TRANSFER_OFF))
    // Стандартная неделя следует переносам: рабочая суббота → звонит (хотя суббота не в маске).
    assertTrue(r.ringOn(sat, fiveDay))
    // Перенесённый на будень выходной → тишина.
    assertFalse(r.ringOn(wed, fiveDay))
    // Обычный рабочий будень — звонит.
    assertTrue(r.ringOn(mon, fiveDay))
  }

  @Test
  fun `2 шестидневка — праздник на субботе глушит, обычная и рабочая суббота звонят`() {
    // Своя неделя (Пн–Сб) — и как маска, и как глобальная: переносы не применяются,
    // суббота — личный рабочий день.
    assertFalse(work(cal(sat to StateDayKind.HOLIDAY), sixDay).ringOn(sat, sixDay))   // праздник — тишина
    assertTrue(work(ProductionCalendar(), sixDay).ringOn(sat, sixDay))               // обычная суббота — звонит
    assertTrue(work(cal(sat to StateDayKind.TRANSFER_WORK), sixDay).ringOn(sat, sixDay)) // рабочая суббота — просто звонит
    assertFalse(work(ProductionCalendar(), sixDay).ringOn(sun, sixDay))             // воскресенье не в неделе — тишина
  }

  @Test
  fun `3 четырёхдневка — перенос-рабочая суббота молчит, праздник во вторник глушит`() {
    val r = work(cal(sat to StateDayKind.TRANSFER_WORK, tue to StateDayKind.HOLIDAY), fourDay)
    assertFalse(r.ringOn(sat, fourDay))  // переносы не применимы к своей неделе, суббота не в маске
    assertFalse(r.ringOn(tue, fourDay))  // праздник глушит личный рабочий день
    assertFalse(r.ringOn(fri, fourDay))  // пятница не в четырёхдневке
    assertTrue(r.ringOn(thu, fourDay))   // четверг — рабочий
  }

  @Test
  fun `4 странная неделя Вт Чт Сб — праздники глушат, переносы игнорируются`() {
    val oddWeek = week(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)
    val r = work(cal(tue to StateDayKind.HOLIDAY, mon to StateDayKind.TRANSFER_WORK), oddWeek)
    assertFalse(r.ringOn(tue, oddWeek))  // праздник глушит
    assertFalse(r.ringOn(mon, oddWeek))  // перенос-рабочий понедельник игнорируется (не в неделе)
    assertTrue(r.ringOn(thu, oddWeek))   // обычный личный день
    assertTrue(r.ringOn(sat, oddWeek))   // обычный личный день
  }

  @Test
  fun `5 по выходным для шестидневки — звонит Вс и в праздник, молчит Пн-Сб`() {
    val r = rest(cal(wed to StateDayKind.HOLIDAY), sixDay)
    assertTrue(r.ringOn(sun, sixDay))   // воскресенье — единственный личный выходной
    assertTrue(r.ringOn(wed, sixDay))   // праздник среди недели — звонит (по выходным)
    assertFalse(r.ringOn(mon, sixDay))  // рабочий — молчит
    assertFalse(r.ringOn(sat, sixDay))  // суббота рабочая при шестидневке — молчит
  }

  @Test
  fun `6 праздники выключены — чистое по галочкам`() {
    // calendar == null: матрица схлопывается в личную неделю.
    assertTrue(work(null).ringOn(sat, sixDay))    // суббота отмечена
    assertFalse(work(null).ringOn(sun, sixDay))   // воскресенье не отмечено
    assertTrue(work(null).ringOn(mon, fiveDay))
    assertFalse(work(null).ringOn(sat, fiveDay))
  }

  @Test
  fun `7 UNKNOWN ведёт себя как обычный день`() {
    val r = work(cal(wed to StateDayKind.UNKNOWN))
    assertTrue(r.ringOn(wed, fiveDay))  // как NONE — по личной неделе
  }

  @Test
  fun `WorkWeek fromMask собирает дни из маски`() {
    val mask = AlarmTimes.maskOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
    assertEquals(
      WorkWeek(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
      WorkWeek.fromMask(mask)
    )
  }
}
