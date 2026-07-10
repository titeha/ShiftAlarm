package ru.titeha.shiftalarm.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Тонкий клиент официального источника производственного календаря РФ — isDayOff.ru.
 *
 * `GET https://isdayoff.ru/api/getdata?year=YYYY&cc=ru` возвращает строку цифр по одной на каждый
 * день года (`0` рабочий, `1` нерабочий, `2` сокращённый, `4` перенос). Разбор строки —
 * [ru.titeha.shiftalarm.schedule.ProductionCalendars.fromIsDayOff].
 *
 * Только загрузка сырой строки. Никакого влияния на звонок: это фоновое обновление кэша, движок
 * читает локальные данные. Любая ошибка сети → null (тихо, планировщик работает на кэше/встроенном).
 */
object HolidayApi {

  /** Загрузить сырой ответ isDayOff для [country]/[year]; null при любой ошибке. */
  suspend fun fetch(country: String, year: Int): String? = withContext(Dispatchers.IO) {
    val url = URL("https://isdayoff.ru/api/getdata?year=$year&cc=${country.lowercase()}")
    var connection: HttpURLConnection? = null
    try {
      connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10_000
        readTimeout = 10_000
      }
      if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
      val body = connection.inputStream.bufferedReader().use { it.readText() }.trim()
      // Ответ — только цифры; коды ошибок isDayOff («100» и т.п.) отсеются валидацией парсера.
      body.ifEmpty { null }
    } catch (_: Exception) {
      null
    } finally {
      connection?.disconnect()
    }
  }
}
