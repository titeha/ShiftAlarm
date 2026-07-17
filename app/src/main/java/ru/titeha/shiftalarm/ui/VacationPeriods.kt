package ru.titeha.shiftalarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import ru.titeha.shiftalarm.data.AlarmPeriod
import ru.titeha.shiftalarm.schedule.PeriodKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Секция периодов без будильника (отпуск/больничный/отгул) для режима смен:
 * список периодов с удалением + кнопка добавления (открывает [AddPeriodDialog]).
 */
@Composable
fun VacationSection(
  alarmId: Long,
  periods: List<AlarmPeriod>,
  periodKinds: List<PeriodKind>,
  onAdd: (AlarmPeriod) -> Unit,
  onRemove: (AlarmPeriod) -> Unit
) {
  var showAdd by remember { mutableStateOf(false) }

  Column(Modifier.fillMaxWidth()) {
    Text("Периоды без будильника:", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))

    if (periods.isEmpty()) {
      Text(
        "Периодов нет. В отпуск/больничный будильник не звонит.",
        style = MaterialTheme.typography.bodySmall
      )
    } else {
      periods.forEach { p ->
        val from = LocalDate.ofEpochDay(p.fromEpochDay)
        val to = LocalDate.ofEpochDay(p.toEpochDay)
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            "${from.localized()} – ${to.localized()} · ${p.reason}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
          )
          TextButton(onClick = { onRemove(p) }) { Text("Удалить") }
        }
      }
    }

    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = { showAdd = true }) { Text("+ Период") }
  }

  if (showAdd) {
    AddPeriodDialog(
      alarmId = alarmId,
      periodKinds = periodKinds,
      onConfirm = { onAdd(it); showAdd = false },
      onDismiss = { showAdd = false }
    )
  }
}

/**
 * Диалог добавления периода. Два способа ввода:
 *  - «По дату» — [DateRangePicker] (выбор диапазона целиком);
 *  - «На N дней» — дата начала + число дней.
 * Результат в обоих случаях — период [from]..[to] включительно.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPeriodDialog(
  alarmId: Long,
  periodKinds: List<PeriodKind>,
  onConfirm: (AlarmPeriod) -> Unit,
  onDismiss: () -> Unit
) {
  var byRange by remember { mutableStateOf(true) }
  var from by remember { mutableStateOf<LocalDate?>(null) }
  var to by remember { mutableStateOf<LocalDate?>(null) }
  var days by remember { mutableStateOf("7") }
  var kind by remember { mutableStateOf(periodKinds.first()) }
  var showRangePicker by remember { mutableStateOf(false) }
  var showStartPicker by remember { mutableStateOf(false) }

  // Итоговая дата конца: для режима «по дату» — to; для «на N дней» — from + (N-1).
  val computedTo: LocalDate? = if (byRange) to
  else from?.let { f -> days.toIntOrNull()?.takeIf { it >= 1 }?.let { f.plusDays((it - 1).toLong()) } }
  val valid = from != null && computedTo != null && !computedTo.isBefore(from)

  DatePickerDialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
    confirmButton = {
      TextButton(
        enabled = valid,
        onClick = {
          onConfirm(
            AlarmPeriod(
              alarmId = alarmId,
              fromEpochDay = from!!.toEpochDay(),
              toEpochDay = computedTo!!.toEpochDay(),
              reason = kind.label
            )
          )
        }
      ) { Text("Добавить") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
  ) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
      Text("Период без будильника", style = MaterialTheme.typography.titleLarge)
      Spacer(Modifier.height(16.dp))

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = byRange, onClick = { byRange = true }, label = { Text("По дату") })
        FilterChip(selected = !byRange, onClick = { byRange = false }, label = { Text("На N дней") })
      }
      Spacer(Modifier.height(16.dp))

      if (byRange) {
        OutlinedButton(onClick = { showRangePicker = true }) {
          val label = if (from != null && to != null) "${from!!.localized()} – ${to!!.localized()}"
          else "Выбрать даты"
          Text(label)
        }
      } else {
        OutlinedButton(onClick = { showStartPicker = true }) {
          Text(from?.let { "С ${it.localized()}" } ?: "Дата начала")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
          value = days,
          onValueChange = { v -> days = v.filter(Char::isDigit).take(3) },
          label = { Text("Дней") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        if (from != null && computedTo != null) {
          Spacer(Modifier.height(4.dp))
          Text("По ${computedTo.localized()}", style = MaterialTheme.typography.bodySmall)
        }
      }

      Spacer(Modifier.height(16.dp))
      Text("Тип:", style = MaterialTheme.typography.bodyMedium)
      Spacer(Modifier.height(4.dp))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        periodKinds.forEach { k ->
          FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label) })
        }
      }
      Spacer(Modifier.height(8.dp))
    }
  }

  if (showRangePicker) {
    val state = rememberDateRangePickerState()
    DatePickerDialog(
      onDismissRequest = { showRangePicker = false },
      confirmButton = {
        TextButton(onClick = {
          state.selectedStartDateMillis?.let { from = it.utcMillisToDate() }
          state.selectedEndDateMillis?.let { to = it.utcMillisToDate() }
          showRangePicker = false
        }) { Text("OK") }
      },
      dismissButton = { TextButton(onClick = { showRangePicker = false }) { Text("Отмена") } }
    ) {
      DateRangePicker(state = state, modifier = Modifier.height(460.dp))
    }
  }

  if (showStartPicker) {
    val state = rememberDatePickerState()
    DatePickerDialog(
      onDismissRequest = { showStartPicker = false },
      confirmButton = {
        TextButton(onClick = {
          state.selectedDateMillis?.let { from = it.utcMillisToDate() }
          showStartPicker = false
        }) { Text("OK") }
      },
      dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text("Отмена") } }
    ) {
      DatePicker(state = state)
    }
  }
}

/** Material DatePicker отдаёт UTC-полночь в миллисекундах — конвертируем в дату без сдвигов. */
private fun Long.utcMillisToDate(): LocalDate =
  Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
