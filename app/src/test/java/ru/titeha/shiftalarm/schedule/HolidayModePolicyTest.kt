package ru.titeha.shiftalarm.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.titeha.shiftalarm.data.AlarmEntity
import java.time.LocalDate
import java.time.LocalTime

class HolidayModePolicyTest {
    @Before
    fun setUp() {
        /*
         * Другие тесты могут временно подменять источник календаря.
         * Здесь нужен детерминированный встроенный календарь РФ-2026.
         */
        ProductionCalendars.source = null
    }

    @After
    fun tearDown() {
        ProductionCalendars.source = null
    }

    @Test
    fun `сменный режим не поддерживает отдельную полярность REST`() {
        assertFalse(
            HolidayModePolicy.supportsRestPolarity(
                AlarmEntity.MODE_SHIFT
            )
        )
    }

    @Test
    fun `простой режим поддерживает полярность REST`() {
        assertTrue(
            HolidayModePolicy.supportsRestPolarity(
                AlarmEntity.MODE_WEEKLY
            )
        )
    }

    @Test
    fun `старая сменная запись REST рассчитывается как WORK`() {
        val alarm = AlarmEntity(
            mode = AlarmEntity.MODE_SHIFT,
            polarity = AlarmEntity.POLARITY_REST
        )

        assertEquals(
            AlarmPolarity.WORK,
            HolidayModePolicy.effectivePolarity(alarm)
        )
    }

    @Test
    fun `простая запись REST сохраняет свою полярность`() {
        val alarm = AlarmEntity(
            mode = AlarmEntity.MODE_WEEKLY,
            polarity = AlarmEntity.POLARITY_REST
        )

        assertEquals(
            AlarmPolarity.REST,
            HolidayModePolicy.effectivePolarity(alarm)
        )
    }

    @Test
    fun `нормализация сменной записи заменяет REST на WORK`() {
        val alarm = AlarmEntity(
            id = 7L,
            label = "Смена",
            mode = AlarmEntity.MODE_SHIFT,
            polarity = AlarmEntity.POLARITY_REST
        )

        val normalized = HolidayModePolicy.normalize(alarm)

        assertEquals(AlarmEntity.POLARITY_WORK, normalized.polarity)
        assertEquals(alarm.id, normalized.id)
        assertEquals(alarm.label, normalized.label)
    }

    @Test
    fun `нормализация уже допустимой записи не создаёт копию`() {
        val alarm = AlarmEntity(
            mode = AlarmEntity.MODE_SHIFT,
            polarity = AlarmEntity.POLARITY_WORK
        )

        assertSame(
            alarm,
            HolidayModePolicy.normalize(alarm)
        )
    }

    @Test
    fun `старая сменная запись REST использует время смены а не скрытое общее время`() {
        val day = LocalDate.of(2026, 6, 24)

        val alarm = AlarmEntity(
            hour = 23,
            minute = 59,
            mode = AlarmEntity.MODE_SHIFT,
            presetId = "2x2",
            anchorEpochDay = day.toEpochDay(),
            honorHolidays = true,
            polarity = AlarmEntity.POLARITY_REST
        )

        val next = AlarmTimes.next(
            alarm = alarm,
            periods = emptyList(),
            overrides = emptyList(),
            from = day.atTime(6, 0)
        )

        assertEquals(
            day.atTime(7, 0),
            next
        )
    }

    @Test
    fun `старая сменная запись REST глушится в официальный нерабочий день как WORK`() {
        val anchor = LocalDate.of(2026, 6, 1)
        val spec = ShiftCycleCodec.encode(
            listOf(
                ShiftType(
                    id = "daily",
                    name = "Каждый день",
                    wakeTime = LocalTime.of(7, 0)
                )
            )
        )

        val alarm = AlarmEntity(
            hour = 23,
            minute = 59,
            mode = AlarmEntity.MODE_SHIFT,
            cycleSpec = spec,
            anchorEpochDay = anchor.toEpochDay(),
            honorHolidays = true,
            polarity = AlarmEntity.POLARITY_REST
        )

        /*
         * После четверга 11 июня: 12 июня — праздник (пропуск). На смены влияет только HOLIDAY,
         * выходные решает цикл (каждый день) → ближайший звонок в субботу 13 июня в 07:00.
         */
        val next = AlarmTimes.next(
            alarm = alarm,
            periods = emptyList(),
            overrides = emptyList(),
            from = LocalDate.of(2026, 6, 11).atTime(8, 0)
        )

        assertEquals(
            LocalDate.of(2026, 6, 13).atTime(7, 0),
            next
        )
    }

    @Test
    fun `простой REST будильник сохраняет прежнее календарное поведение`() {
        val alarm = AlarmEntity(
            hour = 9,
            minute = 0,
            mode = AlarmEntity.MODE_WEEKLY,
            daysMask = 0,
            honorHolidays = true,
            polarity = AlarmEntity.POLARITY_REST
        )

        val next = AlarmTimes.next(
            alarm = alarm,
            periods = emptyList(),
            overrides = emptyList(),
            from = LocalDate.of(2026, 6, 11).atTime(12, 0)
        )

        assertEquals(
            LocalDate.of(2026, 6, 12).atTime(9, 0),
            next
        )
    }
}
