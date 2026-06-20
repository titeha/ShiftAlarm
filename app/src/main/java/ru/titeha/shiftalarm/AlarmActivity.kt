package ru.titeha.shiftalarm

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.titeha.shiftalarm.alarm.AlarmService

/** Полноэкранный экран звонка. Сам звук проигрывает [AlarmService]; здесь только кнопка «Стоп». */
class AlarmActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Показать поверх блокировки и разбудить экран.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      setShowWhenLocked(true)
      setTurnScreenOn(true)
    }

    setContent {
      MaterialTheme {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Text("Подъём!", style = MaterialTheme.typography.displaySmall)
          Spacer(Modifier.height(32.dp))
          Button(onClick = { stopAlarm() }) { Text("Стоп") }
        }
      }
    }
  }

  private fun stopAlarm() {
    AlarmService.stop(this)
    finish()
  }
}
