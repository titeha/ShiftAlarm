package ru.titeha.shiftalarm.schedule

import java.time.LocalTime

/**
 * Сериализация произвольного цикла смен (списка слотов [ShiftType]) в одну строку и обратно —
 * для хранения в колонке БД `alarms.cycleSpec`. Чистая логика, без Android: покрыта юнит-тестами.
 *
 * Формат слота: `<cat>|<time>|<name>`, слоты разделены '\n'.
 *  - `<cat>` — категория: `M`/`D`/`N`/`O` (утро/день/ночь/выходной);
 *  - `<time>` — `HHMM` (4 цифры) время звонка либо пусто (без звонка);
 *  - `<name>` — ярлык слота; экранируются `\`, `|` и перевод строки.
 *
 * Обратная совместимость: старый 2-польный формат `off|<name>` / `HHMM|<name>` (без категории)
 * читается, категория выводится из времени. `cat`/`time` не содержат `|`/`\`, поэтому ведущие
 * `|` — разделители полей.
 */
object ShiftCycleCodec {

  private fun catCode(c: ShiftCategory) = when (c) {
    ShiftCategory.MORNING -> "M"
    ShiftCategory.DAY -> "D"
    ShiftCategory.NIGHT -> "N"
    ShiftCategory.OFF -> "O"
  }

  private fun catFromCode(s: String): ShiftCategory? = when (s) {
    "M" -> ShiftCategory.MORNING
    "D" -> ShiftCategory.DAY
    "N" -> ShiftCategory.NIGHT
    "O" -> ShiftCategory.OFF
    else -> null
  }

  private fun parseTime(s: String): LocalTime? =
    if (s.isEmpty()) null else LocalTime.of(s.substring(0, 2).toInt(), s.substring(2, 4).toInt())

  fun encode(slots: List<ShiftType>): String =
    slots.joinToString("\n") { slot ->
      val time = slot.wakeTime?.let { "%02d%02d".format(it.hour, it.minute) } ?: ""
      "${catCode(slot.category)}|$time|${escape(slot.name)}"
    }

  /** Разбирает строку в слоты. Пустая строка → пустой список. Несовместимый ввод → исключение. */
  fun decode(spec: String): List<ShiftType> {
    if (spec.isEmpty()) return emptyList()
    return spec.split("\n").mapIndexed { i, line ->
      val sep = line.indexOf('|')
      require(sep >= 0) { "Слот без разделителя: '$line'" }
      val first = line.substring(0, sep)
      val rest = line.substring(sep + 1)
      val cat = catFromCode(first)
      if (cat != null) {
        // Новый формат: cat | time | name
        val sep2 = rest.indexOf('|')
        require(sep2 >= 0) { "Слот без времени: '$line'" }
        val wake = parseTime(rest.substring(0, sep2))
        val name = unescape(rest.substring(sep2 + 1))
        ShiftType(id = "c$i", name = name, wakeTime = wake, category = cat)
      } else {
        // Старый формат: head(off/HHMM) | name — категория выводится из времени.
        val wake = if (first == "off") null else parseTime(first)
        ShiftType(id = "c$i", name = unescape(rest), wakeTime = wake)
      }
    }
  }

  private fun escape(s: String): String = buildString {
    for (ch in s) when (ch) {
      '\\' -> append("\\\\")
      '|' -> append("\\|")
      '\n' -> append("\\n")
      else -> append(ch)
    }
  }

  private fun unescape(s: String): String = buildString {
    var i = 0
    while (i < s.length) {
      val ch = s[i]
      if (ch == '\\' && i + 1 < s.length) {
        when (s[i + 1]) {
          '\\' -> append('\\')
          '|' -> append('|')
          'n' -> append('\n')
          else -> append(s[i + 1])
        }
        i += 2
      } else {
        append(ch)
        i++
      }
    }
  }
}
