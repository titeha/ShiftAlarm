package ru.titeha.shiftalarm.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.titeha.shiftalarm.data.AlarmEntity
import ru.titeha.shiftalarm.data.AlarmOverride
import ru.titeha.shiftalarm.data.AlarmPeriod
import ru.titeha.shiftalarm.schedule.AlarmTimes
import ru.titeha.shiftalarm.schedule.ShiftCategory

class AlarmEditorSessionTest {
    private val alarm = AlarmEntity(
        id = 7L,
        label = "Работа",
        enabled = true,
        hour = 7,
        minute = 0,
        mode = AlarmEntity.MODE_SHIFT
    )

    @Test
    fun `новая сессия содержит исходные данные`() {
        val period = period(id = 1L)
        val override = override(id = 2L)

        val session = AlarmEditorSession(
            initialAlarm = alarm,
            initialPeriods = listOf(period),
            initialOverrides = listOf(override)
        )

        assertEquals(alarm, session.draftState.value)
        assertEquals(listOf(period), session.periodsState.value)
        assertEquals(listOf(override), session.overridesState.value)
        assertEquals(EditMethod.SHIFT, session.methodState.value)
        assertFalse(session.hasUnsavedChanges())
    }

    @Test
    fun `изменение будильника сохраняется в объекте сессии`() {
        val session = session()

        session.draftState.value =
            session.draftState.value.copy(
                label = "Новая работа",
                hour = 8
            )

        assertEquals(
            "Новая работа",
            session.draftState.value.label
        )
        assertEquals(
            8,
            session.draftState.value.hour
        )
        assertTrue(session.hasUnsavedChanges())
    }

    @Test
    fun `изменение способа расписания считается несохранённым`() {
        val weekly = alarm.copy(
            mode = AlarmEntity.MODE_WEEKLY,
            daysMask = 0
        )

        val session = AlarmEditorSession(
            initialAlarm = weekly,
            initialPeriods = emptyList(),
            initialOverrides = emptyList()
        )

        assertEquals(
            EditMethod.ONCE,
            session.methodState.value
        )

        session.methodState.value =
            EditMethod.WEEKLY

        assertTrue(session.hasUnsavedChanges())
    }

    @Test
    fun `периоды и правки сохраняются в состоянии сессии`() {
        val session = session()

        session.periodsState.value =
            listOf(period(id = 0L))

        session.overridesState.value =
            listOf(override(id = 0L))

        assertEquals(
            1,
            session.periodsState.value.size
        )
        assertEquals(
            1,
            session.overridesState.value.size
        )
        assertTrue(session.hasUnsavedChanges())
    }

    @Test
    fun `внешнее изменение исходных списков не меняет снимок сессии`() {
        val mutablePeriods =
            mutableListOf(period(id = 1L))

        val mutableOverrides =
            mutableListOf(override(id = 2L))

        val session = AlarmEditorSession(
            initialAlarm = alarm,
            initialPeriods = mutablePeriods,
            initialOverrides = mutableOverrides
        )

        mutablePeriods.clear()
        mutableOverrides.clear()

        assertEquals(
            1,
            session.periodsState.value.size
        )
        assertEquals(
            1,
            session.overridesState.value.size
        )
        assertFalse(session.hasUnsavedChanges())
    }

    @Test
    fun `текущий сменный будильник нормализует старую REST полярность`() {
        val legacyAlarm = alarm.copy(
            honorHolidays = true,
            polarity = AlarmEntity.POLARITY_REST
        )

        val session = AlarmEditorSession(
            initialAlarm = legacyAlarm,
            initialPeriods = emptyList(),
            initialOverrides = emptyList()
        )

        assertEquals(
            AlarmEntity.POLARITY_WORK,
            session.currentAlarm().polarity
        )
        assertFalse(session.hasUnsavedChanges())
    }

    @Test
    fun `флаг диалога хранится в объекте сессии`() {
        val session = session()

        session.discardDialogState.value = true

        assertTrue(
            session.discardDialogState.value
        )
    }

    @Test
    fun `календарный будильник определяется как способ CALENDAR даже с пустой маской`() {
        val calendar = alarm.copy(
            mode = AlarmEntity.MODE_CALENDAR,
            daysMask = 0,
            honorHolidays = true
        )

        assertEquals(
            EditMethod.CALENDAR,
            methodOf(calendar)
        )
    }

    @Test
    fun `календарный способ фиксирует режим и праздники и сохраняет маску`() {
        val weekly = alarm.copy(
            mode = AlarmEntity.MODE_WEEKLY,
            daysMask = AlarmTimes.maskOf(
                java.time.DayOfWeek.MONDAY,
                java.time.DayOfWeek.SATURDAY
            ),
            honorHolidays = false
        )

        val effectiveAlarm = effective(
            draft = weekly,
            method = EditMethod.CALENDAR
        )

        assertEquals(AlarmEntity.MODE_CALENDAR, effectiveAlarm.mode)
        assertTrue(effectiveAlarm.honorHolidays)
        // Инвариант D-2: маска не обнуляется — при возврате в weekly дни сохранятся.
        assertEquals(weekly.daysMask, effectiveAlarm.daysMask)
    }

    @Test
    fun `восстановленный календарный черновик не отбрасывается валидатором снимка`() {
        val calendarDraft = alarm.copy(
            mode = AlarmEntity.MODE_CALENDAR,
            daysMask = 0,
            honorHolidays = true
        )

        val session = AlarmEditorSession(
            initialAlarm = calendarDraft,
            initialPeriods = emptyList(),
            initialOverrides = emptyList()
        )

        val restored =
            AlarmEditorSession.fromSnapshot(
                requireNotNull(
                    AlarmEditorSessionSnapshotCodec
                        .decodeOrNull(
                            requireNotNull(
                                AlarmEditorSessionSnapshotCodec
                                    .encodeOrNull(session.toSnapshot())
                            )
                        )
                )
            )

        assertEquals(EditMethod.CALENDAR, restored.methodState.value)
        assertEquals(
            AlarmEntity.MODE_CALENDAR,
            restored.currentAlarm().mode
        )
        assertFalse(restored.hasUnsavedChanges())
    }

    private fun session(): AlarmEditorSession {
        return AlarmEditorSession(
            initialAlarm = alarm,
            initialPeriods = emptyList(),
            initialOverrides = emptyList()
        )
    }

    private fun period(id: Long): AlarmPeriod {
        return AlarmPeriod(
            id = id,
            alarmId = alarm.id,
            fromEpochDay = 10L,
            toEpochDay = 12L,
            reason = "Отпуск"
        )
    }

    private fun override(id: Long): AlarmOverride {
        return AlarmOverride(
            id = id,
            alarmId = alarm.id,
            fromEpochDay = 15L,
            toEpochDay = 15L,
            category = ShiftCategory.DAY.name,
            wakeMinutes = 8 * 60,
            name = "День"
        )
    }
}
