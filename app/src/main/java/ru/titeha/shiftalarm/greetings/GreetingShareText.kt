package ru.titeha.shiftalarm.greetings

/**
 * Чистый построитель текста для «Поделиться». Формат (ТЗ):
 * «Сегодня — День шахтёра. „Терпение и труд всё перетрут.“ — Будильник работяги».
 * Части, которых нет (праздник или фраза), опускаются; хвост-подпись всегда.
 */
object GreetingShareText {
  private const val APP_TAG = "— Будильник работяги"

  fun build(greeting: Greeting): String {
    val parts = mutableListOf<String>()
    greeting.holidays.firstOrNull()?.let { parts += "Сегодня — ${it.name}." }
    greeting.phrase?.let { phrase ->
      val author = phrase.author?.let { " — $it" } ?: ""
      parts += "„${phrase.text}“$author"
    }
    parts += APP_TAG
    return parts.joinToString(" ")
  }
}
