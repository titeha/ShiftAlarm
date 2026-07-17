package ru.titeha.shiftalarm.greetings

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.TemporalAdjusters

/**
 * «Настроение дня»: чистая модель праздника дня и фразы дня (без Android, без JSON).
 *
 * Модуль ЧИСТО ИНФОРМАЦИОННЫЙ: он ничего не глушит и не будит, к производственному календарю
 * (праздники как выходные) отношения не имеет. Разбор assets/JSON — отдельным Android-слоем;
 * сюда попадает уже готовый [GreetingsDataset], поэтому логика тестируется обычными юнит-тестами.
 */

/** Категория праздника. Порядок объявления = приоритет показа (STATE — самый высокий). */
enum class HolidayKind { STATE, PROFESSIONAL, INTERNATIONAL, FUN }

/**
 * Плавающая дата праздника: [ordinal]-й [dayOfWeek] месяца [month].
 * [ordinal] — 1..4 (первый–четвёртый) или -1 (последний такой день месяца).
 * Пример: последнее воскресенье августа (День шахтёра) = month=8, SUNDAY, ordinal=-1.
 */
data class HolidayRule(val month: Int, val dayOfWeek: DayOfWeek, val ordinal: Int) {
  /**
   * Конкретная дата правила в [year], либо null, если правило неразрешимо в этом году
   * (например, «5-го воскресенья» не бывает). Валидатор датасета отсекает такие правила заранее.
   */
  fun resolve(year: Int): LocalDate? {
    if (month !in 1..12) return null
    val first = LocalDate.of(year, month, 1)
    if (ordinal == -1) return first.with(TemporalAdjusters.lastInMonth(dayOfWeek))
    if (ordinal !in 1..4) return null
    val nth = first.with(TemporalAdjusters.dayOfWeekInMonth(ordinal, dayOfWeek))
    // dayOfWeekInMonth при переполнении уводит в следующий месяц — считаем неразрешимым.
    return if (nth.monthValue == month) nth else null
  }
}

/**
 * Праздник. Дата задаётся РОВНО одним способом: фиксированная [fixed] («MM-DD») ИЛИ [rule].
 * `MonthDay(2, 29)` допустим — [occursOn] вернёт true только в високосные годы.
 */
data class Holiday(
  val id: String,
  val name: String,
  val kind: HolidayKind,
  val description: String,
  val fixed: MonthDay? = null,
  val rule: HolidayRule? = null,
) {
  /** Выпадает ли праздник на календарный день [date]. */
  fun occursOn(date: LocalDate): Boolean = when {
    fixed != null -> fixed == MonthDay.from(date)
    rule != null -> rule.resolve(date.year) == date
    else -> false
  }
}

/** Фраза дня: пословица/цитата из общественного достояния; [author] опционален. */
data class Phrase(val text: String, val author: String? = null)

/** Результат на день: все праздники дня (по приоритету) + фраза дня (или null, если фраз нет). */
data class Greeting(val holidays: List<Holiday>, val phrase: Phrase?) {
  /** Есть ли что показать (хоть праздник, хоть фраза). */
  val isEmpty: Boolean get() = holidays.isEmpty() && phrase == null
}

/** Разобранный датасет «Настроения дня» (обычно из JSON в assets/greetings). */
data class GreetingsDataset(val holidays: List<Holiday>, val phrases: List<Phrase>)
