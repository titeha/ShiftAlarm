package ru.titeha.shiftalarm.data

import android.content.Context

/**
 * Диагностический журнал событий будильника (постоянный кольцевой буфер в SharedPreferences).
 *
 * Пишется из планировщика/ресивера/системных перепланирований, читается экраном диагностики —
 * чтобы можно было понять пост-фактум, что случилось с будильником («почему не зазвонил»).
 * Формат/обрезка — в чистом [AlarmEventLogCodec].
 */
class AlarmEventLog(context: Context) {

  private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  /** Записать событие [type] с деталями [detail] и меткой времени [atMillis]. */
  fun record(type: AlarmEventType, detail: String, atMillis: Long) {
    write(read(), AlarmEvent(atMillis, type, detail))
  }

  /**
   * Записать событие, если такого же ([type] и те же [detail]) нет среди свежих за [windowMillis]:
   * повтор устойчивого состояния не должен вытеснять из журнала срабатывания.
   */
  fun recordIfFresh(type: AlarmEventType, detail: String, atMillis: Long, windowMillis: Long) {
    val current = read()
    if (AlarmEventLogCodec.hasRecentDuplicate(current, type, detail, atMillis, windowMillis)) return
    write(current, AlarmEvent(atMillis, type, detail))
  }

  /** События, самые свежие сверху. */
  fun recent(): List<AlarmEvent> = read().asReversed()

  fun clear() = prefs.edit().remove(KEY).apply()

  private fun read(): List<AlarmEvent> =
    AlarmEventLogCodec.decode(prefs.getString(KEY, "").orEmpty())

  private fun write(events: List<AlarmEvent>, event: AlarmEvent) {
    val updated = AlarmEventLogCodec.appendCapped(events, event, MAX)
    prefs.edit().putString(KEY, AlarmEventLogCodec.encode(updated)).apply()
  }

  private companion object {
    const val PREFS = "alarm_event_log"
    const val KEY = "events"
    const val MAX = 200
  }
}
