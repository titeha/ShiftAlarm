package ru.titeha.shiftalarm.data

import android.content.Context
import ru.titeha.shiftalarm.schedule.ProductionCalendar
import ru.titeha.shiftalarm.schedule.ProductionCalendars

/**
 * Хранение и обновление производственного календаря из офиц. источника (isDayOff.ru).
 *
 * Принцип: звонок НЕ зависит от сети. Сырой ответ источника кэшируется в SharedPreferences; движок
 * читает всегда локально (кэш → встроенные данные) через [ProductionCalendars.resolve]. Сеть только
 * фоново обновляет кэш ([refresh]).
 */
class HolidayCalendarRepository(context: Context) {

  private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  private fun key(country: String, year: Int) = "${country.uppercase()}_$year"

  /** Календарь из кэша (распарсенный), или null — кэша нет либо он повреждён. */
  fun cachedCalendar(country: String, year: Int): ProductionCalendar? {
    val raw = prefs.getString(key(country, year), null) ?: return null
    return try {
      ProductionCalendars.fromIsDayOff(year, raw)
    } catch (_: Exception) {
      null // повреждённый кэш игнорируем — resolve() откатится на встроенные данные
    }
  }

  /** Подключить кэш к движку как источник календарей. Вызывать один раз при старте приложения. */
  fun install() {
    ProductionCalendars.source = { country, year -> cachedCalendar(country, year) }
  }

  /**
   * Обновить кэш для [country]/[year] с офиц. источника. Валидирует ответ парсером перед записью —
   * мусор/коды ошибок не попадут в кэш. true — кэш обновлён. Оффлайн/ошибка — false, старый кэш цел.
   */
  suspend fun refresh(country: String, year: Int): Boolean {
    val raw = HolidayApi.fetch(country, year) ?: return false
    return try {
      ProductionCalendars.fromIsDayOff(year, raw) // проверка: корректная строка нужной длины
      prefs.edit().putString(key(country, year), raw).apply()
      true
    } catch (_: Exception) {
      false
    }
  }

  private companion object {
    const val PREFS = "holiday_calendar_cache"
  }
}
