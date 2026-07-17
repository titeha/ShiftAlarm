package ru.titeha.shiftalarm.greetings

/**
 * Проверка датасета «Настроения дня». Чистая (работает с уже разобранным [GreetingsDataset]),
 * поэтому тестируется обычными юнит-тестами; разбор JSON и его синтаксические ошибки — на слое
 * [GreetingsLoader] (падение разбора = невалидный JSON, ловится тестом на реальных assets).
 *
 * Возвращает список проблем; пустой список = датасет валиден.
 */
object GreetingsValidator {

  fun validate(dataset: GreetingsDataset, years: IntRange = 2026..2030): List<String> {
    val problems = mutableListOf<String>()
    val seenIds = mutableSetOf<String>()

    dataset.holidays.forEach { h ->
      if (h.id.isBlank()) problems += "Пустой id у праздника «${h.name}»"
      else if (!seenIds.add(h.id)) problems += "Дублирующийся id: ${h.id}"
      if (h.name.isBlank()) problems += "Пустое name у «${h.id}»"
      if (h.description.isBlank()) problems += "Пустое description у «${h.id}»"

      val hasFixed = h.fixed != null
      val hasRule = h.rule != null
      if (hasFixed == hasRule) {
        problems += "У «${h.id}» должно быть ровно одно из date/rule (сейчас: ${if (hasFixed) "оба" else "ни одного"})"
      }

      h.rule?.let { rule ->
        if (rule.ordinal != -1 && rule.ordinal !in 1..4) {
          problems += "У «${h.id}» ordinal вне {1..4, -1}: ${rule.ordinal}"
        }
        if (rule.month !in 1..12) problems += "У «${h.id}» month вне 1..12: ${rule.month}"
        // Правило должно разрешаться во все годы диапазона (нет «5-го воскресенья»).
        years.forEach { y ->
          if (rule.resolve(y) == null) problems += "Правило «${h.id}» неразрешимо в $y году"
        }
      }
    }

    dataset.phrases.forEachIndexed { i, p ->
      if (p.text.isBlank()) problems += "Пустой text у фразы #$i"
    }

    return problems
  }
}
