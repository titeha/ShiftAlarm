package ru.titeha.shiftalarm.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.titeha.shiftalarm.alarm.VendorGuide
import ru.titeha.shiftalarm.alarm.VendorSetup

/**
 * Экран «Настроить телефон» для агрессивных прошивок: пошаговая инструкция + best-effort кнопки в
 * нужные системные настройки (автозапуск, энергосбережение) + ссылка dontkillmyapp.com.
 *
 * Состояние автозапуска система не сообщает, поэтому это не пункт готовности, а руководство:
 * без него на Xiaomi/MIUI будильник не переживает перезагрузку (проверено на устройстве).
 */
@Composable
fun VendorSetupScreen(guide: VendorGuide, onBack: () -> Unit) {
  val context = LocalContext.current

  Scaffold { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState())
        .padding(16.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text("Настроить телефон", style = MaterialTheme.typography.headlineSmall)
        TextButton(onClick = onBack) { Text("Назад") }
      }
      Spacer(Modifier.height(4.dp))
      Text(
        "${guide.vendorName}: чтобы будильник срабатывал после перезагрузки и не выгружался системой, " +
          "нужно один раз разрешить автозапуск и снять ограничения батареи.",
        style = MaterialTheme.typography.bodyMedium
      )
      Spacer(Modifier.height(16.dp))

      guide.steps.forEachIndexed { index, step ->
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
          Text("${index + 1}.", style = MaterialTheme.typography.bodyMedium)
          Spacer(Modifier.width(8.dp))
          Text(step, style = MaterialTheme.typography.bodyMedium)
        }
      }
      Spacer(Modifier.height(16.dp))

      if (guide.autostartComponents.isNotEmpty()) {
        Button(
          onClick = { openAutostart(context, guide.autostartComponents) },
          modifier = Modifier.fillMaxWidth()
        ) { Text("Открыть настройки автозапуска") }
        Spacer(Modifier.height(8.dp))
      }

      OutlinedButton(
        onClick = { safeStart(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
        modifier = Modifier.fillMaxWidth()
      ) { Text("Энергосбережение") }
      Spacer(Modifier.height(8.dp))

      OutlinedButton(
        onClick = {
          safeStart(context, Intent(Intent.ACTION_VIEW, Uri.parse(VendorSetup.DONT_KILL_MY_APP_URL)))
        },
        modifier = Modifier.fillMaxWidth()
      ) { Text("Справочник dontkillmyapp.com") }

      Spacer(Modifier.height(16.dp))
      Text(
        "Если кнопка не открыла нужный экран (зависит от версии прошивки) — найдите «Автозапуск» " +
          "в настройках приложения вручную.",
        style = MaterialTheme.typography.labelSmall
      )
    }
  }
}

/** Пробуем открыть экран автозапуска производителя; ни один не сработал — детали приложения. */
private fun openAutostart(context: android.content.Context, components: List<String>) {
  for (component in components) {
    val parts = component.split("/")
    if (parts.size != 2) continue

    val pkg = parts[0]
    val cls = if (parts[1].startsWith(".")) pkg + parts[1] else parts[1]

    val intent = Intent()
      .setClassName(pkg, cls)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    try {
      context.startActivity(intent)
      return
    } catch (_: Exception) {
      // Экран этого имени на данной прошивке отсутствует — пробуем следующий кандидат.
    }
  }

  // Ни один вендорский экран не открылся — ведём в детали приложения.
  safeStart(
    context,
    Intent(
      Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
      Uri.parse("package:${context.packageName}")
    )
  )
}

/** Запустить интент, не падая, если экрана нет. */
private fun safeStart(context: android.content.Context, intent: Intent) {
  try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  } catch (_: Exception) {
    // Экран недоступен на этой прошивке — молча игнорируем.
  }
}
