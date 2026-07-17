package ru.titeha.shiftalarm.schedule

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Категория смены — задаёт цвет/подпись в календаре.
 * Независима от того, звонит ли будильник.
 */
enum class ShiftCategory {
    MORNING,
    DAY,
    NIGHT,
    OFF
}

/**
 * Тип смены на один календарный день.
 *
 * - [wakeTime] — во сколько звонить будильнику; null = в этот календарный день не звонить.
 * - [category] — тип дня для календаря (цвет/подпись), НЕ зависит от [wakeTime]: ночная смена
 *   может быть без звонка, а выходной может содержать вечерний звонок, если этот звонок будит
 *   на ночную смену следующего календарного дня.
 */
data class ShiftType(
    val id: String,
    val name: String,
    val wakeTime: LocalTime?,
    val category: ShiftCategory = categoryFromTime(wakeTime),
) {
    /** Есть ли звонок в этом календарном слоте. */
    val isWorkDay: Boolean get() = wakeTime != null

    companion object {
        /** Выходной — слот без звонка. */
        fun off(name: String = "Выходной") = ShiftType("off", name, null, ShiftCategory.OFF)

        /** Категория по времени звонка, если она не задана явно. */
        fun categoryFromTime(wakeTime: LocalTime?): ShiftCategory = when {
            wakeTime == null -> ShiftCategory.OFF
            wakeTime.hour < 10 -> ShiftCategory.MORNING
            wakeTime.hour < 16 -> ShiftCategory.DAY
            else -> ShiftCategory.NIGHT
        }
    }
}

/**
 * Сменный график: упорядоченный цикл слотов, повторяющийся бесконечно и привязанный к
 * опорной дате [anchorDate] (в этот день — слот с индексом 0).
 */
data class ShiftPattern(
    val slots: List<ShiftType>,
    val anchorDate: LocalDate,
) {
    init {
        require(slots.isNotEmpty()) { "Цикл смен не может быть пустым" }
    }

    companion object {
        /** Простая ротация: [workDays] рабочих подряд, затем [restDays] выходных. */
        fun workRest(
            workDays: Int,
            restDays: Int,
            work: ShiftType,
            anchorDate: LocalDate,
            off: ShiftType = ShiftType.off(),
        ): ShiftPattern {
            require(workDays >= 0 && restDays >= 0 && workDays + restDays > 0)
            return ShiftPattern(List(workDays) { work } + List(restDays) { off }, anchorDate)
        }
    }
}

/** Временная подмена: на период [from]..[to] включительно действует смена [shift]. */
data class TemporarySwap(
    val from: LocalDate,
    val to: LocalDate,
    val shift: ShiftType,
) {
    init {
        require(!to.isBefore(from)) { "Конец периода раньше начала" }
    }

    fun covers(date: LocalDate): Boolean = !date.isBefore(from) && !date.isAfter(to)
}

/**
 * Период без будильника (отпуск/больничный/отгул): на [from]..[to] включительно звонка нет.
 *
 * Важно для ночных смен: период относится к дню смены, а не обязательно к календарному дню
 * звонка. Если ночная смена отмечена в календаре на день D, а звонок на неё стоит вечером D-1,
 * период на D должен глушить звонок D-1, но не должен глушить звонок D, который может будить
 * уже на следующую ночную смену.
 */
data class OffPeriod(
    val from: LocalDate,
    val to: LocalDate,
    val reason: String = "Отпуск",
) {
    init {
        require(!to.isBefore(from)) { "Конец периода раньше начала" }
    }

    fun covers(date: LocalDate): Boolean = !date.isBefore(from) && !date.isAfter(to)

    /** Длина периода в днях включительно. */
    val days: Long get() = ChronoUnit.DAYS.between(from, to) + 1
}

/**
 * Полное расписание: базовая ротация + временные подмены + исключения на дату + периоды без
 * будильника.
 *
 * Приоритет резолва смены для календаря: исключение на дату > подмена на период > период без
 * будильника > базовая ротация.
 */
data class ShiftSchedule(
    val base: ShiftPattern,
    val swaps: List<TemporarySwap> = emptyList(),
    val exceptions: Map<LocalDate, ShiftType> = emptyMap(),
    val offPeriods: List<OffPeriod> = emptyList(),
    val freezeCycleDuringOff: Boolean = false,
)

/**
 * Одно срабатывание будильника смены (для превью «Проверка»): [ringAt] — момент звонка;
 * [servedDate] — день обслуживаемой смены (для ночного блока Варианта Б — следующий день после
 * вечернего звонка); [shift] — сама смена; [eveningBefore] — звонок звенит накануне обслуживаемого дня.
 */
