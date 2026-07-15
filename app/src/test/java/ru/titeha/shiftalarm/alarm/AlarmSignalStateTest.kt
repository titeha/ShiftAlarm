package ru.titeha.shiftalarm.alarm

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AlarmSignalStateTest {
    @Before
    fun setUp() {
        AlarmSignalState.markStopped()
    }

    @After
    fun tearDown() {
        AlarmSignalState.markStopped()
    }

    @Test
    fun `до запуска сигнала состояние неактивно`() {
        assertFalse(AlarmSignalState.isRinging.value)
    }

    @Test
    fun `запуск сервиса переводит состояние в активное`() {
        AlarmSignalState.markStarted()

        assertTrue(AlarmSignalState.isRinging.value)
    }

    @Test
    fun `остановка сервиса переводит состояние в неактивное`() {
        AlarmSignalState.markStarted()
        AlarmSignalState.markStopped()

        assertFalse(AlarmSignalState.isRinging.value)
    }
}
