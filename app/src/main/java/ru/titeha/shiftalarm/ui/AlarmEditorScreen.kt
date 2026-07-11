package ru.titeha.shiftalarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.titeha.shiftalarm.data.AlarmEntity
import ru.titeha.shiftalarm.data.AlarmOverride
import ru.titeha.shiftalarm.data.AlarmPeriod
import ru.titeha.shiftalarm.schedule.AlarmTimes
import ru.titeha.shiftalarm.schedule.CycleAnchor
import ru.titeha.shiftalarm.schedule.PeriodKind
import ru.titeha.shiftalarm.schedule.ProductionCalendars
import ru.titeha.shiftalarm.schedule.ScheduleOverrides
import ru.titeha.shiftalarm.schedule.ShiftCategory
import ru.titeha.shiftalarm.schedule.ShiftCycle
import ru.titeha.shiftalarm.schedule.ShiftEngine
import ru.titeha.shiftalarm.schedule.ShiftSchedule
import ru.titeha.shiftalarm.schedule.ShiftType
import ru.titeha.shiftalarm.schedule.VacationSick
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
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
/** Способ расписания, выбранный чипом. Хранится в UI-состоянии, в entity маппится при сохранении. */
private enum class EditMethod { ONCE, WEEKLY, SHIFT }

/** Способ по данным существующей записи: смены → SHIFT, пустая маска → ONCE, иначе WEEKLY. */
private fun methodOf(alarm: AlarmEntity): EditMethod = when {
  alarm.mode == AlarmEntity.MODE_SHIFT -> EditMethod.SHIFT
  alarm.daysMask == 0 -> EditMethod.ONCE
  else -> EditMethod.WEEKLY
}

/**
 * Черновик, приведённый к активному способу — сохраняется и уходит в превью только он.
 * Черновики других способов живут в [draft] (маска дней, цикл, deleteAfterFiring) и не теряются.
 */
