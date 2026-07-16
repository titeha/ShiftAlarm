package ru.titeha.shiftalarm.ui

import android.app.Application
import android.util.Log
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.titeha.shiftalarm.alarm.AlarmScheduler
import ru.titeha.shiftalarm.data.AlarmEntity
import ru.titeha.shiftalarm.data.AlarmEventLog
import ru.titeha.shiftalarm.data.AlarmEventType
import ru.titeha.shiftalarm.data.AlarmOverride
import ru.titeha.shiftalarm.data.AlarmPeriod
import ru.titeha.shiftalarm.data.AlarmRepository
import ru.titeha.shiftalarm.data.SettingsStore
import ru.titeha.shiftalarm.schedule.ProductionCalendars
import ru.titeha.shiftalarm.schedule.WorkWeek

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
class AlarmListViewModel(
    app: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(app) {
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

    /**
     * Одноразовые сообщения пользователю (Snackbar) об ошибках операций вне редактора:
     * включение/выключение, удаление, открытие. Технические подробности идут в журнал.
     */
    private val _userMessages = Channel<String>(Channel.BUFFERED)

    val userMessages: Flow<String> = _userMessages.receiveAsFlow()

    private val restoredEditorSession: AlarmEditorSession? =
        savedStateHandle.get<String>(EDITOR_SESSION_KEY)
            ?.let { AlarmEditorSessionSnapshotCodec.decodeOrNull(it) }
            ?.let { AlarmEditorSession.fromSnapshot(it) }

    private val _editorSession =
        MutableStateFlow(restoredEditorSession)

    val editorSession: StateFlow<AlarmEditorSession?> =
        _editorSession.asStateFlow()

    private var openEditorJob: Job? = null

    private var editorPersistenceJob: Job? = null

    init {
        val encoded =
            savedStateHandle.get<String>(EDITOR_SESSION_KEY)

        when {
            restoredEditorSession != null ->
                observeEditorSession(restoredEditorSession)

            encoded != null -> {
                // Повреждённый или устаревший снимок не пытаемся восстановить повторно.
                savedStateHandle.remove<String>(EDITOR_SESSION_KEY)

                Log.w(
                    TAG,
                    "Сохранённый черновик редактора повреждён и был удалён"
                )
            }
        }
    }

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
        try {
            val updated = alarm.copy(enabled = enabled)
            val id = repo.upsert(updated)

            AlarmScheduler.reschedule(
                context = context(),
                repo = repo,
                alarm = updated.copy(id = id)
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Не удалось переключить будильник id=${alarm.id}", error)
            _userMessages.send("Не удалось изменить будильник")
        }
    }

    /**
     * Применить новую рабочую неделю: сохранить в настройки, обновить глобальную неделю движка и
     * перепланировать все включённые будильники, чтобы смена выходных подействовала сразу.
     */
    fun applyWorkWeek(week: WorkWeek) = viewModelScope.launch {
        try {
            SettingsStore(context()).setWorkWeek(week)
            ProductionCalendars.workWeek = week
            AlarmScheduler.rescheduleAll(context(), repo, repo.enabled())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Не удалось применить рабочую неделю", error)
            _userMessages.send("Не удалось применить рабочую неделю")
        }
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

        setEditorSession(
            AlarmEditorSession(
                initialAlarm = alarm,
                initialPeriods = emptyList(),
                initialOverrides = emptyList()
            )
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
            try {
                val periods = repo.periodsList(alarm.id)
                val overrides = repo.overridesList(alarm.id)

                setEditorSession(
                    AlarmEditorSession(
                        initialAlarm = alarm,
                        initialPeriods = periods,
                        initialOverrides = overrides
                    )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Не удалось открыть будильник id=${alarm.id}", error)
                _userMessages.send("Не удалось открыть будильник")
            }
        }
    }

    /** Закрыть редактор, если сейчас не идёт сохранение. */
    fun closeEditor() {
        if (_saveState.value is AlarmSaveState.Saving) {
            return
        }

        openEditorJob?.cancel()
        openEditorJob = null

        editorPersistenceJob?.cancel()
        editorPersistenceJob = null

        savedStateHandle.remove<String>(EDITOR_SESSION_KEY)

        _editorSession.value = null
    }

    /**
     * Установить активную сессию редактора: сохранить снимок и начать наблюдение
     * за изменениями черновика для восстановления после уничтожения процесса.
     */
    private fun setEditorSession(session: AlarmEditorSession) {
        editorPersistenceJob?.cancel()

        _editorSession.value = session

        persistEditorSnapshot(session.toSnapshot())

        observeEditorSession(session)
    }

    /** Писать снимок сессии в SavedStateHandle при каждом изменении черновика. */
    private fun observeEditorSession(session: AlarmEditorSession) {
        editorPersistenceJob?.cancel()

        editorPersistenceJob = viewModelScope.launch {
            snapshotFlow { session.toSnapshot() }
                .distinctUntilChanged()
                .collect { snapshot ->
                    // Пишем, только пока наблюдается всё ещё текущая (не закрытая) сессия.
                    if (_editorSession.value === session) {
                        persistEditorSnapshot(snapshot)
                    }
                }
        }
    }

    /** Записать снимок; повреждённый или слишком большой снимок безопасно игнорируется. */
    private fun persistEditorSnapshot(snapshot: AlarmEditorSessionSnapshot) {
        val encoded =
            AlarmEditorSessionSnapshotCodec.encodeOrNull(snapshot)

        if (encoded == null) {
            savedStateHandle.remove<String>(EDITOR_SESSION_KEY)

            Log.w(
                TAG,
                "Черновик редактора слишком велик или повреждён; " +
                    "восстановление после process death отключено"
            )

            return
        }

        savedStateHandle[EDITOR_SESSION_KEY] = encoded
    }

    /**
     * Самотест звонка: создаёт разовый будильник примерно через минуту и планирует его боевым путём
     * (тот же Scheduler → Receiver → Service → экран). Удаляется после срабатывания (deleteAfterFiring),
     * поэтому в списке не копится. Пользователь может заблокировать экран и проверить полный путь.
     */
    fun runSelfTest() = viewModelScope.launch {
        try {
            val fireAt = java.time.LocalDateTime.now().plusMinutes(1)
            val test = AlarmEntity(
                label = "Проверка будильника",
                enabled = true,
                hour = fireAt.hour,
                minute = fireAt.minute,
                mode = AlarmEntity.MODE_WEEKLY,
                daysMask = 0,
                deleteAfterFiring = true
            )

            val id = repo.upsert(test)
            AlarmScheduler.reschedule(context(), repo, test.copy(id = id))

            _userMessages.send("Тестовый будильник зазвонит примерно через минуту — можно заблокировать экран")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Не удалось запустить самотест звонка", error)
            _userMessages.send("Не удалось запустить проверку")
        }
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
        try {
            AlarmScheduler.cancel(context(), alarm.id)

            AlarmEventLog(context()).record(
                AlarmEventType.CANCELLED,
                "id=${alarm.id} (удалён)",
                System.currentTimeMillis()
            )

            repo.delete(alarm)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Не удалось удалить будильник id=${alarm.id}", error)
            _userMessages.send("Не удалось удалить будильник")
        }
    }

    private fun context(): Application =
        getApplication()

    private companion object {
        const val TAG = "AlarmListViewModel"

        const val EDITOR_SESSION_KEY = "alarm_editor_session_v1"
    }
}
