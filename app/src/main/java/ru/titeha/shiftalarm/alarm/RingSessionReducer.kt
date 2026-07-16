package ru.titeha.shiftalarm.alarm

/**
 * Чистая логика «сессии звонка»: от первого срабатывания до «Стоп» или «Пропущен».
 *
 * Без Android-типов — редьюсер `(state, event) -> (state, actions)` полностью проверяется unit-тестами.
 * Побочные эффекты (поставить снуз-звонок, записать в журнал, закрыть сессию) описываются [RingAction],
 * а исполняет их Android-слой.
 *
 * Единый счётчик [RingSessionState.snoozeCount] на сессию: ручной снуз и авто-перезвон делят общий
 * лимит [RingConfig.maxSnoozes]. После «Стоп»/отмены сессия закрывается и счётчик обнуляется —
 * бесконечного звона не бывает.
 */

/** Фаза сессии звонка. */
enum class RingPhase { RINGING, SNOOZING, CLOSED }

/**
 * Состояние сессии, привязанное к паре (id будильника, плановое время исходного срабатывания).
 * Плановое время — часть ключа: разные срабатывания одного будильника — разные сессии.
 */
data class RingSessionState(
    val alarmId: Long,
    val scheduledTriggerAtMillis: Long,
    val snoozeCount: Int = 0,
    val phase: RingPhase = RingPhase.RINGING,
)

/** Настройки звонка (глобальные, раздел «Звонок»). */
data class RingConfig(
    val ringDurationMinutes: Int = 5,      // T — сколько звенит без взаимодействия до авто-перезвона
    val snoozeIntervalMinutes: Int = 5,    // интервал снуза
    val maxSnoozes: Int = 3,               // M — общий лимит (0 = снуз выключен)
    val autoRepeatEnabled: Boolean = true, // авто-перезвон невыключенного звонка
) {
    val snoozeIntervalMillis: Long get() = snoozeIntervalMinutes.toLong() * 60_000L
    val ringDurationMillis: Long get() = ringDurationMinutes.toLong() * 60_000L
}

/** Событие сессии. */
sealed interface RingEvent {
    /** Будильник зазвонил (исходное срабатывание или срабатывание снуза). */
    data object Fired : RingEvent

    /** Пользователь нажал «Стоп». */
    data object StopPressed : RingEvent

    /** Пользователь нажал «Отложить». */
    data object SnoozePressed : RingEvent

    /** Прошло T минут без взаимодействия (авто-перезвон; появляется на этапе 2). */
    data object RingTimeout : RingEvent

    /** Внешняя отмена: выключение тумблером, удаление, сохранение правок будильника. */
    data object Cancelled : RingEvent
}

/** Запись в диагностический журнал по событию сессии. */
sealed interface RingJournalEntry {
    /** Ручной снуз: попытка [count] из лимита, следующее срабатывание в [untilMillis]. */
    data class Snooze(val count: Int, val untilMillis: Long) : RingJournalEntry

    /** Авто-перезвон невыключенного: попытка [count], следующее срабатывание в [untilMillis]. */
    data class AutoSnooze(val count: Int, val untilMillis: Long) : RingJournalEntry

    /** Лимит исчерпан / авто-перезвон выключен — звонок не был выключен. */
    data object Missed : RingJournalEntry

    /** Пользователь остановил звонок. */
    data object Stopped : RingJournalEntry

    /** Сессия отменена извне. */
    data object Cancelled : RingJournalEntry
}

/** Побочное действие, которое должен выполнить Android-слой. */
sealed interface RingAction {
    /** Поставить снуз-звонок на [atMillis] (через AlarmScheduler, отдельный неймспейс + RingCache). */
    data class ScheduleSnooze(val atMillis: Long) : RingAction

    /** Отметить звонок как пропущенный (карточка «не был выключен» + журнал плана/факта). */
    data object MarkMissed : RingAction

    /** Закрыть сессию: снять снуз-PendingIntent, вычистить из RingCache и стора сессий. */
    data object CloseSession : RingAction

