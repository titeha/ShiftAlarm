package ru.titeha.shiftalarm.greetings

import java.time.LocalDate

/**
 * Чистый резолвер «Настроения дня»: по дате отдаёт праздники дня и фразу дня.
 * Без Android и I/O — [dataset] уже разобран из assets Android-слоем.
 */
class DayGreetingResolver(private val dataset: GreetingsDataset) {

  /**
   * Праздники и фраза на [date].
   * - Праздники: все, что выпадают на день, по приоритету категории (STATE > PROFESSIONAL >
   *   INTERNATIONAL > FUN); внутри одной категории — в порядке датасета (сортировка стабильна).
   *   Карточка показывает первый, остальные — «и ещё N» в bottom sheet.
   * - Фраза: детерминирована датой (см. [phraseFor]).
   */
  fun forDate(date: LocalDate): Greeting {
    val holidays = dataset.holidays
      .filter { it.occursOn(date) }
      .sortedBy { it.kind.ordinal }
    return Greeting(holidays = holidays, phrase = phraseFor(date))
  }

  /**
   * Фраза дня, детерминированная датой: стабильный хэш строки `YYYY-MM-DD` по модулю размера
   * списка. `String.hashCode` в Java контрактно стабилен (одинаков на всех JVM/Android), поэтому
   * у всех пользователей в один день — одна и та же фраза. null, если фраз в датасете нет.
   */
  fun phraseFor(date: LocalDate): Phrase? {
    if (dataset.phrases.isEmpty()) return null
    val index = Math.floorMod(date.toString().hashCode(), dataset.phrases.size)
    return dataset.phrases[index]
  }
}
