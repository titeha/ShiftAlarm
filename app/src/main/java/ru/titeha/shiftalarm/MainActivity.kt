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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ru.titeha.shiftalarm.data.AlarmEntity
import ru.titeha.shiftalarm.data.AlarmOverride
import ru.titeha.shiftalarm.data.AlarmPeriod
import ru.titeha.shiftalarm.data.SettingsStore
import ru.titeha.shiftalarm.schedule.AlarmTimes
import ru.titeha.shiftalarm.schedule.ShiftCycleCodec
import ru.titeha.shiftalarm.schedule.ShiftPresets
import ru.titeha.shiftalarm.ui.AlarmEditorScreen
import ru.titeha.shiftalarm.ui.AlarmListViewModel
import ru.titeha.shiftalarm.alarm.CachedAlarm
import ru.titeha.shiftalarm.alarm.DirectBootAlarmStore
import ru.titeha.shiftalarm.alarm.VendorSetup
import ru.titeha.shiftalarm.ui.AlarmReadinessBanner
import ru.titeha.shiftalarm.ui.VendorSetupScreen
import ru.titeha.shiftalarm.ui.AlarmSaveState
import ru.titeha.shiftalarm.ui.DiagnosticsScreen
import ru.titeha.shiftalarm.ui.rememberCurrentMinute
import ru.titeha.shiftalarm.ui.SettingsScreen
import ru.titeha.shiftalarm.ui.theme.AppTheme
import ru.titeha.shiftalarm.ui.theme.ThemeMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
        val vm: AlarmListViewModel = viewModel()
        val state by vm.uiState.collectAsStateWithLifecycle()
        val saveState by vm.saveState.collectAsStateWithLifecycle()
        val editorSession by vm.editorSession.collectAsStateWithLifecycle()

        var showDiagnostics by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        var showVendorSetup by remember { mutableStateOf(false) }

        // Инструкция по автозапуску для агрессивных прошивок (Xiaomi и т.п.); null — прошивка не из них.
        val vendorGuide = remember { VendorSetup.forManufacturer(Build.MANUFACTURER) }
        var vendorDismissed by remember { mutableStateOf(settings.vendorSetupDismissed()) }

        // Пропущенные звонки (обнаружены планировщиком): показываем карточку, пока не закроют.
        val directBootStore = remember { DirectBootAlarmStore(applicationContext) }
        var missedAlarms by remember { mutableStateOf(directBootStore.readMissed()) }

        var saveWarning by remember { mutableStateOf<String?>(null) }

        val snackbarHostState = remember { SnackbarHostState() }

        // Контекстный запрос уведомлений (не вслепую на старте): по действию с будильником.
        val requestNotifications = rememberNotificationPermissionRequester()

        LaunchedEffect(Unit) {
          vm.userMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
          }
        }

        LaunchedEffect(saveState) {
          val result = saveState as? AlarmSaveState.Saved
            ?: return@LaunchedEffect

          /*
           * Данные уже сохранены. Закрываем редактор только после результата
           * ViewModel и отдельно показываем предупреждение AlarmManager.
           */
          vm.closeEditor()
          saveWarning = result.warningMessage
          vm.resetSaveState()

          // Будильник создан/сохранён — уместный момент попросить разрешение на уведомления.
          requestNotifications()
        }

        Box(modifier = Modifier.fillMaxSize()) {
        val currentEditor = editorSession
        when {
          showSettings -> {
            BackHandler { showSettings = false }
            SettingsScreen(
              themeMode = themeMode,
              dynamicColor = dynamicColor,
              onThemeMode = { themeMode = it; settings.setThemeMode(it) },
              onDynamicColor = { dynamicColor = it; settings.setDynamicColor(it) },
              onRunSelfTest = { vm.runSelfTest() },
              onOpenPhoneSetup = if (vendorGuide != null) {
                { showSettings = false; showVendorSetup = true }
              } else {
                null
              },
              onBack = { showSettings = false }
            )
          }

          showVendorSetup && vendorGuide != null -> {
            BackHandler { showVendorSetup = false }
            VendorSetupScreen(guide = vendorGuide, onBack = { showVendorSetup = false })
          }

          showDiagnostics -> {
            BackHandler { showDiagnostics = false }
            DiagnosticsScreen(onBack = { showDiagnostics = false })
          }

          currentEditor != null -> {
            AlarmEditorScreen(
              session = currentEditor,
              onSave = { alarm, periods, overrides ->
                vm.save(
                  alarm,
                  periods,
                  overrides
                )
              },
              onCancel = {
                vm.resetSaveState()
                vm.closeEditor()
              },
              isSaving =
                saveState is AlarmSaveState.Saving,
              saveErrorMessage =
                (saveState as? AlarmSaveState.Failed)
                  ?.message,
              onDismissSaveError = {
                vm.resetSaveState()
              }
            )
          }

          else -> {
            AlarmListScreen(
              alarms = state.alarms,
              periodsByAlarm = state.periodsByAlarm,
              overridesByAlarm = state.overridesByAlarm,
              onAdd = {
                vm.resetSaveState()
                vm.openNewEditor(
                  defaultAlarm()
                )
              },
              onEdit = { alarm ->
                vm.resetSaveState()
                vm.openEditor(alarm)
              },
              onToggle = { alarm, on ->
                vm.setEnabled(alarm, on)
                // Включение будильника — уместный момент попросить разрешение на уведомления.
                if (on) requestNotifications()
              },
              onDelete = { vm.delete(it) },
              onOpenDiagnostics = { showDiagnostics = true },
              onOpenSettings = { showSettings = true },
              showVendorSetupHint = vendorGuide != null && !vendorDismissed,
              onOpenVendorSetup = { showVendorSetup = true },
              onDismissVendorSetup = {
                vendorDismissed = true
                settings.setVendorSetupDismissed()
              },
              missedAlarms = missedAlarms,
              vendorSetupAvailable = vendorGuide != null,
              onDismissMissed = {
                directBootStore.clearMissed()
                missedAlarms = emptyList()
              }
            )
          }
        }

        saveWarning?.let { warning ->
          AlertDialog(
            onDismissRequest = {
              saveWarning = null
            },
            title = {
              Text("Будильник сохранён")
            },
            text = {
              Text(warning)
            },
            confirmButton = {
              TextButton(
                onClick = {
                  saveWarning = null
                }
              ) {
                Text("Понятно")
              }
            }
          )
        }

          SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
          )
        }
      }
    }
  }
}

