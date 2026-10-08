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
import ru.titeha.shiftalarm.schedule.WorkWeekPreset
import ru.titeha.shiftalarm.data.HolidayCalendarRepository
import ru.titeha.shiftalarm.ui.theme.ThemeMode
import java.time.DayOfWeek
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
  fontScale: Float = 1f,
  onFontScale: (Float) -> Unit = {},
  ringConfig: RingConfig = RingConfig(),
  onRingConfig: (RingConfig) -> Unit = {},
  dismissMode: DismissMode = DismissMode.NORMAL,
  onDismissMode: (DismissMode) -> Unit = {},
  weekStart: WeekStart = WeekStart.AUTO,
  onWeekStart: (WeekStart) -> Unit = {},
  weekPairNaming: WeekPairNaming = WeekPairNaming.PARITY,
  onWeekPairNaming: (WeekPairNaming) -> Unit = {},
  workWeekPreset: WorkWeekPreset = WorkWeekPreset.DEFAULT,
  workWeekCustomMask: Int = 0,
  onWorkWeek: (WorkWeekPreset, Int) -> Unit = { _, _ -> },
  onRunSelfTest: () -> Unit = {},
  onRefreshHolidays: () -> Unit = {},
  onOpenPhoneSetup: (() -> Unit)? = null,
  dayGreetingCard: Boolean = true,
  onDayGreetingCard: (Boolean) -> Unit = {},
  dayGreetingNotification: Boolean = false,
  onDayGreetingNotification: (Boolean) -> Unit = {},
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

      SectionCard("Тема") {
        EqualChips(
          items = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK),
          columns = 2,
          label = { when (it) { ThemeMode.SYSTEM -> "Система"; ThemeMode.LIGHT -> "Светлая"; ThemeMode.DARK -> "Тёмная" } },
          selected = { themeMode == it },
          onSelect = onThemeMode,
        )

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
      }
      Spacer(Modifier.height(24.dp))
      Spacer(Modifier.height(16.dp))
      SectionCard(
        title = "Размер шрифта",
        description = "Увеличивает текст во всём приложении (поверх системного размера шрифта)."
      ) {
        EqualChips(
          items = listOf(
            1.0f to "Обычный", 1.15f to "Крупнее", 1.3f to "Крупный", 1.5f to "Очень крупный"
          ),
          columns = 1,
          label = { it.second },
          selected = { kotlin.math.abs(fontScale - it.first) < 0.01f },
          onSelect = { onFontScale(it.first) },
        )
      }
      Spacer(Modifier.height(24.dp))
      Spacer(Modifier.height(16.dp))
      SectionCard("Надёжность") {
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
      }
      Spacer(Modifier.height(24.dp))
      Spacer(Modifier.height(16.dp))
      SectionCard("Звонок") {
        Text("Интервал снуза, мин", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        EqualChips(
          items = listOf(5, 10, 15),
          columns = 3,
          label = { it.toString() },
          selected = { ringConfig.snoozeIntervalMinutes == it },
          onSelect = {
            if (ringConfig.snoozeIntervalMinutes != it) {
              onRingConfig(ringConfig.copy(snoozeIntervalMinutes = it))
            }
          },
        )
        Spacer(Modifier.height(8.dp))
        Text("Сколько раз откладывать", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        EqualChips(
          items = listOf(0, 1, 2, 3, 5),
          columns = 5,
          label = { it.toString() },
          selected = { ringConfig.maxSnoozes == it },
          onSelect = {
            if (ringConfig.maxSnoozes != it) onRingConfig(ringConfig.copy(maxSnoozes = it))
          },
        )
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
          EqualChips(
            items = listOf(5, 10, 15),
            columns = 3,
            label = { it.toString() },
            selected = { ringConfig.ringDurationMinutes == it },
            onSelect = {
              if (ringConfig.ringDurationMinutes != it) {
                onRingConfig(ringConfig.copy(ringDurationMinutes = it))
              }
            },
          )
        }
      }
      if (FeatureFlags.HARD_MODE) {
        val sensorCtx = LocalContext.current
        val hasStepDetector = remember { sensorPresent(sensorCtx, Sensor.TYPE_STEP_DETECTOR) }
        val hasAccelerometer = remember { sensorPresent(sensorCtx, Sensor.TYPE_ACCELEROMETER) }

        Spacer(Modifier.height(16.dp))
        SectionCard(
          title = "Выключение звонка",
          description = "Задание усложняет «Стоп»; «Отложить» работает всегда."
        ) {
          EqualChips(
            items = buildList {
              add(DismissMode.NORMAL)
              add(DismissMode.MATH)
              if (hasStepDetector) add(DismissMode.STEPS)
              if (hasAccelerometer) add(DismissMode.SHAKE)
            },
            columns = 2,
            label = {
              when (it) {
                DismissMode.NORMAL -> "Обычное"
                DismissMode.MATH -> "Пример"
                DismissMode.STEPS -> "Шаги"
                DismissMode.SHAKE -> "Тряска"
              }
            },
            selected = { dismissMode == it },
            onSelect = onDismissMode,
          )
        }
      }

      Spacer(Modifier.height(24.dp))
      Spacer(Modifier.height(16.dp))
      SectionCard(
        title = "Начало недели",
        description = "Только вид: порядок колонок в календаре и чипов дней. На расчёт звонка не влияет."
      ) {
        EqualChips(
          items = listOf(
            WeekStart.AUTO to "Авто",
            WeekStart.MONDAY to "Пн",
            WeekStart.SUNDAY to "Вс",
            WeekStart.SATURDAY to "Сб",
          ),
          columns = 2,
          label = { it.second },
          selected = { weekStart == it.first },
          onSelect = { onWeekStart(it.first) },
        )
      }
      Spacer(Modifier.height(24.dp))
      Spacer(Modifier.height(16.dp))
      SectionCard(
        title = "Учебные недели (чёт/нечёт)",
        description = "Как называть пару чередующихся недель в учебных будильниках."
      ) {
        EqualChips(
          items = WeekPairNaming.entries.toList(),
          columns = 1,
          label = { "${it.odd}/${it.even}" },
          selected = { weekPairNaming == it },
          onSelect = onWeekPairNaming,
        )
      }
      Spacer(Modifier.height(24.dp))
      Spacer(Modifier.height(16.dp))
      SectionCard(
        title = "Рабочая неделя",
        description = "Когда вы работаете. Влияет на расчёт «По выходным и праздникам» и на переносы выходных у будильников с праздниками; дни звонка обычных будильников не меняет."
      ) {
        EqualChips(
          items = WorkWeekPreset.entries.toList(),
          columns = 1,
          label = { it.title },
          selected = { workWeekPreset == it },
          onSelect = { onWorkWeek(it, workWeekCustomMask) },
        )
        if (workWeekPreset == WorkWeekPreset.CUSTOM) {
          Spacer(Modifier.height(8.dp))
          Text("Дни, когда вы работаете:", style = MaterialTheme.typography.bodyMedium)
          Spacer(Modifier.height(4.dp))
          EqualChips(
            items = DayOfWeek.entries.toList(),
            columns = 4,
            label = { WORK_WEEK_DOW[it.value - 1] },
            selected = { workWeekCustomMask and (1 shl (it.value - 1)) != 0 },
            onSelect = { day ->
              val bit = 1 shl (day.value - 1)
              val on = workWeekCustomMask and bit != 0
              val mask = if (on) workWeekCustomMask and bit.inv() else workWeekCustomMask or bit
              onWorkWeek(WorkWeekPreset.CUSTOM, mask)
            },
          )
          if (workWeekCustomMask == 0) {
            Spacer(Modifier.height(4.dp))
            Text(
              "Не выбран ни один день — расчёт вернётся к неделе по умолчанию.",
              style = MaterialTheme.typography.labelSmall
            )
          }
        }
      }
      Spacer(Modifier.height(24.dp))
      Spacer(Modifier.height(16.dp))
      SectionCard("Праздничный календарь") {
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
        TextButton(onClick = onRefreshHolidays) {
          Text("Обновить сейчас")
        }
        Spacer(Modifier.height(4.dp))
        holidayInfo.forEach { (yr, ts) ->
          Text(
            if (ts != null) "$yr — обновлён ${formatStamp(ts)}" else "$yr — встроенные данные",
            style = MaterialTheme.typography.bodyMedium
          )
        }

        // «Настроение дня» целиком под флагом (релиз «только исправления»): раздел не показываем.
      }
      if (FeatureFlags.DAY_GREETING) {
        Spacer(Modifier.height(16.dp))
        SectionCard(
          title = "Настроение дня",
          description = "После «Стоп» — праздник дня и фраза дня. Показывается, только если будильник звонил; на надёжность звонка не влияет."
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = dayGreetingCard, onCheckedChange = onDayGreetingCard)
            Spacer(Modifier.width(8.dp))
            Text("Карточка после «Стоп»", style = MaterialTheme.typography.bodyMedium)
          }
          Spacer(Modifier.height(8.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = dayGreetingNotification, onCheckedChange = onDayGreetingNotification)
            Spacer(Modifier.width(8.dp))
            Column {
              Text("Уведомление", style = MaterialTheme.typography.bodyMedium)
              Text(
                "Отдельный канал «Настроение дня» — можно отключить, на звонок не влияет.",
                style = MaterialTheme.typography.bodySmall
              )
            }
          }
        }
      }
    }
  }
}

/** Есть ли на устройстве датчик [type] — иначе опцию жёсткого режима не показываем. */
private fun sensorPresent(context: android.content.Context, type: Int): Boolean =
  context.getSystemService(SensorManager::class.java)?.getDefaultSensor(type) != null

/** Короткие имена дней для чипов «Рабочая неделя» (порядок — Пн…Вс, как в [DayOfWeek.entries]). */
private val WORK_WEEK_DOW = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

private val STAMP_FMT: DateTimeFormatter =
  DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

/** Метка времени (epoch millis) → «дд.мм.гггг чч:мм» в часовом поясе устройства. */
private fun formatStamp(millis: Long): String =
  Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(STAMP_FMT)
