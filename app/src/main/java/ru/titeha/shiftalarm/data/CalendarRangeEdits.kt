package ru.titeha.shiftalarm.data

/**
 * Чистые операции редактирования включительных диапазонов календаря.
 *
 * Выбранный пользователем диапазон вычитается только из пересекающейся части записи.
 * Дни до и после выбранного диапазона сохраняются. Если диапазон вырезан из середины,
 * исходная запись разбивается на две.
 *
 * Изменённые фрагменты получают id = 0L, потому что после разрезания это уже новые
 * строки для Room. Непересекающиеся записи возвращаются без изменений.
 */
object CalendarRangeEdits {
    /**
     * Удалить [fromEpochDay]..[toEpochDay] из периодов без будильника.
     */
    fun removeFromPeriods(
        periods: List<AlarmPeriod>,
        fromEpochDay: Long,
        toEpochDay: Long
    ): List<AlarmPeriod> {
        val removed = normalizedRange(fromEpochDay, toEpochDay)

        return periods
            .flatMap { period ->
                val source = EpochRange(period.fromEpochDay, period.toEpochDay)
                val fragments = subtract(source, removed)

                if (fragments.size == 1 && fragments.single() == source) {
                    listOf(period)
                } else {
                    fragments.map { fragment ->
                        period.copy(
                            id = 0L,
                            fromEpochDay = fragment.from,
                            toEpochDay = fragment.to
                        )
                    }
                }
            }
            .sortedWith(
                compareBy<AlarmPeriod> { it.fromEpochDay }
                    .thenBy { it.toEpochDay }
                    .thenBy { it.reason }
            )
    }

    /**
     * Удалить [fromEpochDay]..[toEpochDay] из правок календаря.
     */
    fun removeFromOverrides(
        overrides: List<AlarmOverride>,
        fromEpochDay: Long,
        toEpochDay: Long
    ): List<AlarmOverride> {
        val removed = normalizedRange(fromEpochDay, toEpochDay)

        return overrides
            .flatMap { override ->
                val source = EpochRange(override.fromEpochDay, override.toEpochDay)
                val fragments = subtract(source, removed)

                if (fragments.size == 1 && fragments.single() == source) {
                    listOf(override)
                } else {
                    fragments.map { fragment ->
                        override.copy(
                            id = 0L,
                            fromEpochDay = fragment.from,
                            toEpochDay = fragment.to
                        )
                    }
                }
            }
            .sortedWith(
                compareBy<AlarmOverride> { it.fromEpochDay }
                    .thenBy { it.toEpochDay }
                    .thenBy { it.category }
                    .thenBy { it.name }
            )
    }

    /**
     * Заменить выбранную часть периодов записью [replacement].
     *
     * Пересекающиеся части старых периодов вырезаются, внешние фрагменты сохраняются.
     */
    fun replacePeriod(
        periods: List<AlarmPeriod>,
        replacement: AlarmPeriod
    ): List<AlarmPeriod> {
        val normalized = normalizedRange(
            replacement.fromEpochDay,
            replacement.toEpochDay
        )

        val newPeriod = replacement.copy(
            id = 0L,
            fromEpochDay = normalized.from,
            toEpochDay = normalized.to
        )

        return (
            removeFromPeriods(periods, normalized.from, normalized.to) + newPeriod
        ).sortedWith(
            compareBy<AlarmPeriod> { it.fromEpochDay }
                .thenBy { it.toEpochDay }
                .thenBy { it.reason }
        )
    }

    /**
     * Заменить выбранную часть правок записью [replacement].
     *
     * Пересекающиеся части старых правок вырезаются, внешние фрагменты сохраняются.
     */
    fun replaceOverride(
        overrides: List<AlarmOverride>,
        replacement: AlarmOverride
    ): List<AlarmOverride> {
        val normalized = normalizedRange(
            replacement.fromEpochDay,
            replacement.toEpochDay
        )

        val newOverride = replacement.copy(
            id = 0L,
            fromEpochDay = normalized.from,
            toEpochDay = normalized.to
        )

        return (
            removeFromOverrides(overrides, normalized.from, normalized.to) + newOverride
        ).sortedWith(
            compareBy<AlarmOverride> { it.fromEpochDay }
                .thenBy { it.toEpochDay }
                .thenBy { it.category }
                .thenBy { it.name }
        )
    }

    /**
     * Вычесть [removed] из [source].
     *
     * Возможные результаты:
     * - исходный диапазон без изменений;
     * - пустой список;
     * - один укороченный диапазон;
     * - два диапазона, если вырезана середина.
     */
    private fun subtract(
        source: EpochRange,
        removed: EpochRange
    ): List<EpochRange> {
        /*
         * Повреждённую исходную запись не меняем в этом слое.
         * Её защитная обработка относится к чтению данных из БД.
         */
        if (source.from > source.to) {
            return listOf(source)
        }

        if (source.to < removed.from || source.from > removed.to) {
            return listOf(source)
        }

        return buildList {
            if (source.from < removed.from) {
                add(
                    EpochRange(
                        from = source.from,
                        to = minOf(source.to, removed.from - 1L)
                    )
                )
            }

            if (source.to > removed.to) {
                add(
                    EpochRange(
                        from = maxOf(source.from, removed.to + 1L),
                        to = source.to
                    )
                )
            }
        }
    }

    /** Нормализовать направление выбранного диапазона. */
    private fun normalizedRange(first: Long, second: Long): EpochRange {
        return if (first <= second) {
            EpochRange(first, second)
        } else {
            EpochRange(second, first)
        }
    }

    private data class EpochRange(
        val from: Long,
        val to: Long
    )
}
