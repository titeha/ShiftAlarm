package ru.titeha.shiftalarm.schedule

import java.time.LocalDate

/**
 * Встроенные производственные календари по стране/году. Пока только РФ; расширяется в модуле
 * «календарь по странам» (Казахстан → …).
 *
 * ВАЖНО про данные: нерабочие праздники из ТК РФ (ст. 112) стабильны из года в год и заданы
 * надёжно. Переносы выходных (перенесённые на будни дни отдыха и «рабочие субботы») задаются
 * ежегодным постановлением Правительства — их НУЖНО СВЕРЯТЬ с официальным календарём каждого года.
 * Сейчас включены только очевидные переносы; полный набор — доводка (в т.ч. при добавлении стран).
 */
object ProductionCalendars {

  private fun d(year: Int, month: Int, day: Int) = LocalDate.of(year, month, day)

  /**
   * РФ, 2026. Фиксированные нерабочие праздники (ТК ст. 112) + очевидные переносы дней отдыха
   * с выпавших на выходные праздников (8 марта — Вс → 9 марта; 9 мая — Сб → 11 мая).
   * «Рабочие субботы» (workingWeekends) не заданы — сверить с постановлением на 2026.
   */
  val RU_2026 = ProductionCalendar(
    holidays = setOf(
      // Новогодние каникулы и Рождество.
      d(2026, 1, 1), d(2026, 1, 2), d(2026, 1, 3), d(2026, 1, 4),
      d(2026, 1, 5), d(2026, 1, 6), d(2026, 1, 7), d(2026, 1, 8),
      d(2026, 2, 23),           // День защитника Отечества (Пн)
      d(2026, 3, 8),            // Международный женский день (Вс)
      d(2026, 3, 9),            // перенос выходного с 8 марта
      d(2026, 5, 1),            // Праздник Весны и Труда (Пт)
      d(2026, 5, 9),            // День Победы (Сб)
      d(2026, 5, 11),           // перенос выходного с 9 мая
      d(2026, 6, 12),           // День России (Пт)
      d(2026, 11, 4)            // День народного единства (Ср)
    )
  )

  /** Календарь по коду страны и году; null — данных нет. Сейчас только "RU"/2026. */
  fun of(country: String, year: Int): ProductionCalendar? = when {
    country.equals("RU", ignoreCase = true) && year == 2026 -> RU_2026
    else -> null
  }

  /**
   * Встроенный календарь по коду страны (последний доступный набор данных). Пока только РФ.
   * Даты вне известных годов трактуются лишь по правилу Сб/Вс (без праздников) — доводка данных
   * и онлайн-обновление придут отдельным слоем.
   */
  fun bundled(country: String): ProductionCalendar? =
    if (country.equals("RU", ignoreCase = true)) RU_2026 else null

  /**
   * Парсер ответа isDayOff.ru (`GET https://isdayoff.ru/api/getdata?year=YYYY&cc=ru`): строка из
   * цифр по одной на КАЖДЫЙ день года по порядку (1 января → 31 декабря). Коды: `0` рабочий,
   * `1` нерабочий (выходной/праздник), `2` сокращённый предпраздничный (рабочий), `4` рабочий день
   * (перенос). Всё, кроме `1`, считаем рабочим.
   *
   * Чистая функция (без сети): переводит строку в [ProductionCalendar] относительно нашей модели —
   * будни-нерабочие уходят в `holidays`, рабочие Сб/Вс — в `workingWeekends`. Сетевой запрос и кэш —
   * отдельный слой.
   *
   * @throws IllegalArgumentException если длина не совпадает с числом дней в году (защита от мусора).
   */
  fun fromIsDayOff(year: Int, data: String): ProductionCalendar {
    val digits = data.trim()
    val start = LocalDate.of(year, 1, 1)
    val daysInYear = if (start.isLeapYear) 366 else 365
    require(digits.length == daysInYear) {
      "isDayOff: ожидалось $daysInYear цифр для $year, пришло ${digits.length}"
    }
    val holidays = mutableSetOf<LocalDate>()
    val workingWeekends = mutableSetOf<LocalDate>()
    var date = start
    for (ch in digits) {
      val nonWorking = ch == '1'
      val isWeekend = date.dayOfWeek == java.time.DayOfWeek.SATURDAY ||
        date.dayOfWeek == java.time.DayOfWeek.SUNDAY
      if (nonWorking && !isWeekend) holidays += date       // праздник/перенос на будень
      if (!nonWorking && isWeekend) workingWeekends += date // рабочая суббота/воскресенье
      date = date.plusDays(1)
    }
    return ProductionCalendar(holidays, workingWeekends)
  }
}
