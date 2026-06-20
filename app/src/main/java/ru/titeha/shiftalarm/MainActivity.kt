package ru.titeha.shiftalarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.analog.AnalogTimePicker
import java.time.LocalTime

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      MaterialTheme {
        // Пока — каркас: проверяем, что подключённый с JitPack циферблат работает.
        var time by remember { mutableStateOf(LocalTime.of(7, 0)) }

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
        }
      }
    }
  }
}
