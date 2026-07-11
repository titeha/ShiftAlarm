package ru.titeha.shiftalarm

import android.content.Intent
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.titeha.shiftalarm.alarm.AlarmService

/** Полноэкранный экран звонка. Сам звук проигрывает [AlarmService]; здесь только текст и «Стоп». */
class AlarmActivity : ComponentActivity() {

  // Название будильника из intent (может обновиться, если пришёл новый звонок — activity singleTask).
  private var label by mutableStateOf("")

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Показать поверх блокировки и разбудить экран.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      setShowWhenLocked(true)
      setTurnScreenOn(true)
    }

    label = intent.getStringExtra(AlarmService.EXTRA_LABEL).orEmpty()

    setContent {
      MaterialTheme {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          // Название будильника, а если оно пустое — дефолт «Подъём!».
          Text(
            AlarmService.displayText(label),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center
          )
          Spacer(Modifier.height(32.dp))
          Button(onClick = { stopAlarm() }) { Text("Стоп") }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    label = intent.getStringExtra(AlarmService.EXTRA_LABEL).orEmpty()
  }

  private fun stopAlarm() {
    AlarmService.stop(this)
    finish()
  }
}
