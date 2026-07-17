package ru.titeha.shiftalarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ru.titeha.shiftalarm.data.SettingsStore
import ru.titeha.shiftalarm.schedule.WeekPairNaming
import ru.titeha.shiftalarm.schedule.resolve
import java.time.DayOfWeek
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.analog.AnalogTimePicker
import ru.titeha.shiftalarm.data.AlarmEntity
import ru.titeha.shiftalarm.schedule.ShiftCategory
import ru.titeha.shiftalarm.schedule.ShiftCycle
import ru.titeha.shiftalarm.schedule.ShiftCycleCodec
import ru.titeha.shiftalarm.schedule.ShiftPresets
import ru.titeha.shiftalarm.schedule.ShiftType
import ru.titeha.shiftalarm.schedule.SlotRun
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
  // decodeOrNull (не строгий decode): повреждённый cycleSpec не должен ронять открытие редактора —
  // тогда покажем пустой «свой цикл», а планировщик и так откатывается на пресет.
  val slots = draft.cycleSpec?.let { ShiftCycleCodec.decodeOrNull(it) }.orEmpty()

  fun setSlots(newSlots: List<ShiftType>) =
    onChange(draft.copy(cycleSpec = ShiftCycleCodec.encode(newSlots)))

  val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

  val context = LocalContext.current
  val weekStart = remember { SettingsStore(context).weekStart().resolve(Locale.getDefault()) }
  val pairNaming = remember { SettingsStore(context).weekPairNaming() }
  var showStudyWizard by remember { mutableStateOf(false) }

  Text("График:", style = MaterialTheme.typography.titleMedium)
  Spacer(Modifier.height(8.dp))

  // Пресеты как точка входа: «возьми шаблон и поправь». Под чипом — мини-полоска цветов цикла.
  FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
  ) {
    ShiftPresets.all.forEach { preset ->
      val presetSlots = remember(preset.id) { templateSlotsOf(preset.id) }
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(92.dp)
      ) {
        ModeChip(preset.title, !custom && draft.presetId == preset.id) {
          onChange(
            draft.copy(
              presetId = preset.id,
              cycleSpec = null,
              anchorEpochDay = LocalDate.now().toEpochDay()
            )
          )
        }
        Spacer(Modifier.height(3.dp))
        MiniCycleStrip(presetSlots, dark, Modifier.fillMaxWidth().padding(horizontal = 4.dp))
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
    // Учебный шаблон: мастер собирает 7/14-дневный цикл поверх сменного режима.
    ModeChip("Учёба", false) { showStudyWizard = true }
  }

  if (showStudyWizard) {
    StudyWizard(
      weekStart = weekStart,
      naming = pairNaming,
      onDone = { plan ->
        onChange(
          draft.copy(
            cycleSpec = ShiftCycleCodec.encode(plan.slots),
            anchorEpochDay = plan.anchorDate.toEpochDay(),
            label = draft.label.ifBlank { "Учёба" }
          )
        )
        showStudyWizard = false
      },
      onDismiss = { showStudyWizard = false }
    )
  }

  if (!custom) {
    Spacer(Modifier.height(8.dp))
    Text("Время подъёма задаётся графиком смены.", style = MaterialTheme.typography.bodySmall)
    return
  }

  // --- Сегментная лента блоков цикла; редактирование выбранного блока — в bottom sheet ---
  val runs = ShiftCycle.group(slots)
  Spacer(Modifier.height(12.dp))
  Text(
    "Цикл ${slots.size} дн. (${runs.size} бл.): повторяется по кругу. Блок — несколько одинаковых " +
      "дней подряд. Тап по блоку — правка типа, количества, будильника.",
    style = MaterialTheme.typography.bodySmall
  )
  Spacer(Modifier.height(8.dp))

  // selected — подсветка блока в ленте; editing — индекс блока, для которого открыт sheet.
  var selected by remember { mutableStateOf<Int?>(null) }
  var editing by remember { mutableStateOf<Int?>(null) }
  var pendingNew by remember { mutableStateOf<PendingNewBlock?>(null) }

  fun applyRuns(newRuns: List<SlotRun>) {
    setSlots(ShiftCycle.expand(newRuns))
  }

  CycleStrip(
    runs = runs,
    selectedIndex = selected,
    dark = dark,
    onSelect = { index ->
      pendingNew = null
      selected = index
      editing = index
    }
  )

  Spacer(Modifier.height(8.dp))

  Button(
    onClick = {
      /*
       * Новый блок пока существует только как локальный черновик.
       * cycleSpec изменится после подтверждения галочкой.
       */
      val at = (
              (selected ?: runs.lastIndex) + 1
              ).coerceIn(0, runs.size)

      val neighbours = setOfNotNull(
        runs.getOrNull(at - 1)?.slot?.category,
        runs.getOrNull(at)?.slot?.category
      )

      val category = ShiftCycle.distinctCategoryFrom(neighbours)

      pendingNew = PendingNewBlock(
        insertAt = at,
        block = SlotRun(
          slot = ShiftCycle.blockOf(category),
          count = 1
        )
      )

      editing = null
    },
    enabled = ShiftCycleEditLimits.canAddBlock(slots.size)
  ) {
    Text("+ блок")
  }

  if (slots.isEmpty()) {
    Spacer(Modifier.height(8.dp))
    Text(
      "Цикл пуст — добавьте хотя бы один блок. Сохранение недоступно.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.error
    )
  }

  editing?.let { index ->
    if (runs.isNotEmpty()) {
      val i = index.coerceIn(0, runs.lastIndex)
      val originalBlock = runs[i]
      val otherDays = slots.size - originalBlock.count

      BlockSheet(
        title = "Блок",
        block = originalBlock,
        isFirst = i == 0,
        isLast = i == runs.lastIndex,
        otherDays = otherDays,
        canRemove = runs.size > 1,
        showStructuralActions = true,
        onCommit = { edited ->
          applyRuns(
            runs.toMutableList().also {
              it[i] = edited
            }
          )
          editing = null
        },
        onCancel = {
          editing = null
        },
        onMoveLeft = { edited ->
          applyRuns(
            runs.toMutableList().also {
              it[i] = edited
              it.add(i - 1, it.removeAt(i))
            }
          )
          selected = i - 1
          editing = i - 1
        },
        onMoveRight = { edited ->
          applyRuns(
            runs.toMutableList().also {
              it[i] = edited
              it.add(i + 1, it.removeAt(i))
            }
          )
          selected = i + 1
          editing = i + 1
        },
        onDuplicate = { edited ->
          /*
           * Кнопка уже учитывает локальный размер, но проверка здесь
           * оставлена как последний рубеж структурной операции.
           */
          if (
            ShiftCycleEditLimits.canDuplicate(
              otherDays = otherDays,
              editedCount = edited.count
            )
          ) {
            applyRuns(
              runs.toMutableList().also {
                it[i] = edited
                it.add(i + 1, edited)
              }
            )

            /*
             * Идентичные соседние блоки после нормализации сольются.
             * Поэтому остаёмся на исходном индексе.
             */
            selected = i
            editing = i
          }
        },
        onRemove = {
          applyRuns(
            runs.toMutableList().also {
              it.removeAt(i)
            }
          )
          selected = null
          editing = null
        }
      )
    }
  }

  pendingNew?.let { pending ->
    val insertAt = pending.insertAt.coerceIn(0, runs.size)

    BlockSheet(
      title = "Новый блок",
      block = pending.block,
      isFirst = true,
      isLast = true,
      otherDays = slots.size,
      canRemove = false,
      showStructuralActions = false,
      onCommit = { edited ->
        /*
         * Галочка доступна только для допустимого размера.
         * Повторная проверка защищает саму операцию вставки.
         */
        if (
          ShiftCycleEditLimits.canUseBlockCount(
            otherDays = slots.size,
            count = edited.count
          )
        ) {
          applyRuns(
            runs.toMutableList().also {
              it.add(insertAt, edited)
            }
          )
          selected = null
        }

        pendingNew = null
      },
      onCancel = {
        /*
         * cycleSpec ещё не менялся, поэтому отмена полностью
         * отбрасывает новый блок.
         */
        pendingNew = null
      },
      onMoveLeft = { _ -> },
      onMoveRight = { _ -> },
      onDuplicate = { _ -> },
      onRemove = {}
    )
  }
}

