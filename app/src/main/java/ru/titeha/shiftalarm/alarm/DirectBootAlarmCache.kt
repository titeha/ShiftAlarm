package ru.titeha.shiftalarm.alarm

import android.content.Context
import androidx.core.content.edit
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
    /** AUD-2: разовый будильник — после locked-срабатывания следующее звонок не ставится. */
    val oneShot: Boolean = false,
)

/**
 * Кодек кэша ближайших звонков (список ↔ строка). Чистый и версионированный, поэтому round-trip и
 * повреждённые данные проверяются обычными unit-тестами. Метка кодируется URL-safe Base64, чтобы не
 * конфликтовать с разделителями.
 *
 * Формат текущей версии `v2`: первая строка `v2`, далее по строке на запись
 * `id|millis|base64url(label)|oneShot`, где `oneShot` — `0`/`1`.
 *
 * Обратная совместимость: понимается и старый `v1` (`id|millis|base64url(label)`, без флага), потому
 * что в prefs пользователя могли остаться данные, записанные предыдущей версией приложения.
 */
object DirectBootAlarmCacheCodec {
    private const val VERSION_V1 = "v1"
    private const val VERSION_V2 = "v2"

    private fun b64(value: String): String =
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun unb64(value: String): String =
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)

    fun encode(alarms: List<CachedAlarm>): String = buildString {
        append(VERSION_V2)
        alarms.forEach { alarm ->
            append('\n')
            append(alarm.alarmId)
            append('|')
            append(alarm.triggerAtMillis)
            append('|')
            append(b64(alarm.label))
            append('|')
            append(if (alarm.oneShot) "1" else "0")
        }
    }

    fun decodeOrNull(encoded: String): List<CachedAlarm>? {
        return try {
            val lines = encoded.split('\n')
            when (lines.firstOrNull()) {
                VERSION_V2 -> lines.drop(1)
                    .filter { it.isNotEmpty() }
                    .map { line ->
                        val parts = line.split('|')
                        require(parts.size == 4) { "Ожидалось 4 поля в записи кэша v2." }
                        CachedAlarm(
                            alarmId = parts[0].toLong(),
                            triggerAtMillis = parts[1].toLong(),
                            label = unb64(parts[2]),
                            oneShot = parts[3] == "1",
                        )
                    }

                VERSION_V1 -> lines.drop(1)
                    .filter { it.isNotEmpty() }
                    .map { line ->
                        val parts = line.split('|')
                        require(parts.size == 3) { "Ожидалось 3 поля в записи кэша v1." }
                        CachedAlarm(
                            alarmId = parts[0].toLong(),
                            triggerAtMillis = parts[1].toLong(),
                            label = unb64(parts[2]),
                        )
                    }

                else -> null
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
/**
 * Детект пропущенных звонков по старому кэшу (TL02): записи, чьё время прошло больше чем на
 * [graceMs], а приложение не отработало срабатывание (при штатном звонке кэш обновился бы).
 * Чистая функция — покрыта unit-тестами; вызывать ДО любой перезаписи кэша
 * (см. `AlarmScheduler.collectMissed`), иначе перезапись скроет просроченные записи.
 */
internal fun staleMissed(
  cache: List<CachedAlarm>,
  nowMillis: Long,
  graceMs: Long
): List<CachedAlarm> = cache.filter { it.triggerAtMillis < nowMillis - graceMs }

/**
 * AUD-2: собрать записи плана одного будильника из списка будущих моментов срабатывания.
 * Чистая функция — покрыта unit-тестами; разовый будильник несёт флаг [CachedAlarm.oneShot].
 */
internal fun planEntries(
  alarmId: Long,
  label: String,
  nextMillis: List<Long>,
  oneShot: Boolean,
): List<CachedAlarm> = nextMillis.map { millis ->
  CachedAlarm(alarmId = alarmId, triggerAtMillis = millis, label = label, oneShot = oneShot)
}

/**
 * AUD-2: ближайшая будущая (строго позже [nowMillis]) запись этого будильника.
 * Нужна locked-пути, чтобы после срабатывания поставить следующее звонок без Room.
 */
internal fun nextCached(
  cache: List<CachedAlarm>,
  alarmId: Long,
  nowMillis: Long,
): CachedAlarm? = cache
  .filter { it.alarmId == alarmId && it.triggerAtMillis > nowMillis }
  .minByOrNull { it.triggerAtMillis }

/**
 * AUD-2: убрать ровно сработавшую запись и сохранить остальные записи того же будильника.
 *
 * Сначала ищем запись с точным временем срабатывания; если exact нет (locked re-arm мог сдвинуть
 * звонок на "сейчас") — убираем самую раннюю запись этого будильника не позже сработавшего времени.
 */
internal fun removeFired(
  cache: List<CachedAlarm>,
  alarmId: Long,
  firedAtMillis: Long,
): List<CachedAlarm> {
  val exact = cache.indexOfFirst { it.alarmId == alarmId && it.triggerAtMillis == firedAtMillis }
  val index = if (exact >= 0) {
    exact
  } else {
    cache.indices
      .filter { cache[it].alarmId == alarmId && cache[it].triggerAtMillis <= firedAtMillis }
      .minByOrNull { cache[it].triggerAtMillis }
      ?: return cache
  }
  return cache.filterIndexed { i, _ -> i != index }
}

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
     * Убрать запись будильника из кэша ближайших звонков (сработал, удалён или выключен).
     *
     * Нужна для разового будильника: его срабатывание не идёт через перепланирование, поэтому
     * без явной уборки запись остаётся в кэше просроченной и на следующем обновлении кэша
     * детектор пропуска считает её пропущенным звонком (F-1).
     */
    fun removeBase(alarmId: Long) {
        val remaining = read().filterNot { it.alarmId == alarmId }
        prefs.edit()
            .putString(KEY, DirectBootAlarmCacheCodec.encode(remaining))
            .apply()
    }

    /**
     * AUD-2: убрать из плана ровно сработавшую запись, сохранив будущие записи этого будильника
     * (следующее срабатывание ставится из них в locked-пути).
     */
    fun removeFired(alarmId: Long, firedAtMillis: Long) {
        val remaining = removeFired(read(), alarmId, firedAtMillis)
        prefs.edit { putString(KEY, DirectBootAlarmCacheCodec.encode(remaining)) }
    }

    /**
     * AUD-2: отметить, что разовый будильник прозвонил до разблокировки. Room недоступна, поэтому
     * отметка живёт в device-protected хранилище и применяется при `USER_UNLOCKED`.
     */
    fun markOneShotFired(alarmId: Long) {
        val ids = prefs.getStringSet(KEY_FIRED_ONESHOT, emptySet()).orEmpty()
        prefs.edit { putStringSet(KEY_FIRED_ONESHOT, ids + alarmId.toString()) }
    }

    /** AUD-2: id разовых будильников, прозвонивших в locked-режиме и ждущих применения. */
    fun readFiredOneShots(): List<Long> =
        prefs.getStringSet(KEY_FIRED_ONESHOT, emptySet()).orEmpty()
            .mapNotNull(String::toLongOrNull)

    /** AUD-2: снять отметку разового после применения (выключение/удаление по Room). */
    fun clearFiredOneShot(alarmId: Long) {
        val ids = prefs.getStringSet(KEY_FIRED_ONESHOT, emptySet()).orEmpty()
        prefs.edit { putStringSet(KEY_FIRED_ONESHOT, ids - alarmId.toString()) }
    }

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
        const val KEY_FIRED_ONESHOT = "fired_oneshot_v1"
        const val MAX_MISSED = 10
    }
}
