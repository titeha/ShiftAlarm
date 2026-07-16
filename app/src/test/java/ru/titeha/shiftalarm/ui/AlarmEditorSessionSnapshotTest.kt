package ru.titeha.shiftalarm.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.titeha.shiftalarm.data.AlarmEntity
import ru.titeha.shiftalarm.data.AlarmOverride
import ru.titeha.shiftalarm.data.AlarmPeriod
import ru.titeha.shiftalarm.schedule.ShiftCategory

class AlarmEditorSessionSnapshotTest {
    @Test
    fun `полный снимок проходит round trip`() {
        val snapshot = sampleSnapshot()

        val encoded =
            AlarmEditorSessionSnapshotCodec
                .encodeOrNull(snapshot)

        val decoded = encoded?.let(
            AlarmEditorSessionSnapshotCodec::decodeOrNull
        )

        assertEquals(snapshot, decoded)
    }

    @Test
    fun `русский текст и специальные символы сохраняются`() {
        val source = sampleSnapshot().copy(
            currentAlarm =
                sampleSnapshot().currentAlarm.copy(
                    label =
                        "Ночная смена | участок №7\nВторая строка",
                    cycleSpec =
                        "N|2100|Ночь\\|бригада\nO||Выходной"
                )
        )

        val decoded =
            AlarmEditorSessionSnapshotCodec
                .encodeOrNull(source)
                ?.let(
                    AlarmEditorSessionSnapshotCodec::decodeOrNull
                )

        assertEquals(source, decoded)
    }

    @Test
    fun `повреждённая строка не восстанавливается`() {
        assertNull(
            AlarmEditorSessionSnapshotCodec
                .decodeOrNull(
                    "это не base64"
                )
        )
    }

    @Test
    fun `слишком большой снимок не помещается в saved state`() {
        val huge = sampleSnapshot().copy(
            currentAlarm =
                sampleSnapshot().currentAlarm.copy(
                    label = "Я".repeat(70_000)
                )
        )

        assertNull(
            AlarmEditorSessionSnapshotCodec
                .encodeOrNull(huge)
        )
    }

    @Test
    fun `восстановленная сессия сохраняет черновик и dirty state`() {
        val original = AlarmEditorSession(
            initialAlarm =
                sampleSnapshot().initialAlarm,
            initialPeriods =
                sampleSnapshot().initialPeriods,
            initialOverrides =
                sampleSnapshot().initialOverrides
        )

        original.draftState.value =
            original.draftState.value.copy(
                label = "Изменённый будильник",
                hour = 9
            )

        original.methodState.value =
            EditMethod.WEEKLY

        original.periodsState.value =
            original.periodsState.value +
                AlarmPeriod(
                    alarmId = 7L,
                    fromEpochDay = 100L,
                    toEpochDay = 102L,
                    reason = "Отгул"
                )

        original.discardDialogState.value = true

        val restored =
            AlarmEditorSession.fromSnapshot(
                requireNotNull(
                    AlarmEditorSessionSnapshotCodec
                        .decodeOrNull(
                            requireNotNull(
                                AlarmEditorSessionSnapshotCodec
                                    .encodeOrNull(
                                        original.toSnapshot()
                                    )
                            )
                        )
                )
            )

        assertEquals(
            "Изменённый будильник",
            restored.draftState.value.label
        )

        assertEquals(
            9,
            restored.draftState.value.hour
        )

        assertEquals(
            EditMethod.WEEKLY,
            restored.methodState.value
        )

        assertEquals(
            2,
            restored.periodsState.value.size
        )

        assertTrue(
            restored.discardDialogState.value
        )

        assertTrue(
            restored.hasUnsavedChanges()
        )
    }

    @Test
    fun `неизменённая сессия после восстановления остаётся чистой`() {
        val initial = sampleSnapshot()
            .copy(
                currentAlarm =
                    sampleSnapshot().initialAlarm,
                currentPeriods =
                    sampleSnapshot().initialPeriods,
                currentOverrides =
                    sampleSnapshot().initialOverrides,
                currentMethod =
                    EditMethod.SHIFT,
                discardDialogVisible =
                    false
            )

        val restored =
            AlarmEditorSession.fromSnapshot(initial)

        assertFalse(
            restored.hasUnsavedChanges()
        )
    }

    private fun sampleSnapshot():
        AlarmEditorSessionSnapshot {
        val initialAlarm = AlarmEntity(
            id = 7L,
            label = "Работа",
            enabled = true,
            hour = 7,
            minute = 30,
            mode = AlarmEntity.MODE_SHIFT,
            daysMask = 0,
            presetId = "2x2",
            anchorEpochDay = 20_000L,
            cycleSpec = null,
            deleteAfterFiring = false,
            freezeCycleDuringOff = true,
            honorHolidays = true,
            polarity = AlarmEntity.POLARITY_WORK
        )

        val initialPeriods = listOf(
            AlarmPeriod(
                id = 11L,
                alarmId = 7L,
                fromEpochDay = 20_010L,
                toEpochDay = 20_014L,
                reason = "Отпуск"
            )
        )

        val initialOverrides = listOf(
            AlarmOverride(
                id = 12L,
                alarmId = 7L,
                fromEpochDay = 20_020L,
                toEpochDay = 20_020L,
                category =
                    ShiftCategory.DAY.name,
                wakeMinutes = 8 * 60 + 15,
                name = "Отработка"
            )
        )

        return AlarmEditorSessionSnapshot(
            initialAlarm = initialAlarm,
            initialPeriods = initialPeriods,
            initialOverrides = initialOverrides,
            currentAlarm = initialAlarm.copy(
                label = "Работа после восстановления",
                cycleSpec =
                    "D|0700|День\nO||Выходной"
            ),
            currentPeriods = initialPeriods,
            currentOverrides = initialOverrides,
            currentMethod = EditMethod.SHIFT,
            discardDialogVisible = true
        )
    }
}
