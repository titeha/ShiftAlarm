package ru.titeha.shiftalarm.alarm

import android.content.Context
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Одна запись кэша ближайших звонков: чего достаточно, чтобы ПЕРЕВЫСТАВИТЬ будильник вслепую,
 * не обращаясь к базе (которая до разблокировки устройства недоступна).
 */
data class CachedAlarm(
    val alarmId: Long,
    val triggerAtMillis: Long,
    val label: String,
)

/**
 * Кодек кэша ближайших звонков (список ↔ строка). Чистый и версионированный, поэтому round-trip и
 * повреждённые данные проверяются обычными unit-тестами. Метка кодируется URL-safe Base64, чтобы не
 * конфликтовать с разделителями.
 *
 * Формат: первая строка `v1`, далее по строке на запись `id|millis|base64url(label)`.
 */
object DirectBootAlarmCacheCodec {
    private const val VERSION = "v1"

    fun encode(alarms: List<CachedAlarm>): String = buildString {
        append(VERSION)
        alarms.forEach { alarm ->
            append('\n')
            append(alarm.alarmId)
            append('|')
            append(alarm.triggerAtMillis)
            append('|')
            append(
                Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(alarm.label.toByteArray(StandardCharsets.UTF_8))
            )
        }
    }

    fun decodeOrNull(encoded: String): List<CachedAlarm>? {
        return try {
            val lines = encoded.split('\n')
            if (lines.isEmpty() || lines[0] != VERSION) return null

            lines.drop(1)
                .filter { it.isNotEmpty() }
                .map { line ->
                    val parts = line.split('|')
                    require(parts.size == 3) { "Ожидалось 3 поля в записи кэша." }

                    CachedAlarm(
                        alarmId = parts[0].toLong(),
                        triggerAtMillis = parts[1].toLong(),
                        label = String(
                            Base64.getUrlDecoder().decode(parts[2]),
                            StandardCharsets.UTF_8
                        )
                    )
                }
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Device-protected хранилище кэша ближайших звонков.
 *
 * Пишется в credential-НЕзависимое (device-protected) хранилище через
 * [Context.createDeviceProtectedStorageContext], поэтому доступно ещё ДО ввода пин-кода —
 * `directBootAware`-ресивер читает его при `LOCKED_BOOT_COMPLETED` и перевыставляет будильники,
 * не трогая Room (та до разблокировки недоступна).
 */
class DirectBootAlarmStore(context: Context) {
    private val prefs = context.applicationContext
        .createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun write(alarms: List<CachedAlarm>) {
        prefs.edit()
            .putString(KEY, DirectBootAlarmCacheCodec.encode(alarms))
            .apply()
    }

    fun read(): List<CachedAlarm> =
        prefs.getString(KEY, null)
            ?.let { DirectBootAlarmCacheCodec.decodeOrNull(it) }
            ?: emptyList()

    /**
     * Отложенные (снуз) звонки — отдельный список, чтобы перевыставление после ребута внутри снуз-окна
     * не теряло звонок. Тот же формат [CachedAlarm]; принадлежность к снузу задаётся самим списком.
     */
    fun readSnoozes(): List<CachedAlarm> =
        prefs.getString(KEY_SNOOZE, null)
            ?.let { DirectBootAlarmCacheCodec.decodeOrNull(it) }
            ?: emptyList()

    /** Вставить/обновить снуз-звонок будильника (по alarmId — один активный снуз на будильник). */
    fun putSnooze(snooze: CachedAlarm) {
        val others = readSnoozes().filterNot { it.alarmId == snooze.alarmId }
        prefs.edit()
            .putString(KEY_SNOOZE, DirectBootAlarmCacheCodec.encode(others + snooze))
            .apply()
    }

    /** Убрать снуз-звонок будильника (стоп/отмена/исчерпание). */
    fun removeSnooze(alarmId: Long) {
        val remaining = readSnoozes().filterNot { it.alarmId == alarmId }
        prefs.edit()
            .putString(KEY_SNOOZE, DirectBootAlarmCacheCodec.encode(remaining))
            .apply()
    }

    /** Пропущенные звонки (обнаружены как «застрявшие» в кэше после гэпа), для показа пользователю. */
    fun readMissed(): List<CachedAlarm> =
        prefs.getString(KEY_MISSED, null)
            ?.let { DirectBootAlarmCacheCodec.decodeOrNull(it) }
            ?: emptyList()

    /** Добавить пропущенные к уже накопленным (последние [MAX_MISSED]). */
    fun addMissed(missed: List<CachedAlarm>) {
        if (missed.isEmpty()) return
        val combined = (readMissed() + missed).takeLast(MAX_MISSED)
        prefs.edit()
            .putString(KEY_MISSED, DirectBootAlarmCacheCodec.encode(combined))
            .apply()
    }

    fun clearMissed() {
        prefs.edit().remove(KEY_MISSED).apply()
    }

    private companion object {
        const val PREFS = "direct_boot_alarms"
        const val KEY = "cache_v1"
        const val KEY_MISSED = "missed_v1"
        const val KEY_SNOOZE = "snoozes_v1"
        const val MAX_MISSED = 10
    }
}
