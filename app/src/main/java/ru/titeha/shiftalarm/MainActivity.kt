package ru.titeha.shiftalarm

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import ru.titeha.shiftalarm.alarm.AlarmScheduler
import ru.titeha.shiftalarm.data.AlarmEntity
import ru.titeha.shiftalarm.data.AlarmEventLog
import ru.titeha.shiftalarm.data.AlarmEventType
import ru.titeha.shiftalarm.data.AlarmOverride
import ru.titeha.shiftalarm.data.AlarmPeriod
import ru.titeha.shiftalarm.data.AlarmRepository
import ru.titeha.shiftalarm.data.SettingsStore
import ru.titeha.shiftalarm.schedule.AlarmTimes
import ru.titeha.shiftalarm.schedule.ShiftCycleCodec
import ru.titeha.shiftalarm.schedule.ShiftPresets
import ru.titeha.shiftalarm.ui.AlarmEditorScreen
import ru.titeha.shiftalarm.ui.AlarmReadinessBanner
import ru.titeha.shiftalarm.ui.DiagnosticsScreen
import ru.titeha.shiftalarm.ui.SettingsScreen
import ru.titeha.shiftalarm.ui.theme.AppTheme
import ru.titeha.shiftalarm.ui.theme.ThemeMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      val settings = remember { SettingsStore(applicationContext) }
      var themeMode by remember { mutableStateOf(settings.themeMode()) }
      var dynamicColor by remember { mutableStateOf(settings.dynamicColor()) }
      val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
      }
      AppTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
        val context = LocalContext.current
        val repo = remember { AlarmRepository(context.applicationContext) }
        val scope = rememberCoroutineScope()
        val alarms by repo.all.collectAsStateWithLifecycle(initialValue = emptyList())
        // Периоды отпуска всех будильников — чтобы превью «след:» в списке глушило отпускные дни.
        val periodsAll by repo.allPeriods.collectAsStateWithLifecycle(initialValue = emptyList())
        val periodsByAlarm = remember(periodsAll) { periodsAll.groupBy { it.alarmId } }
        // Правки календаря всех будильников — чтобы превью «след:» учитывало подмены/исключения.
        val overridesAll by repo.allOverrides.collectAsStateWithLifecycle(initialValue = emptyList())
        val overridesByAlarm = remember(overridesAll) { overridesAll.groupBy { it.alarmId } }

        // null — список; иначе редактор: (будильник, его периоды отпуска, его правки календаря).
        var editing by remember {
          mutableStateOf<Triple<AlarmEntity, List<AlarmPeriod>, List<AlarmOverride>>?>(null)
        }
        var showDiagnostics by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }

        RequestNotificationPermission()

        // Включение/выключение, тест — периоды не трогаем (они уже в БД).
        fun saveAndSchedule(alarm: AlarmEntity) {
          scope.launch {
            val id = repo.upsert(alarm)
            AlarmScheduler.reschedule(context, repo, alarm.copy(id = id))
          }
        }

        // Сохранение из редактора — пересохраняем периоды отпуска и правки календаря вместе с будильником.
        fun saveFromEditor(
          alarm: AlarmEntity,
          periods: List<AlarmPeriod>,
          overrides: List<AlarmOverride>
        ) {
          scope.launch {
            val id = repo.saveWithChildren(alarm, periods, overrides)
            AlarmScheduler.reschedule(context, repo, alarm.copy(id = id))
          }
        }

        fun remove(alarm: AlarmEntity) {
          scope.launch {
            AlarmScheduler.cancel(context, alarm.id)
            AlarmEventLog(context).record(
              AlarmEventType.CANCELLED, "id=${alarm.id} (удалён)", System.currentTimeMillis()
            )
            repo.delete(alarm)
          }
        }

        val current = editing
        when {
          showSettings -> {
            BackHandler { showSettings = false }
            SettingsScreen(
              themeMode = themeMode,
              dynamicColor = dynamicColor,
              onThemeMode = { themeMode = it; settings.setThemeMode(it) },
              onDynamicColor = { dynamicColor = it; settings.setDynamicColor(it) },
              onBack = { showSettings = false }
            )
          }

          showDiagnostics -> {
            BackHandler { showDiagnostics = false }
            DiagnosticsScreen(onBack = { showDiagnostics = false })
          }

          current != null -> {
            BackHandler { editing = null }
            AlarmEditorScreen(
              initial = current.first,
              initialPeriods = current.second,
              initialOverrides = current.third,
              onSave = { alarm, periods, overrides ->
                saveFromEditor(alarm, periods, overrides); editing = null
              },
              onCancel = { editing = null }
            )
          }

          else -> {
            AlarmListScreen(
              alarms = alarms,
              periodsByAlarm = periodsByAlarm,
              overridesByAlarm = overridesByAlarm,
              onAdd = { editing = Triple(defaultAlarm(), emptyList(), emptyList()) },
              onEdit = { alarm ->
                scope.launch {
                  editing = Triple(alarm, repo.periodsList(alarm.id), repo.overridesList(alarm.id))
                }
              },
              onToggle = { alarm, on -> saveAndSchedule(alarm.copy(enabled = on)) },
              onDelete = { remove(it) },
              onOpenDiagnostics = { showDiagnostics = true },
              onOpenSettings = { showSettings = true }
            )
          }
        }
      }
    }
  }
}

