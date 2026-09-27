package ru.titeha.shiftalarm.data

/** Тип события будильника для диагностического журнала. */
enum class AlarmEventType {
  SCHEDULED,   // запланирован на конкретное время
  FIRED,       // сработал (прошёл проверку актуальности)
  SKIPPED,     // срабатывание отклонено (устаревшее/выключен)
  CANCELLED,   // снят
  RESCHEDULED, // перепланирование после системного события (reboot/time/timezone/обновление)
  SIGNAL_DEGRADED, // звук не удалось запустить — сигнал только вибрацией
  SNOOZED, // отложен (ручной снуз или авто-перезвон невыключенного)
  MISSED, // звонок не был выключен: лимит снуза исчерпан / авто-перезвон выключен
  ERROR, // исключение при обработке (ресивер) — чтобы «почему не сработало» было видно в журнале
  CALENDAR_DEGRADED, // нет данных производственного календаря на год звонка: нерабочие дни не учтены
}

/**
 * «Важные» события — то, что напрямую отвечает «почему зазвонил/не зазвонил»: сработал, пропущен,
 * сигнал деградировал, звонок рассчитан без данных календаря, ошибка. Остальное (запланирован/снят/
 * перепланирован) — техническое, шумит.
 */
val AlarmEventType.isImportant: Boolean
  get() = this == AlarmEventType.FIRED ||
    this == AlarmEventType.SKIPPED ||
    this == AlarmEventType.SIGNAL_DEGRADED ||
    this == AlarmEventType.MISSED ||
    this == AlarmEventType.ERROR ||
    this == AlarmEventType.CALENDAR_DEGRADED

/** Одна запись журнала: когда ([atMillis]), что ([type]) и детали ([detail], напр. id и время). */
data class AlarmEvent(
  val atMillis: Long,
  val type: AlarmEventType,
  val detail: String,
)

/**
 * Чистая (без Android) сериализация журнала: строки `millis\ttype\tdetail`, разделённые `\n`.
 * Хранение — в [AlarmEventLog]. Вынесено за шов, чтобы покрыть формат и обрезку юнит-тестами.
 */
object AlarmEventLogCodec {

  /** Убрать разделители из деталей, чтобы не поломать формат. */
  private fun sanitize(s: String): String = s.replace('\t', ' ').replace('\n', ' ')

  fun encode(events: List<AlarmEvent>): String =
    events.joinToString("\n") { "${it.atMillis}\t${it.type.name}\t${sanitize(it.detail)}" }

  /** Разобрать журнал; строки с неизвестным типом/битым форматом молча пропускаются. */
  fun decode(text: String): List<AlarmEvent> =
    text.lineSequence()
      .filter { it.isNotBlank() }
      .mapNotNull { line ->
        val parts = line.split('\t', limit = 3)
        if (parts.size < 3) return@mapNotNull null
        val millis = parts[0].toLongOrNull() ?: return@mapNotNull null
        val type = runCatching { AlarmEventType.valueOf(parts[1]) }.getOrNull() ?: return@mapNotNull null
        AlarmEvent(millis, type, parts[2])
      }
      .toList()

  /** Добавить [event] в конец и обрезать до [max] самых свежих (хронологический порядок). */
  fun appendCapped(events: List<AlarmEvent>, event: AlarmEvent, max: Int): List<AlarmEvent> {
    val combined = events + event
    return if (combined.size > max) combined.takeLast(max) else combined
  }

  /**
   * Есть ли среди [events] свежее (не старше [windowMillis] от [nowMillis]) событие [type] с теми же
   * [detail]: повтор устойчивого состояния писать не нужно, иначе он вытеснит из кольцевого буфера
   * срабатывания и пропуски.
   *
   * Сравниваются полные детали — в них и id будильника, и год, поэтому ключ «будильник + год»
   * получается без разбора строки. Метка из будущего (перевод часов назад/вперёд) считается свежей:
   * лучше пропустить запись, чем продублировать её.
   */
  fun hasRecentDuplicate(
    events: List<AlarmEvent>,
    type: AlarmEventType,
    detail: String,
    nowMillis: Long,
    windowMillis: Long
  ): Boolean = events.any {
    it.type == type && it.detail == detail && nowMillis - it.atMillis < windowMillis
  }
}
