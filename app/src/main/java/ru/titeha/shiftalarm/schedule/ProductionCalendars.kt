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
    kinds = buildMap {
      // Новогодние каникулы и Рождество — праздники.
      for (day in 1..8) put(d(2026, 1, day), StateDayKind.HOLIDAY)
      put(d(2026, 2, 23), StateDayKind.HOLIDAY)  // День защитника Отечества (Пн)
      put(d(2026, 3, 8), StateDayKind.HOLIDAY)   // Международный женский день (Вс)
      put(d(2026, 3, 9), StateDayKind.TRANSFER_OFF)  // перенос выходного с 8 марта
      put(d(2026, 5, 1), StateDayKind.HOLIDAY)   // Праздник Весны и Труда (Пт)
      put(d(2026, 5, 9), StateDayKind.HOLIDAY)   // День Победы (Сб)
      put(d(2026, 5, 11), StateDayKind.TRANSFER_OFF)  // перенос выходного с 9 мая
      put(d(2026, 6, 12), StateDayKind.HOLIDAY)  // День России (Пт)
      put(d(2026, 11, 4), StateDayKind.HOLIDAY)  // День народного единства (Ср)
    }
  )

  /** Календарь по коду страны и году; null — данных нет. Сейчас только "RU"/2026. */
  fun of(country: String, year: Int): ProductionCalendar? = when {
    country.equals("RU", ignoreCase = true) && year == 2026 -> RU_2026
    else -> null
  }

  /**
   * Внешний источник календарей (локальный кэш, обновляемый с офиц. ресурса). Устанавливается
   * Android-слоем при старте приложения. По умолчанию null — движок работает на встроенных данных
   * (и остаётся чисто тестируемым). Читается из фонового планировщика, поэтому `@Volatile`.
   */
  @Volatile
  var source: ((country: String, year: Int) -> ProductionCalendar?)? = null

  /**
   * Календарь ИМЕННО для [country]/[year]: внешний источник (кэш) → встроенные данные этого года.
   * null — данных для этого года нет (НЕ подставляем календарь другого года — праздники не совпадут).
   * Синхронно и оффлайн-безопасно (сеть не трогается).
   */
  fun resolve(country: String, year: Int): ProductionCalendar? =
    source?.invoke(country, year) ?: of(country, year)

  /**
   * Календарь, покрывающий [fromYear] и следующий год — для поиска ближайшего звонка, который может
   * уйти за границу года (декабрь → январь). Объединяет holidays/workingWeekends доступных лет (даты
   * разных лет не пересекаются, поэтому объединение корректно). Год без данных → его даты трактуются
   * лишь по обычным Сб/Вс. null — данных нет ни на один из двух лет.
   */
  fun merged(country: String, fromYear: Int): ProductionCalendar? {
    val a = resolve(country, fromYear)
    val b = resolve(country, fromYear + 1)
    return when {
      a != null && b != null ->
        ProductionCalendar(
          a.holidays + b.holidays,
          a.workingWeekends + b.workingWeekends,
          a.kinds + b.kinds,
        )
      else -> a ?: b
    }
  }

  /**
   * Парсер ответа isDayOff.ru (`GET https://isdayoff.ru/api/getdata?year=YYYY&cc=ru`): строка из
   * цифр по одной на КАЖДЫЙ день года по порядку (1 января → 31 декабря). Коды: `0` рабочий,
   * `1` нерабочий (выходной/праздник), `2` сокращённый предпраздничный (рабочий), `4` рабочий день
   * (перенос). Всё, кроме `1`, считаем рабочим.
   *
   * Чистая функция (без сети): переводит строку в типизированный [ProductionCalendar] — нерабочий
   * будень → [StateDayKind.HOLIDAY] (API не отличает праздник от перенесённого выходного — тонкий
   * TRANSFER_OFF приходит из встроенного списка), сокращённый (`2`) → [StateDayKind.PRE_HOLIDAY],
   * рабочий выходной → [StateDayKind.TRANSFER_WORK]. Сетевой запрос и кэш — отдельный слой.
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
    // Каждый символ — код дня (0 рабочий, 1 нерабочий, 2 сокращённый, 4 перенос). Иначе это не
    // календарь, а мусор/код ошибки нужной длины — отвергаем, чтобы не принять его за данные.
    require(digits.all { it in "0124" }) {
      "isDayOff: недопустимые символы в ответе для $year"
    }
    val kinds = mutableMapOf<LocalDate, StateDayKind>()
    var date = start
    for (ch in digits) {
      val isWeekend = date.dayOfWeek == java.time.DayOfWeek.SATURDAY ||
        date.dayOfWeek == java.time.DayOfWeek.SUNDAY
      when {
        // Нерабочий будень: праздник или перенесённый на будень выходной. API их не различает —
        // берём HOLIDAY (тонкий тип TRANSFER_OFF, если нужен, приходит из встроенного списка).
        ch == '1' -> if (!isWeekend) kinds[date] = StateDayKind.HOLIDAY
        // Рабочий выходной (перенос): любой рабочий код на Сб/Вс.
        isWeekend -> kinds[date] = StateDayKind.TRANSFER_WORK
        // Сокращённый предпраздничный будень (рабочий).
        ch == '2' -> kinds[date] = StateDayKind.PRE_HOLIDAY
        // '0'/'4' в будень — обычный рабочий день, особого типа нет.
      }
      date = date.plusDays(1)
    }
    return ProductionCalendar(kinds = kinds)
  }
}
