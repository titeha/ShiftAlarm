package ru.titeha.shiftalarm.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.analog.AnalogTimePicker
import ru.titeha.shiftalarm.data.AlarmEntity
import ru.titeha.shiftalarm.data.AlarmOverride
import ru.titeha.shiftalarm.data.AlarmPeriod
import ru.titeha.shiftalarm.schedule.AlarmTimes
import ru.titeha.shiftalarm.schedule.OffPeriod
import ru.titeha.shiftalarm.schedule.PeriodKind
import ru.titeha.shiftalarm.schedule.ScheduleOverrides
import ru.titeha.shiftalarm.schedule.ShiftCategory
import ru.titeha.shiftalarm.schedule.ShiftEngine
import ru.titeha.shiftalarm.schedule.ShiftSchedule
import ru.titeha.shiftalarm.schedule.VacationSick
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val DOW_SHORT = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

/** Дата в формате текущей локали устройства (РФ/КЗ → 25.06.2026, US → Jun 25, 2026). */
internal fun LocalDate.localized(): String =
  format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))

/**
 * Экран добавления/редактирования одного будильника.
 * Возвращает результат через [onSave] (с уже подготовленной записью) или [onCancel].
 */
@Composable
fun AlarmEditorScreen(
  initial: AlarmEntity,
  initialPeriods: List<AlarmPeriod>,
  initialOverrides: List<AlarmOverride>,
  onSave: (AlarmEntity, List<AlarmPeriod>, List<AlarmOverride>) -> Unit,
  onCancel: () -> Unit
) {
  var draft by remember { mutableStateOf(initial) }
  var periods by remember { mutableStateOf(initialPeriods) }
  var overrides by remember { mutableStateOf(initialOverrides) }
  val isNew = initial.id == 0L

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      if (isNew) "Новый будильник" else "Будильник",
      style = MaterialTheme.typography.headlineSmall
    )
    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
      value = draft.label,
      onValueChange = { draft = draft.copy(label = it) },
      label = { Text("Название (необязательно)") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(16.dp))

    // Режим
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      ModeChip("По дням недели", draft.mode == AlarmEntity.MODE_WEEKLY) {
        draft = draft.copy(mode = AlarmEntity.MODE_WEEKLY)
      }
      ModeChip("Смены", draft.mode == AlarmEntity.MODE_SHIFT) {
        draft = draft.copy(
          mode = AlarmEntity.MODE_SHIFT,
          anchorEpochDay = if (draft.anchorEpochDay == 0L)
            LocalDate.now().toEpochDay() else draft.anchorEpochDay
        )
      }
    }
    Spacer(Modifier.height(16.dp))

    if (draft.mode == AlarmEntity.MODE_WEEKLY) {
      WeeklyEditor(draft) { draft = it }
    } else {
      ShiftEditor(
        draft = draft,
        periods = periods,
        overrides = overrides,
        onChange = { draft = it },
        onAddPeriod = { periods = periods + it },
        onRemovePeriod = { p -> periods = periods - p },
        onPeriodsChange = { periods = it },
        onOverridesChange = { overrides = it }
      )
    }

    Spacer(Modifier.height(16.dp))
    HolidaySection(draft) { draft = it }

    Spacer(Modifier.height(24.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Отмена") }
      Button(
        onClick = { onSave(draft, periods, overrides) },
        modifier = Modifier.weight(1f)
      ) { Text("Сохранить") }
    }
  }
}

@Composable
private fun WeeklyEditor(draft: AlarmEntity, onChange: (AlarmEntity) -> Unit) {
  AnalogTimePicker(
    time = LocalTime.of(draft.hour, draft.minute),
    onTimeChange = { t -> onChange(draft.copy(hour = t.hour, minute = t.minute)) },
    snapLabel = "5 минут",
    nowLabel = "Сейчас"
  )
  Spacer(Modifier.height(16.dp))

  Text("Дни повтора:", style = MaterialTheme.typography.titleMedium)
  Spacer(Modifier.height(8.dp))
  FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
  ) {
    DayOfWeek.entries.forEach { day ->
      val on = AlarmTimes.maskHas(draft.daysMask, day)
      val toggle = {
        val bit = AlarmTimes.bitOf(day)
        onChange(draft.copy(daysMask = if (on) draft.daysMask and bit.inv() else draft.daysMask or bit))
      }
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { toggle() }
      ) {
        Checkbox(checked = on, onCheckedChange = { toggle() })
        Text(DOW_SHORT[day.value - 1])
      }
    }
  }
  Spacer(Modifier.height(8.dp))

  if (draft.daysMask == 0) {
    Text("Без выбранных дней — разовый будильник.", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Switch(
        checked = draft.deleteAfterFiring,
        onCheckedChange = { onChange(draft.copy(deleteAfterFiring = it)) }
      )
      Spacer(Modifier.width(8.dp))
      Text("Удалить после срабатывания", style = MaterialTheme.typography.bodyMedium)
    }
  }
}