data class AlarmOccurrence(
    val ringAt: LocalDateTime,
    val servedDate: LocalDate,
    val shift: ShiftType,
    val eveningBefore: Boolean,
)

/** Чистый (без UI и I/O) движок резолва смены и ближайшего звонка. */
object ShiftEngine {
    /** Какая смена выпадает на [date] по графику [pattern]. Работает и для дат до опорной. */
    fun shiftOn(date: LocalDate, pattern: ShiftPattern): ShiftType {
        val len = pattern.slots.size
        val daysFromAnchor = ChronoUnit.DAYS.between(pattern.anchorDate, date)
        val index = Math.floorMod(daysFromAnchor, len.toLong()).toInt()
        return pattern.slots[index]
    }

    /**
     * Смена на [date] с учётом исключений, подмен и периодов без будильника.
     * Этот метод нужен календарю/UI: день из [OffPeriod] показывается как выходной.
     */
    fun shiftOn(date: LocalDate, schedule: ShiftSchedule): ShiftType {
        schedule.exceptions[date]?.let { return it }
        schedule.swaps.firstOrNull { it.covers(date) }?.let { return it.shift }

        if (schedule.offPeriods.any { it.covers(date) }) {
            return ShiftType.off()
        }

        return baseShiftOn(date, schedule)
    }

    /**
     * Во сколько будить в [date] по базовому паттерну без пользовательских периодов.
     * Для простого паттерна день звонка и день смены совпадают.
     */
    fun wakeTimeOn(date: LocalDate, pattern: ShiftPattern): LocalTime? = shiftOn(date, pattern).wakeTime

    /**
     * Во сколько будить в календарный день [date], или null, если звонка быть не должно.
     *
     * Для дневных/утренних смен звонок относится к этому же дню. Для ночного блока Варианта Б
     * звонок вечером D может относиться к ночной смене, отмеченной в календаре на D+1. Поэтому
     * периоды без будильника проверяются по дню обслуживаемой смены, а не только по дню звонка.
     */
    fun wakeTimeOn(date: LocalDate, schedule: ShiftSchedule): LocalTime? =
        shiftOn(date, schedule).wakeTime

    /**
     * Как [wakeTimeOn], но с учётом производственного календаря: звонок «скрывается», если
     * ОБСЛУЖИВАЕМЫЙ день ([servedDateForAlarmOn]) — государственный ПРАЗДНИК. На смены влияет только
     * [StateDayKind.HOLIDAY]: выходные и переносы решает сам цикл (вахтовик работает и в Сб/Вс).
     * Нужно календарю редактора, чтобы точка-звонок совпадала с реальным планированием.
     */
    fun wakeTimeOn(
        date: LocalDate,
        schedule: ShiftSchedule,
        calendar: ProductionCalendar?,
    ): LocalTime? {
        val served = servedDateForAlarmOn(date, schedule)
        // Зеркалим nextAlarm: время звонка — из слота дня БЕЗ применения периодов (чтобы период на дне
        // звонка не стирал звонок, служащий следующей ночи), а глушим по ОБСЛУЖИВАЕМОМУ дню. Иначе
        // точка-звонок в календаре расходилась бы с реальным планированием на краю «отпуск + ночь».
        val wake = shiftOnIgnoringOffPeriods(date, schedule).wakeTime ?: return null
        if (schedule.offPeriods.any { it.covers(served) }) return null
        if (calendar != null && calendar.kindOf(served) == StateDayKind.HOLIDAY) return null
        return wake
    }

    /**
     * Ближайший момент звонка строго после [from] по расписанию [schedule].
     *
     * И праздники ([calendar]), и периоды без будильника ([ShiftSchedule.offPeriods]) глушат звонок
     * по дню ОБСЛУЖИВАЕМОЙ смены ([servedDateForAlarmOn]), а не по дню вечернего звонка. Для ночного
     * блока Варианта Б это важно: звонок вечером D-1 обслуживает ночь, помеченную на D, поэтому отпуск
     * (и праздник) на D должны глушить именно этот звонок. Само время звонка берётся из слота дня
     * (без вычёркивания периодом), чтобы период не стирал слот, обслуживающий следующую ночь.
     *
     * На смены из госкалендаря влияет ТОЛЬКО [StateDayKind.HOLIDAY]: выходные и переносы решает сам
     * цикл (смены/вахта идут и по Сб/Вс).
     */
    fun nextAlarm(
        from: LocalDateTime,
        schedule: ShiftSchedule,
        searchDays: Int = 366,
        calendar: ProductionCalendar? = null,
    ): LocalDateTime? {
        var date = from.toLocalDate()

        repeat(searchDays) {
            val servedDate = servedDateForAlarmOn(date, schedule)
            val calendarWorking = calendar == null ||
                calendar.kindOf(servedDate) != StateDayKind.HOLIDAY
            val onLeave = schedule.offPeriods.any { it.covers(servedDate) }

            if (calendarWorking && !onLeave) {
                val wake = shiftOnIgnoringOffPeriods(date, schedule).wakeTime
                if (wake != null) {
                    val candidate = date.atTime(wake)
                    if (candidate.isAfter(from)) return candidate
                }
            }

            date = date.plusDays(1)
        }

        return null
    }

