package ru.titeha.shiftalarm

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
        val time = LocalTime.of(state.hour, state.minute)

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
            onTimeChange = { t ->
              scope.launch { store.save(t.hour, t.minute, state.enabled) }
              if (state.enabled) AlarmScheduler.scheduleAt(context, t.hour, t.minute)
            },
            snapLabel = "5 минут"
          )

          Spacer(Modifier.height(16.dp))

          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              if (state.enabled) "Будильник включён" else "Будильник выключен",
              style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.width(12.dp))
            Switch(
              checked = state.enabled,
              onCheckedChange = { on ->
                scope.launch { store.save(state.hour, state.minute, on) }
                if (on) {
                  AlarmScheduler.scheduleAt(context, state.hour, state.minute)
                } else {
                  AlarmScheduler.cancel(context)
                }
              }
            )
          }

          Spacer(Modifier.height(24.dp))

          OutlinedButton(
            onClick = {
              AlarmScheduler.schedule(context, System.currentTimeMillis() + 10_000)
            },
            modifier = Modifier.fillMaxWidth()
          ) { Text("Тест (через 10 сек)") }
        }
      }
    }
  }
}