@Composable
private fun ShiftEditor(
  draft: AlarmEntity,
  periods: List<AlarmPeriod>,
  overrides: List<AlarmOverride>,
  onChange: (AlarmEntity) -> Unit,
  onAddPeriod: (AlarmPeriod) -> Unit,
  onRemovePeriod: (AlarmPeriod) -> Unit,
  onPeriodsChange: (List<AlarmPeriod>) -> Unit,
  onOverridesChange: (List<AlarmOverride>) -> Unit
) {
  ShiftCycleEditor(draft = draft, onChange = onChange)
  Spacer(Modifier.height(8.dp))
  Text(
    "Отсчёт цикла — с ${LocalDate.ofEpochDay(draft.anchorEpochDay).localized()} (день 1).",
    style = MaterialTheme.typography.bodySmall
  )

  Spacer(Modifier.height(16.dp))
  VacationSection(
    alarmId = draft.id,
    periods = periods,
    onAdd = onAddPeriod,
    onRemove = onRemovePeriod
  )

  Spacer(Modifier.height(16.dp))
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Switch(
      checked = draft.freezeCycleDuringOff,
      onCheckedChange = { onChange(draft.copy(freezeCycleDuringOff = it)) }
    )
    Spacer(Modifier.width(8.dp))
    Column {
      Text("Заморозить цикл на время отпуска", style = MaterialTheme.typography.bodyMedium)
      Text(
        if (draft.freezeCycleDuringOff)
          "После отпуска цикл продолжится с той смены, где ты ушёл."
        else
          "Цикл крутится по календарю; после отпуска — смена «по графику».",
        style = MaterialTheme.typography.bodySmall
      )
    }
  }

  // Наглядный календарь резолва смен — от текущего черновика, с применёнными правками.
  Spacer(Modifier.height(16.dp))
  var showCalendar by remember { mutableStateOf(false) }
  var rangeMode by remember { mutableStateOf(false) }
  var rangeStart by remember { mutableStateOf<LocalDate?>(null) }
  // Ожидающая правки цель (диалог открыт): from..to; один день = from == to.
  var pending by remember { mutableStateOf<Pair<LocalDate, LocalDate>?>(null) }
  // Текущее расписание с применёнными правками — общее для календаря и обработчика правок.
  val base = AlarmTimes.shiftBase(draft)
  val schedule = base?.let {
    ScheduleOverrides.apply(
      ShiftSchedule(
        base = it,
        offPeriods = periods.map { p ->
          OffPeriod(
            LocalDate.ofEpochDay(p.fromEpochDay),
            LocalDate.ofEpochDay(p.toEpochDay),
            p.reason
          )
        },
        freezeCycleDuringOff = draft.freezeCycleDuringOff
      ),
      overrides.map { o -> o.toDayOverride() }
    )
  }
  TextButton(onClick = { showCalendar = !showCalendar }) {
    Text(if (showCalendar) "Скрыть календарь" else "Показать календарь смен")
  }
  if (showCalendar) {
    if (schedule != null) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ModeChip("Один день", !rangeMode) { rangeMode = false; rangeStart = null }
        ModeChip("Диапазон", rangeMode) { rangeMode = true; rangeStart = null }
      }
      Spacer(Modifier.height(4.dp))
      Text(
        when {
          !rangeMode -> "Нажми на день, чтобы подменить смену."
          rangeStart == null -> "Диапазон: нажми первый день блока."
          else -> "Начало: ${rangeStart!!.localized()}. Нажми второй день блока."
        },
        style = MaterialTheme.typography.bodySmall
      )
      Spacer(Modifier.height(4.dp))
      ShiftCalendarView(
        schedule,
        onDayClick = { day ->
          if (!rangeMode) {
            pending = day to day
          } else {
            val start = rangeStart
            if (start == null) {
              rangeStart = day
            } else {
              pending = if (day.isBefore(start)) day to start else start to day
              rangeStart = null
            }
          }
        },
        highlightDay = rangeStart
      )
    } else {
      Text("Не удалось построить график для календаря.", style = MaterialTheme.typography.bodySmall)
    }
  }

  pending?.let { (from, to) ->
    val title = if (from == to) from.localized() else "${from.localized()} — ${to.localized()}"
    // Периоды, пересекающие выбранный диапазон (для замены/снятия).
    val overlappingPeriods = periods.filter {
      it.fromEpochDay <= to.toEpochDay() && it.toEpochDay >= from.toEpochDay()
    }
    DayOverrideDialog(
      title = title,
      onPickCategory = { category ->
        // Умная отмена ночи: одиночный «Выходной» на ночь-дне снимает звонок, будящий на ЭТУ
        // ночь (он на предыдущем дне), но сохраняет исходящий звонок (см. ScheduleOverrides).
        val isSingleNightOff = from == to && category == ShiftCategory.OFF && schedule != null &&
          ShiftEngine.shiftOn(from, schedule).category == ShiftCategory.NIGHT
        if (isSingleNightOff) {
          var next = overrides
          ScheduleOverrides.cancelNight(from) { ShiftEngine.shiftOn(it, schedule!!) }.forEach { o ->
            next = next.withExactOverride(
              draft.id, o.from, o.to, o.shift.category, o.shift.wakeTime, o.shift.name
            )
          }
          onOverridesChange(next)
        } else {
          onOverridesChange(overrides.withOverrideRange(draft.id, from, to, category))
        }
        overlappingPeriods.forEach(onRemovePeriod) // смена перекрывает период на этих днях
        pending = null
      },
      onPickPeriod = { kind ->
        // Период (без будильника) на день/диапазон; снимаем пересекающие правки-смены.
        onOverridesChange(overrides.withoutRange(from, to))
        if (kind == PeriodKind.SICK) {
          // Больничный: если попал в отпуск — продлить отпуск (ТК РФ), см. VacationSick.
          val spans = periods.map {
            VacationSick.Span(it.fromEpochDay, it.toEpochDay, PeriodKind.fromReason(it.reason))
          }
          val sick = VacationSick.Span(from.toEpochDay(), to.toEpochDay(), PeriodKind.SICK)
          onPeriodsChange(
            VacationSick.applySick(spans, sick).map {
              AlarmPeriod(
                alarmId = draft.id,
                fromEpochDay = it.from,
                toEpochDay = it.to,
                reason = it.kind.label
              )
            }
          )
        } else {
          overlappingPeriods.forEach(onRemovePeriod) // другой период — заменяем
          onAddPeriod(
            AlarmPeriod(
              alarmId = draft.id,
              fromEpochDay = from.toEpochDay(),
              toEpochDay = to.toEpochDay(),
              reason = kind.label
            )
          )
        }
        pending = null
      },
      onClear = {
        onOverridesChange(overrides.withoutRange(from, to))
        overlappingPeriods.forEach(onRemovePeriod)
        pending = null
      },
      onDismiss = { pending = null }
    )
  }
}

