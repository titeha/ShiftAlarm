package ru.titeha.shiftalarm.greetings

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.MonthDay

/**
 * Загрузка датасета «Настроения дня» из assets. Разбор — через `org.json` (часть Android SDK,
 * без новых зависимостей). Локаль — в имени файла, с фолбэком на `ru`. Это единственный
 * Android-зависимый слой; вся логика ([DayGreetingResolver]) работает с уже разобранным
 * [GreetingsDataset]. Сеть не используется — только assets.
 */
object GreetingsLoader {

  /** Датасет из `assets/greetings/holidays_<locale>.json` + `phrases_<locale>.json`; фолбэк на `ru`. */
  fun fromAssets(context: Context, locale: String = "ru"): GreetingsDataset {
    val loc = if (assetExists(context, "greetings/holidays_$locale.json")) locale else "ru"
    return parse(
      holidaysJson = readAsset(context, "greetings/holidays_$loc.json"),
      phrasesJson = readAsset(context, "greetings/phrases_$loc.json"),
    )
  }

  /** Чистый разбор двух JSON-строк в датасет (для тестов на реальных assets). */
  fun parse(holidaysJson: String, phrasesJson: String): GreetingsDataset =
    GreetingsDataset(
      holidays = JSONArray(holidaysJson).objects().map(::parseHoliday),
      phrases = JSONArray(phrasesJson).objects().map(::parsePhrase),
    )

  private fun parseHoliday(o: JSONObject): Holiday {
    val fixed = o.optString("date").takeIf { it.isNotEmpty() }?.let(::parseMonthDay)
    val rule = o.optJSONObject("rule")?.let {
      HolidayRule(
        month = it.getInt("month"),
        dayOfWeek = DayOfWeek.valueOf(it.getString("dayOfWeek").uppercase()),
        ordinal = it.getInt("ordinal"),
      )
    }
    return Holiday(
      id = o.getString("id"),
      name = o.getString("name"),
      kind = HolidayKind.valueOf(o.getString("kind").uppercase()),
      description = o.optString("description"),
      fixed = fixed,
      rule = rule,
    )
  }

  private fun parsePhrase(o: JSONObject): Phrase =
    Phrase(text = o.getString("text"), author = o.optString("author").takeIf { it.isNotEmpty() })

  /** «MM-DD» → MonthDay (02-29 допустим — покажется только в високосные годы). */
  private fun parseMonthDay(mmdd: String): MonthDay {
    val parts = mmdd.split("-")
    require(parts.size == 2) { "Неверный формат даты «$mmdd», ожидалось MM-DD" }
    return MonthDay.of(parts[0].toInt(), parts[1].toInt())
  }

  private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

  private fun readAsset(context: Context, path: String): String =
    context.assets.open(path).bufferedReader().use { it.readText() }

  private fun assetExists(context: Context, path: String): Boolean =
    try {
      context.assets.open(path).close(); true
    } catch (_: Exception) {
      false
    }
}
