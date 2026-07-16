package ru.titeha.shiftalarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.dp
import ru.titeha.shiftalarm.alarm.AlarmPermissions
import ru.titeha.shiftalarm.alarm.AlarmReadinessIssue
import ru.titeha.shiftalarm.data.AlarmEvent
import ru.titeha.shiftalarm.data.AlarmEventLog
import ru.titeha.shiftalarm.data.AlarmEventType
import ru.titeha.shiftalarm.data.isImportant
import java.time.Instant
import java.time.ZoneId

/**
 * Экран диагностики: статус готовности (разрешения) + журнал событий будильника
 * (запланирован / сработал / пропущен / перепланирован). Помогает понять «почему не зазвонил».
 */
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val log = remember { AlarmEventLog(context) }
  var events by remember { mutableStateOf(log.recent()) }
  var issues by remember { mutableStateOf(AlarmPermissions.issues(context)) }
  var importantOnly by remember { mutableStateOf(true) }

  // Перечитывать журнал и статус разрешений при каждом возврате на экран (в т.ч. из настроек).
  val activity = context as? ComponentActivity
  DisposableEffect(activity) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        events = log.recent()
        issues = AlarmPermissions.issues(context)
      }
    }
    activity?.lifecycle?.addObserver(observer)
    onDispose { activity?.lifecycle?.removeObserver(observer) }
  }

  Scaffold { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(16.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text("Диагностика", style = MaterialTheme.typography.headlineSmall)
        TextButton(onClick = onBack) { Text("Назад") }
      }
      Spacer(Modifier.height(12.dp))

      // Статус готовности по каждому разрешению (выводим из списка проблем, он свеж на ON_RESUME).
      Text("Готовность", style = MaterialTheme.typography.titleMedium)
      Spacer(Modifier.height(4.dp))
      ReadinessRow("Точные будильники", AlarmReadinessIssue.EXACT_ALARM !in issues)
      ReadinessRow("Уведомления", AlarmReadinessIssue.NOTIFICATIONS !in issues)
      ReadinessRow("Полноэкранные уведомления", AlarmReadinessIssue.FULL_SCREEN !in issues)
      ReadinessRow("Энергосбережение (рекомендация)", AlarmReadinessIssue.BATTERY !in issues)
      ReadinessRow("Громкость будильника (рекомендация)", AlarmReadinessIssue.ALARM_VOLUME !in issues)
      Spacer(Modifier.height(16.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text("Журнал событий", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { log.clear(); events = emptyList() }) { Text("Очистить") }
      }
      // Фильтр: скрыть технические события (запланирован/снят/перепланирован), оставить важные.
      FilterChip(
        selected = importantOnly,
        onClick = { importantOnly = !importantOnly },
        label = { Text("Только важные") }
      )
      Spacer(Modifier.height(4.dp))

      val shown = events.filter { !importantOnly || it.type.isImportant }
      if (shown.isEmpty()) {
        Text(
          if (events.isEmpty()) "Событий пока нет." else "Важных событий нет.",
          style = MaterialTheme.typography.bodySmall
        )
      } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          items(shown) { event -> EventRow(event) }
        }
      }
    }
  }
}

@Composable
private fun EventRow(event: AlarmEvent) {
  Column(Modifier.fillMaxWidth()) {
    Text(
      "${formatTime(event.atMillis)} · ${typeLabel(event.type)}",
      style = MaterialTheme.typography.bodyMedium
    )
    Text(event.detail, style = MaterialTheme.typography.bodySmall)
  }
}

private fun formatTime(millis: Long): String {
  val dt = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime()
  return "%02d.%02d %02d:%02d:%02d".format(dt.dayOfMonth, dt.monthValue, dt.hour, dt.minute, dt.second)
}

private fun typeLabel(type: AlarmEventType): String = when (type) {
  AlarmEventType.SCHEDULED -> "Запланирован"
  AlarmEventType.FIRED -> "Сработал"
  AlarmEventType.SKIPPED -> "Пропущен"
  AlarmEventType.CANCELLED -> "Снят"
  AlarmEventType.RESCHEDULED -> "Перепланирован"
  AlarmEventType.SIGNAL_DEGRADED -> "Сигнал только вибрацией"
  AlarmEventType.SNOOZED -> "Отложен"
  AlarmEventType.MISSED -> "Не был выключен"
  AlarmEventType.ERROR -> "Ошибка обработки"
}

@Composable
private fun ReadinessRow(label: String, ok: Boolean) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    Text(
      if (ok) "OK" else "нет",
      style = MaterialTheme.typography.bodyMedium,
      color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    )
  }
}