private fun effective(draft: AlarmEntity, method: EditMethod): AlarmEntity = when (method) {
  EditMethod.ONCE -> draft.copy(mode = AlarmEntity.MODE_WEEKLY, daysMask = 0)
  EditMethod.WEEKLY -> draft.copy(mode = AlarmEntity.MODE_WEEKLY)
  EditMethod.SHIFT -> draft.copy(mode = AlarmEntity.MODE_SHIFT)
}

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
  var method by remember { mutableStateOf(methodOf(initial)) }
  val isNew = initial.id == 0L

  Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    horizontalAlignment = Alignment.Start
  ) {
    Text(
      if (isNew) "Новый будильник" else "Будильник",
      style = MaterialTheme.typography.headlineSmall,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth()
    )

    // ── Основное ──
    SectionHeader("Основное")
    OutlinedTextField(
      value = draft.label,
      onValueChange = { draft = draft.copy(label = it) },
      label = { Text("Название (необязательно)") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Switch(checked = draft.enabled, onCheckedChange = { draft = draft.copy(enabled = it) })
      Spacer(Modifier.width(8.dp))
      Text("Включён", style = MaterialTheme.typography.bodyMedium)
    }

    // ── Когда звонить ──
    SectionHeader("Когда звонить")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      ModeChip("Разово", method == EditMethod.ONCE) {
        if (method != EditMethod.ONCE) {
          method = EditMethod.ONCE
          // Разовый по умолчанию удаляется после срабатывания (для нового способа — вкл).
          draft = draft.copy(mode = AlarmEntity.MODE_WEEKLY, deleteAfterFiring = true)
        }
      }
      ModeChip("По дням недели", method == EditMethod.WEEKLY) {
        if (method != EditMethod.WEEKLY) {
          method = EditMethod.WEEKLY
          draft = draft.copy(mode = AlarmEntity.MODE_WEEKLY)
        }
      }
      ModeChip("По графику", method == EditMethod.SHIFT) {
        if (method != EditMethod.SHIFT) {
          method = EditMethod.SHIFT
          draft = draft.copy(
            mode = AlarmEntity.MODE_SHIFT,
            anchorEpochDay = if (draft.anchorEpochDay == 0L)
              LocalDate.now().toEpochDay() else draft.anchorEpochDay
          )
        }
      }
    }
    Spacer(Modifier.height(12.dp))
    when (method) {
      EditMethod.ONCE -> OnceContent(draft) { draft = it }
      EditMethod.WEEKLY -> WeeklyDaysContent(draft) { draft = it }
      EditMethod.SHIFT -> {
        ShiftCycleEditor(draft = draft, onChange = { draft = it })
        Spacer(Modifier.height(12.dp))
        CycleAnchorQuestion(draft) { draft = it }
      }
    }

    // ── Когда не звонить ──
    SectionHeader("Когда не звонить")
    HolidaySection(draft) { draft = it }
    if (method == EditMethod.SHIFT) {
      Spacer(Modifier.height(16.dp))
      VacationSection(
        alarmId = draft.id,
        periods = periods,
        onAdd = { periods = periods + it },
        onRemove = { p -> periods = periods - p }
      )
      Spacer(Modifier.height(16.dp))
      FreezeCycleToggle(draft) { draft = it }
    } else {
      // TODO: periods for weekly (AlarmTimes) — движок weekly периоды пока не умеет (non-goals ТЗ).
    }

    // ── Проверка ──
    SectionHeader("Проверка")
    SchedulePreview(effective(draft, method), periods, overrides)
    if (method == EditMethod.SHIFT) {
      Spacer(Modifier.height(8.dp))
      ShiftCalendarAndOverrides(
        draft = draft,
        periods = periods,
        overrides = overrides,
        onAddPeriod = { periods = periods + it },
        onRemovePeriod = { p -> periods = periods - p },
        onPeriodsChange = { periods = it },
        onOverridesChange = { overrides = it }
      )
    } else if (method == EditMethod.WEEKLY) {
      Spacer(Modifier.height(8.dp))
      WeeklyCalendarSection(effective(draft, method))
    }

    Spacer(Modifier.height(24.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Отмена") }
      Button(
        onClick = { onSave(effective(draft, method), periods, overrides) },
        modifier = Modifier.weight(1f)
      ) { Text("Сохранить") }
    }
  }
  }
}

/** Заголовок секции формы (тонкая линия-разделитель + акцентный подзаголовок). */
@Composable
private fun SectionHeader(text: String) {
  Spacer(Modifier.height(20.dp))
  Text(
    text,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    fontWeight = FontWeight.Bold
  )
  HorizontalDivider(Modifier.padding(top = 4.dp, bottom = 8.dp))
}

/** Крупная кнопка-время: открывает компактный пикер по тапу (общая для «Разово» и «По дням недели»). */
@Composable
private fun TimeButton(draft: AlarmEntity, onChange: (AlarmEntity) -> Unit) {
  var pickingTime by remember { mutableStateOf(false) }
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text("Время:", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.width(8.dp))
    TextButton(onClick = { pickingTime = true }) {
      Text("%02d:%02d".format(draft.hour, draft.minute), style = MaterialTheme.typography.displaySmall)
    }
  }
  if (pickingTime) {
    TimePickerDialog(
      time = LocalTime.of(draft.hour, draft.minute),
      onPick = { t -> onChange(draft.copy(hour = t.hour, minute = t.minute)) },
      onDismiss = { pickingTime = false }
    )
  }
}

