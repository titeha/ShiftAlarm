package ru.titeha.shiftalarm.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import java.time.LocalDateTime

private const val MILLIS_PER_MINUTE = 60_000L

/**
 * Начало минуты для расчётов UI.
 *
 * Секунды и наносекунды обнуляются, чтобы все карточки и предпросмотры
 * использовали одну и ту же временную точку.
 */
internal fun minuteStart(value: LocalDateTime): LocalDateTime {
    return value.withSecond(0).withNano(0)
}

/**
 * Сколько миллисекунд осталось до следующей календарной минуты.
 *
 * Значение всегда находится в диапазоне 1..60000.
 */
internal fun millisUntilNextMinute(value: LocalDateTime): Long {
    val elapsedMillis =
        value.second * 1_000L +
            value.nano / 1_000_000L

    return (MILLIS_PER_MINUTE - elapsedMillis)
        .coerceIn(1L, MILLIS_PER_MINUTE)
}

/**
 * Общее текущее время UI с точностью до минуты.
 *
 * Состояние обновляется на ближайшей границе минуты. При ручном переводе
 * системного времени возможное запаздывание ограничено одной минутой:
 * на следующем такте значение будет перечитано из системных часов.
 */
@Composable
internal fun rememberCurrentMinute(): LocalDateTime {
    val currentMinute by produceState(
        initialValue = minuteStart(LocalDateTime.now())
    ) {
        while (true) {
            val beforeDelay = LocalDateTime.now()

            delay(
                millisUntilNextMinute(beforeDelay)
            )

            value = minuteStart(
                LocalDateTime.now()
            )
        }
    }

    return currentMinute
}
