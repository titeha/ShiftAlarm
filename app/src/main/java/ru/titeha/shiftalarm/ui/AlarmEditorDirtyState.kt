package ru.titeha.shiftalarm.ui

import ru.titeha.shiftalarm.data.AlarmEntity
import ru.titeha.shiftalarm.data.AlarmOverride
import ru.titeha.shiftalarm.data.AlarmPeriod

/**
 * Чистая проверка несохранённых изменений редактора.
 *
 * Порядок периодов и правок не влияет на смысл расписания, поэтому перед сравнением
 * списки приводятся к стабильному порядку. Все поля записей, включая id, участвуют
 * в сравнении.
 */
object AlarmEditorDirtyState {
    /**
     * true, если пользователь изменил сам будильник, периоды или правки календаря.
     *
     * Выбранный способ расписания сравнивается отдельно в `AlarmEditorScreen`,
     * потому что это UI-состояние, а не отдельное поле `AlarmEntity`.
     */
    fun hasUnsavedChanges(
        initialAlarm: AlarmEntity,
        currentAlarm: AlarmEntity,
        initialPeriods: List<AlarmPeriod>,
        currentPeriods: List<AlarmPeriod>,
        initialOverrides: List<AlarmOverride>,
        currentOverrides: List<AlarmOverride>
    ): Boolean {
        return initialAlarm != currentAlarm ||
            normalizePeriods(initialPeriods) != normalizePeriods(currentPeriods) ||
            normalizeOverrides(initialOverrides) != normalizeOverrides(currentOverrides)
    }

    private fun normalizePeriods(periods: List<AlarmPeriod>): List<AlarmPeriod> {
        return periods.sortedWith(
            compareBy<AlarmPeriod> { it.fromEpochDay }
                .thenBy { it.toEpochDay }
                .thenBy { it.reason }
                .thenBy { it.alarmId }
                .thenBy { it.id }
        )
    }

    private fun normalizeOverrides(
        overrides: List<AlarmOverride>
    ): List<AlarmOverride> {
        return overrides.sortedWith(
            compareBy<AlarmOverride> { it.fromEpochDay }
                .thenBy { it.toEpochDay }
                .thenBy { it.category }
                .thenBy { it.wakeMinutes ?: -1 }
                .thenBy { it.name }
                .thenBy { it.alarmId }
                .thenBy { it.id }
        )
    }
}