/** Русский ярлык категории для метки правки. */
private fun labelOfCategory(category: ShiftCategory): String = when (category) {
  ShiftCategory.MORNING -> "Утро"
  ShiftCategory.DAY -> "День"
  ShiftCategory.NIGHT -> "Ночь"
  ShiftCategory.OFF -> "Выходной"
}

/**
 * Заменить/добавить правку на [from]..[to] сменой категории [category] (выходной = без звонка).
 * Один день = `from == to`. Время звонка — по категории ([defaultAlarmFor]). Пересекающиеся старые
 * правки снимаются, чтобы не накапливались.
 */
private fun List<AlarmOverride>.withOverrideRange(
  alarmId: Long,
  from: LocalDate,
  to: LocalDate,
  category: ShiftCategory
): List<AlarmOverride> = withExactOverride(
  alarmId, from, to, category,
  if (category == ShiftCategory.OFF) null else defaultAlarmFor(category),
  labelOfCategory(category)
)

/** Как [withOverrideRange], но с явными временем звонка [wakeTime] и подписью [name]. */
private fun List<AlarmOverride>.withExactOverride(
  alarmId: Long,
  from: LocalDate,
  to: LocalDate,
  category: ShiftCategory,
  wakeTime: LocalTime?,
  name: String
): List<AlarmOverride> {
  val ovr = AlarmOverride(
    alarmId = alarmId,
    fromEpochDay = from.toEpochDay(),
    toEpochDay = to.toEpochDay(),
    category = category.name,
    wakeMinutes = wakeTime?.let { it.hour * 60 + it.minute },
    name = name
  )
  return withoutRange(from, to) + ovr
}