/** Способ «Разово»: время + тумблер «Удалить после срабатывания». */
@Composable
private fun OnceContent(draft: AlarmEntity, onChange: (AlarmEntity) -> Unit) {
  TimeButton(draft, onChange)
  Spacer(Modifier.height(8.dp))
  Text(
    "Прозвонит один раз в ближайшее наступление этого времени.",
    style = MaterialTheme.typography.bodySmall
  )
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

/** Способ «По дням недели»: время + пресеты дней + чекбоксы дней. */
@Composable
private fun WeeklyDaysContent(draft: AlarmEntity, onChange: (AlarmEntity) -> Unit) {
  TimeButton(draft, onChange)
  Spacer(Modifier.height(16.dp))

  Text("Дни повтора:", style = MaterialTheme.typography.titleMedium)
  Spacer(Modifier.height(8.dp))
  // Дни повтора — нейтральные (звонок может быть не по работе: тренировка, кружок). «Рабочая неделя»
  // как понятие движка (work/off + начало недели) — отдельная будущая настройка, см. roadmap.
  val allDays = AlarmTimes.maskOf(*DayOfWeek.entries.toTypedArray())
  val weekdays = AlarmTimes.maskOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
  )
  val weekends = AlarmTimes.maskOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedButton(onClick = { onChange(draft.copy(daysMask = allDays)) }) { Text("Все") }
    OutlinedButton(onClick = { onChange(draft.copy(daysMask = weekdays)) }) { Text("Будни") }
    OutlinedButton(onClick = { onChange(draft.copy(daysMask = weekends)) }) { Text("Выходные") }
    OutlinedButton(onClick = { onChange(draft.copy(daysMask = 0)) }) { Text("Очистить") }
  }
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
  if (draft.daysMask == 0) {
    Spacer(Modifier.height(8.dp))
    Text(
      "Дни не выбраны — выбери дни повтора или воспользуйся пресетами.",
      style = MaterialTheme.typography.bodySmall
    )
  }
}

/** Тумблер «Заморозить цикл на время отпуска» (только для графика). */
@Composable
private fun FreezeCycleToggle(draft: AlarmEntity, onChange: (AlarmEntity) -> Unit) {
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
}

/** Строка «Следующий звонок: дата · время» — из боевого расчёта [AlarmTimes.next] (тот же движок). */
@Composable
private fun NextRingLine(alarm: AlarmEntity, periods: List<AlarmPeriod>, overrides: List<AlarmOverride>) {
  val dayOverrides = remember(overrides) { overrides.mapNotNull { it.toDayOverrideOrNull() } }
  val next = remember(alarm, periods, dayOverrides) {
    AlarmTimes.next(alarm, periods, dayOverrides, LocalDateTime.now())
  }
  Text(
    next?.let { "Следующий звонок: ${it.toLocalDate().localized()} · %02d:%02d".format(it.hour, it.minute) }
      ?: "Следующий звонок: не запланирован",
    style = MaterialTheme.typography.bodyMedium
  )
}

/**
 * Превью «Проверка»: строка «Следующий звонок» + для графика мини-лента 14 дней (цвет типа +
 * колокольчик) и строки ближайших звонков (с «накануне» для ночей); для «по дням недели» — ближайшие
 * 3 срабатывания. Всё считается движком ([AlarmTimes]/[ShiftEngine]) — совпадает с реальным звонком.
 */
@Composable
private fun SchedulePreview(alarm: AlarmEntity, periods: List<AlarmPeriod>, overrides: List<AlarmOverride>) {
  val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
  val now = remember { LocalDateTime.now() }
  val today = remember { now.toLocalDate() }
  val dayOverrides = remember(overrides) { overrides.mapNotNull { it.toDayOverrideOrNull() } }

  NextRingLine(alarm, periods, overrides)

  if (alarm.mode == AlarmEntity.MODE_SHIFT) {
    val calendar = remember(alarm.honorHolidays, today) {
      if (alarm.honorHolidays) ProductionCalendars.merged("RU", today.year) else null
    }
    val schedule = remember(alarm, periods, dayOverrides) {
      AlarmTimes.shiftScheduleOf(alarm, periods, dayOverrides)
    }
    if (schedule == null) {
      Text("Не удалось построить график для превью.", style = MaterialTheme.typography.bodySmall)
    } else {
      Spacer(Modifier.height(8.dp))
      Text("Ближайшие дни", style = MaterialTheme.typography.labelMedium)
      Spacer(Modifier.height(4.dp))
      PreviewStrip(schedule, calendar, today, days = 14, dark = dark)
      Spacer(Modifier.height(8.dp))
      val occ = remember(schedule, calendar, now) { ShiftEngine.nextAlarms(now, schedule, count = 6, calendar = calendar) }
      if (occ.isEmpty()) {
        Text("Ближайших звонков нет.", style = MaterialTheme.typography.bodySmall)
      } else {
        occ.forEach { OccurrenceRow(it) }
      }
    }
  } else if (alarm.daysMask != 0) {
    // «По дням недели» — ближайшие 3 срабатывания (разовый показываем только строкой выше).
    val firings = remember(alarm, periods, dayOverrides, now) {
      buildList {
        var cursor = now
        repeat(3) {
          val f = AlarmTimes.next(alarm, periods, dayOverrides, cursor) ?: return@buildList
          add(f)
          cursor = f
        }
      }
    }
    if (firings.isNotEmpty()) {
      Spacer(Modifier.height(4.dp))
      Text("Ближайшие дни", style = MaterialTheme.typography.labelMedium)
      firings.forEach {
        Text(
          "• ${it.toLocalDate().localized()} · %02d:%02d".format(it.hour, it.minute),
          style = MaterialTheme.typography.bodySmall
        )
      }
    }
  }
}