/** Карточка «звонок мог не прозвенеть» — приложение не отработало срабатывание (обычно OEM-выгрузка). */
@Composable
private fun MissedAlarmCard(
  missed: List<CachedAlarm>,
  vendorSetupAvailable: Boolean,
  onOpenVendorSetup: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(modifier = modifier.fillMaxWidth()) {
    Column(Modifier.padding(12.dp)) {
      Text(
        "Звонок мог не прозвенеть",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.error
      )
      Spacer(Modifier.height(4.dp))
      val fmt = remember { DateTimeFormatter.ofPattern("dd.MM HH:mm") }
      missed.takeLast(3).forEach { m ->
        val time = Instant.ofEpochMilli(m.triggerAtMillis)
          .atZone(ZoneId.systemDefault()).format(fmt)
        Text("• ${m.label.ifBlank { "Будильник" }} — $time", style = MaterialTheme.typography.bodySmall)
      }
      Spacer(Modifier.height(8.dp))
      Text(
        "Похоже, система выгрузила приложение. Настройте автозапуск и энергосбережение, чтобы это не повторялось.",
        style = MaterialTheme.typography.labelSmall
      )
      Spacer(Modifier.height(8.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (vendorSetupAvailable) {
          Button(onClick = onOpenVendorSetup) { Text("Настроить телефон") }
        }
        TextButton(onClick = onDismiss) { Text("Понятно") }
      }
    }
  }
}

/** Скрываемая карточка «Настроить телефон» для агрессивных прошивок (автозапуск после ребута). */
@Composable
private fun VendorSetupHintCard(
  onOpen: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(modifier = modifier.fillMaxWidth()) {
    Column(Modifier.padding(12.dp)) {
      Text("Настройте телефон для надёжности", style = MaterialTheme.typography.titleSmall)
      Spacer(Modifier.height(4.dp))
      Text(
        "На этой прошивке будильник может не сработать после перезагрузки, пока не разрешён " +
          "автозапуск. Настроить нужно один раз.",
        style = MaterialTheme.typography.bodySmall
      )
      Spacer(Modifier.height(8.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onOpen) { Text("Настроить") }
        TextButton(onClick = onDismiss) { Text("Скрыть") }
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
  onOpenSettings: () -> Unit,
  showVendorSetupHint: Boolean = false,
  onOpenVendorSetup: () -> Unit = {},
  onDismissVendorSetup: () -> Unit = {},
  missedAlarms: List<CachedAlarm> = emptyList(),
  vendorSetupAvailable: Boolean = false,
  onDismissMissed: () -> Unit = {}
) {
  val currentMinute = rememberCurrentMinute()
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

      // Пропущенный звонок (система выгрузила приложение) — реактивная подсказка.
      if (missedAlarms.isNotEmpty()) {
        MissedAlarmCard(
          missed = missedAlarms,
          vendorSetupAvailable = vendorSetupAvailable,
          onOpenVendorSetup = onOpenVendorSetup,
          onDismiss = onDismissMissed,
          modifier = Modifier.padding(bottom = 12.dp)
        )
      }

      // Подсказка «Настроить телефон» для агрессивных прошивок (автозапуск). Скрываемая.
      if (showVendorSetupHint) {
        VendorSetupHintCard(
          onOpen = onOpenVendorSetup,
          onDismiss = onDismissVendorSetup,
          modifier = Modifier.padding(bottom = 12.dp)
        )
      }

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
              now = currentMinute,
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
  now: LocalDateTime,
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
          val dayOverrides = remember(overrides) {
            overrides.mapNotNull {
              it.toDayOverrideOrNull()
            }
          }

          val next = remember(
            alarm,
            periods,
            dayOverrides,
            now
          ) {
            AlarmTimes.next(
              alarm,
              periods,
              dayOverrides,
              now
            )
          }
          if (next != null) {
            Text("след: ${formatNext(next, now.toLocalDate())}", style = MaterialTheme.typography.bodySmall)
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

private fun formatNext(value: LocalDateTime, today: LocalDate): String {
  val time = "%02d:%02d".format(value.hour, value.minute)
  return if (value.toLocalDate() == today) "сегодня $time"
  else "%02d.%02d %s".format(value.dayOfMonth, value.monthValue, time)
}

/**
 * Контекстный запрос разрешения на уведомления (Android 13+).
 *
 * Системный диалог НЕ дёргается вслепую на старте. Вместо этого возвращается функция «запросить,
 * если нужно»: при создании/включении будильника показываем короткое обоснование, и только по
 * «Разрешить» — системный диалог. Спрашиваем один раз (флаг в [SettingsStore]); дальше постоянный
 * путь в настройки даёт баннер готовности. На Android < 13 и при уже выданном разрешении — no-op.
 */
@Composable
private fun rememberNotificationPermissionRequester(): () -> Unit {
  val context = LocalContext.current
  val settings = remember { SettingsStore(context) }
  var showRationale by remember { mutableStateOf(false) }

  val launcher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { /* результат не важен: статус покажет баннер готовности */ }

  if (showRationale) {
    AlertDialog(
      onDismissRequest = { showRationale = false },
      title = { Text("Разрешить уведомления?") },
      text = {
        Text(
          "Будильнику нужны уведомления, чтобы показать экран звонка и кнопку «Стоп». " +
            "Без них сигнал может не всплыть."
        )
      },
      confirmButton = {
        TextButton(onClick = {
          showRationale = false
          settings.setNotificationPromptDone()
          launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }) { Text("Разрешить") }
      },
      dismissButton = {
        TextButton(onClick = {
          showRationale = false
          settings.setNotificationPromptDone()
        }) { Text("Не сейчас") }
      }
    )
  }

  return {
    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
      PackageManager.PERMISSION_GRANTED &&
      !settings.notificationPromptDone()
    ) {
      showRationale = true
    }
  }
}