/** Убрать все правки, пересекающие [from]..[to] («вернуть по графику»). */
private fun List<AlarmOverride>.withoutRange(from: LocalDate, to: LocalDate): List<AlarmOverride> {
  val lo = from.toEpochDay()
  val hi = to.toEpochDay()
  return filterNot { it.fromEpochDay <= hi && it.toEpochDay >= lo }
}

/**
 * Диалог правки дня/диапазона: выбрать смену (Утро/День/Ночь/Выходной), задать период без
 * будильника (отпуск/больничный/отгул/свой счёт) или вернуть по графику. [title] — дата или
 * «дата — дата». Времена звонка смены — по категории ([defaultAlarmFor]); точную минуту позже.
 */
@Composable
private fun DayOverrideDialog(
  title: String,
  onPickCategory: (ShiftCategory) -> Unit,
  onPickPeriod: (PeriodKind) -> Unit,
  onClear: () -> Unit,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("Сделать сменой:", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        listOf(
          ShiftCategory.MORNING, ShiftCategory.DAY, ShiftCategory.NIGHT, ShiftCategory.OFF
        ).forEach { cat ->
          TextButton(
            onClick = { onPickCategory(cat) },
            modifier = Modifier.fillMaxWidth()
          ) {
            val suffix = if (cat == ShiftCategory.OFF) "" else
              " (${defaultAlarmFor(cat).format(DateTimeFormatter.ofPattern("HH:mm"))})"
            Text(labelOfCategory(cat) + suffix, modifier = Modifier.fillMaxWidth())
          }
        }
        Spacer(Modifier.height(8.dp))
        Text("Период (без будильника):", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        PeriodKind.entries.forEach { kind ->
          TextButton(
            onClick = { onPickPeriod(kind) },
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(kind.label, modifier = Modifier.fillMaxWidth())
          }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
          Text("Вернуть по графику", modifier = Modifier.fillMaxWidth())
        }
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
  )
}

@Composable
internal fun ModeChip(text: String, selected: Boolean, onClick: () -> Unit) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = { Text(text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
  )
}

/**
 * Секция «Учитывать праздники» (производственный календарь) + выбор полярности:
 * буди по рабочим (нерабочие глушатся) или по выходным (звонит в выходные/праздники/переносы).
 */
@Composable
private fun HolidaySection(draft: AlarmEntity, onChange: (AlarmEntity) -> Unit) {
  Column(Modifier.fillMaxWidth()) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Switch(
        checked = draft.honorHolidays,
        onCheckedChange = { onChange(draft.copy(honorHolidays = it)) }
      )
      Spacer(Modifier.width(8.dp))
      Column {
        Text("Учитывать праздники", style = MaterialTheme.typography.bodyMedium)
        Text(
          "Производственный календарь РФ: праздники и переносы выходных.",
          style = MaterialTheme.typography.bodySmall
        )
      }
    }
    if (draft.honorHolidays) {
      Spacer(Modifier.height(8.dp))
      Text("Когда будить:", style = MaterialTheme.typography.bodyMedium)
      Spacer(Modifier.height(4.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ModeChip("По рабочим", draft.polarity == AlarmEntity.POLARITY_WORK) {
          onChange(draft.copy(polarity = AlarmEntity.POLARITY_WORK))
        }
        ModeChip("По выходным", draft.polarity == AlarmEntity.POLARITY_REST) {
          onChange(draft.copy(polarity = AlarmEntity.POLARITY_REST))
        }
      }
      Spacer(Modifier.height(4.dp))
      Text(
        if (draft.polarity == AlarmEntity.POLARITY_REST)
          "Звонит в выходные и праздники (в т.ч. перенесённые), молчит в рабочие дни."
        else
          "Звонит по графику, но глушится в праздники и выходные (по календарю).",
        style = MaterialTheme.typography.bodySmall
      )
    }
  }
}