private fun defaultAlarm() = AlarmEntity(
  hour = 7, minute = 0,
  mode = AlarmEntity.MODE_WEEKLY,
  daysMask = AlarmTimes.maskOf(*DayOfWeek.entries.toTypedArray()),
  enabled = true
)

@Composable
private fun AlarmListScreen(
  alarms: List<AlarmEntity>,
  periodsByAlarm: Map<Long, List<AlarmPeriod>>,
  overridesByAlarm: Map<Long, List<AlarmOverride>>,
  onAdd: () -> Unit,
  onEdit: (AlarmEntity) -> Unit,
  onToggle: (AlarmEntity, Boolean) -> Unit,
  onDelete: (AlarmEntity) -> Unit,
  onOpenDiagnostics: () -> Unit,
  onOpenSettings: () -> Unit
) {
  Scaffold(
    floatingActionButton = {
      // Плавающая кнопка «+»: правый нижний угол, поверх списка, чуть крупнее стандартной, круглая.
      FloatingActionButton(
        onClick = onAdd,
        modifier = Modifier.size(64.dp),
        shape = CircleShape
      ) {
        Icon(
          Icons.Filled.Add,
          contentDescription = "Добавить будильник",
          modifier = Modifier.size(30.dp)
        )
      }
    }
  ) { padding ->
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
        Text(
          "Будильники",
          style = MaterialTheme.typography.headlineSmall,
          modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onOpenDiagnostics) { Text("Диагностика") }
        IconButton(onClick = onOpenSettings) {
          Icon(Icons.Filled.Settings, contentDescription = "Настройки")
        }
      }
      Spacer(Modifier.height(12.dp))

      // Предупреждение о разрешениях, мешающих звонку (показывается только при проблемах).
      AlarmReadinessBanner(Modifier.padding(bottom = 12.dp))

      if (alarms.isEmpty()) {
        Text("Список пуст. Добавьте будильник.", style = MaterialTheme.typography.bodyMedium)
      } else {
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          // Нижний отступ, чтобы последний будильник не прятался за плавающей кнопкой.
          contentPadding = PaddingValues(bottom = 88.dp)
        ) {
          items(alarms, key = { it.id }) { alarm ->
            AlarmRow(
              alarm = alarm,
              periods = periodsByAlarm[alarm.id].orEmpty(),
              overrides = overridesByAlarm[alarm.id].orEmpty(),
              onClick = { onEdit(alarm) },
              onToggle = { on -> onToggle(alarm, on) },
              onDelete = { onDelete(alarm) }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun AlarmRow(
  alarm: AlarmEntity,
  periods: List<AlarmPeriod>,
  overrides: List<AlarmOverride>,
  onClick: () -> Unit,
  onToggle: (Boolean) -> Unit,
  onDelete: () -> Unit
) {
  var confirmOff by remember { mutableStateOf(false) }
  var confirmDelete by remember { mutableStateOf(false) }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        val title = if (alarm.label.isBlank()) "%02d:%02d".format(alarm.hour, alarm.minute)
        else "%02d:%02d · ${alarm.label}".format(alarm.hour, alarm.minute)
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(describe(alarm), style = MaterialTheme.typography.bodyMedium)
        if (alarm.enabled) {
          val dayOverrides = overrides.mapNotNull { it.toDayOverrideOrNull() }
          val next = AlarmTimes.next(alarm, periods, dayOverrides, LocalDateTime.now())
          if (next != null) {
            Text("след: ${formatNext(next)}", style = MaterialTheme.typography.bodySmall)
          }
        }
      }
      // Выключение — с подтверждением (чтобы не отключить случайно); включение — сразу.
      Switch(
        checked = alarm.enabled,
        onCheckedChange = { on -> if (on) onToggle(true) else confirmOff = true }
      )
      Spacer(Modifier.width(4.dp))
      IconButton(onClick = { confirmDelete = true }) {
        Icon(Icons.Filled.Delete, contentDescription = "Удалить будильник")
      }
    }
  }

  if (confirmOff) {
    AlertDialog(
      onDismissRequest = { confirmOff = false },
      title = { Text("Выключить будильник?") },
      text = { Text("Он не будет звонить, пока снова не включишь.") },
      confirmButton = {
        TextButton(onClick = { onToggle(false); confirmOff = false }) { Text("Выключить") }
      },
      dismissButton = {
        TextButton(onClick = { confirmOff = false }) { Text("Отмена") }
      }
    )
  }

  if (confirmDelete) {
    AlertDialog(
      onDismissRequest = { confirmDelete = false },
      title = { Text("Удалить будильник?") },
      text = { Text("Настройки этого будильника будут потеряны безвозвратно.") },
      confirmButton = {
        TextButton(onClick = { onDelete(); confirmDelete = false }) { Text("Удалить") }
      },
      dismissButton = {
        TextButton(onClick = { confirmDelete = false }) { Text("Отмена") }
      }
    )
  }
}

private val DOW_SHORT = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

private fun describe(alarm: AlarmEntity): String {
  if (alarm.mode == AlarmEntity.MODE_SHIFT) {
    alarm.cycleSpec?.let { spec ->
      val slots = ShiftCycleCodec.decodeOrNull(spec)

      if (!slots.isNullOrEmpty()) {
        return "Смены: свой цикл (${slots.size} дн.)"
      }
    }
    val title = ShiftPresets.byId(alarm.presetId)?.title ?: alarm.presetId
    return "Смены: $title"
  }
  val mask = alarm.daysMask
  return when {
    mask == 0 -> if (alarm.deleteAfterFiring) "Разовый (удалится)" else "Разовый"
    mask == AlarmTimes.maskOf(*DayOfWeek.entries.toTypedArray()) -> "Каждый день"
    else -> DayOfWeek.entries
      .filter { AlarmTimes.maskHas(mask, it) }
      .joinToString(", ") { DOW_SHORT[it.value - 1] }
  }
}

private fun formatNext(dt: LocalDateTime): String {
  val today = LocalDate.now()
  val time = "%02d:%02d".format(dt.hour, dt.minute)
  return if (dt.toLocalDate() == today) "сегодня $time"
  else "%02d.%02d %s".format(dt.dayOfMonth, dt.monthValue, time)
}

@Composable
private fun RequestNotificationPermission() {
  val context = LocalContext.current
  val launcher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) {}
  LaunchedEffect(Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
      PackageManager.PERMISSION_GRANTED
    ) {
      launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }
}
