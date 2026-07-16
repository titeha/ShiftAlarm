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
   * Внешний источник календарей (локальный кэш, обновляемый с офиц. ресурса). Устанавливается
   * Android-слоем при старте приложения. По умолчанию null — движок работает на встроенных данных
   * (и остаётся чисто тестируемым). Читается из фонового планировщика, поэтому `@Volatile`.
   */
  @Volatile
  var source: ((country: String, year: Int) -> ProductionCalendar?)? = null

  /**
   * Глобальная «рабочая неделя» (сколько дней рабочих + начало недели). Устанавливается Android-слоем
   * из настроек при старте и при изменении настройки. По умолчанию [WorkWeek.DEFAULT] (5/Пн = Сб/Вс
   * выходные) — движок остаётся чисто тестируемым. Применяется ко всем выдаваемым календарям
   * ([resolve]/[merged]). Читается из фонового планировщика, поэтому `@Volatile`.
   *
   * NB (v1): неделя задаёт лишь БАЗОВЫЙ выходной поверх праздничного календаря страны. Праздники и
   * переносы берутся из RU-фида в его Сб/Вс-рамке, поэтому при 6-дневке праздник, выпавший на субботу,
   * может «потеряться». Приемлемо для v1; глубокая переклассификация дней — отдельная задача.
   */
  @Volatile
  var workWeek: WorkWeek = WorkWeek.DEFAULT

  /**
   * Календарь ИМЕННО для [country]/[year]: внешний источник (кэш) → встроенные данные этого года.
   * null — данных для этого года нет (НЕ подставляем календарь другого года — праздники не совпадут).
   * Синхронно и оффлайн-безопасно (сеть не трогается). К результату применяется текущая [workWeek].
   */
  fun resolve(country: String, year: Int): ProductionCalendar? =
    (source?.invoke(country, year) ?: of(country, year))?.withWorkWeek(workWeek)

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
        // a/b уже несут текущую workWeek (из resolve); новый объединённый строим с ней же.
        ProductionCalendar(a.holidays + b.holidays, a.workingWeekends + b.workingWeekends, workWeek)
      else -> a ?: b
    }
  }

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
    // Каждый символ — код дня (0 рабочий, 1 нерабочий, 2 сокращённый, 4 перенос). Иначе это не
    // календарь, а мусор/код ошибки нужной длины — отвергаем, чтобы не принять его за данные.
    require(digits.all { it in "0124" }) {
      "isDayOff: недопустимые символы в ответе для $year"
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