    /** Записать событие в диагностический журнал. */
    data class Journal(val entry: RingJournalEntry) : RingAction
}

/** Результат редьюсера: новое состояние и список действий (в порядке применения). */
data class RingReduceResult(
    val state: RingSessionState,
    val actions: List<RingAction>,
)

object RingSessionReducer {

    /**
     * Применить [event] к [state] при настройках [config] и текущем времени [nowMillis].
     * Чистая функция: одинаковый вход → одинаковый выход, без обращений к системным часам.
     */
    fun reduce(
        state: RingSessionState,
        event: RingEvent,
        config: RingConfig,
        nowMillis: Long,
    ): RingReduceResult = when (event) {
        RingEvent.Fired -> onFired(state)
        RingEvent.StopPressed -> onStop(state)
        RingEvent.SnoozePressed -> onSnoozePressed(state, config, nowMillis)
        RingEvent.RingTimeout -> onRingTimeout(state, config, nowMillis)
        RingEvent.Cancelled -> onCancelled(state)
    }

    /** Можно ли ещё откладывать (общий лимит ручного и авто). */
    fun canSnooze(state: RingSessionState, config: RingConfig): Boolean =
        config.maxSnoozes > 0 && state.snoozeCount < config.maxSnoozes

    /** Сколько отложить ещё доступно (для подписи «осталось N»). */
    fun remainingSnoozes(state: RingSessionState, config: RingConfig): Int =
        (config.maxSnoozes - state.snoozeCount).coerceAtLeast(0)

    private fun onFired(state: RingSessionState): RingReduceResult =
        RingReduceResult(state.copy(phase = RingPhase.RINGING), emptyList())

    private fun onStop(state: RingSessionState): RingReduceResult =
        RingReduceResult(
            state.copy(snoozeCount = 0, phase = RingPhase.CLOSED),
            listOf(RingAction.Journal(RingJournalEntry.Stopped), RingAction.CloseSession),
        )

    private fun onCancelled(state: RingSessionState): RingReduceResult =
        RingReduceResult(
            state.copy(snoozeCount = 0, phase = RingPhase.CLOSED),
            listOf(RingAction.Journal(RingJournalEntry.Cancelled), RingAction.CloseSession),
        )

    private fun onSnoozePressed(
        state: RingSessionState,
        config: RingConfig,
        nowMillis: Long,
    ): RingReduceResult {
        // Лимит исчерпан / снуз выключен: кнопку прячут, но защищаемся — событие игнорируем.
        if (!canSnooze(state, config)) {
            return RingReduceResult(state, emptyList())
        }
        val next = state.snoozeCount + 1
        val at = nowMillis + config.snoozeIntervalMillis
        return RingReduceResult(
            state.copy(snoozeCount = next, phase = RingPhase.SNOOZING),
            listOf(
                RingAction.ScheduleSnooze(at),
                RingAction.Journal(RingJournalEntry.Snooze(next, at)),
            ),
        )
    }

    private fun onRingTimeout(
        state: RingSessionState,
        config: RingConfig,
        nowMillis: Long,
    ): RingReduceResult {
        // Авто-перезвон, пока включён и не исчерпан лимит; иначе — пропущен.
        return if (config.autoRepeatEnabled && canSnooze(state, config)) {
            val next = state.snoozeCount + 1
            val at = nowMillis + config.snoozeIntervalMillis
            RingReduceResult(
                state.copy(snoozeCount = next, phase = RingPhase.SNOOZING),
                listOf(
                    RingAction.ScheduleSnooze(at),
                    RingAction.Journal(RingJournalEntry.AutoSnooze(next, at)),
                ),
            )
        } else {
            RingReduceResult(
                state.copy(phase = RingPhase.CLOSED),
                listOf(
                    RingAction.Journal(RingJournalEntry.Missed),
                    RingAction.MarkMissed,
                    RingAction.CloseSession,
                ),
            )
        }
    }
}
