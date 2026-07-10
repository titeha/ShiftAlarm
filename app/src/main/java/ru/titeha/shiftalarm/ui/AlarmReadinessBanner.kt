package ru.titeha.shiftalarm.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ru.titeha.shiftalarm.alarm.AlarmPermissions
import ru.titeha.shiftalarm.alarm.AlarmReadinessIssue

/**
 * Предупреждение вверху списка: что мешает будильнику надёжно сработать (точные будильники,
 * уведомления, энергосбережение) + кнопка перехода в нужные настройки. Статус перечитывается при
 * каждом возврате в приложение (ON_RESUME), поэтому после настройки баннер сам обновляется/исчезает.
 * Если проблем нет — не показывается.
 */
@Composable
fun AlarmReadinessBanner(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  var issues by remember { mutableStateOf(AlarmPermissions.issues(context)) }

  // Перечитывать при возврате из настроек — через жизненный цикл Activity (без доп. зависимостей).
  val activity = context as? ComponentActivity
  DisposableEffect(activity) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) issues = AlarmPermissions.issues(context)
    }
    activity?.lifecycle?.addObserver(observer)
    onDispose { activity?.lifecycle?.removeObserver(observer) }
  }

  if (issues.isEmpty()) return

  Card(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.errorContainer,
      contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ),
  ) {
    Column(Modifier.padding(12.dp)) {
      Text(
        "Будильник может не сработать",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
      )
      issues.forEach { issue ->
        Spacer(Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(Modifier.weight(1f)) {
            Text(titleOf(issue), style = MaterialTheme.typography.bodyMedium)
            Text(explainOf(issue), style = MaterialTheme.typography.bodySmall)
          }
          TextButton(onClick = {
            runCatching { context.startActivity(AlarmPermissions.settingsIntent(context, issue)) }
          }) { Text("Настроить") }
        }
      }
    }
  }
}

private fun titleOf(issue: AlarmReadinessIssue): String = when (issue) {
  AlarmReadinessIssue.EXACT_ALARM -> "Точные будильники выключены"
  AlarmReadinessIssue.NOTIFICATIONS -> "Уведомления выключены"
  AlarmReadinessIssue.BATTERY -> "Экономия батареи ограничивает приложение"
}

private fun explainOf(issue: AlarmReadinessIssue): String = when (issue) {
  AlarmReadinessIssue.EXACT_ALARM -> "Иначе звонок может опоздать или не сработать."
  AlarmReadinessIssue.NOTIFICATIONS -> "Без них экран звонка может не показаться."
  AlarmReadinessIssue.BATTERY -> "Добавьте приложение в исключения, чтобы звонок не задерживался."
}
