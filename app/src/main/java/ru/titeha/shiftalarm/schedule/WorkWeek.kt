package ru.titeha.shiftalarm.schedule

import java.time.DayOfWeek

/**
 * «Рабочая неделя» — сколько дней в неделе рабочих ([workDays]) и с какого дня неделя начинается
 * ([weekStart]).
 *
 * В отличие от нейтральной маски звонка будильника (дни могут быть тренировкой/кружком), это ЧЁТКОЕ
 * понятие про труд: рабочие дни идут N ПОДРЯД от начала недели, остальные — выходные по умолчанию.
 * Пример «четырёхдневки на ВАЗе»: `workDays=4, weekStart=MONDAY` → рабочие Пн–Чт, выходные Пт/Сб/Вс.
 *
 * Модель чистая (без Android/I-O) — задаёт лишь БАЗОВЫЙ статус дня недели. Поверх неё
 * [ProductionCalendar] накладывает праздники и переносы. Полярность будильника (буди по рабочим /
 * по выходным) — отдельно и per-alarm.
 *
 * По умолчанию [DEFAULT] = 5 дней с понедельника: рабочие Пн–Пт, выходные Сб/Вс — ровно то, что было
 * захардкожено раньше.
 */
data class WorkWeek(
    val workDays: Int = 5,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
) {
    init {
        require(workDays in 1..7) { "workDays должно быть в диапазоне 1..7, получено $workDays" }
    }

    /** Множество рабочих дней недели: [workDays] дней подряд от [weekStart]. */
    val workingDays: Set<DayOfWeek>
        get() = (0 until workDays)
            .map { offset -> weekStart.plus(offset.toLong()) }
            .toSet()

    /** true — [day] рабочий по этой рабочей неделе (без учёта праздников/переносов). */
    fun isWorkingWeekday(day: DayOfWeek): Boolean = day in workingDays

    /** true — [day] выходной по умолчанию (не входит в рабочие дни недели). */
    fun isWeekend(day: DayOfWeek): Boolean = !isWorkingWeekday(day)

    companion object {
        /** Стандартная пятидневка с понедельника (рабочие Пн–Пт, выходные Сб/Вс). */
        val DEFAULT = WorkWeek()
    }
}
