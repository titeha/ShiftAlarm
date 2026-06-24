package ru.titeha.shiftalarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import ru.titeha.shiftalarm.schedule.AlarmTimes
import ru.titeha.shiftalarm.schedule.ShiftPresets
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

private val DOW_SHORT = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

/**
 * Экран добавления/редактирования одного будильника.
 * Возвращает результат через [onSave] (с уже подготовленной записью) или [onCancel].
 */
@Composable
fun AlarmEditorScreen(
  initial: AlarmEntity,
  onSave: (AlarmEntity) -> Unit,
  onCancel: () -> Unit
) {
  var draft by remember { mutableStateOf(initial) }
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
      ShiftEditor(draft) { draft = it }
    }

    Spacer(Modifier.height(24.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Отмена") }
      Button(onClick = { onSave(draft) }, modifier = Modifier.weight(1f)) { Text("Сохранить") }
    }
  }
}

@Composable
private fun WeeklyEditor(draft: AlarmEntity, onChange: (AlarmEntity) -> Unit) {
  AnalogTimePicker(
    time = LocalTime.of(draft.hour, draft.minute),
    onTimeChange = { t -> onChange(draft.copy(hour = t.hour, minute = t.minute)) },
    snapLabel = "5 минут"
  )
  Spacer(Modifier.height(16.dp))

  Text("Дни повтора:", style = MaterialTheme.typography.titleMedium)
  Spacer(Modifier.height(8.dp))
  FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
  ) {
    DayOfWeek.entries.forEach { day ->
      val on = AlarmTimes.maskHas(draft.daysMask, day)
      FilterChip(
        selected = on,
        onClick = {
          val bit = AlarmTimes.bitOf(day)
          onChange(draft.copy(daysMask = if (on) draft.daysMask and bit.inv() else draft.daysMask or bit))
        },
        label = { Text(DOW_SHORT[day.value - 1]) }
      )
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
      Spacer(Modifier.height(0.dp))
      Text("  Удалить после срабатывания", style = MaterialTheme.typography.bodyMedium)
    }
  }
}

@Composable
private fun ShiftEditor(draft: AlarmEntity, onChange: (AlarmEntity) -> Unit) {
  Text("Время подъёма задаётся графиком смены.", style = MaterialTheme.typography.bodyMedium)
  Spacer(Modifier.height(8.dp))
  Text("График:", style = MaterialTheme.typography.titleMedium)
  Spacer(Modifier.height(8.dp))
  FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
  ) {
    ShiftPresets.all.forEach { preset ->
      ModeChip(preset.title, draft.presetId == preset.id) {
        onChange(draft.copy(presetId = preset.id, anchorEpochDay = LocalDate.now().toEpochDay()))
      }
    }
  }
  Spacer(Modifier.height(8.dp))
  Text(
    "Отсчёт цикла — с сегодняшнего дня (${LocalDate.now()}).",
    style = MaterialTheme.typography.bodySmall
  )
}

@Composable
private fun ModeChip(text: String, selected: Boolean, onClick: () -> Unit) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = { Text(text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
  )
}
