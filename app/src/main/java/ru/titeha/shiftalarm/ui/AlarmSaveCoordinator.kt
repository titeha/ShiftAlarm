package ru.titeha.shiftalarm.ui

import kotlinx.coroutines.CancellationException
import ru.titeha.shiftalarm.data.AlarmEntity
import ru.titeha.shiftalarm.data.AlarmOverride
import ru.titeha.shiftalarm.data.AlarmPeriod

/** Этап сохранения, на котором произошла техническая ошибка. */
enum class AlarmSaveStage {
    DATA,
    SCHEDULING
}

/** Итог одной попытки сохранения будильника. */
sealed interface AlarmSaveResult {
    /**
     * Данные сохранены.
     *
     * [warningMessage] заполнено, если запись в БД завершилась успешно,
     * но системный сигнал не удалось перепланировать.
     */
    data class Saved(
        val alarmId: Long,
        val warningMessage: String? = null
    ) : AlarmSaveResult

    /** Транзакция данных не завершилась, изменения не применены. */
    data class Failed(
        val message: String
    ) : AlarmSaveResult
}

/** Состояние сохранения для Compose-экрана. */
sealed interface AlarmSaveState {
    data object Idle : AlarmSaveState
    data object Saving : AlarmSaveState

    data class Saved(
        val alarmId: Long,
        val warningMessage: String? = null
    ) : AlarmSaveState

    data class Failed(
        val message: String
    ) : AlarmSaveState
}

/**
 * Выполняет сохранение в два честно различимых этапа:
 *
 * 1. Транзакционно записывает будильник и дочерние данные.
 * 2. Обновляет системный сигнал AlarmManager.
 *
 * Если первый этап не удался, редактор должен остаться открыт.
 *
 * Если первый этап завершился, а второй нет, данные уже находятся в БД.
 * Молчаливый откат в этом месте опасен: он потребовал бы восстанавливать старую
 * запись, периоды, правки и прежний системный сигнал. Поэтому результат считается
 * сохранённым, но содержит заметное предупреждение пользователю.
 *
 * Класс не зависит от Android API и проверяется обычными unit-тестами.
 */
class AlarmSaveCoordinator(
    private val persist: suspend (
        AlarmEntity,
        List<AlarmPeriod>,
        List<AlarmOverride>
    ) -> Long,
    private val reschedule: suspend (AlarmEntity) -> Unit,
    private val onError: (AlarmSaveStage, Throwable) -> Unit = { _, _ -> }
) {
    suspend fun save(
        alarm: AlarmEntity,
        periods: List<AlarmPeriod>,
        overrides: List<AlarmOverride>
    ): AlarmSaveResult {
        val alarmId = try {
            persist(alarm, periods, overrides)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            onError(AlarmSaveStage.DATA, error)

            return AlarmSaveResult.Failed(
                message = DATA_SAVE_ERROR_MESSAGE
            )
        }

        val savedAlarm = alarm.copy(id = alarmId)

        return try {
            reschedule(savedAlarm)

            AlarmSaveResult.Saved(
                alarmId = alarmId
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            onError(AlarmSaveStage.SCHEDULING, error)

            AlarmSaveResult.Saved(
                alarmId = alarmId,
                warningMessage = SCHEDULING_WARNING_MESSAGE
            )
        }
    }

    companion object {
        const val DATA_SAVE_ERROR_MESSAGE =
            "Не удалось сохранить будильник. Изменения не применены. Повторите попытку."

        const val SCHEDULING_WARNING_MESSAGE =
            "Настройки сохранены, но системный сигнал не удалось обновить. " +
                "Проверьте разрешения будильника и повторно сохраните запись."
    }
}
