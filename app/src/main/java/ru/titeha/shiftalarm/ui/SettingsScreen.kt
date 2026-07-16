package ru.titeha.shiftalarm.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.titeha.shiftalarm.data.HolidayCalendarRepository
import ru.titeha.shiftalarm.ui.theme.ThemeMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Экран настроек приложения. Пока тема (режим + динамические цвета). Сюда будут добавляться
 * будущие настройки (страна праздников, правила отпуска и т.п.).
 */
@Composable
fun SettingsScreen(
  themeMode: ThemeMode,
  dynamicColor: Boolean,
  onThemeMode: (ThemeMode) -> Unit,
  onDynamicColor: (Boolean) -> Unit,
  onRunSelfTest: () -> Unit = {},
  onOpenPhoneSetup: (() -> Unit)? = null,
  onBack: () -> Unit,
) {
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
        Text("Настройки", style = MaterialTheme.typography.headlineSmall)
        TextButton(onClick = onBack) { Text("Назад") }
      }
      Spacer(Modifier.height(16.dp))

      Text("Тема", style = MaterialTheme.typography.titleMedium)
      Spacer(Modifier.height(8.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = themeMode == ThemeMode.SYSTEM,
          onClick = { onThemeMode(ThemeMode.SYSTEM) },
          label = { Text("Система") }
        )
        FilterChip(
          selected = themeMode == ThemeMode.LIGHT,
          onClick = { onThemeMode(ThemeMode.LIGHT) },
          label = { Text("Светлая") }
        )
        FilterChip(
          selected = themeMode == ThemeMode.DARK,
          onClick = { onThemeMode(ThemeMode.DARK) },
          label = { Text("Тёмная") }
        )
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Switch(checked = dynamicColor, onCheckedChange = onDynamicColor)
          Spacer(Modifier.width(8.dp))
          Column {
            Text("Динамические цвета", style = MaterialTheme.typography.bodyMedium)
            Text(
              "Подстраивать палитру под обои (Material You).",
              style = MaterialTheme.typography.bodySmall
            )
          }
        }
      }

      Spacer(Modifier.height(24.dp))
      Text("Надёжность", style = MaterialTheme.typography.titleMedium)
      Spacer(Modifier.height(8.dp))
      TextButton(onClick = onRunSelfTest) { Text("Проверить будильник") }
      Text(
        "Тестовый звонок примерно через минуту — проверить весь путь сигнала (можно заблокировать экран).",
        style = MaterialTheme.typography.labelSmall
      )
      if (onOpenPhoneSetup != null) {
        Spacer(Modifier.height(12.dp))
        Text(
          "На вашей прошивке будильник может не срабатывать после перезагрузки без автозапуска.",
          style = MaterialTheme.typography.bodySmall
        )
        TextButton(onClick = onOpenPhoneSetup) { Text("Настроить телефон") }
      }

      Spacer(Modifier.height(24.dp))
      Text("Праздничный календарь", style = MaterialTheme.typography.titleMedium)
      Spacer(Modifier.height(8.dp))
      val context = LocalContext.current
      val holidayInfo = remember {
        val repo = HolidayCalendarRepository(context)
        val y = LocalDate.now().year
        listOf(y, y + 1).map { yr -> yr to repo.lastUpdated("RU", yr) }
      }
      Text(
        "Источник: isdayoff.ru (РФ), обновляется в фоне при запуске.",
        style = MaterialTheme.typography.bodySmall
      )
      Spacer(Modifier.height(4.dp))
      holidayInfo.forEach { (yr, ts) ->
        Text(
          if (ts != null) "$yr — обновлён ${formatStamp(ts)}" else "$yr — встроенные данные",
          style = MaterialTheme.typography.bodyMedium
        )
      }
    }
  }
}

private val STAMP_FMT: DateTimeFormatter =
  DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

/** Метка времени (epoch millis) → «дд.мм.гггг чч:мм» в часовом поясе устройства. */
private fun formatStamp(millis: Long): String =
  Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(STAMP_FMT)
