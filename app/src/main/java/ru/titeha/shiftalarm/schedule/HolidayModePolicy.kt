package ru.titeha.shiftalarm.schedule

import ru.titeha.shiftalarm.data.AlarmEntity

/**
 * Правила совместимости производственного календаря с режимами будильника.
 *
 * Полярность REST — отдельный простой календарный будильник с одним временем.
 * Она не является модификатором сменного цикла: у смен уже есть собственные
 * времена для утра, дня и ночи.
 *
 * Поэтому для MODE_SHIFT календарь работает только как фильтр WORK:
 * официальные нерабочие дни глушат звонки графика, но не подменяют их скрытым
 * полем AlarmEntity.hour/minute.
 */
object HolidayModePolicy {
    /**
     * Поддерживает ли режим отдельную полярность «будить по выходным».
     *
     * Сменный график её не поддерживает: для такого сценария пользователь
     * создаёт отдельный простой будильник.
     */
    fun supportsRestPolarity(mode: String): Boolean {
        return mode != AlarmEntity.MODE_SHIFT
    }

    /**
     * Фактическая полярность для расчёта.
     *
     * Старые или повреждённые сменные записи с REST безопасно трактуются как WORK.
     */
    fun effectivePolarity(alarm: AlarmEntity): AlarmPolarity {
        if (!supportsRestPolarity(alarm.mode)) {
            return AlarmPolarity.WORK
        }

        return if (alarm.polarity == AlarmEntity.POLARITY_REST) {
            AlarmPolarity.REST
        } else {
            AlarmPolarity.WORK
        }
    }

    /**
     * Привести запись к допустимому состоянию выбранного режима.
     *
     * Для сменного графика сохраняется только WORK. Остальные режимы
     * не изменяются.
     */
    fun normalize(alarm: AlarmEntity): AlarmEntity {
        if (
            supportsRestPolarity(alarm.mode) ||
            alarm.polarity == AlarmEntity.POLARITY_WORK
        ) {
            return alarm
        }

        return alarm.copy(
            polarity = AlarmEntity.POLARITY_WORK
        )
    }
}
