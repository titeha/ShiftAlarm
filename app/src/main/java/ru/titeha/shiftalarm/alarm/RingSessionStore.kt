package ru.titeha.shiftalarm.alarm

import android.content.Context

/**
 * Кодек состояний сессий звонка (список ↔ строка). Чистый и версионированный — round-trip и
 * повреждённые данные проверяются обычными unit-тестами.
 *
 * Формат: первая строка `v1`, далее по строке на сессию `alarmId|scheduled|snoozeCount|phase`.
 */
object RingSessionCodec {
    private const val VERSION = "v1"

    fun encode(sessions: List<RingSessionState>): String = buildString {
        append(VERSION)
        sessions.forEach { s ->
            append('\n')
            append(s.alarmId)
            append('|')
            append(s.scheduledTriggerAtMillis)
            append('|')
            append(s.snoozeCount)
            append('|')
            append(s.phase.name)
        }
    }

    fun decodeOrNull(encoded: String): List<RingSessionState>? {
        return try {
            val lines = encoded.split('\n')
            if (lines.isEmpty() || lines[0] != VERSION) return null

            lines.drop(1)
                .filter { it.isNotEmpty() }
                .map { line ->
                    val parts = line.split('|')
                    require(parts.size == 4) { "Ожидалось 4 поля в записи сессии." }
                    RingSessionState(
                        alarmId = parts[0].toLong(),
                        scheduledTriggerAtMillis = parts[1].toLong(),
                        snoozeCount = parts[2].toInt(),
                        phase = RingPhase.valueOf(parts[3]),
                    )
                }
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Device-protected хранилище активных сессий звонка.
 *
 * Пишется в credential-НЕзависимое хранилище ([Context.createDeviceProtectedStorageContext]), поэтому
 * состояние (в т.ч. счётчик снуза) переживает перезагрузку внутри снуз-окна и доступно до разблокировки.
 * Сессия привязана к паре (alarmId, плановое время исходного срабатывания).
 */
class RingSessionStore(context: Context) {
    private val prefs = context.applicationContext
        .createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(): List<RingSessionState> =
        prefs.getString(KEY, null)
            ?.let { RingSessionCodec.decodeOrNull(it) }
            ?: emptyList()

    /** Сессия по паре ключа, либо null. */
    fun find(alarmId: Long, scheduledTriggerAtMillis: Long): RingSessionState? =
        all().firstOrNull {
            it.alarmId == alarmId && it.scheduledTriggerAtMillis == scheduledTriggerAtMillis
        }

    /** Вставить/обновить сессию (по ключу). */
    fun put(session: RingSessionState) {
        val others = all().filterNot {
            it.alarmId == session.alarmId &&
                it.scheduledTriggerAtMillis == session.scheduledTriggerAtMillis
        }
        write(others + session)
    }

    /** Удалить сессию (по ключу). */
    fun remove(alarmId: Long, scheduledTriggerAtMillis: Long) {
        val remaining = all().filterNot {
            it.alarmId == alarmId && it.scheduledTriggerAtMillis == scheduledTriggerAtMillis
        }
        write(remaining)
    }

    /** Удалить все сессии этого будильника (любое плановое время) — для отмены тумблером/удаления. */
    fun removeAllFor(alarmId: Long) {
        write(all().filterNot { it.alarmId == alarmId })
    }

    private fun write(sessions: List<RingSessionState>) {
        prefs.edit().putString(KEY, RingSessionCodec.encode(sessions)).apply()
    }

    private companion object {
        const val PREFS = "ring_sessions"
        const val KEY = "sessions_v1"
    }
}
