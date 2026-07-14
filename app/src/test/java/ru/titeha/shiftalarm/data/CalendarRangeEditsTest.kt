package ru.titeha.shiftalarm.data

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.titeha.shiftalarm.schedule.ShiftCategory
import java.time.LocalDate

class CalendarRangeEditsTest {
    private val alarmId = 7L

    @Test
    fun `вырезание дня из середины периода сохраняет обе части`() {
        val source = period(id = 42L, fromDay = 1, toDay = 14, reason = "Отпуск")

        val result = CalendarRangeEdits.removeFromPeriods(
            periods = listOf(source),
            fromEpochDay = day(5),
            toEpochDay = day(5)
        )

        assertEquals(
            listOf(
                source.copy(id = 0L, fromEpochDay = day(1), toEpochDay = day(4)),
                source.copy(id = 0L, fromEpochDay = day(6), toEpochDay = day(14))
            ),
            result
        )
    }

    @Test
    fun `вырезание начала периода сохраняет правую часть`() {
        val source = period(id = 42L, fromDay = 1, toDay = 14)

        val result = CalendarRangeEdits.removeFromPeriods(
            periods = listOf(source),
            fromEpochDay = day(1),
            toEpochDay = day(3)
        )

        assertEquals(
            listOf(source.copy(id = 0L, fromEpochDay = day(4), toEpochDay = day(14))),
            result
        )
    }

    @Test
    fun `вырезание конца периода сохраняет левую часть`() {
        val source = period(id = 42L, fromDay = 1, toDay = 14)

        val result = CalendarRangeEdits.removeFromPeriods(
            periods = listOf(source),
            fromEpochDay = day(10),
            toEpochDay = day(14)
        )

        assertEquals(
            listOf(source.copy(id = 0L, fromEpochDay = day(1), toEpochDay = day(9))),
            result
        )
    }

    @Test
    fun `вырезание всего периода удаляет его`() {
        val source = period(id = 42L, fromDay = 1, toDay = 14)

        val result = CalendarRangeEdits.removeFromPeriods(
            periods = listOf(source),
            fromEpochDay = day(1),
            toEpochDay = day(14)
        )

        assertEquals(emptyList<AlarmPeriod>(), result)
    }

    @Test
    fun `непересекающийся период остаётся без изменений и сохраняет id`() {
        val source = period(id = 42L, fromDay = 1, toDay = 4)

        val result = CalendarRangeEdits.removeFromPeriods(
            periods = listOf(source),
            fromEpochDay = day(8),
            toEpochDay = day(10)
        )

        assertEquals(listOf(source), result)
    }

    @Test
    fun `замена части периода сохраняет внешние фрагменты`() {
        val vacation = period(
            id = 42L,
            fromDay = 1,
            toDay = 14,
            reason = "Отпуск"
        )

        val replacement = period(
            fromDay = 5,
            toDay = 6,
            reason = "Отгул"
        )

        val result = CalendarRangeEdits.replacePeriod(
            periods = listOf(vacation),
            replacement = replacement
        )

        assertEquals(
            listOf(
                vacation.copy(id = 0L, fromEpochDay = day(1), toEpochDay = day(4)),
                replacement.copy(id = 0L),
                vacation.copy(id = 0L, fromEpochDay = day(7), toEpochDay = day(14))
            ),
            result
        )
    }

    @Test
    fun `вырезание середины правки сохраняет её параметры`() {
        val source = override(
            id = 17L,
            fromDay = 1,
            toDay = 10,
            category = ShiftCategory.NIGHT,
            wakeMinutes = 21 * 60,
            name = "Ночь"
        )

        val result = CalendarRangeEdits.removeFromOverrides(
            overrides = listOf(source),
            fromEpochDay = day(5),
            toEpochDay = day(5)
        )

        assertEquals(
            listOf(
                source.copy(id = 0L, fromEpochDay = day(1), toEpochDay = day(4)),
                source.copy(id = 0L, fromEpochDay = day(6), toEpochDay = day(10))
            ),
            result
        )
    }

    @Test
    fun `замена части правки сохраняет старые части по краям`() {
        val old = override(
            id = 17L,
            fromDay = 1,
            toDay = 10,
            category = ShiftCategory.DAY,
            wakeMinutes = 8 * 60,
            name = "День"
        )

        val replacement = override(
            fromDay = 4,
            toDay = 6,
            category = ShiftCategory.OFF,
            wakeMinutes = null,
            name = "Выходной"
        )

        val result = CalendarRangeEdits.replaceOverride(
            overrides = listOf(old),
            replacement = replacement
        )

        assertEquals(
            listOf(
                old.copy(id = 0L, fromEpochDay = day(1), toEpochDay = day(3)),
                replacement.copy(id = 0L),
                old.copy(id = 0L, fromEpochDay = day(7), toEpochDay = day(10))
            ),
            result
        )
    }

    @Test
    fun `обратное направление выбранного диапазона нормализуется`() {
        val source = period(id = 42L, fromDay = 1, toDay = 10)

        val result = CalendarRangeEdits.removeFromPeriods(
            periods = listOf(source),
            fromEpochDay = day(7),
            toEpochDay = day(4)
        )

        assertEquals(
            listOf(
                source.copy(id = 0L, fromEpochDay = day(1), toEpochDay = day(3)),
                source.copy(id = 0L, fromEpochDay = day(8), toEpochDay = day(10))
            ),
            result
        )
    }

    private fun period(
        id: Long = 0L,
        fromDay: Int,
        toDay: Int,
        reason: String = "Отпуск"
    ): AlarmPeriod {
        return AlarmPeriod(
            id = id,
            alarmId = alarmId,
            fromEpochDay = day(fromDay),
            toEpochDay = day(toDay),
            reason = reason
        )
    }

    private fun override(
        id: Long = 0L,
        fromDay: Int,
        toDay: Int,
        category: ShiftCategory,
        wakeMinutes: Int?,
        name: String
    ): AlarmOverride {
        return AlarmOverride(
            id = id,
            alarmId = alarmId,
            fromEpochDay = day(fromDay),
            toEpochDay = day(toDay),
            category = category.name,
            wakeMinutes = wakeMinutes,
            name = name
        )
    }

    private fun day(dayOfMonth: Int): Long {
        return LocalDate.of(2026, 8, dayOfMonth).toEpochDay()
    }
}
