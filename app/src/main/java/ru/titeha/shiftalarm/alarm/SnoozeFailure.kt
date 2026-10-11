package ru.titeha.shiftalarm.alarm

/**
 * AUD-12 (S-01): можно ли останавливать текущий сигнал после реакции сессии.
 *
 * [hadSnoozeAttempt] — редьюсер запросил постановку снуза (`RingAction.ScheduleSnooze`);
 * [snoozeScheduled] — снуз реально поставлен (`AlarmScheduler.scheduleSnoozeAt` == true).
 *
 * Глушим сигнал, если снуз НЕ запрашивался (Стоп/пропуск/лимит) или запрашивался и удался.
 * Если снуз запрашивался и НЕ удался — сигнал оставляем играть: иначе пользователь получает
 * «тишину» без будущего звонка (S-01). Правило одно для ручного снуза и авто-перезвона.
 */
internal fun shouldStopRingingAfterSnooze(
    hadSnoozeAttempt: Boolean,
    snoozeScheduled: Boolean,
): Boolean = !hadSnoozeAttempt || snoozeScheduled
