package ru.titeha.shiftalarm

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.analog.AnalogTimePicker
import kotlinx.coroutines.launch
import ru.titeha.shiftalarm.alarm.AlarmScheduler
import ru.titeha.shiftalarm.data.AlarmState
import ru.titeha.shiftalarm.data.AlarmStore
import ru.titeha.shiftalarm.schedule.ShiftPresets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      MaterialTheme {
        val context = LocalContext.current
        val store = remember { AlarmStore(context.applicationContext) }
        val scope = rememberCoroutineScope()
        val state by store.state.collectAsState(initial = AlarmState())

        // Сохранить новое состояние и перепланировать будильник.
        fun apply(newState: AlarmState) {
          scope.launch { store.save(newState) }
          AlarmScheduler.applyFromState(context, newState)
        }

        RequestNotificationPermission()

        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          // Режим
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeButton("Каждый день", state.mode == AlarmState.MODE_DAILY) {
              apply(state.copy(mode = AlarmState.MODE_DAILY))
            }
            ModeButton("Смены", state.mode == AlarmState.MODE_SHIFT) {
              apply(
                state.copy(
                  mode = AlarmState.MODE_SHIFT,
                  anchorEpochDay = LocalDate.now().toEpochDay()
                )
              )
            }
          }

          Spacer(Modifier.height(16.dp))

          if (state.mode == AlarmState.MODE_DAILY) {
            AnalogTimePicker(
              time = LocalTime.of(state.hour, state.minute),
              onTimeChange = { t -> apply(state.copy(hour = t.hour, minute = t.minute)) },
              snapLabel = "5 минут"
            )
          } else {
            Text("График:", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            FlowRow(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
              ShiftPresets.all.forEach { preset ->
                ModeButton(preset.title, state.presetId == preset.id) {
                  apply(
                    state.copy(
                      presetId = preset.id,
                      anchorEpochDay = LocalDate.now().toEpochDay()
                    )
                  )
                }
              }
            }
          }

          Spacer(Modifier.height(24.dp))

          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              if (state.enabled) "Будильник включён" else "Будильник выключен",
              style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.width(12.dp))
            Switch(
              checked = state.enabled,
              onCheckedChange = { on -> apply(state.copy(enabled = on)) }
            )
          }

          val next = if (state.enabled) AlarmScheduler.nextTrigger(state) else null
          if (next != null) {
            Spacer(Modifier.height(8.dp))
            Text("Следующий звонок: ${formatNext(next)}", style = MaterialTheme.typography.bodyLarge)
          }

          Spacer(Modifier.height(24.dp))

          OutlinedButton(
            onClick = { AlarmScheduler.schedule(context, System.currentTimeMillis() + 10_000) },
            modifier = Modifier.fillMaxWidth()
          ) { Text("Тест (через 10 сек)") }
        }
      }
    }
  }
}

@Composable
private fun ModeButton(text: String, selected: Boolean, onClick: () -> Unit) {
  if (selected) {
    FilledTonalButton(onClick = onClick) { Text(text) }
  } else {
    OutlinedButton(onClick = onClick) { Text(text) }
  }
}

@Composable
private fun RequestNotificationPermission() {
  val context = LocalContext.current
  val launcher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) {}
  LaunchedEffect(Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
      PackageManager.PERMISSION_GRANTED
    ) {
      launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }
}

/** Формат «дд.мм чч:мм» (или «чч:мм», если сегодня). */
private fun formatNext(dt: LocalDateTime): String {
  val today = LocalDate.now()
  val time = "%02d:%02d".format(dt.hour, dt.minute)
  return if (dt.toLocalDate() == today) {
    "сегодня $time"
  } else {
    "%02d.%02d %s".format(dt.dayOfMonth, dt.monthValue, time)
  }
}
