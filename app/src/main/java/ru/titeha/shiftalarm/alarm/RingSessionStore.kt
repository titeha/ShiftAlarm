package ru.titeha.shiftalarm.alarm

import android.content.Context
import androidx.core.content.edit

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
/**
 * AUD-3 (F-02): вставить/обновить сессию будильника (одна активная сессия на будильник).
 * Чистая функция — RMW вынесен из Android-обёртки, чтобы покрыть его тестами.
 */
internal fun withSession(
    sessions: List<RingSessionState>,
    session: RingSessionState,
): List<RingSessionState> = sessions.filterNot { it.alarmId == session.alarmId } + session

/** AUD-3: убрать сессию будильника. */
internal fun withoutSession(
    sessions: List<RingSessionState>,
    alarmId: Long,
): List<RingSessionState> = sessions.filterNot { it.alarmId == alarmId }

class RingSessionStore(context: Context) {
    private val prefs = context.applicationContext
        .createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(): List<RingSessionState> = synchronized(LOCK) { allLocked() }

    /** Активная сессия будильника, либо null. Ключ — alarmId: одна активная сессия на будильник. */
    fun find(alarmId: Long): RingSessionState? = synchronized(LOCK) {
        allLocked().firstOrNull { it.alarmId == alarmId }
    }

    /** Вставить/обновить сессию (по alarmId). AUD-3: RMW под общим замком, `commit()`. */
    fun put(session: RingSessionState) = synchronized(LOCK) {
        writeLocked(withSession(allLocked(), session))
    }

    /** Удалить сессию будильника. AUD-3: RMW под общим замком, `commit()`. */
    fun remove(alarmId: Long) = synchronized(LOCK) {
        writeLocked(withoutSession(allLocked(), alarmId))
    }

    // --- вызывать ТОЛЬКО под замком [LOCK] ---

    private fun allLocked(): List<RingSessionState> =
        prefs.getString(KEY, null)
            ?.let { RingSessionCodec.decodeOrNull(it) }
            ?: emptyList()

    private fun writeLocked(sessions: List<RingSessionState>) {
        // commit(): следующий шаг (авто-перезвон/счётчик снуза) зависит от факта записи.
        prefs.edit(commit = true) { putString(KEY, RingSessionCodec.encode(sessions)) }
    }

    private companion object {
        /**
         * AUD-3 (F-02): общий замок на ВСЕ экземпляры [RingSessionStore]. Экземпляров много
         * (receiver/service/UI), без общего замка `all() → write` теряет параллельные обновления.
         */
        private val LOCK = Any()
        const val PREFS = "ring_sessions"
        const val KEY = "sessions_v1"
    }
}
