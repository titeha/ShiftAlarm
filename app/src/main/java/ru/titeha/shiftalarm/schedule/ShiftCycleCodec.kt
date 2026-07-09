package ru.titeha.shiftalarm.schedule

import java.time.LocalTime

/**
 * Сериализация произвольного цикла смен (списка слотов [ShiftType]) в одну строку и обратно —
 * для хранения в колонке БД `alarms.cycleSpec`. Чистая логика, без Android: покрыта юнит-тестами.
 *
 * Формат слота: `cat|time|name`, слоты разделены '\n'.
 * - `cat` — категория: `M`/`D`/`N`/`O` (утро/день/ночь/выходной);
 * - `time` — `HHMM` (4 цифры) время звонка либо пусто (без звонка);
 * - `name` — ярлык слота; экранируются `\`, `|` и перевод строки.
 *
 * Обратная совместимость: старый 2-польный формат `off|name` / `HHMM|name`
 * читается, категория выводится из времени.
 */
object ShiftCycleCodec {
  private fun catCode(category: ShiftCategory): String {
    return when (category) {
      ShiftCategory.MORNING -> "M"
      ShiftCategory.DAY -> "D"
      ShiftCategory.NIGHT -> "N"
      ShiftCategory.OFF -> "O"
    }
  }

  private fun catFromCode(source: String): ShiftCategory? {
    return when (source) {
      "M" -> ShiftCategory.MORNING
      "D" -> ShiftCategory.DAY
      "N" -> ShiftCategory.NIGHT
      "O" -> ShiftCategory.OFF
      else -> null
    }
  }

  private fun parseTime(source: String): LocalTime? {
    if (source.isEmpty()) {
      return null
    }

    require(source.length == 4 && source.all { it.isDigit() }) {
      "Некорректное время слота: '$source'"
    }

    val hour = source.substring(0, 2).toInt()
    val minute = source.substring(2, 4).toInt()

    require(hour in 0..23 && minute in 0..59) {
      "Время слота вне диапазона: '$source'"
    }

    return LocalTime.of(hour, minute)
  }

  fun encode(slots: List<ShiftType>): String {
    return slots.joinToString("\n") { slot ->
      val time = slot.wakeTime?.let { "%02d%02d".format(it.hour, it.minute) } ?: ""

      "${catCode(slot.category)}|$time|${escape(slot.name)}"
    }
  }

  /**
   * Безопасно разбирает строку в слоты.
   *
   * Возвращает null, если сохранённый цикл повреждён или записан в несовместимом формате.
   * Используется при чтении данных из БД, чтобы битый `cycleSpec` не ронял планировщик и UI.
   */
  fun decodeOrNull(spec: String): List<ShiftType>? {
    return try {
      decode(spec)
    } catch (_: RuntimeException) {
      null
    }
  }

  /**
   * Разбирает строку в слоты.
   *
   * Пустая строка → пустой список.
   * Несовместимый ввод → исключение. Для чтения из БД предпочитай [decodeOrNull].
   */
  fun decode(spec: String): List<ShiftType> {
    if (spec.isEmpty()) {
      return emptyList()
    }

    return spec.split("\n").mapIndexed { index, line ->
      val sep = line.indexOf('|')

      require(sep >= 0) {
        "Слот без разделителя: '$line'"
      }

      val first = line.substring(0, sep)
      val rest = line.substring(sep + 1)
      val category = catFromCode(first)

      if (category != null) {
        val sep2 = rest.indexOf('|')

        require(sep2 >= 0) {
          "Слот без имени: '$line'"
        }

        val wake = parseTime(rest.substring(0, sep2))
        val name = unescape(rest.substring(sep2 + 1))

        ShiftType(
          id = "c$index",
          name = name,
          wakeTime = wake,
          category = category
        )
      } else {
        val wake = if (first == "off") {
          null
        } else {
          parseTime(first)
        }

        ShiftType(
          id = "c$index",
          name = unescape(rest),
          wakeTime = wake
        )
      }
    }
  }

  private fun escape(source: String): String {
    return buildString {
      for (ch in source) {
        when (ch) {
          '\\' -> append("\\\\")
          '|' -> append("\\|")
          '\n' -> append("\\n")
          else -> append(ch)
        }
      }
    }
  }

  private fun unescape(source: String): String {
    return buildString {
      var index = 0

      while (index < source.length) {
        val ch = source[index]

        if (ch == '\\' && index + 1 < source.length) {
          when (source[index + 1]) {
            '\\' -> append('\\')
            '|' -> append('|')
            'n' -> append('\n')
            else -> append(source[index + 1])
          }

          index += 2
        } else {
          append(ch)
          index++
        }
      }
    }
  }
}