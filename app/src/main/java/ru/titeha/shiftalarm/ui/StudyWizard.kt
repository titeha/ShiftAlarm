package ru.titeha.shiftalarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ru.titeha.shiftalarm.schedule.Parity
import ru.titeha.shiftalarm.schedule.StudyPlan
import ru.titeha.shiftalarm.schedule.StudyPlanBuilder
import ru.titeha.shiftalarm.schedule.WeekPairNaming
import ru.titeha.shiftalarm.schedule.orderedDaysOfWeek
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

private val DOW_SHORT = mapOf(
  DayOfWeek.MONDAY to "Пн", DayOfWeek.TUESDAY to "Вт", DayOfWeek.WEDNESDAY to "Ср",
  DayOfWeek.THURSDAY to "Чт", DayOfWeek.FRIDAY to "Пт", DayOfWeek.SATURDAY to "Сб",
  DayOfWeek.SUNDAY to "Вс"
)

/**
 * Мастер создания учебного будильника (аддитивный). Собирает сетку времён подъёма (7 или 14 дней) и
 * отдаёт готовый [StudyPlan] через [onDone]; вызывающий превращает его в обычный сменный будильник.
 * Начало отсчёта недель цикла — всегда понедельник; [weekStart] крутит лишь порядок строк.
 */
@Composable
fun StudyWizard(
  weekStart: DayOfWeek,
  naming: WeekPairNaming,
  onDone: (StudyPlan) -> Unit,
  onDismiss: () -> Unit,
) {
  var step by remember { mutableIntStateOf(1) }
  var twoWeeks by remember { mutableStateOf(false) }
  val grid = remember { mutableStateListOf<LocalTime?>() }
  var parity by remember { mutableStateOf<Parity?>(null) }
  var picking by remember { mutableStateOf<Int?>(null) }

  fun initGrid(two: Boolean) {
    val week = List<LocalTime?>(7) { if (it < 5) LocalTime.of(7, 0) else null } // Пн–Пт 07:00, Сб/Вс выходные
    grid.clear()
    grid.addAll(week)
    if (two) grid.addAll(week)
  }

  fun finish() {
    runCatching {
      StudyPlanBuilder.build(grid.toList(), if (twoWeeks) parity else null, LocalDate.now())
    }.onSuccess(onDone)
  }

  Dialog(onDismissRequest = onDismiss) {
    Card {
      Column(
        Modifier
          .padding(16.dp)
          .verticalScroll(rememberScrollState())
      ) {
        Text("Учебный будильник", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        when (step) {
          1 -> {
            Text("Как устроена учебная неделя?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Button(
              onClick = { twoWeeks = false; initGrid(false); step = 2 },
              modifier = Modifier.fillMaxWidth()
            ) { Text("Одинаковая каждую неделю") }
            Spacer(Modifier.height(8.dp))
            Button(
              onClick = { twoWeeks = true; initGrid(true); step = 2 },
              modifier = Modifier.fillMaxWidth()
            ) { Text("Две недели чередуются (${naming.odd}/${naming.even})") }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onDismiss) { Text("Отмена") }
          }

          2 -> {
            Text("Время подъёма", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (twoWeeks) {
              Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(40.dp))
                Text(naming.odd, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text(naming.even, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
              }
            }
            orderedDaysOfWeek(weekStart).forEach { day ->
              val i = day.value - 1
              Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(DOW_SHORT.getValue(day), Modifier.width(40.dp))
                DayTimeCell(grid, i, Modifier.weight(1f)) { picking = i }
                if (twoWeeks) DayTimeCell(grid, 7 + i, Modifier.weight(1f)) { picking = 7 + i }
              }
            }
            if (twoWeeks) {
              Spacer(Modifier.height(4.dp))
              TextButton(onClick = { for (i in 0 until 7) grid[7 + i] = grid[i] }) {
                Text("Скопировать ${naming.odd} → ${naming.even}")
              }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              TextButton(onClick = { step = 1 }) { Text("Назад") }
              if (twoWeeks) {
                Button(onClick = { step = 3 }, enabled = grid.any { it != null }) { Text("Далее") }
              } else {
                Button(onClick = { finish() }, enabled = grid.any { it != null }) { Text("Готово") }
              }
            }
          }

          else -> {
            Text("Какая неделя сейчас?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FilterChip(
                selected = parity == Parity.ODD,
                onClick = { parity = Parity.ODD },
                label = { Text(naming.odd) }
              )
              FilterChip(
                selected = parity == Parity.EVEN,
                onClick = { parity = Parity.EVEN },
                label = { Text(naming.even) }
              )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              TextButton(onClick = { step = 2 }) { Text("Назад") }
              Button(
                onClick = { finish() },
                enabled = parity != null && grid.any { it != null }
              ) { Text("Готово") }
            }
          }
        }
      }
    }
  }

  picking?.let { idx ->
    TimePickerDialog(
      time = grid[idx] ?: LocalTime.of(7, 0),
      onPick = { grid[idx] = it },
      onDismiss = { picking = null }
    )
  }
}

/** Ячейка дня: «Выходной» (тап → учебный день 07:00) или время (тап → пикер, ×  → выходной). */
@Composable
private fun DayTimeCell(
  grid: MutableList<LocalTime?>,
  index: Int,
  modifier: Modifier = Modifier,
  onPickTime: () -> Unit,
) {
  val time = grid[index]
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    if (time == null) {
      TextButton(onClick = { grid[index] = LocalTime.of(7, 0) }) { Text("Выходной") }
    } else {
      OutlinedButton(onClick = onPickTime) { Text("%02d:%02d".format(time.hour, time.minute)) }
      TextButton(onClick = { grid[index] = null }) { Text("×") }
    }
  }
}
