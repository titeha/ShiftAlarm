package ru.titeha.shiftalarm.alarm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import ru.titeha.shiftalarm.data.AlarmEntity

class AlarmRescheduleBatchTest {
    @Test
    fun `пустой набор возвращает пустой успешный отчёт`() = runBlocking {
        val report = AlarmRescheduleBatch.run(
            alarms = emptyList(),
            operation = {}
        )

        assertEquals(0, report.totalCount)
        assertEquals(0, report.succeededCount)
        assertEquals(0, report.failedCount)
        assertTrue(report.allSucceeded)
    }

    @Test
    fun `все успешные будильники обрабатываются по порядку`() = runBlocking {
        val processed = mutableListOf<Long>()
        val alarms = listOf(
            alarm(1L),
            alarm(2L),
            alarm(3L)
        )

        val report = AlarmRescheduleBatch.run(
            alarms = alarms,
            operation = {
                processed += it.id
            }
        )

        assertEquals(listOf(1L, 2L, 3L), processed)
        assertEquals(3, report.totalCount)
        assertEquals(3, report.succeededCount)
        assertEquals(0, report.failedCount)
        assertTrue(report.allSucceeded)
    }

    @Test
    fun `ошибка одного будильника не останавливает следующие`() = runBlocking {
        val processed = mutableListOf<Long>()
        val alarms = listOf(
            alarm(1L),
            alarm(2L),
            alarm(3L)
        )

        val report = AlarmRescheduleBatch.run(
            alarms = alarms,
            operation = {
                processed += it.id

                if (it.id == 2L) {
                    error("Повреждённый график")
                }
            }
        )

        assertEquals(listOf(1L, 2L, 3L), processed)
        assertEquals(3, report.totalCount)
        assertEquals(2, report.succeededCount)
        assertEquals(1, report.failedCount)
        assertFalse(report.allSucceeded)

        assertEquals(2L, report.failures.single().alarmId)
        assertTrue(
            report.failures.single().reason.contains(
                "Повреждённый график"
            )
        )
    }

    @Test
    fun `несколько ошибок сохраняются в порядке обработки`() = runBlocking {
        val report = AlarmRescheduleBatch.run(
            alarms = listOf(
                alarm(10L),
                alarm(20L),
                alarm(30L)
            ),
            operation = {
                if (it.id != 20L) {
                    throw IllegalStateException(
                        "Ошибка ${it.id}"
                    )
                }
            }
        )

        assertEquals(1, report.succeededCount)
        assertEquals(2, report.failedCount)
        assertEquals(
            listOf(10L, 30L),
            report.failures.map { it.alarmId }
        )
        assertEquals("10, 30", report.failedIds())
    }

    @Test
    fun `короткий список id сообщает о скрытых ошибках`() = runBlocking {
        val report = AlarmRescheduleBatch.run(
            alarms = (1L..7L).map(::alarm),
            operation = {
                error("Ошибка")
            }
        )

        assertEquals(
            "1, 2, 3 и ещё 4",
            report.failedIds(limit = 3)
        )
    }

    @Test
    fun `отмена корутины пробрасывается и прекращает пакет`() = runBlocking {
        val processed = mutableListOf<Long>()

        try {
            AlarmRescheduleBatch.run(
                alarms = listOf(
                    alarm(1L),
                    alarm(2L),
                    alarm(3L)
                ),
                operation = {
                    processed += it.id

                    if (it.id == 2L) {
                        throw CancellationException(
                            "Операция отменена"
                        )
                    }
                }
            )

            fail("Ожидалась CancellationException")
        } catch (_: CancellationException) {
            // Ожидаемое поведение.
        }

        assertEquals(
            listOf(1L, 2L),
            processed
        )
    }

    private fun alarm(id: Long): AlarmEntity {
        return AlarmEntity(
            id = id,
            label = "Будильник $id"
        )
    }
}
