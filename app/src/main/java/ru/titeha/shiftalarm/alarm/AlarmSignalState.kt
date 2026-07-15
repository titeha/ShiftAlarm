package ru.titeha.shiftalarm.alarm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Состояние активного сигнала внутри процесса приложения.
 *
 * Источник истины — [AlarmService]. Экран остановки только наблюдает состояние:
 * когда сервис прекращает звук и вибрацию любым способом, экран закрывается сам.
 *
 * Состояние намеренно не сохраняется на диск. После гибели процесса сервис и экран
 * также прекращают существование, поэтому восстанавливать старый сигнал нельзя.
 */
object AlarmSignalState {
    private val _isRinging = MutableStateFlow(false)

    /** true, пока [AlarmService] обслуживает активный сигнал. */
    val isRinging: StateFlow<Boolean> =
        _isRinging.asStateFlow()

    /** Сервис начал обслуживать сигнал. */
    internal fun markStarted() {
        _isRinging.value = true
    }

    /** Сервис полностью остановил сигнал. */
    internal fun markStopped() {
        _isRinging.value = false
    }
}
