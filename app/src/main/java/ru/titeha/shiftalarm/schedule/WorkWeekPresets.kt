package ru.titeha.shiftalarm.schedule

import java.time.DayOfWeek

/**
 * Пресеты рабочей недели пользователя и построение [WorkWeek] из настройки.
 * Чистая логика (без Android и I/O).
 *
 * Это НЕ дни звонка: маска будильника остаётся нейтральной и единственным источником «дня звонка»
 * (`docs/WORK_WEEK_AND_STUDY.md` §1). Рабочая неделя входит в расчёт только через правило
 * `transfersApply` и как инвертируемая неделя для полярности REST — см. [PersonalDayResolver].
 */
enum class WorkWeekPreset(val title: String, val days: Set<DayOfWeek>) {
    FIVE_DAY_MON_FRI(
        "5/2, Пн–Пт",
        setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
        )
    ),
    SIX_DAY_MON_SAT(
        "6-дневка, Пн–Сб",
        setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
        )
    ),
    FOUR_DAY_MON_THU(
        "4-дневка, Пн–Чт",
        setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY
        )
    ),

    /** Произвольный набор: дни берутся из сохранённой маски. */
    CUSTOM("Свой набор", emptySet());

    companion object {
        /**
         * Значение по умолчанию. Совпадает с [CountryProfile.RU.standardWeek], поэтому у будильников,
         * созданных до появления настройки, правило переносов не меняется.
         */
        val DEFAULT: WorkWeekPreset = FIVE_DAY_MON_FRI

        /** Пресет по сохранённому имени; неизвестное, старое или пустое значение → [DEFAULT]. */
        fun byName(name: String?): WorkWeekPreset =
            entries.firstOrNull { it.name == name?.trim() } ?: DEFAULT
    }
}

/**
 * Рабочая неделя из настройки. Для [WorkWeekPreset.CUSTOM] дни берутся из [customMask] — формат
 * маски тот же, что у будильника (Пн = бит 0 … Вс = бит 6, см. [AlarmTimes.bitOf]).
 *
 * Пустая маска у [WorkWeekPreset.CUSTOM] — незавершённое или повреждённое состояние настройки,
 * поэтому откатываемся на неделю по умолчанию. Пустая [WorkWeek] сделала бы «по выходным»
 * ежедневным, так что защитное чтение здесь обязательно.
 */
fun workWeekOf(preset: WorkWeekPreset, customMask: Int): WorkWeek = when {
    preset != WorkWeekPreset.CUSTOM -> WorkWeek(preset.days)
    customMask == 0 -> WorkWeek(WorkWeekPreset.DEFAULT.days)
    else -> WorkWeek.fromMask(customMask)
}
