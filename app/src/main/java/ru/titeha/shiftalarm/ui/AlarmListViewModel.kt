package ru.titeha.shiftalarm.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.titeha.shiftalarm.alarm.AlarmScheduler
import ru.titeha.shiftalarm.data.AlarmEntity
import ru.titeha.shiftalarm.data.AlarmEventLog
import ru.titeha.shiftalarm.data.AlarmEventType
import ru.titeha.shiftalarm.data.AlarmOverride
import ru.titeha.shiftalarm.data.AlarmPeriod
import ru.titeha.shiftalarm.data.AlarmRepository

/** Состояние экрана списка будильников: сами будильники + их периоды и правки, сгруппированные по id. */
data class AlarmListUiState(
  val alarms: List<AlarmEntity> = emptyList(),
  val periodsByAlarm: Map<Long, List<AlarmPeriod>> = emptyMap(),
  val overridesByAlarm: Map<Long, List<AlarmOverride>> = emptyMap(),
)

/**
 * ViewModel экрана списка: держит состояние (потоки из Room) и операции (сохранение/удаление/
 * переключение с перепланированием). Composable остаётся тонким — только отображение и вызовы.
 */
class AlarmListViewModel(app: Application) : AndroidViewModel(app) {

  private val repo = AlarmRepository(app)

  val uiState: StateFlow<AlarmListUiState> =
    combine(repo.all, repo.allPeriods, repo.allOverrides) { alarms, periods, overrides ->
      AlarmListUiState(
        alarms = alarms,
        periodsByAlarm = periods.groupBy { it.alarmId },
        overridesByAlarm = overrides.groupBy { it.alarmId },
      )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlarmListUiState())

  /** Периоды и правки будильника — для открытия редактора. */
  suspend fun childrenFor(alarm: AlarmEntity): Pair<List<AlarmPeriod>, List<AlarmOverride>> =
    repo.periodsList(alarm.id) to repo.overridesList(alarm.id)

  /** Включить/выключить будильник (не трогая периоды — они уже в БД) и перепланировать. */
  fun setEnabled(alarm: AlarmEntity, enabled: Boolean) = viewModelScope.launch {
    val updated = alarm.copy(enabled = enabled)
    val id = repo.upsert(updated)
    AlarmScheduler.reschedule(context(), repo, updated.copy(id = id))
  }

  /** Сохранить будильник с периодами и правками (одной транзакцией) и перепланировать. */
  fun save(alarm: AlarmEntity, periods: List<AlarmPeriod>, overrides: List<AlarmOverride>) =
    viewModelScope.launch {
      val id = repo.saveWithChildren(alarm, periods, overrides)
      AlarmScheduler.reschedule(context(), repo, alarm.copy(id = id))
    }

  /** Удалить будильник, снять сигнал и записать событие в журнал. */
  fun delete(alarm: AlarmEntity) = viewModelScope.launch {
    AlarmScheduler.cancel(context(), alarm.id)
    AlarmEventLog(context()).record(
      AlarmEventType.CANCELLED, "id=${alarm.id} (удалён)", System.currentTimeMillis()
    )
    repo.delete(alarm)
  }

  private fun context() = getApplication<Application>()
}
