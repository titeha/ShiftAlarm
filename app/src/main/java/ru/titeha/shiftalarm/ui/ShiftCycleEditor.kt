package ru.titeha.shiftalarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import dev.analog.AnalogTimePicker
import ru.titeha.shiftalarm.data.AlarmEntity
import ru.titeha.shiftalarm.schedule.ShiftCategory
import ru.titeha.shiftalarm.schedule.ShiftCycleCodec
import ru.titeha.shiftalarm.schedule.ShiftPresets
import ru.titeha.shiftalarm.schedule.ShiftType
import java.time.LocalDate
import java.time.LocalTime

/** Слоты пресета как стартовый шаблон редактора (опорная дата не важна — берём слоты). */
internal fun templateSlotsOf(presetId: String): List<ShiftType> =
  ShiftPresets.byId(presetId)?.build(LocalDate.now())?.base?.slots
    ?: listOf(ShiftType("c0", "Смена", LocalTime.of(7, 0)), ShiftType.off())

/**
 * Выбор графика смены: пресеты-шаблоны + «Свой цикл» с полноценным редактором слотов.
 * Источник правды — [AlarmEntity.cycleSpec]: null = используется пресет [AlarmEntity.presetId];
 * иначе цикл редактируется здесь (слоты декодируются из spec, правки кодируются обратно).
 */
@Composable
fun ShiftCycleEditor(draft: AlarmEntity, onChange: (AlarmEntity) -> Unit) {
  val custom = draft.cycleSpec != null
  val slots = draft.cycleSpec?.let { ShiftCycleCodec.decode(it) }.orEmpty()

  fun setSlots(newSlots: List<ShiftType>) =
    onChange(draft.copy(cycleSpec = ShiftCycleCodec.encode(newSlots)))

  Text("График:", style = MaterialTheme.typography.titleMedium)
  Spacer(Modifier.height(8.dp))

  FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
  ) {
    ShiftPresets.all.forEach { preset ->
      ModeChip(preset.title, !custom && draft.presetId == preset.id) {
        onChange(
          draft.copy(
            presetId = preset.id,
            cycleSpec = null,
            anchorEpochDay = LocalDate.now().toEpochDay()
          )
        )
      }
    }
    // Переход в режим своего цикла: за основу берём слоты текущего пресета.
    ModeChip("Свой цикл", custom) {
      onChange(
        draft.copy(
          cycleSpec = ShiftCycleCodec.encode(templateSlotsOf(draft.presetId)),
          anchorEpochDay = if (draft.anchorEpochDay == 0L)
            LocalDate.now().toEpochDay() else draft.anchorEpochDay
        )
      )
    }
  }

  if (!custom) {
    Spacer(Modifier.height(8.dp))
    Text("Время подъёма задаётся графиком смены.", style = MaterialTheme.typography.bodySmall)
    return
  }

  // --- Редактор произвольного цикла ---
  Spacer(Modifier.height(12.dp))
  Text(
    "Цикл из ${slots.size} дн.: повторяется по кругу. Каждый слот — рабочий день (со временем " +
      "подъёма) или выходной.",
    style = MaterialTheme.typography.bodySmall
  )
  Spacer(Modifier.height(8.dp))

  slots.forEachIndexed { i, slot ->
    SlotCard(
      index = i,
      slot = slot,
      isFirst = i == 0,
      isLast = i == slots.lastIndex,
      onChange = { updated -> setSlots(slots.toMutableList().also { it[i] = updated }) },
      onRemove = { setSlots(slots.toMutableList().also { it.removeAt(i) }) },
      onMoveUp = { setSlots(slots.toMutableList().also { it.add(i - 1, it.removeAt(i)) }) },
      onMoveDown = { setSlots(slots.toMutableList().also { it.add(i + 1, it.removeAt(i)) }) }
    )
    Spacer(Modifier.height(8.dp))
  }

  Button(
    onClick = { setSlots(slots + ShiftType("c${slots.size}", "Смена", LocalTime.of(7, 0))) },
    enabled = slots.size < 60
  ) { Text("+ День") }

  if (slots.isEmpty()) {
    Spacer(Modifier.height(8.dp))
    Text(
      "Цикл пуст — добавьте хотя бы один день, иначе будет использован пресет.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.error
    )
  }
}

private val CATEGORIES = listOf(
  ShiftCategory.MORNING to "Утро",
  ShiftCategory.DAY to "День",
  ShiftCategory.NIGHT to "Ночь",
  ShiftCategory.OFF to "Выходной"
)

/** Время будильника по умолчанию при включении тумблера — под категорию (звонок раньше старта). */
private fun defaultAlarmFor(category: ShiftCategory): LocalTime = when (category) {
  ShiftCategory.MORNING -> LocalTime.of(5, 0)
  ShiftCategory.DAY -> LocalTime.of(13, 0)
  ShiftCategory.NIGHT, ShiftCategory.OFF -> LocalTime.of(21, 0)
}

@Composable
private fun SlotCard(
  index: Int,
  slot: ShiftType,
  isFirst: Boolean,
  isLast: Boolean,
  onChange: (ShiftType) -> Unit,
  onRemove: () -> Unit,
  onMoveUp: () -> Unit,
  onMoveDown: () -> Unit
) {
  var pickingTime by remember { mutableStateOf(false) }
  val wt = slot.wakeTime

  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("День ${index + 1}", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onMoveUp, enabled = !isFirst) { Text("↑") }
        TextButton(onClick = onMoveDown, enabled = !isLast) { Text("↓") }
        TextButton(onClick = onRemove) { Text("Удалить") }
      }

      OutlinedTextField(
        value = slot.name,
        onValueChange = { onChange(slot.copy(name = it)) },
        label = { Text("Название дня") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
      )
      Spacer(Modifier.height(8.dp))

      // Тип дня — задаёт цвет на календаре, не зависит от будильника.
      Text("Тип (цвет в календаре):", style = MaterialTheme.typography.labelMedium)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CATEGORIES.forEach { (cat, label) ->
          ModeChip(label, slot.category == cat) { onChange(slot.copy(category = cat)) }
        }
      }
      Spacer(Modifier.height(8.dp))

      // Будильник — отдельно: ночь можно без звонка, выходной — со звонком (уход в ночь вечером).
      Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(
          checked = wt != null,
          onCheckedChange = { on ->
            onChange(slot.copy(wakeTime = if (on) defaultAlarmFor(slot.category) else null))
          }
        )
        Spacer(Modifier.width(8.dp))
        Text("Будильник", style = MaterialTheme.typography.bodyMedium)
        if (wt != null) {
          Spacer(Modifier.width(8.dp))
          TextButton(onClick = { pickingTime = true }) {
            Text("%02d:%02d".format(wt.hour, wt.minute))
          }
        }
      }
    }
  }

  if (pickingTime && wt != null) {
    SlotTimeDialog(
      time = wt,
      onPick = { onChange(slot.copy(wakeTime = it)) },
      onDismiss = { pickingTime = false }
    )
  }
}

@Composable
private fun SlotTimeDialog(time: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
  androidx.compose.material3.AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
    title = { Text("Время подъёма") },
    text = {
      AnalogTimePicker(
        time = time,
        onTimeChange = onPick,
        snapLabel = "5 минут",
        nowLabel = "Сейчас"
      )
    }
  )
}
