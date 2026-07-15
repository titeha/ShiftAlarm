package ru.titeha.shiftalarm.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import ru.titeha.shiftalarm.data.AlarmEntity

class AlarmSaveCoordinatorTest {
    private val alarm = AlarmEntity(
        id = 0L,
        label = "Работа",
        enabled = true,
        hour = 7,
        minute = 0
    )

    @Test
    fun `успешная запись перепланирует будильник с новым id`() = runBlocking {
        var scheduledAlarm: AlarmEntity? = null

        val coordinator = AlarmSaveCoordinator(
            persist = { _, _, _ -> 42L },
            reschedule = { saved ->
                scheduledAlarm = saved
            }
        )

        val result = coordinator.save(
            alarm = alarm,
            periods = emptyList(),
            overrides = emptyList()
        )

        assertTrue(result is AlarmSaveResult.Saved)
        result as AlarmSaveResult.Saved

        assertEquals(42L, result.alarmId)
        assertNull(result.warningMessage)
        assertEquals(42L, scheduledAlarm?.id)
        assertEquals(alarm.label, scheduledAlarm?.label)
    }

    @Test
    fun `ошибка записи не запускает перепланирование`() = runBlocking {
        var rescheduleCalled = false
        var failedStage: AlarmSaveStage? = null

        val coordinator = AlarmSaveCoordinator(
            persist = { _, _, _ ->
                error("БД недоступна")
            },
            reschedule = {
                rescheduleCalled = true
            },
            onError = { stage, _ ->
                failedStage = stage
            }
        )

        val result = coordinator.save(
            alarm = alarm,
            periods = emptyList(),
            overrides = emptyList()
        )

        assertTrue(result is AlarmSaveResult.Failed)
        result as AlarmSaveResult.Failed

        assertEquals(
            AlarmSaveCoordinator.DATA_SAVE_ERROR_MESSAGE,
            result.message
        )
        assertEquals(AlarmSaveStage.DATA, failedStage)
        assertFalse(rescheduleCalled)
    }

    @Test
    fun `ошибка перепланирования не выдаётся за потерю сохранённых данных`() =
        runBlocking {
            var failedStage: AlarmSaveStage? = null

            val coordinator = AlarmSaveCoordinator(
                persist = { _, _, _ -> 77L },
                reschedule = {
                    error("AlarmManager отказал")
                },
                onError = { stage, _ ->
                    failedStage = stage
                }
            )

            val result = coordinator.save(
                alarm = alarm,
                periods = emptyList(),
                overrides = emptyList()
            )

            assertTrue(result is AlarmSaveResult.Saved)
            result as AlarmSaveResult.Saved

            assertEquals(77L, result.alarmId)
            assertEquals(
                AlarmSaveCoordinator.SCHEDULING_WARNING_MESSAGE,
                result.warningMessage
            )
            assertEquals(AlarmSaveStage.SCHEDULING, failedStage)
        }

    @Test
    fun `отмена корутины на этапе записи пробрасывается наружу`() = runBlocking {
        val coordinator = AlarmSaveCoordinator(
            persist = { _, _, _ ->
                throw CancellationException("Отмена")
            },
            reschedule = {}
        )

        try {
            coordinator.save(
                alarm = alarm,
                periods = emptyList(),
                overrides = emptyList()
            )
            fail("Ожидалась CancellationException")
        } catch (_: CancellationException) {
            // Ожидаемое поведение.
        }
    }

    @Test
    fun `отмена корутины на этапе перепланирования пробрасывается наружу`() =
        runBlocking {
            val coordinator = AlarmSaveCoordinator(
                persist = { _, _, _ -> 15L },
                reschedule = {
                    throw CancellationException("Отмена")
                }
            )

            try {
                coordinator.save(
                    alarm = alarm,
                    periods = emptyList(),
                    overrides = emptyList()
                )
                fail("Ожидалась CancellationException")
            } catch (_: CancellationException) {
                // Ожидаемое поведение.
            }
        }
}