    /**
     * Ближайшие [count] срабатываний строго после [from] — для превью «Проверка». Каждое несёт момент
     * звонка, обслуживаемую смену и признак «накануне». Тот же путь, что [nextAlarm], поэтому превью
     * совпадает с реальным планированием (периоды, правки, праздники учитываются одинаково).
     */
    fun nextAlarms(
        from: LocalDateTime,
        schedule: ShiftSchedule,
        count: Int,
        calendar: ProductionCalendar? = null,
    ): List<AlarmOccurrence> {
        val result = mutableListOf<AlarmOccurrence>()
        var cursor = from
        while (result.size < count) {
            val ring = nextAlarm(cursor, schedule, calendar = calendar) ?: break
            val served = servedDateForAlarmOn(ring.toLocalDate(), schedule)
            val shift = shiftOnIgnoringOffPeriods(served, schedule)
            result.add(
                AlarmOccurrence(ring, served, shift, eveningBefore = served != ring.toLocalDate())
            )
            cursor = ring
        }
        return result
    }

    /** Базовая ротация с учётом «заморозки» цикла на время периодов без будильника. */
    private fun baseShiftOn(date: LocalDate, schedule: ShiftSchedule): ShiftType {
        val pattern = schedule.base
        val len = pattern.slots.size
        val raw = ChronoUnit.DAYS.between(pattern.anchorDate, date)
        val effective = if (schedule.freezeCycleDuringOff) {
            raw - frozenDaysBefore(date, pattern.anchorDate, schedule.offPeriods)
        } else {
            raw
        }
        val index = Math.floorMod(effective, len.toLong()).toInt()
        return pattern.slots[index]
    }

    /** Сколько дней периода прошло в полуинтервале [anchor, date). */
    private fun frozenDaysBefore(date: LocalDate, anchor: LocalDate, offPeriods: List<OffPeriod>): Long {
        if (!date.isAfter(anchor)) return 0L

        var count = 0L
        for (period in offPeriods) {
            val lo = maxOf(period.from, anchor)
            val hi = minOf(period.to, date.minusDays(1))
            if (!lo.isAfter(hi)) count += ChronoUnit.DAYS.between(lo, hi) + 1
        }

        return count
    }

    /**
     * Резолв смены без применения [OffPeriod].
     *
     * Метод нужен расчёту звонка: период без будильника должен глушить обслуживаемую смену, но не
     * должен стирать сам слот звонка, если этот слот обслуживает следующую ночную смену.
     */
    private fun shiftOnIgnoringOffPeriods(date: LocalDate, schedule: ShiftSchedule): ShiftType {
        schedule.exceptions[date]?.let { return it }
        schedule.swaps.firstOrNull { it.covers(date) }?.let { return it.shift }
        return baseShiftOn(date, schedule)
    }

    /**
     * День смены, которую обслуживает звонок календарного дня [alarmDate].
     *
     * В обычном случае звонок обслуживает этот же день. В ночном блоке Варианта Б вечерний звонок
     * на выходном или ночном слоте обслуживает ночную смену следующего календарного дня.
     */
    private fun servedDateForAlarmOn(alarmDate: LocalDate, schedule: ShiftSchedule): LocalDate {
        val current = shiftOnIgnoringOffPeriods(alarmDate, schedule)
        if (current.wakeTime == null) return alarmDate

        val next = shiftOnIgnoringOffPeriods(alarmDate.plusDays(1), schedule)
        val currentCanCarryNightAlarm = current.category == ShiftCategory.OFF || current.category == ShiftCategory.NIGHT

        return if (currentCanCarryNightAlarm && next.category == ShiftCategory.NIGHT) {
            alarmDate.plusDays(1)
        } else {
            alarmDate
        }
    }
}
