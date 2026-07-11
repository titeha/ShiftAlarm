package ru.titeha.shiftalarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ru.titeha.shiftalarm.schedule.ShiftCalendar
import ru.titeha.shiftalarm.schedule.ShiftCalendar.DayKind
import ru.titeha.shiftalarm.schedule.ProductionCalendars
import ru.titeha.shiftalarm.schedule.ShiftCategory
import ru.titeha.shiftalarm.schedule.ShiftEngine
import ru.titeha.shiftalarm.schedule.ShiftSchedule
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val WEEKDAYS = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

/**
 * Цвет ячейки по типу дня. Хью сохраняются (семантика цвета смен узнаваема), но на тёмной теме
 * берутся более глубокие варианты, чтобы гармонировать с фоном и держать контраст с белым текстом.
 */
internal fun colorOf(kind: DayKind, dark: Boolean): Color = if (!dark) when (kind) {
  DayKind.MORNING -> Color(0xFFFFD54F)  // утро — жёлтый
  DayKind.DAY -> Color(0xFFFF9800)      // день — оранжевый
  DayKind.NIGHT -> Color(0xFF42A5F5)    // ночь — синий
  DayKind.OFF -> Color(0xFFE57373)      // выходной — красный
  DayKind.VACATION -> Color(0xFF66BB6A) // отпуск — зелёный
  DayKind.SICK -> Color(0xFF7C8A3E)     // больничный — болотный
  DayKind.DAYOFF -> Color(0xFF4DB6AC)   // отгул — бирюзовый
  DayKind.UNPAID -> Color(0xFF9575CD)   // за свой счёт — сиреневый
} else when (kind) {
  DayKind.MORNING -> Color(0xFF6D5A16)
  DayKind.DAY -> Color(0xFF8A5200)
  DayKind.NIGHT -> Color(0xFF1E4A6E)
  DayKind.OFF -> Color(0xFF7A3B3B)
  DayKind.VACATION -> Color(0xFF33612F)
  DayKind.SICK -> Color(0xFF444C22)
  DayKind.DAYOFF -> Color(0xFF2A6560)
  DayKind.UNPAID -> Color(0xFF4E3E73)
}

/** Цвет текста/значка на цветной ячейке: чёрный на светлой теме, белый на тёмной. */
internal fun onCellColor(dark: Boolean): Color = if (dark) Color.White else Color.Black

/** Категория смены → тип дня календаря (для переиспользования палитры в ленте цикла). */
internal fun categoryToDayKind(category: ShiftCategory): DayKind = when (category) {
  ShiftCategory.MORNING -> DayKind.MORNING
  ShiftCategory.DAY -> DayKind.DAY
  ShiftCategory.NIGHT -> DayKind.NIGHT
  ShiftCategory.OFF -> DayKind.OFF
}

private fun labelOf(kind: DayKind): String = when (kind) {
  DayKind.MORNING -> "Утро"
  DayKind.DAY -> "День"
  DayKind.NIGHT -> "Ночь"
  DayKind.OFF -> "Выходной"
  DayKind.VACATION -> "Отпуск"
  DayKind.SICK -> "Больничный"
  DayKind.DAYOFF -> "Отгул"
  DayKind.UNPAID -> "Свой счёт"
}

/**
 * Наглядный календарь смен: месяц с цветовой меткой типа дня + легенда, листание ←/→.
 * Тип дня берётся из [ShiftCalendar.kindOf] по переданному расписанию [schedule].
 *
 * Если задан [onDayClick] — ячейки кликабельны (тап по дню → подмена/исключение в редакторе);
 * без него календарь read-only. Если задан [onRangeSelected] — доступен жест long-press + drag для
 * выделения диапазона в пределах видимого месяца (поверх тап-тап, для одной руки/быстрого выбора).
 */
