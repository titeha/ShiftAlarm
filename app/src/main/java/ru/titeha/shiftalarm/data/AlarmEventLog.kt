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
    val current = AlarmEventLogCodec.decode(prefs.getString(KEY, "").orEmpty())
    val updated = AlarmEventLogCodec.appendCapped(current, AlarmEvent(atMillis, type, detail), MAX)
    prefs.edit().putString(KEY, AlarmEventLogCodec.encode(updated)).apply()
  }

  /** События, самые свежие сверху. */
  fun recent(): List<AlarmEvent> =
    AlarmEventLogCodec.decode(prefs.getString(KEY, "").orEmpty()).asReversed()

  fun clear() = prefs.edit().remove(KEY).apply()

  private companion object {
    const val PREFS = "alarm_event_log"
    const val KEY = "events"
    const val MAX = 200
  }
}