/**
 * Новый блок, который ещё не записан в cycleSpec.
 *
 * Только подтверждение в bottom sheet переносит его в основной цикл.
 */
private data class PendingNewBlock(
  val insertAt: Int,
  val block: SlotRun
)

private val CATEGORIES = listOf(
  ShiftCategory.MORNING to "Утро",
  ShiftCategory.DAY to "День",
  ShiftCategory.NIGHT to "Ночь",
  ShiftCategory.OFF to "Выходной"
)

/** Односимвольная метка типа для сегмента ленты («У/Д/Н/В»). */
private fun shortLabel(category: ShiftCategory): String = when (category) {
  ShiftCategory.MORNING -> "У"
  ShiftCategory.DAY -> "Д"
  ShiftCategory.NIGHT -> "Н"
  ShiftCategory.OFF -> "В"
}

/** Тонкая цветная полоска цикла (превью пресета): ширина сегмента ∝ числу дней, без подписей. */
@Composable
private fun MiniCycleStrip(slots: List<ShiftType>, dark: Boolean, modifier: Modifier = Modifier) {
  val runs = ShiftCycle.group(slots)
  if (runs.isEmpty()) return
  Row(modifier.height(6.dp), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
    runs.forEach { run ->
      Box(
        Modifier
          .weight(run.count.toFloat())
          .fillMaxHeight()
          .background(colorOf(categoryToDayKind(run.slot.category), dark), RoundedCornerShape(2.dp))
      )
    }
  }
}

