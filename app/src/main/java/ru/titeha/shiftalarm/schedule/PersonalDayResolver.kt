package ru.titeha.shiftalarm.schedule

import java.time.DayOfWeek

/**
 * Личная рабочая неделя — набор рабочих дней недели пользователя. Для weekly-будильника это его же
 * отмеченные дни (маска), без новых полей и UI. Отличается от госкалендаря: тот про страну, эта — про
 * график конкретного человека.
 */
data class WorkWeek(val workingDays: Set<DayOfWeek>) {
    companion object {
        /** Из маски дней будильника (Пн = бит 0 … Вс = бит 6, как в [AlarmTimes.bitOf]). */
        fun fromMask(daysMask: Int): WorkWeek =
            WorkWeek(DayOfWeek.entries.filterTo(mutableSetOf()) { daysMask and (1 shl (it.value - 1)) != 0 })
    }
}

/**
 * Профиль страны для матрицы «неделя × праздники». Пока только РФ.
 *
 * Официальная государственная шестидневка (отдельный 6-дневный производственный календарь страны) —
 * ВНЕ рамок: до появления такого источника Пн–Сб трактуется как «своя неделя» (действуют только чистые
 * праздники, не переносы). [CountryProfile] — точка расширения под будущий платный модуль по странам.
 */
data class CountryProfile(
    val standardWeek: WorkWeek,
    val defaultWeekStart: DayOfWeek,
) {
    companion object {
        val RU = CountryProfile(
            standardWeek = WorkWeek(
                setOf(
                    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
                )
            ),
            defaultWeekStart = DayOfWeek.MONDAY,
        )
    }
}

/**
 * Резолвер «звонит ли будильник в конкретный день», разводящий личную неделю и госкалендарь по явной
 * матрице композиции (см. `docs/WORKWEEK_AND_HOLIDAYS.md` §2). Чистая логика (без Android/I-O).
 *
 * Правило переносов: они имеют смысл только для СТАНДАРТНОЙ недели страны
 * (`transfersApplicable = workWeek == profile.standardWeek`). Для «своей» недели действуют лишь чистые
 * праздники ([StateDayKind.HOLIDAY]).
 *
 * [calendar] `null` — праздники не учитываются (тумблер выключен): матрица схлопывается в «по личной
 * неделе». Полярность REST требует календаря, поэтому без него ведёт себя как WORK (см. вызывающий код).
 */
class PersonalDayResolver(
    private val profile: CountryProfile,
    private val calendar: ProductionCalendar?,
    private val polarity: AlarmPolarity,
) {

    /** Звонит ли будильник с личной неделей [workWeek] в день [date]. */
    fun ringOn(date: java.time.LocalDate, workWeek: WorkWeek): Boolean {
        val work = isWorkDay(date, workWeek)
        // «По выходным» = инверсия «рабочего дня»: тишина в рабочие, звонок в личные выходные,
        // праздники и перенесённые выходные.
        return if (polarity == AlarmPolarity.REST) !work else work
    }

    /** «Рабочий ли день» для пользователя — по матрице §2. */
    private fun isWorkDay(date: java.time.LocalDate, workWeek: WorkWeek): Boolean {
        val personal = date.dayOfWeek in workWeek.workingDays
        val cal = calendar ?: return personal // праздники выключены — чистая личная неделя
        val transfersApply = workWeek == profile.standardWeek

        return when (cal.kindOf(date)) {
            StateDayKind.HOLIDAY -> false                                  // праздник — тишина всегда
            StateDayKind.TRANSFER_OFF -> if (transfersApply) false else personal
            StateDayKind.TRANSFER_WORK -> if (transfersApply) true else personal
            StateDayKind.NONE, StateDayKind.UNKNOWN, StateDayKind.PRE_HOLIDAY -> personal
        }
    }
}
