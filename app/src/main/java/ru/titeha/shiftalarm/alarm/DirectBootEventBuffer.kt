package ru.titeha.shiftalarm.alarm

import android.content.Context
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Одно событие, записанное ДО разблокировки устройства (Direct Boot), когда основной журнал
 * (credential-encrypted [ru.titeha.shiftalarm.data.AlarmEventLog]) ещё недоступен.
 */
data class BufferedEvent(
    val atMillis: Long,
    val type: String,
    val detail: String,
)

/**
 * Кодек буфера событий Direct Boot (список ↔ строка). Чистый и версионированный — round-trip и
 * повреждённые данные проверяются обычными unit-тестами. Поля кодируются URL-safe Base64, чтобы
 * переносы строк/разделители в тексте не ломали формат.
 *
 * Формат: первая строка `v1`, далее по строке на событие `millis|base64url(type)|base64url(detail)`.
 */
object DirectBootEventBufferCodec {
    private const val VERSION = "v1"

    private fun b64(value: String): String =
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun unb64(value: String): String =
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)

    fun encode(events: List<BufferedEvent>): String = buildString {
        append(VERSION)
        events.forEach { event ->
            append('\n')
            append(event.atMillis)
            append('|')
            append(b64(event.type))
            append('|')
            append(b64(event.detail))
        }
    }

    fun decodeOrNull(encoded: String): List<BufferedEvent>? {
        return try {
            val lines = encoded.split('\n')
            if (lines.isEmpty() || lines[0] != VERSION) return null

            lines.drop(1)
                .filter { it.isNotEmpty() }
                .map { line ->
                    val parts = line.split('|')
                    require(parts.size == 3) { "Ожидалось 3 поля в записи буфера." }
                    BufferedEvent(
                        atMillis = parts[0].toLong(),
                        type = unb64(parts[1]),
                        detail = unb64(parts[2]),
                    )
                }
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Device-protected буфер диагностических событий, накопленных до разблокировки.
 *
 * Пока устройство залочено, основной журнал (CE) недоступен — события про Direct Boot (перевыставление
 * из кэша, звонок залоченным, деградация сигнала) складываем сюда, в credential-НЕзависимое хранилище.
 * После разблокировки [ru.titeha.shiftalarm.alarm.BootReceiver] переливает буфер в основной журнал с
 * пометкой `direct_boot` и очищает его ([drain]).
 */
class DirectBootEventBuffer(context: Context) {
    private val prefs = context.applicationContext
        .createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Добавить событие в буфер (хранятся последние [MAX] штук). */
    fun add(type: String, detail: String, atMillis: Long) {
        val combined = (read() + BufferedEvent(atMillis, type, detail)).takeLast(MAX)
        prefs.edit()
            .putString(KEY, DirectBootEventBufferCodec.encode(combined))
            .apply()
    }

    fun read(): List<BufferedEvent> =
        prefs.getString(KEY, null)
            ?.let { DirectBootEventBufferCodec.decodeOrNull(it) }
            ?: emptyList()

    /** Прочитать всё и очистить буфер (для перелива в основной журнал после разблокировки). */
    fun drain(): List<BufferedEvent> {
        val events = read()
        if (events.isNotEmpty()) {
            prefs.edit().remove(KEY).apply()
        }
        return events
    }

    private companion object {
        const val PREFS = "direct_boot_alarms"
        const val KEY = "events_v1"
        const val MAX = 50
    }
}
