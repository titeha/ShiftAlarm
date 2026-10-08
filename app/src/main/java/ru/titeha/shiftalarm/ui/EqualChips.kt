package ru.titeha.shiftalarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/*
 * «Пилюля» — единая форма чипов во всём приложении (владелец, 2026-10-09: кнопки и чипы
 * разного вида). Кнопки M3 по умолчанию пилюлеобразные, поэтому чипы приводим к той же
 * форме здесь, а не правим десятки кнопочных вызовов.
 */
internal val PillShape = RoundedCornerShape(percent = 50)

/**
 * Ряды чипов ОДИНАКОВОЙ ширины: кнопки логического раздела одного размера и не «подгоняются»
 * под содержание (требование владельца, 2026-10-08; UI01/UI02). [columns] — чипов в ряду;
 * неполный последний ряд дополняется невидимыми ячейками, чтобы ширины не плясали.
 */
@Composable
internal fun <T> EqualChips(
  items: List<T>,
  columns: Int,
  label: (T) -> String,
  selected: (T) -> Boolean,
  onSelect: (T) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    items.chunked(columns).forEach { rowItems ->
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        rowItems.forEach { item ->
          FilterChip(
            selected = selected(item),
            onClick = { onSelect(item) },
            shape = PillShape,
            label = {
              Text(
                label(item),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            },
            modifier = Modifier.weight(1f)
          )
        }
        repeat(columns - rowItems.size) {
          Spacer(Modifier.weight(1f))
        }
      }
    }
  }
}
