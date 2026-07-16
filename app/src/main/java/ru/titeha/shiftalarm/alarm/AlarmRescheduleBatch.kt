package ru.titeha.shiftalarm.alarm

import kotlinx.coroutines.CancellationException
import ru.titeha.shiftalarm.data.AlarmEntity

/** Ошибка перепланирования одного конкретного будильника. */
data class AlarmRescheduleFailure(
    val alarmId: Long,
    val reason: String
)

/** Итог массового перепланирования. */
data class AlarmRescheduleReport(
    val totalCount: Int,
    val succeededCount: Int,
    val failures: List<AlarmRescheduleFailure>
) {
    init {
        require(totalCount >= 0) {
            "Общее число будильников не может быть отрицательным."
        }

        require(succeededCount >= 0) {
            "Число успешных операций не может быть отрицательным."
        }

        require(succeededCount + failures.size == totalCount) {
            "Число успешных и ошибочных операций должно совпадать с общим числом."
        }
    }

    val failedCount: Int
        get() = failures.size

    val allSucceeded: Boolean
        get() = failures.isEmpty()

    /** Короткий перечень id для Logcat и диагностической сводки. */
    fun failedIds(limit: Int = 5): String {
        require(limit > 0) {
            "Лимит списка id должен быть положительным."
        }

        if (failures.isEmpty()) {
            return ""
        }

        val shown = failures
            .take(limit)
            .joinToString(", ") {
                it.alarmId.toString()
            }

        val hiddenCount = failures.size - limit

        return if (hiddenCount > 0) {
            "$shown и ещё $hiddenCount"
        } else {
            shown
        }
    }
}

/**
 * Последовательно выполняет операцию для набора будильников.
 *
 * Ошибка одного элемента записывается в отчёт и не мешает обработать остальные.
 * [CancellationException] всегда пробрасывается наружу: отменённую корутину
 * нельзя превращать в обычную ошибку одного будильника.
 */
object AlarmRescheduleBatch {
    suspend fun run(
        alarms: List<AlarmEntity>,
        operation: suspend (AlarmEntity) -> Unit
    ): AlarmRescheduleReport {
        var succeededCount = 0
        val failures = mutableListOf<AlarmRescheduleFailure>()

        for (alarm in alarms) {
            try {
                operation(alarm)
                succeededCount++
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failures += AlarmRescheduleFailure(
                    alarmId = alarm.id,
                    reason = errorReason(error)
                )
            }
        }

        return AlarmRescheduleReport(
            totalCount = alarms.size,
            succeededCount = succeededCount,
            failures = failures
        )
    }

    /** Безопасное короткое описание исключения для внутренней диагностики. */
    private fun errorReason(error: Throwable): String {
        val type = error.javaClass.simpleName
            .ifBlank { "Ошибка" }

        val message = error.message
            ?.trim()
            ?.take(MAX_REASON_LENGTH)
            .orEmpty()

        return if (message.isBlank()) {
            type
        } else {
            "$type: $message"
        }
    }

    private const val MAX_REASON_LENGTH = 160
}
