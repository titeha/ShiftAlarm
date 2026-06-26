package ru.titeha.shiftalarm.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.analog.AnalogTimePicker
import ru.titeha.shiftalarm.data.AlarmEntity
import ru.titeha.shiftalarm.data.AlarmPeriod
import ru.titeha.shiftalarm.schedule.AlarmTimes
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val DOW_SHORT = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

/** Дата в формате текущей локали устройства (РФ/КЗ → 25.06.2026, US → Jun 25, 2026). */
internal fun LocalDate.localized(): String =
  format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))

/**
 * Экран добавления/редактирования одного будильника.
 * Возвращает результат через [onSave] (с уже подготовленной записью) или [onCancel].
 */
@Composable
fun AlarmEditorScreen(
  initial: AlarmEntity,
  initialPeriods: List<AlarmPeriod>,
  onSave: (AlarmEntity, List<AlarmPeriod>) -> Unit,
  onCancel: () -> Unit
) {
  var draft by remember { mutableStateOf(initial) }
  var periods by remember { mutableStateOf(initialPeriods) }
  val isNew = initial.id == 0L

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      if (isNew) "Новый будильник" else "Будильник",
      style = MaterialTheme.typography.headlineSmall
    )
    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
      value = draft.label,
      onValueChange = { draft = draft.copy(label = it) },
      label = { Text("Название (необязательно)") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(16.dp))

    // Режим
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      ModeChip("По дням недели", draft.mode == AlarmEntity.MODE_WEEKLY) {
        draft = draft.copy(mode = AlarmEntity.MODE_WEEKLY)
      }
      ModeChip("Смены", draft.mode == AlarmEntity.MODE_SHIFT) {
        draft = draft.copy(
          mode = AlarmEntity.MODE_SHIFT,
          anchorEpochDay = if (draft.anchorEpochDay == 0L)
            LocalDate.now().toEpochDay() else draft.anchorEpochDay
        )
      }
    }
    Spacer(Modifier.height(16.dp))

    if (draft.mode == AlarmEntity.MODE_WEEKLY) {
      WeeklyEditor(draft) { draft = it }
    } else {
      ShiftEditor(
        draft = draft,
        periods = periods,
        onChange = { draft = it },
        onAddPeriod = { periods = periods + it },
        onRemovePeriod = { p -> periods = periods - p }
      )
    }

    Spacer(Modifier.height(24.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Отмена") }
      Button(onClick = { onSave(draft, periods) }, modifier = Modifier.weight(1f)) { Text("Сохранить") }
    }
  }
}

@Composable
private fun WeeklyEditor(draft: AlarmEntity, onChange: (AlarmEntity) -> Unit) {
  AnalogTimePicker(
    time = LocalTime.of(draft.hour, draft.minute),
    onTimeChange = { t -> onChange(draft.copy(hour = t.hour, minute = t.minute)) },
    snapLabel = "5 минут",
    nowLabel = "Сейчас"
  )
  Spacer(Modifier.height(16.dp))

  Text("Дни повтора:", style = MaterialTheme.typography.titleMedium)
  Spacer(Modifier.height(8.dp))
  FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
  ) {
    DayOfWeek.entries.forEach { day ->
      val on = AlarmTimes.maskHas(draft.daysMask, day)
      val toggle = {
        val bit = AlarmTimes.bitOf(day)
        onChange(draft.copy(daysMask = if (on) draft.daysMask and bit.inv() else draft.daysMask or bit))
      }
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { toggle() }
      ) {
        Checkbox(checked = on, onCheckedChange = { toggle() })
        Text(DOW_SHORT[day.value - 1])
      }
    }
  }
  Spacer(Modifier.height(8.dp))

  if (draft.daysMask == 0) {
    Text("Без выбранных дней — разовый будильник.", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Switch(
        checked = draft.deleteAfterFiring,
        onCheckedChange = { onChange(draft.copy(deleteAfterFiring = it)) }
      )
      Spacer(Modifier.width(8.dp))
      Text("Удалить после срабатывания", style = MaterialTheme.typography.bodyMedium)
    }
  }
}

@Composable
private fun ShiftEditor(
  draft: AlarmEntity,
  periods: List<AlarmPeriod>,
  onChange: (AlarmEntity) -> Unit,
  onAddPeriod: (AlarmPeriod) -> Unit,
  onRemovePeriod: (AlarmPeriod) -> Unit
) {
  ShiftCycleEditor(draft = draft, onChange = onChange)
  Spacer(Modifier.height(8.dp))
  Text(
    "Отсчёт цикла — с ${LocalDate.ofEpochDay(draft.anchorEpochDay).localized()} (день 1).",
    style = MaterialTheme.typography.bodySmall
  )

  Spacer(Modifier.height(16.dp))
  VacationSection(
    alarmId = draft.id,
    periods = periods,
    onAdd = onAddPeriod,
    onRemove = onRemovePeriod
  )

  Spacer(Modifier.height(16.dp))
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Switch(
      checked = draft.freezeCycleDuringOff,
      onCheckedChange = { onChange(draft.copy(freezeCycleDuringOff = it)) }
    )
    Spacer(Modifier.width(8.dp))
    Column {
      Text("Заморозить цикл на время отпуска", style = MaterialTheme.typography.bodyMedium)
      Text(
        if (draft.freezeCycleDuringOff)
          "После отпуска цикл продолжится с той смены, где ты ушёл."
        else
          "Цикл крутится по календарю; после отпуска — смена «по графику».",
        style = MaterialTheme.typography.bodySmall
      )
    }
  }
}

@Composable
internal fun ModeChip(text: String, selected: Boolean, onClick: () -> Unit) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = { Text(text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
  )
}
