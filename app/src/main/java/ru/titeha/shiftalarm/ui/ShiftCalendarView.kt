package ru.titeha.shiftalarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.titeha.shiftalarm.schedule.ShiftCalendar
import ru.titeha.shiftalarm.schedule.ShiftCalendar.DayKind
import ru.titeha.shiftalarm.schedule.ShiftEngine
import ru.titeha.shiftalarm.schedule.ShiftSchedule
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val WEEKDAYS = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

/** Цвет ячейки по типу дня (палитра Фазы 1, выводится из времени подъёма). */
private fun colorOf(kind: DayKind): Color = when (kind) {
  DayKind.MORNING -> Color(0xFFFFD54F)  // утро — жёлтый
  DayKind.DAY -> Color(0xFFFF9800)      // день — оранжевый
  DayKind.NIGHT -> Color(0xFF42A5F5)    // ночь — синий
  DayKind.OFF -> Color(0xFFE57373)      // выходной — красный
  DayKind.VACATION -> Color(0xFF66BB6A) // отпуск — зелёный
}

private fun labelOf(kind: DayKind): String = when (kind) {
  DayKind.MORNING -> "Утро"
  DayKind.DAY -> "День"
  DayKind.NIGHT -> "Ночь"
  DayKind.OFF -> "Выходной"
  DayKind.VACATION -> "Отпуск"
}

/**
 * Наглядный календарь смен (read-only): месяц с цветовой меткой типа дня + легенда, листание ←/→.
 * Тип дня берётся из [ShiftCalendar.kindOf] по переданному расписанию [schedule].
 */
@Composable
fun ShiftCalendarView(schedule: ShiftSchedule, modifier: Modifier = Modifier) {
  var month by remember { mutableStateOf(YearMonth.now()) }
  val today = LocalDate.now()

  Column(modifier = modifier.fillMaxWidth()) {
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
    cells.chunked(7).forEach { week ->
      Row(Modifier.fillMaxWidth()) {
        for (i in 0 until 7) {
          val date = week.getOrNull(i)
          DayCell(
            date = date,
            kind = date?.let { ShiftCalendar.kindOf(it, schedule) },
            rings = date != null && ShiftEngine.wakeTimeOn(date, schedule) != null,
            isToday = date == today,
            modifier = Modifier.weight(1f)
          )
        }
      }
    }

    Spacer(Modifier.height(8.dp))
    Legend()
    Spacer(Modifier.height(4.dp))
    Text("• — в этот день звонит будильник", style = MaterialTheme.typography.labelSmall)
  }
}

@Composable
private fun DayCell(date: LocalDate?, kind: DayKind?, rings: Boolean, isToday: Boolean, modifier: Modifier) {
  Box(
    modifier = modifier
      .aspectRatio(1f)
      .padding(2.dp)
      .then(if (kind != null) Modifier.background(colorOf(kind), RoundedCornerShape(6.dp)) else Modifier)
      .then(
        if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(6.dp))
        else Modifier
      ),
    contentAlignment = Alignment.Center
  ) {
    if (date != null) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          "${date.dayOfMonth}",
          color = Color.Black,
          fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
          style = MaterialTheme.typography.bodySmall
        )
        // Точка — будильник в этот день звонит (в т.ч. на выходном вых* перед ночами).
        if (rings) {
          Box(
            Modifier
              .size(5.dp)
              .background(Color.Black, RoundedCornerShape(50))
          )
        }
      }
    }
  }
}

@Composable
private fun Legend() {
  FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    DayKind.entries.forEach { kind ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          Modifier
            .size(14.dp)
            .background(colorOf(kind), RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.size(4.dp))
        Text(labelOf(kind), style = MaterialTheme.typography.labelSmall)
      }
    }
  }
}
