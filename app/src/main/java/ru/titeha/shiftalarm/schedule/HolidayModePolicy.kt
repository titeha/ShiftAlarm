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
     * Дискриминатор режима будильника для календарной логики — единое место защитного чтения,
     * как в [AlarmTimes.effectiveMode].
     */
    private fun effectiveMode(alarm: AlarmEntity): String = when (alarm.mode) {
        AlarmEntity.MODE_SHIFT -> AlarmEntity.MODE_SHIFT
        AlarmEntity.MODE_CALENDAR -> AlarmEntity.MODE_CALENDAR
        else -> AlarmEntity.MODE_WEEKLY
    }

    /**
     * Поддерживает ли режим отдельную полярность «будить по выходным».
     *
     * После TD03 (Q4=4а) — только режим [AlarmEntity.MODE_CALENDAR]. Недельный
     * и сменный режимы её не поддерживают: для сценария «будить по выходным»
     * используется отдельный будильник в режиме calendar.
     */
    fun supportsRestPolarity(mode: String): Boolean {
        return mode == AlarmEntity.MODE_CALENDAR
    }

    /**
     * Фактическая полярность для расчёта.
     *
     * REST действует только в режиме calendar (проверяется через [effectiveMode],
     * инвариант D-6: неизвестный mode трактуется как weekly → WORK). Остальные
     * режимы, включая legacy-записи weekly+REST, безопасно трактуются как WORK.
     */
    fun effectivePolarity(alarm: AlarmEntity): AlarmPolarity {
        if (effectiveMode(alarm) == AlarmEntity.MODE_CALENDAR) {
            return AlarmPolarity.REST
        }
        return AlarmPolarity.WORK
    }

    /**
     * Привести запись к допустимому состоянию выбранного режима.
     *
     * Сохранена для гашения legacy-REST у weekly/shift (инвариант D-5: колонка
     * polarity остаётся, но движок её не читает вне calendar). Для calendar
     * запись не изменяется; для остальных режимов REST заменяется на WORK.
     */
    fun normalize(alarm: AlarmEntity): AlarmEntity {
        if (
            supportsRestPolarity(effectiveMode(alarm)) ||
            alarm.polarity == AlarmEntity.POLARITY_WORK
        ) {
            return alarm
        }

        return alarm.copy(
            polarity = AlarmEntity.POLARITY_WORK
        )
    }
}
