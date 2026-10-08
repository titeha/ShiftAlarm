package ru.titeha.shiftalarm.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Карточка-секция: визуально отделяет разделы настроек и редактора друг от друга
 * (владелец, 2026-10-09: «разделы слиплись, разделение только текстом»). Заголовок и
 * необязательное пояснение живут внутри карточки; содержимое — лямбда [content].
 *
 * Сворачивание («открывашки») сознательно НЕ делается: разделы короткие, лишний тап
 * ради скрытия двух-трёх строк вредит быстрее, чем помогает; отделение решается
 * поверхностью карточки. Если владелец захочет сворачивание — отдельная задача.
 */
@Composable
internal fun SectionCard(
  title: String,
  description: String? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium)
      if (description != null) {
        Spacer(Modifier.height(4.dp))
        Text(description, style = MaterialTheme.typography.bodySmall)
      }
      Spacer(Modifier.height(12.dp))
      content()
    }
  }
}
