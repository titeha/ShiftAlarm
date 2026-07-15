package ru.titeha.shiftalarm.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.titeha.shiftalarm.data.AlarmEntity
import ru.titeha.shiftalarm.data.AlarmOverride
import ru.titeha.shiftalarm.data.AlarmPeriod
import ru.titeha.shiftalarm.schedule.ShiftCategory

class AlarmEditorDirtyStateTest {
    private val initialAlarm = AlarmEntity(
        id = 7L,
        label = "Работа",
        enabled = true,
        hour = 7,
        minute = 0,
        mode = AlarmEntity.MODE_SHIFT
    )

    @Test
    fun `неизменённые данные не считаются грязными`() {
        assertFalse(
            AlarmEditorDirtyState.hasUnsavedChanges(
                initialAlarm = initialAlarm,
                currentAlarm = initialAlarm.copy(),
                initialPeriods = listOf(period(id = 1L)),
                currentPeriods = listOf(period(id = 1L)),
                initialOverrides = listOf(override(id = 2L)),
                currentOverrides = listOf(override(id = 2L))
            )
        )
    }

    @Test
    fun `изменение будильника считается несохранённым`() {
        assertTrue(
            AlarmEditorDirtyState.hasUnsavedChanges(
                initialAlarm = initialAlarm,
                currentAlarm = initialAlarm.copy(label = "Новая работа"),
                initialPeriods = emptyList(),
                currentPeriods = emptyList(),
                initialOverrides = emptyList(),
                currentOverrides = emptyList()
            )
        )
    }

    @Test
    fun `добавление периода считается несохранённым`() {
        assertTrue(
            AlarmEditorDirtyState.hasUnsavedChanges(
                initialAlarm = initialAlarm,
                currentAlarm = initialAlarm,
                initialPeriods = emptyList(),
                currentPeriods = listOf(period(id = 0L)),
                initialOverrides = emptyList(),
                currentOverrides = emptyList()
            )
        )
    }

    @Test
    fun `удаление периода считается несохранённым`() {
        assertTrue(
            AlarmEditorDirtyState.hasUnsavedChanges(
                initialAlarm = initialAlarm,
                currentAlarm = initialAlarm,
                initialPeriods = listOf(period(id = 1L)),
                currentPeriods = emptyList(),
                initialOverrides = emptyList(),
                currentOverrides = emptyList()
            )
        )
    }

    @Test
    fun `изменение периода считается несохранённым`() {
        val initial = period(id = 1L, reason = "Отпуск")
        val current = initial.copy(reason = "Отгул")

        assertTrue(
            AlarmEditorDirtyState.hasUnsavedChanges(
                initialAlarm = initialAlarm,
                currentAlarm = initialAlarm,
                initialPeriods = listOf(initial),
                currentPeriods = listOf(current),
                initialOverrides = emptyList(),
                currentOverrides = emptyList()
            )
        )
    }

    @Test
    fun `изменение правки календаря считается несохранённым`() {
        val initial = override(id = 2L, wakeMinutes = 8 * 60)
        val current = initial.copy(wakeMinutes = 9 * 60)

        assertTrue(
            AlarmEditorDirtyState.hasUnsavedChanges(
                initialAlarm = initialAlarm,
                currentAlarm = initialAlarm,
                initialPeriods = emptyList(),
                currentPeriods = emptyList(),
                initialOverrides = listOf(initial),
                currentOverrides = listOf(current)
            )
        )
    }

    @Test
    fun `другой порядок периодов не считается изменением`() {
        val first = period(id = 1L, from = 10L, to = 12L)
        val second = period(id = 2L, from = 20L, to = 22L)

        assertFalse(
            AlarmEditorDirtyState.hasUnsavedChanges(
                initialAlarm = initialAlarm,
                currentAlarm = initialAlarm,
                initialPeriods = listOf(first, second),
                currentPeriods = listOf(second, first),
                initialOverrides = emptyList(),
                currentOverrides = emptyList()
            )
        )
    }

    @Test
    fun `другой порядок правок не считается изменением`() {
        val first = override(id = 1L, from = 10L, to = 10L)
        val second = override(id = 2L, from = 20L, to = 20L)

        assertFalse(
            AlarmEditorDirtyState.hasUnsavedChanges(
                initialAlarm = initialAlarm,
                currentAlarm = initialAlarm,
                initialPeriods = emptyList(),
                currentPeriods = emptyList(),
                initialOverrides = listOf(first, second),
                currentOverrides = listOf(second, first)
            )
        )
    }

    private fun period(
        id: Long,
        from: Long = 10L,
        to: Long = 12L,
        reason: String = "Отпуск"
    ): AlarmPeriod {
        return AlarmPeriod(
            id = id,
            alarmId = initialAlarm.id,
            fromEpochDay = from,
            toEpochDay = to,
            reason = reason
        )
    }

    private fun override(
        id: Long,
        from: Long = 15L,
        to: Long = 15L,
        wakeMinutes: Int? = 8 * 60
    ): AlarmOverride {
        return AlarmOverride(
            id = id,
            alarmId = initialAlarm.id,
            fromEpochDay = from,
            toEpochDay = to,
            category = ShiftCategory.DAY.name,
            wakeMinutes = wakeMinutes,
            name = "День"
        )
    }
}
