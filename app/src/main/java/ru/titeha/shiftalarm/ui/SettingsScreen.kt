package ru.titeha.shiftalarm.ui

import android.os.Build
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import android.hardware.Sensor
import android.hardware.SensorManager
import ru.titeha.shiftalarm.alarm.DismissMode
import ru.titeha.shiftalarm.alarm.FeatureFlags
import ru.titeha.shiftalarm.alarm.RingConfig
import ru.titeha.shiftalarm.schedule.WeekPairNaming
import ru.titeha.shiftalarm.schedule.WeekStart
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
  ringConfig: RingConfig = RingConfig(),
  onRingConfig: (RingConfig) -> Unit = {},
  dismissMode: DismissMode = DismissMode.NORMAL,
  onDismissMode: (DismissMode) -> Unit = {},
  weekStart: WeekStart = WeekStart.AUTO,
  onWeekStart: (WeekStart) -> Unit = {},
  weekPairNaming: WeekPairNaming = WeekPairNaming.PARITY,
  onWeekPairNaming: (WeekPairNaming) -> Unit = {},
  onRunSelfTest: () -> Unit = {},
  onOpenPhoneSetup: (() -> Unit)? = null,
  onBack: () -> Unit,
) {
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
      Text("Звонок", style = MaterialTheme.typography.titleMedium)
      Spacer(Modifier.height(8.dp))
      Text("Интервал снуза, мин", style = MaterialTheme.typography.bodyMedium)
      Spacer(Modifier.height(4.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(5, 10, 15).forEach { minutes ->
          FilterChip(
            selected = ringConfig.snoozeIntervalMinutes == minutes,
            onClick = {
              if (ringConfig.snoozeIntervalMinutes != minutes) {
                onRingConfig(ringConfig.copy(snoozeIntervalMinutes = minutes))
              }
            },
            label = { Text(minutes.toString()) }
          )
        }
      }
      Spacer(Modifier.height(8.dp))
      Text("Сколько раз откладывать", style = MaterialTheme.typography.bodyMedium)
      Spacer(Modifier.height(4.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0, 1, 2, 3, 5).forEach { limit ->
          FilterChip(
            selected = ringConfig.maxSnoozes == limit,
            onClick = {
              if (ringConfig.maxSnoozes != limit) onRingConfig(ringConfig.copy(maxSnoozes = limit))
            },
            label = { Text(limit.toString()) }
          )
        }
      }
      Spacer(Modifier.height(4.dp))
      Text(
        if (ringConfig.maxSnoozes == 0) "0 — снуз выключен, кнопки «Отложить» не будет."
        else "После лимита звонок больше не откладывается.",
        style = MaterialTheme.typography.labelSmall
      )

      Spacer(Modifier.height(12.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(
          checked = ringConfig.autoRepeatEnabled,
          onCheckedChange = { onRingConfig(ringConfig.copy(autoRepeatEnabled = it)) }
        )
        Spacer(Modifier.width(8.dp))
        Column {
          Text("Авто-перезвон невыключенного", style = MaterialTheme.typography.bodyMedium)
          Text(
            "Если звонок не выключили, сам отложится (в пределах лимита).",
            style = MaterialTheme.typography.bodySmall
          )
        }
      }
      if (ringConfig.autoRepeatEnabled) {
        Spacer(Modifier.height(8.dp))
        Text("Через сколько минут авто-перезвон", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(5, 10, 15).forEach { minutes ->
            FilterChip(
              selected = ringConfig.ringDurationMinutes == minutes,
              onClick = {
                if (ringConfig.ringDurationMinutes != minutes) {
                  onRingConfig(ringConfig.copy(ringDurationMinutes = minutes))
                }
              },
              label = { Text(minutes.toString()) }
            )
          }
        }
      }

      if (FeatureFlags.HARD_MODE) {
        val sensorCtx = LocalContext.current
        val hasStepDetector = remember { sensorPresent(sensorCtx, Sensor.TYPE_STEP_DETECTOR) }
        val hasAccelerometer = remember { sensorPresent(sensorCtx, Sensor.TYPE_ACCELEROMETER) }

        Spacer(Modifier.height(24.dp))
        Text("Выключение звонка", style = MaterialTheme.typography.titleMedium)
        Text(
          "Задание усложняет «Стоп»; «Отложить» работает всегда.",
          style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = dismissMode == DismissMode.NORMAL,
            onClick = { onDismissMode(DismissMode.NORMAL) },
            label = { Text("Обычное") }
          )
          FilterChip(
            selected = dismissMode == DismissMode.MATH,
            onClick = { onDismissMode(DismissMode.MATH) },
            label = { Text("Пример") }
          )
          if (hasStepDetector) {
            FilterChip(
              selected = dismissMode == DismissMode.STEPS,
              onClick = { onDismissMode(DismissMode.STEPS) },
              label = { Text("Шаги") }
            )
          }
          if (hasAccelerometer) {
            FilterChip(
              selected = dismissMode == DismissMode.SHAKE,
              onClick = { onDismissMode(DismissMode.SHAKE) },
              label = { Text("Тряска") }
            )
          }
        }
      }

      Spacer(Modifier.height(24.dp))
      Text("Начало недели", style = MaterialTheme.typography.titleMedium)
      Text(
        "Только вид: порядок колонок в календаре и чипов дней. На расчёт звонка не влияет.",
        style = MaterialTheme.typography.bodySmall
      )
      Spacer(Modifier.height(8.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
          WeekStart.AUTO to "Авто",
          WeekStart.MONDAY to "Пн",
          WeekStart.SUNDAY to "Вс",
          WeekStart.SATURDAY to "Сб",
        ).forEach { (value, title) ->
          FilterChip(
            selected = weekStart == value,
            onClick = { if (weekStart != value) onWeekStart(value) },
            label = { Text(title) }
          )
        }
      }

      Spacer(Modifier.height(24.dp))
      Text("Учебные недели (чёт/нечёт)", style = MaterialTheme.typography.titleMedium)
      Text(
        "Как называть пару чередующихся недель в учебных будильниках.",
        style = MaterialTheme.typography.bodySmall
      )
      Spacer(Modifier.height(8.dp))
      androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        WeekPairNaming.entries.forEach { naming ->
          FilterChip(
            selected = weekPairNaming == naming,
            onClick = { if (weekPairNaming != naming) onWeekPairNaming(naming) },
            label = { Text("${naming.odd}/${naming.even}") }
          )
        }
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

/** Есть ли на устройстве датчик [type] — иначе опцию жёсткого режима не показываем. */
private fun sensorPresent(context: android.content.Context, type: Int): Boolean =
  context.getSystemService(SensorManager::class.java)?.getDefaultSensor(type) != null

private val STAMP_FMT: DateTimeFormatter =
  DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

/** Метка времени (epoch millis) → «дд.мм.гггг чч:мм» в часовом поясе устройства. */
private fun formatStamp(millis: Long): String =
  Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(STAMP_FMT)