/** Мини-лента ближайших [days] дней: цвет типа смены + число + колокольчик, если звонит. */
@Composable
private fun PreviewStrip(
  schedule: ShiftSchedule,
  calendar: ru.titeha.shiftalarm.schedule.ProductionCalendar?,
  start: LocalDate,
  days: Int,
  dark: Boolean
) {
  val onCell = onCellColor(dark)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(3.dp)
  ) {
    for (k in 0 until days) {
      val d = start.plusDays(k.toLong())
      val cat = ShiftEngine.shiftOn(d, schedule).category
      val rings = ShiftEngine.wakeTimeOn(d, schedule, calendar) != null
      Column(
        modifier = Modifier
          .width(34.dp)
          .background(colorOf(categoryToDayKind(cat), dark), RoundedCornerShape(6.dp))
          .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(DOW_SHORT[d.dayOfWeek.value - 1], color = onCell, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Text("${d.dayOfMonth}", color = onCell, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        if (rings) {
          Icon(Icons.Filled.Notifications, contentDescription = null, tint = onCell, modifier = Modifier.size(11.dp))
        } else {
          Spacer(Modifier.height(11.dp))
        }
      }
    }
  }
}

/** Строка ближайшего звонка смены: «Пн 13 · Утро · звонок 05:30» (для ночи — «звонок накануне»). */
@Composable
private fun OccurrenceRow(occ: ru.titeha.shiftalarm.schedule.AlarmOccurrence) {
  val dow = DOW_SHORT[occ.servedDate.dayOfWeek.value - 1]
  val label = labelOfCategory(occ.shift.category)
  val time = "%02d:%02d".format(occ.ringAt.hour, occ.ringAt.minute)
  val alarm = if (occ.eveningBefore) "звонок накануне, $time" else "звонок $time"
  Text("$dow ${occ.servedDate.dayOfMonth} · $label · $alarm", style = MaterialTheme.typography.bodySmall)
}

/** День развёрнутого цикла: индекс + слот + порядковый номер внутри своего блока. */
private data class CycleDay(
  val index: Int,
  val slot: ShiftType,
  val ordinalInBlock: Int,
  val blockSize: Int
)

/** Развернуть слоты в дни с метаданными блока (порядковый номер дня внутри блока). */
private fun cycleDaysOf(slots: List<ShiftType>): List<CycleDay> {
  val result = mutableListOf<CycleDay>()
  var idx = 0
  ShiftCycle.group(slots).forEach { run ->
    for (o in 0 until run.count) {
      result.add(CycleDay(idx, run.slot, o + 1, run.count))
      idx++
    }
  }
  return result
}

/** Дата вопроса-якоря: «ср 15 июля» (день недели + число + месяц в родительном формате). */
private val QUESTION_DATE_FMT = DateTimeFormatter.ofPattern("EEE d MMMM", Locale.getDefault())
private fun LocalDate.questionFormat(): String = format(QUESTION_DATE_FMT)

/**
 * Якорь цикла через вопрос «Сегодня, ... — какая смена?». Пользователь выбирает тип, а при
 * неоднозначности (тип встречается несколько раз) — конкретный день цикла. Хранение — [CycleAnchor]:
 * опорная дата = сегодня − индекс выбранного дня, инвариант «сегодня резолвится в выбранный день».
 * Для существующего будильника показывается summary из текущего anchor.
 */
@Composable
private fun CycleAnchorQuestion(draft: AlarmEntity, onChange: (AlarmEntity) -> Unit) {
  val today = remember { LocalDate.now() }
  val slots = remember(draft.cycleSpec, draft.presetId) { AlarmTimes.shiftBase(draft)?.slots ?: emptyList() }
  if (slots.isEmpty()) {
    Text("Цикл не задан.", style = MaterialTheme.typography.bodySmall)
    return
  }
  val days = remember(slots) { cycleDaysOf(slots) }
  val currentIndex = CycleAnchor.todayIndex(today, LocalDate.ofEpochDay(draft.anchorEpochDay), slots.size)
  val anchorDate = LocalDate.ofEpochDay(draft.anchorEpochDay)
  val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

  var picking by remember { mutableStateOf(false) }
  var chosenCat by remember { mutableStateOf<ShiftCategory?>(null) }
  var pickingDate by remember { mutableStateOf(false) }
  var showAdvanced by remember { mutableStateOf(false) }

  fun applyIndex(idx: Int) {
    onChange(draft.copy(anchorEpochDay = CycleAnchor.anchorDateForToday(today, idx).toEpochDay()))
    picking = false
    chosenCat = null
  }

  if (!picking) {
    val cur = days.getOrNull(currentIndex) ?: days.first()
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        "Сегодня: ${labelOfCategory(cur.slot.category)} (${cur.ordinalInBlock}-й из ${cur.blockSize})",
        style = MaterialTheme.typography.bodyMedium
      )
      Spacer(Modifier.weight(1f))
      TextButton(onClick = { picking = true; chosenCat = null }) { Text("Изменить") }
    }
    return
  }

  Text("Сегодня, ${today.questionFormat()} — какая смена?", style = MaterialTheme.typography.bodyMedium)
  Spacer(Modifier.height(6.dp))
  val cats = remember(days) { days.map { it.slot.category }.distinct() }
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    cats.forEach { cat ->
      ModeChip(labelOfCategory(cat), chosenCat == cat) {
        val candidates = days.filter { it.slot.category == cat }
        if (candidates.size == 1) applyIndex(candidates.first().index) else chosenCat = cat
      }
    }
  }

  // Дизамбигуация: тип встречается несколько раз → показать дни, выбрать конкретный.
  chosenCat?.let { cat ->
    if (days.count { it.slot.category == cat } > 1) {
      Spacer(Modifier.height(8.dp))
      Text("Который из этих дней — сегодня?", style = MaterialTheme.typography.bodyMedium)
      Spacer(Modifier.height(4.dp))
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
      ) {
        days.forEach { di ->
          val isCandidate = di.slot.category == cat
          DaySquare(di, isCandidate, dark) { applyIndex(di.index) }
        }
      }
    }
  }

  // Дополнительно — альтернатива для вахты: «Начало цикла с даты» (день 1 = эта дата, можно будущую).
  Spacer(Modifier.height(8.dp))
  TextButton(onClick = { showAdvanced = !showAdvanced }) {
    Text(if (showAdvanced) "Скрыть дополнительно" else "Дополнительно")
  }
  if (showAdvanced) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("Начало цикла с даты:", style = MaterialTheme.typography.bodySmall)
      Spacer(Modifier.width(8.dp))
      TextButton(onClick = { pickingDate = true }) { Text(anchorDate.localized()) }
    }
    Text("День 1 цикла — эта дата (можно будущую: «заезд с …»).", style = MaterialTheme.typography.labelSmall)
  }
  TextButton(onClick = { picking = false; chosenCat = null }) { Text("Отмена") }

  if (pickingDate) {
    StartDatePickerDialog(
      initial = anchorDate,
      onPick = { d ->
        onChange(draft.copy(anchorEpochDay = d.toEpochDay()))
        picking = false
        chosenCat = null
      },
      onDismiss = { pickingDate = false }
    )
  }
}

