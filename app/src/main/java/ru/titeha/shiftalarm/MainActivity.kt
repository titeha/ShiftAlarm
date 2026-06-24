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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.launch
import ru.titeha.shiftalarm.alarm.AlarmScheduler
import ru.titeha.shiftalarm.data.AlarmEntity
import ru.titeha.shiftalarm.data.AlarmRepository
import ru.titeha.shiftalarm.schedule.AlarmTimes
import ru.titeha.shiftalarm.schedule.ShiftPresets
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      MaterialTheme {
        val context = LocalContext.current
        val repo = remember { AlarmRepository(context.applicationContext) }
        val scope = rememberCoroutineScope()
        val alarms by repo.all.collectAsState(initial = emptyList())

        RequestNotificationPermission()

        // Сохранить будильник и перепланировать его.
        fun saveAndSchedule(alarm: AlarmEntity) {
          scope.launch {
            val id = repo.upsert(alarm)
            val saved = alarm.copy(id = id)
            AlarmScheduler.reschedule(context, saved)
          }
        }

        fun remove(alarm: AlarmEntity) {
          scope.launch {
            AlarmScheduler.cancel(context, alarm.id)
            repo.delete(alarm)
          }
        }

        Scaffold { padding ->
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(padding)
              .padding(16.dp)
          ) {
            Text("Будильники", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Button(onClick = {
                saveAndSchedule(
                  AlarmEntity(
                    hour = 7, minute = 0,
                    mode = AlarmEntity.MODE_WEEKLY,
                    daysMask = AlarmTimes.maskOf(*DayOfWeek.entries.toTypedArray()),
                    enabled = true
                  )
                )
              }) { Text("+ Будильник") }

              OutlinedButton(onClick = {
                val fireAt = LocalDateTime.now().plusMinutes(1)
                saveAndSchedule(
                  AlarmEntity(
                    label = "Тест",
                    hour = fireAt.hour, minute = fireAt.minute,
                    mode = AlarmEntity.MODE_WEEKLY, daysMask = 0,
                    deleteAfterFiring = true, enabled = true
                  )
                )
              }) { Text("Тест (+1 мин)") }
            }

            Spacer(Modifier.height(12.dp))

            if (alarms.isEmpty()) {
              Text(
                "Список пуст. Добавьте будильник.",
                style = MaterialTheme.typography.bodyMedium
              )
            } else {
              LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(alarms, key = { it.id }) { alarm ->
                  AlarmRow(
                    alarm = alarm,
                    onToggle = { on -> saveAndSchedule(alarm.copy(enabled = on)) },
                    onDelete = { remove(alarm) }
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AlarmRow(
  alarm: AlarmEntity,
  onToggle: (Boolean) -> Unit,
  onDelete: () -> Unit
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          "%02d:%02d".format(alarm.hour, alarm.minute),
          style = MaterialTheme.typography.headlineMedium
        )
        Text(describe(alarm), style = MaterialTheme.typography.bodyMedium)
        if (alarm.enabled) {
          val next = AlarmTimes.next(alarm, LocalDateTime.now())
          if (next != null) {
            Text("след: ${formatNext(next)}", style = MaterialTheme.typography.bodySmall)
          }
        }
      }
      Switch(checked = alarm.enabled, onCheckedChange = onToggle)
      Spacer(Modifier.width(8.dp))
      TextButton(onClick = onDelete) { Text("Удалить") }
    }
  }
}

private val DOW_SHORT = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

/** Человекочитаемое описание режима будильника. */
private fun describe(alarm: AlarmEntity): String {
  if (alarm.mode == AlarmEntity.MODE_SHIFT) {
    val title = ShiftPresets.byId(alarm.presetId)?.title ?: alarm.presetId
    return "Смены: $title"
  }
  val mask = alarm.daysMask
  return when {
    mask == 0 -> if (alarm.deleteAfterFiring) "Разовый (удалится)" else "Разовый"
    mask == AlarmTimes.maskOf(*DayOfWeek.entries.toTypedArray()) -> "Каждый день"
    else -> DayOfWeek.entries
      .filter { AlarmTimes.maskHas(mask, it) }
      .joinToString(", ") { DOW_SHORT[it.value - 1] }
  }
}

private fun formatNext(dt: LocalDateTime): String {
  val today = LocalDate.now()
  val time = "%02d:%02d".format(dt.hour, dt.minute)
  return if (dt.toLocalDate() == today) "сегодня $time"
  else "%02d.%02d %s".format(dt.dayOfMonth, dt.monthValue, time)
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
