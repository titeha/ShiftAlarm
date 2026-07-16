package ru.titeha.shiftalarm.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.titeha.shiftalarm.alarm.AlarmScheduler
import ru.titeha.shiftalarm.data.AlarmEntity
import ru.titeha.shiftalarm.data.AlarmEventLog
import ru.titeha.shiftalarm.data.AlarmEventType
import ru.titeha.shiftalarm.data.AlarmOverride
import ru.titeha.shiftalarm.data.AlarmPeriod
import ru.titeha.shiftalarm.data.AlarmRepository

/** Состояние экрана списка будильников и сгруппированных дочерних данных. */
data class AlarmListUiState(
    val alarms: List<AlarmEntity> = emptyList(),
    val periodsByAlarm: Map<Long, List<AlarmPeriod>> = emptyMap(),
    val overridesByAlarm: Map<Long, List<AlarmOverride>> = emptyMap()
)

/**
 * ViewModel экрана списка: держит потоки Room и операции над будильниками.
 *
 * Сохранение имеет отдельное состояние, чтобы редактор не закрывался раньше
 * результата транзакции и не запускал несколько параллельных сохранений.
 */
class AlarmListViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AlarmRepository(app)

    val uiState: StateFlow<AlarmListUiState> =
        combine(
            repo.all,
            repo.allPeriods,
            repo.allOverrides
        ) { alarms, periods, overrides ->
            AlarmListUiState(
                alarms = alarms,
                periodsByAlarm = periods.groupBy { it.alarmId },
                overridesByAlarm = overrides.groupBy { it.alarmId }
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AlarmListUiState()
        )

    private val saveCoordinator = AlarmSaveCoordinator(
        persist = { alarm, periods, overrides ->
            repo.saveWithChildren(alarm, periods, overrides)
        },
        reschedule = { savedAlarm ->
            AlarmScheduler.reschedule(
                context = context(),
                repo = repo,
                alarm = savedAlarm
            )
        },
        onError = { stage, error ->
            val message = when (stage) {
                AlarmSaveStage.DATA ->
                    "Не удалось сохранить данные будильника"

                AlarmSaveStage.SCHEDULING ->
                    "Данные сохранены, но системный сигнал не перепланирован"
            }

            Log.e(TAG, message, error)
        }
    )

    private val _saveState =
        MutableStateFlow<AlarmSaveState>(AlarmSaveState.Idle)

    val saveState: StateFlow<AlarmSaveState> =
        _saveState.asStateFlow()

    private val _editorSession =
        MutableStateFlow<AlarmEditorSession?>(null)

    val editorSession: StateFlow<AlarmEditorSession?> =
        _editorSession.asStateFlow()

    private var openEditorJob: Job? = null

    /** Периоды и правки будильника для открытия редактора. */
    suspend fun childrenFor(
        alarm: AlarmEntity
    ): Pair<List<AlarmPeriod>, List<AlarmOverride>> {
        return repo.periodsList(alarm.id) to
            repo.overridesList(alarm.id)
    }

    /** Включить или выключить будильник и перепланировать сигнал. */
    fun setEnabled(
        alarm: AlarmEntity,
        enabled: Boolean
    ) = viewModelScope.launch {
        val updated = alarm.copy(enabled = enabled)
        val id = repo.upsert(updated)

        AlarmScheduler.reschedule(
            context = context(),
            repo = repo,
            alarm = updated.copy(id = id)
        )
    }

    /**
     * Сохранить будильник с периодами и правками.
     *
     * Повторный вызов во время активного сохранения игнорируется.
     */
    fun save(
        alarm: AlarmEntity,
        periods: List<AlarmPeriod>,
        overrides: List<AlarmOverride>
    ) {
        if (_saveState.value is AlarmSaveState.Saving) {
            return
        }

        _saveState.value = AlarmSaveState.Saving

        viewModelScope.launch {
            _saveState.value = when (
                val result = saveCoordinator.save(
                    alarm = alarm,
                    periods = periods,
                    overrides = overrides
                )
            ) {
                is AlarmSaveResult.Saved ->
                    AlarmSaveState.Saved(
                        alarmId = result.alarmId,
                        warningMessage = result.warningMessage
                    )

                is AlarmSaveResult.Failed ->
                    AlarmSaveState.Failed(
                        message = result.message
                    )
            }
        }
    }

    /** Открыть чистую сессию создания нового будильника. */
    fun openNewEditor(alarm: AlarmEntity) {
        if (_saveState.value is AlarmSaveState.Saving) {
            return
        }

        openEditorJob?.cancel()
        openEditorJob = null

        _editorSession.value = AlarmEditorSession(
            initialAlarm = alarm,
            initialPeriods = emptyList(),
            initialOverrides = emptyList()
        )
    }

    /**
     * Загрузить дочерние данные и открыть сессию существующего будильника.
     *
     * Работа идёт в viewModelScope, поэтому поворот Activity не отменяет загрузку.
     */
    fun openEditor(alarm: AlarmEntity) {
        if (_saveState.value is AlarmSaveState.Saving) {
            return
        }

        openEditorJob?.cancel()

        openEditorJob = viewModelScope.launch {
            val periods = repo.periodsList(alarm.id)
            val overrides = repo.overridesList(alarm.id)

            _editorSession.value = AlarmEditorSession(
                initialAlarm = alarm,
                initialPeriods = periods,
                initialOverrides = overrides
            )
        }
    }

    /** Закрыть редактор, если сейчас не идёт сохранение. */
    fun closeEditor() {
        if (_saveState.value is AlarmSaveState.Saving) {
            return
        }

        openEditorJob?.cancel()
        openEditorJob = null
        _editorSession.value = null
    }

    /**
     * Сбросить завершённый результат перед новой сессией редактора
     * или после обработки сообщения.
     */
    fun resetSaveState() {
        if (_saveState.value !is AlarmSaveState.Saving) {
            _saveState.value = AlarmSaveState.Idle
        }
    }

    /** Удалить будильник, снять сигнал и записать событие в журнал. */
    fun delete(alarm: AlarmEntity) = viewModelScope.launch {
        AlarmScheduler.cancel(context(), alarm.id)

        AlarmEventLog(context()).record(
            AlarmEventType.CANCELLED,
            "id=${alarm.id} (удалён)",
            System.currentTimeMillis()
        )

        repo.delete(alarm)
    }

    private fun context(): Application =
        getApplication()

    private companion object {
        const val TAG = "AlarmListViewModel"
    }
}
