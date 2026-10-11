package ru.titeha.shiftalarm.alarm

import ru.titeha.shiftalarm.data.AlarmEntity

/**
 * AUD-10 (R-02): что сделать с будильником по отметке «разовый прозвонил до разблокировки».
 * Чистое решение без `android.*` — вынесено из Android-обёртки, чтобы reconcile покрывался тестами.
 */
sealed interface FiredOneShotAction {
    /** Будильник по отметке уже удалён — нужно только снять отметку. */
    data object ClearMarkOnly : FiredOneShotAction

    /** Будильник с `deleteAfterFiring` — удалить. */
    data class Delete(val alarm: AlarmEntity) : FiredOneShotAction

    /** Иначе — выключить (`enabled = false`). */
    data class Disable(val alarm: AlarmEntity) : FiredOneShotAction
}

/**
 * AUD-10 (R-02): решение по отметке сработавшего в локе разового будильника.
 *
 * Идемпотентно по смыслу: если будильник уже удалён — [FiredOneShotAction.ClearMarkOnly];
 * если уже выключен — повторно [FiredOneShotAction.Disable] (безопасно).
 */
internal fun firedOneShotAction(alarm: AlarmEntity?): FiredOneShotAction = when {
    alarm == null -> FiredOneShotAction.ClearMarkOnly
    alarm.deleteAfterFiring -> FiredOneShotAction.Delete(alarm)
    else -> FiredOneShotAction.Disable(alarm)
}
