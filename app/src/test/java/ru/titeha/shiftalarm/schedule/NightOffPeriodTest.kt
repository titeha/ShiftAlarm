package ru.titeha.shiftalarm.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.titeha.shiftalarm.data.AlarmEntity
import ru.titeha.shiftalarm.data.AlarmPeriod
import java.time.LocalDate
import java.time.LocalDateTime

class NightOffPeriodTest {
    private val anchor: LocalDate = LocalDate.of(2026, 1, 5)

    private fun at(date: LocalDate, hour: Int, minute: Int): LocalDateTime = date.atTime(hour, minute)

    private fun mdnAlarm() = AlarmEntity(
        mode = AlarmEntity.MODE_SHIFT,
        presetId = "mdn",
        anchorEpochDay = anchor.toEpochDay(),
    )

    private fun period(date: LocalDate) = AlarmPeriod(
        alarmId = 1,
        fromEpochDay = date.toEpochDay(),
        toEpochDay = date.toEpochDay(),
    )

    @Test
    fun `период на первой ночной смене глушит звонок накануне`() {
        val night1 = anchor.plusDays(10)
        val departDay = night1.minusDays(1)

        val next = AlarmTimes.next(
            alarm = mdnAlarm(),
            periods = listOf(period(night1)),
            from = at(departDay, 20, 0),
        )

        assertEquals(at(night1, 21, 0), next)
    }

    @Test
    fun `период на первой ночной смене не глушит звонок на вторую ночную`() {
        val night1 = anchor.plusDays(10)

        val next = AlarmTimes.next(
            alarm = mdnAlarm(),
            periods = listOf(period(night1)),
            from = at(night1, 20, 0),
        )

        assertEquals(at(night1, 21, 0), next)
    }

    @Test
    fun `период на двух соседних ночных сменах глушит оба обслуживающих звонка`() {
        val night1 = anchor.plusDays(10)
        val night2 = anchor.plusDays(11)
        val departDay = night1.minusDays(1)

        val periods = listOf(
            AlarmPeriod(
                alarmId = 1,
                fromEpochDay = night1.toEpochDay(),
                toEpochDay = night2.toEpochDay(),
            ),
        )

        val next = AlarmTimes.next(
            alarm = mdnAlarm(),
            periods = periods,
            from = at(departDay, 20, 0),
        )

        assertNull(next)
    }
}
