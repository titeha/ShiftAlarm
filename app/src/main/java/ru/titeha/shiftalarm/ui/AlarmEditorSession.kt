package ru.titeha.shiftalarm.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import ru.titeha.shiftalarm.data.AlarmEntity
import ru.titeha.shiftalarm.data.AlarmOverride
import ru.titeha.shiftalarm.data.AlarmPeriod
import ru.titeha.shiftalarm.schedule.HolidayModePolicy

/** Способ расписания, выбранный в редакторе. */
internal enum class EditMethod {
    ONCE,
    WEEKLY,
    SHIFT
}

/** Определить способ расписания по сохранённой записи. */
internal fun methodOf(alarm: AlarmEntity): EditMethod {
    return when {
        alarm.mode == AlarmEntity.MODE_SHIFT ->
            EditMethod.SHIFT

        alarm.daysMask == 0 ->
            EditMethod.ONCE

        else ->
            EditMethod.WEEKLY
    }
}

/**
 * Привести общий черновик к фактически выбранному способу расписания.
 *
 * Черновики других способов остаются в [draft], но расчёт, проверка и сохранение
 * используют только результат этой функции.
 */
internal fun effective(
    draft: AlarmEntity,
    method: EditMethod
): AlarmEntity {
    return when (method) {
        EditMethod.ONCE ->
            draft.copy(
                mode = AlarmEntity.MODE_WEEKLY,
                daysMask = 0
            )

        EditMethod.WEEKLY ->
            draft.copy(
                mode = AlarmEntity.MODE_WEEKLY
            )

        EditMethod.SHIFT ->
            HolidayModePolicy.normalize(
                draft.copy(
                    mode = AlarmEntity.MODE_SHIFT
                )
            )
    }
}

/**
 * Состояние одной сессии редактора будильника.
 *
 * Объект хранится в [AlarmListViewModel], поэтому переживает пересоздание
 * Activity при повороте экрана. Compose-экран только читает и изменяет
 * предоставленные [MutableState].
 *
 * Этот класс не восстанавливает состояние после уничтожения процесса.
 * Поддержка process death будет добавлена отдельным шагом через SavedStateHandle.
 */
class AlarmEditorSession(
    initialAlarm: AlarmEntity,
    initialPeriods: List<AlarmPeriod>,
    initialOverrides: List<AlarmOverride>
) {
    internal val initialMethod: EditMethod =
        methodOf(initialAlarm)

    private val initialAlarmSnapshot: AlarmEntity =
        effective(
            initialAlarm,
            initialMethod
        )

    private val initialPeriodsSnapshot: List<AlarmPeriod> =
        initialPeriods.toList()

    private val initialOverridesSnapshot: List<AlarmOverride> =
        initialOverrides.toList()

    internal val draftState: MutableState<AlarmEntity> =
        mutableStateOf(initialAlarm)

    internal val periodsState: MutableState<List<AlarmPeriod>> =
        mutableStateOf(initialPeriods.toList())

    internal val overridesState: MutableState<List<AlarmOverride>> =
        mutableStateOf(initialOverrides.toList())

    internal val methodState: MutableState<EditMethod> =
        mutableStateOf(initialMethod)

    internal val discardDialogState: MutableState<Boolean> =
        mutableStateOf(false)

    val isNew: Boolean =
        initialAlarm.id == 0L

    /** Текущий черновик в форме, пригодной для расчёта и сохранения. */
    internal fun currentAlarm(): AlarmEntity {
        return effective(
            draft = draftState.value,
            method = methodState.value
        )
    }

    /** Есть ли изменения относительно состояния при открытии редактора. */
    internal fun hasUnsavedChanges(): Boolean {
        return methodState.value != initialMethod ||
            AlarmEditorDirtyState.hasUnsavedChanges(
                initialAlarm = initialAlarmSnapshot,
                currentAlarm = currentAlarm(),
                initialPeriods = initialPeriodsSnapshot,
                currentPeriods = periodsState.value,
                initialOverrides = initialOverridesSnapshot,
                currentOverrides = overridesState.value
            )
    }
}