/**
 * Сегментная лента цикла: горизонтальный ряд блоков, ширина ∝ числу дней. Внутри «У ×3» + колокольчик
 * (если звонит); цвет фона — палитра типов из календаря. Выбранный блок в рамке-акценте. Тап = выбор.
 */
@Composable
private fun CycleStrip(runs: List<SlotRun>, selectedIndex: Int?, dark: Boolean, onSelect: (Int) -> Unit) {
  if (runs.isEmpty()) return
  Row(
    modifier = Modifier.fillMaxWidth().height(52.dp),
    horizontalArrangement = Arrangement.spacedBy(3.dp)
  ) {
    runs.forEachIndexed { i, run ->
      val slot = run.slot
      val onCell = onCellColor(dark)
      val selected = i == selectedIndex
      Box(
        modifier = Modifier
          .weight(run.count.toFloat())
          .widthIn(min = 28.dp)
          .fillMaxHeight()
          .background(colorOf(categoryToDayKind(slot.category), dark), RoundedCornerShape(6.dp))
          .then(
            if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
            else Modifier
          )
          .clickable { onSelect(i) }
          .padding(2.dp),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            "${shortLabel(slot.category)} ×${run.count}",
            color = onCell,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
          )
          if (slot.wakeTime != null) {
            Icon(Icons.Filled.Notifications, contentDescription = null, tint = onCell, modifier = Modifier.size(12.dp))
          }
        }
      }
    }
  }
}

