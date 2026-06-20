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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.analog.AnalogTimePicker
import ru.titeha.shiftalarm.alarm.AlarmScheduler
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      MaterialTheme {
        val context = LocalContext.current
        var time by remember { mutableStateOf(LocalTime.of(7, 0)) }
        var status by remember { mutableStateOf("") }

        // Разрешение на уведомления (Android 13+) — без него не покажется экран звонка.
        val notifPermission = rememberLauncherForActivityResult(
          ActivityResultContracts.RequestPermission()
        ) {}
        LaunchedEffect(Unit) {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
          ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
          }
        }

        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text("Подъём в: $time", style = MaterialTheme.typography.titleLarge)
          Spacer(Modifier.height(16.dp))

          AnalogTimePicker(
            time = time,
            onTimeChange = { time = it },
            snapLabel = "5 минут"
          )

          Spacer(Modifier.height(16.dp))

          Button(
            onClick = {
              val triggerAt = nextTriggerMillis(time)
              AlarmScheduler.schedule(context, triggerAt)
              status = "Будильник установлен на %02d:%02d".format(time.hour, time.minute)
            },
            modifier = Modifier.fillMaxWidth()
          ) { Text("Поставить будильник") }

          Spacer(Modifier.height(8.dp))

          OutlinedButton(
            onClick = {
              AlarmScheduler.schedule(context, System.currentTimeMillis() + 10_000)
              status = "Тест: будильник через 10 секунд"
            },
            modifier = Modifier.fillMaxWidth()
          ) { Text("Тест (через 10 сек)") }

          Spacer(Modifier.height(8.dp))

          OutlinedButton(
            onClick = {
              AlarmScheduler.cancel(context)
              status = "Будильник отменён"
            },
            modifier = Modifier.fillMaxWidth()
          ) { Text("Отменить") }

          if (status.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(status, style = MaterialTheme.typography.bodyLarge)
          }
        }
      }
    }
  }
}

/** Ближайший момент срабатывания для выбранного времени (сегодня, иначе завтра). */
private fun nextTriggerMillis(time: LocalTime): Long {
  val now = LocalDateTime.now()
  var target = now.toLocalDate().atTime(time)
  if (!target.isAfter(now)) target = target.plusDays(1)
  return target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