@Composable
fun ShiftCalendarView(
  schedule: ShiftSchedule,
  modifier: Modifier = Modifier,
  onDayClick: ((LocalDate) -> Unit)? = null,
  highlightDay: LocalDate? = null,
  honorHolidays: Boolean = false,
  onRangeSelected: ((LocalDate, LocalDate) -> Unit)? = null
) {
  var month by remember { mutableStateOf(YearMonth.now()) }
  val today = LocalDate.now()
  // Календарь праздников для точки-звонка — только если будильник учитывает праздники. Так точка
  // совпадает с тем, что реально запланирует планировщик (не рисуем звонок на празднике).
  val holidayCal = remember(month, honorHolidays) {
    if (honorHolidays) ProductionCalendars.merged("RU", month.year) else null
  }
  // Фактическая тема (учитывает выбор пользователя, не только систему) — по яркости поверхности.
  val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

  Column(
    modifier = modifier
      .fillMaxWidth()
      // Свайп влево/вправо листает месяцы (в дополнение к стрелкам ‹ ›). Быстрый горизонтальный
      // жест; выделение диапазона (long-press + drag) срабатывает иначе и поглощает свой жест.
      .pointerInput(Unit) {
        var dx = 0f
        val threshold = 48.dp.toPx()
        detectHorizontalDragGestures(
          onDragStart = { dx = 0f },
          onHorizontalDrag = { _, amount -> dx += amount },
          onDragEnd = {
            if (dx > threshold) month = month.minusMonths(1)
            else if (dx < -threshold) month = month.plusMonths(1)
          }
        )
      }
  ) {
    // Заголовок: ‹ Месяц Год ›
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹") }
      val title = month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
      Text("$title ${month.year}", style = MaterialTheme.typography.titleMedium)
      TextButton(onClick = { month = month.plusMonths(1) }) { Text("›") }
    }
    Spacer(Modifier.height(4.dp))

    // Шапка дней недели
    Row(Modifier.fillMaxWidth()) {
      WEEKDAYS.forEach { d ->
        Text(
          d,
          modifier = Modifier.weight(1f),
          textAlign = androidx.compose.ui.text.style.TextAlign.Center,
          style = MaterialTheme.typography.labelSmall
        )
      }
    }

    // Сетка месяца. Ведущие пустые ячейки — до первого дня (неделя с понедельника).
    val firstDay = month.atDay(1)
    val lead = firstDay.dayOfWeek.value - 1
    val cells: List<LocalDate?> =
      List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    val weeks = cells.chunked(7)
    val rows = weeks.size

    // Выделение диапазона жестом long-press + drag (поверх тап-тап, не заменяя его). Позиция пальца
    // → ячейка по размеру сетки; за пределами месяца — clamp к ближайшему дню. Хаптика на смене границы.
    var gridSize by remember { mutableStateOf(IntSize.Zero) }
    var dragStart by remember { mutableStateOf<LocalDate?>(null) }
    var dragEnd by remember { mutableStateOf<LocalDate?>(null) }
    val haptic = LocalHapticFeedback.current

    fun dayAt(pos: Offset): LocalDate {
      val cw = if (gridSize.width > 0) gridSize.width / 7f else 1f
      val ch = if (rows > 0 && gridSize.height > 0) gridSize.height / rows.toFloat() else 1f
      val col = (pos.x / cw).toInt().coerceIn(0, 6)
      val row = (pos.y / ch).toInt().coerceIn(0, rows - 1)
      val day = (row * 7 + col - lead + 1).coerceIn(1, month.lengthOfMonth())
      return month.atDay(day)
    }

    val dragRange: ClosedRange<LocalDate>? = dragStart?.let { s ->
      dragEnd?.let { e -> minOf(s, e)..maxOf(s, e) }
    }

    Column(
      Modifier
        .fillMaxWidth()
        .onSizeChanged { gridSize = it }
        .then(
          if (onRangeSelected != null) Modifier.pointerInput(rows, lead, month) {
            detectDragGesturesAfterLongPress(
              onDragStart = { off ->
                val d = dayAt(off)
                dragStart = d; dragEnd = d
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              },
              onDrag = { change, _ ->
                val d = dayAt(change.position)
                if (d != dragEnd) {
                  dragEnd = d
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
              },
              onDragEnd = {
                val s = dragStart; val e = dragEnd
                if (s != null && e != null) onRangeSelected(minOf(s, e), maxOf(s, e))
                dragStart = null; dragEnd = null
              },
              onDragCancel = { dragStart = null; dragEnd = null }
            )
          } else Modifier
        )
    ) {
      weeks.forEach { week ->
        Row(Modifier.fillMaxWidth()) {
          for (i in 0 until 7) {
            val date = week.getOrNull(i)
            DayCell(
              date = date,
              kind = date?.let { ShiftCalendar.kindOf(it, schedule) },
              rings = date != null && ShiftEngine.wakeTimeOn(date, schedule, holidayCal) != null,
              isToday = date == today,
              isHighlighted = date != null && date == highlightDay,
              isInDragRange = date != null && dragRange != null && date in dragRange,
              dark = dark,
              onClick = if (date != null && onDayClick != null) {
                { onDayClick(date) }
              } else null,
              modifier = Modifier.weight(1f)
            )
          }
        }
      }
    }

    Spacer(Modifier.height(8.dp))
    Legend(dark)
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(12.dp))
      Spacer(Modifier.size(4.dp))
      Text("— в этот день звонит будильник", style = MaterialTheme.typography.labelSmall)
    }
  }
}

@Composable
private fun DayCell(
  date: LocalDate?,
  kind: DayKind?,
  rings: Boolean,
  isToday: Boolean,
  isHighlighted: Boolean,
  isInDragRange: Boolean,
  dark: Boolean,
  onClick: (() -> Unit)?,
  modifier: Modifier
) {
  // Якорь диапазона / дни выделяемого drag-диапазона — рамкой основного цвета; сегодня — контрастной.
  val border = when {
    isHighlighted -> 3.dp to MaterialTheme.colorScheme.primary
    isInDragRange -> 2.dp to MaterialTheme.colorScheme.primary
    isToday -> 2.dp to MaterialTheme.colorScheme.onSurface
    else -> null
  }
  val onCell = onCellColor(dark)
  Box(
    modifier = modifier
      .aspectRatio(1f)
      .padding(2.dp)
      .then(if (kind != null) Modifier.background(colorOf(kind, dark), RoundedCornerShape(6.dp)) else Modifier)
      .then(
        if (border != null) Modifier.border(border.first, border.second, RoundedCornerShape(6.dp))
        else Modifier
      )
      .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    contentAlignment = Alignment.Center
  ) {
    if (date != null) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          "${date.dayOfMonth}",
          color = onCell,
          fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
          style = MaterialTheme.typography.bodySmall
        )
        // Колокольчик — будильник в этот день звонит (в т.ч. на выходном вых* перед ночами).
        if (rings) {
          Icon(
            Icons.Filled.Notifications,
            contentDescription = null,
            tint = onCell,
            modifier = Modifier.size(11.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun Legend(dark: Boolean) {
  FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    DayKind.entries.forEach { kind ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          Modifier
            .size(14.dp)
            .background(colorOf(kind, dark), RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.size(4.dp))
        Text(labelOf(kind), style = MaterialTheme.typography.labelSmall)
      }
    }
  }
}