/**
 * Редактирование блока в bottom sheet. Правки копятся ЛОКАЛЬНО и применяются только по ✓ (onCommit);
 * ✕ или смахивание вниз — откат (onCancel). Тип (сегменты), «Дней подряд» (степпер), будильник
 * (тумблер + время), название в «Дополнительно», действия (сдвиг/дублировать/удалить — с фиксацией
 * текущих правок блока).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockSheet(
  title: String,
  block: SlotRun,
  isFirst: Boolean,
  isLast: Boolean,
  otherDays: Int,
  canRemove: Boolean,
  showStructuralActions: Boolean,
  onCommit: (SlotRun) -> Unit,
  onCancel: () -> Unit,
  onMoveLeft: (SlotRun) -> Unit,
  onMoveRight: (SlotRun) -> Unit,
  onDuplicate: (SlotRun) -> Unit,
  onRemove: () -> Unit
) {
  // Локальный черновик блока: правки не трогают цикл, пока не нажата ✓. re-seed при смене блока.
  var slot by remember(block) { mutableStateOf(block.slot) }
  var count by remember(block) { mutableStateOf(block.count) }
  var pickingTime by remember { mutableStateOf(false) }
  var showAdvanced by remember(block) { mutableStateOf(false) }
  val wt = slot.wakeTime
  val staged = SlotRun(slot, count)
  val maxCount = ShiftCycleEditLimits.maxBlockCount(otherDays)
  val countFits = ShiftCycleEditLimits.canUseBlockCount(otherDays = otherDays, count = count)
  val duplicateAllowed = ShiftCycleEditLimits.canDuplicate(otherDays = otherDays, editedCount = count)

  ModalBottomSheet(onDismissRequest = onCancel) {
    Column(
      Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 24.dp)
    ) {
      // Шапка: ✕ отмена — «Блок» — ✓ фиксация.
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onCancel) {
          Icon(Icons.Filled.Close, contentDescription = "Отмена")
        }
        Text(
          title,
          style = MaterialTheme.typography.titleMedium,
          textAlign = TextAlign.Center,
          modifier = Modifier.weight(1f)
        )
        IconButton(
          onClick = { onCommit(staged) },
          enabled = countFits
        ) {
          Icon(
            Icons.Filled.Check,
            contentDescription = "Готово",
            tint = MaterialTheme.colorScheme.primary
          )
        }
      }
      Spacer(Modifier.height(8.dp))

      // Тип — сегментированные кнопки. Смена типа переименовывает авто-имя и правит будильник.
      SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        CATEGORIES.forEachIndexed { idx, entry ->
          val (cat, label) = entry
          SegmentedButton(
            selected = slot.category == cat,
            onClick = { slot = ShiftCycle.retype(slot, cat) },
            shape = SegmentedButtonDefaults.itemShape(idx, CATEGORIES.size)
          ) { Text(label) }
        }
      }
      Spacer(Modifier.height(12.dp))

      // Дней подряд — степпер 1..30.
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Дней подряд:", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { if (count > 1) count-- }, enabled = count > 1) {
          Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text("$count", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { count++ }, enabled = count < maxCount) {
          Text("+", style = MaterialTheme.typography.titleLarge)
        }
      }
      if (maxCount < ShiftCycleEditLimits.MAX_BLOCK_DAYS) {
        val limitText = if (maxCount > 0) {
          "С учётом остальных блоков здесь доступно до $maxCount дн."
        } else {
          "Остальные блоки уже занимают весь лимит цикла. Уменьшите или удалите другой блок."
        }

        Text(
          limitText,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.tertiary
        )
      }
      Spacer(Modifier.height(4.dp))

      // Будильник — тумблер + время. Ночь можно без звонка, выходной — со звонком.
      Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = wt != null, onCheckedChange = { slot = ShiftCycle.setAlarm(slot, it) })
        Spacer(Modifier.width(8.dp))
        Text("Будильник", style = MaterialTheme.typography.bodyMedium)
        if (wt != null) {
          Spacer(Modifier.weight(1f))
          TextButton(onClick = { pickingTime = true }) {
            Text("%02d:%02d".format(wt.hour, wt.minute), style = MaterialTheme.typography.titleMedium)
          }
        }
      }
      // Подпись-смысл (часть смысла, не подсказка): ночь звонит накануне, выходной со звонком.
      if (wt != null) {
        val hint = when (slot.category) {
          ShiftCategory.NIGHT -> "Звонок накануне вечером, в %02d:%02d".format(wt.hour, wt.minute)
          ShiftCategory.OFF -> "Выходной со звонком"
          else -> null
        }
        if (hint != null) {
          Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
      }
      Spacer(Modifier.height(8.dp))

      // Дополнительно — пользовательское название блока (по умолчанию — по типу).
      TextButton(onClick = { showAdvanced = !showAdvanced }) {
        Text(if (showAdvanced) "Скрыть дополнительно" else "Дополнительно")
      }
      if (showAdvanced) {
        OutlinedTextField(
          value = slot.name,
          onValueChange = { slot = slot.copy(name = it) },
          label = { Text("Название блока") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        Text("По умолчанию — по типу («Утро», «День»…).", style = MaterialTheme.typography.labelSmall)
      }
      Spacer(Modifier.height(12.dp))

      // Действия над блоком — фиксируют текущие правки блока, затем структурная операция.
      if (showStructuralActions) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(
            onClick = { onMoveLeft(staged) },
            enabled = !isFirst && countFits
          ) {
            Text("Сдвинуть влево")
          }

          TextButton(
            onClick = { onMoveRight(staged) },
            enabled = !isLast && countFits
          ) {
            Text("Сдвинуть вправо")
          }

          TextButton(
            onClick = { onDuplicate(staged) },
            enabled = duplicateAllowed
          ) {
            Text("Дублировать")
          }

          TextButton(
            onClick = onRemove,
            enabled = canRemove
          ) {
            Text("Удалить")
          }
        }
      }
    }
  }

  if (pickingTime && wt != null) {
    TimePickerDialog(
      time = wt,
      onPick = { slot = slot.copy(wakeTime = it) },
      onDismiss = { pickingTime = false }
    )
  }
}

/** Диалог выбора времени (аналоговый пикер по тапу). Переиспользуется в недельном редакторе. */
@Composable
internal fun TimePickerDialog(time: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
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