/** Квадратик дня цикла: цвет типа + номер дня; кандидат — с подписью «N-й», прочие приглушены. */
@Composable
private fun DaySquare(day: CycleDay, active: Boolean, dark: Boolean, onClick: () -> Unit) {
  val onCell = onCellColor(dark)
  Column(
    modifier = Modifier
      .width(40.dp)
      .then(if (!active) Modifier.alpha(0.4f) else Modifier)
      .background(colorOf(categoryToDayKind(day.slot.category), dark), RoundedCornerShape(6.dp))
      .then(if (active) Modifier.clickable { onClick() } else Modifier)
      .padding(vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text("${day.index + 1}", color = onCell, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    Text(
      if (active) "${day.ordinalInBlock}-й" else "",
      color = onCell,
      style = MaterialTheme.typography.labelSmall,
      maxLines = 1
    )
  }
}

/** Диалог выбора даты начала цикла (Material3). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartDatePickerDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
  val state = rememberDatePickerState(initialSelectedDateMillis = initial.toEpochDay() * 86_400_000L)
  DatePickerDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(onClick = {
        state.selectedDateMillis?.let { onPick(LocalDate.ofEpochDay(it / 86_400_000L)) }
        onDismiss()
      }) { Text("Готово") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
  ) { DatePicker(state = state) }
}

/** Сворачиваемый календарь недельного будильника (наглядно видно эффект «учитывать праздники»). */
@Composable
private fun WeeklyCalendarSection(alarm: AlarmEntity) {
  var show by remember { mutableStateOf(false) }
  TextButton(onClick = { show = !show }) {
    Text(if (show) "Скрыть календарь" else "Календарь")
  }
  if (show) WeeklyCalendar(alarm)
}

/**
 * Сворачиваемый блок «Календарь и правки» (по умолчанию свёрнут): наглядный календарь резолва смен
 * от текущего черновика + диалог подмены смены/периода на день или диапазон. Только для графика.
 */
@Composable
private fun ShiftCalendarAndOverrides(
  draft: AlarmEntity,
  periods: List<AlarmPeriod>,
  overrides: List<AlarmOverride>,
  onAddPeriod: (AlarmPeriod) -> Unit,
  onRemovePeriod: (AlarmPeriod) -> Unit,
  onPeriodsChange: (List<AlarmPeriod>) -> Unit,
  onOverridesChange: (List<AlarmOverride>) -> Unit
) {
  var showCalendar by remember { mutableStateOf(false) }
  var rangeMode by remember { mutableStateOf(false) }
  var rangeStart by remember { mutableStateOf<LocalDate?>(null) }
  // Ожидающая правки цель (диалог открыт): from..to; один день = from == to.
  var pending by remember { mutableStateOf<Pair<LocalDate, LocalDate>?>(null) }
  // Текущее расписание с применёнными правками — общее для календаря и обработчика правок.
  val schedule = AlarmTimes.shiftScheduleOf(draft, periods, overrides.mapNotNull { it.toDayOverrideOrNull() })
  TextButton(onClick = { showCalendar = !showCalendar }) {
    Text(if (showCalendar) "Скрыть: календарь и правки" else "Календарь и правки")
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
      Text(
        "Или зажми день и веди пальцем — выделит диапазон в этом месяце.",
        style = MaterialTheme.typography.labelSmall
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
        highlightDay = rangeStart,
        honorHolidays = draft.honorHolidays,
        onRangeSelected = { from, to -> pending = from to to; rangeStart = null }
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
  if (category == ShiftCategory.OFF) null else ShiftCycle.defaultAlarmFor(category),
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
              " (${ShiftCycle.defaultAlarmFor(cat).format(DateTimeFormatter.ofPattern("HH:mm"))})"
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
