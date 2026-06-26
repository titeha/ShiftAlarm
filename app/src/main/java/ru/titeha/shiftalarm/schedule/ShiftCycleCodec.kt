package ru.titeha.shiftalarm.schedule

import java.time.LocalTime

/**
 * Сериализация произвольного цикла смен (списка слотов [ShiftType]) в одну строку и обратно —
 * для хранения в колонке БД `alarms.cycleSpec`. Чистая логика, без Android: покрыта юнит-тестами.
 *
 * Формат: слоты разделены '\n'. Каждый слот — `<head>|<name>`, где
 *  - `<head>` = `off` для выходного (без звонка) либо `HHMM` (4 цифры) — время побудки;
 *  - `<name>` — ярлык слота, в нём экранируются `\`, `|` и перевод строки.
 *
 * `head` не содержит `|`/`\`, поэтому первый `|` в строке слота — всегда разделитель.
 */
object ShiftCycleCodec {

  fun encode(slots: List<ShiftType>): String =
    slots.joinToString("\n") { slot ->
      val head = slot.wakeTime?.let { "%02d%02d".format(it.hour, it.minute) } ?: "off"
      "$head|${escape(slot.name)}"
    }

  /** Разбирает строку в слоты. Пустая строка → пустой список. Несовместимый ввод → исключение. */
  fun decode(spec: String): List<ShiftType> {
    if (spec.isEmpty()) return emptyList()
    return spec.split("\n").mapIndexed { i, line ->
      val sep = line.indexOf('|')
      require(sep >= 0) { "Слот без разделителя: '$line'" }
      val head = line.substring(0, sep)
      val name = unescape(line.substring(sep + 1))
      val wake = if (head == "off") null
      else LocalTime.of(head.substring(0, 2).toInt(), head.substring(2, 4).toInt())
      ShiftType(id = "c$i", name = name, wakeTime = wake)
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
