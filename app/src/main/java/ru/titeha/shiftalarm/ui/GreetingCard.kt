package ru.titeha.shiftalarm.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.titeha.shiftalarm.data.SettingsStore
import ru.titeha.shiftalarm.greetings.DayGreetingGate
import ru.titeha.shiftalarm.greetings.DayGreetingResolver
import ru.titeha.shiftalarm.greetings.Greeting
import ru.titeha.shiftalarm.greetings.GreetingShareText
import ru.titeha.shiftalarm.greetings.GreetingsDataset
import ru.titeha.shiftalarm.greetings.GreetingsLoader
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate

/** Состояние карточки «Настроение дня»: что показать (или null) и как её закрыть на сегодня. */
class DayGreetingUiState(val greeting: Greeting?, val onClose: () -> Unit)

/**
 * Готовит карточку «Настроение дня» для главной. Грузит датасет из assets (в IO), перечитывает
 * сигнал на каждом ON_RESUME (чтобы карточка появилась сразу после выключения будильника) и решает
 * через чистый [DayGreetingGate]. Ошибку загрузки глушит — фича необязательная.
 */
@Composable
fun rememberDayGreeting(): DayGreetingUiState {
  val context = LocalContext.current
  val settings = remember { SettingsStore(context) }

  // Пересчёт на возврате в приложение (после выключения будильника) и после закрытия карточки.
  var tick by remember { mutableIntStateOf(0) }
  val owner = LocalLifecycleOwner.current
  DisposableEffect(owner) {
    val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
    owner.lifecycle.addObserver(obs)
    onDispose { owner.lifecycle.removeObserver(obs) }
  }

  val dataset by produceState<GreetingsDataset?>(null) {
    value = withContext(Dispatchers.IO) {
      runCatching { GreetingsLoader.fromAssets(context) }.getOrNull()
    }
  }

  val greeting = remember(tick, dataset) {
    val ds = dataset ?: return@remember null
    val today = LocalDate.now()
    val g = DayGreetingResolver(ds).forDate(today)
    if (
      DayGreetingGate.shouldShowCard(
        cardEnabled = settings.dayGreetingCardEnabled(),
        today = today,
        lastDismissedEpochDay = settings.lastDismissedEpochDay(),
        cardHandledEpochDay = settings.greetingCardHandledEpochDay(),
        greeting = g,
      )
    ) g else null
  }

  return DayGreetingUiState(
    greeting = greeting,
    onClose = {
      settings.setGreetingCardHandled(LocalDate.now().toEpochDay())
      tick++
    },
  )
}

/**
 * Карточка «Настроение дня»: главный праздник + фраза дня. Тап — подробнее (bottom sheet),
 * крестик — скрыть на сегодня. Чисто информационная, к надёжности звонка отношения не имеет.
 */
@Composable
fun GreetingCard(
  greeting: Greeting,
  onClick: () -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
      Column(modifier = Modifier.weight(1f)) {
        greeting.holidays.firstOrNull()?.let { top ->
          Text(
            "Сегодня — ${top.name}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          if (greeting.holidays.size > 1) {
            Text(
              "и ещё ${greeting.holidays.size - 1} — нажми, чтобы посмотреть",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
        greeting.phrase?.let { phrase ->
          if (greeting.holidays.isNotEmpty()) Spacer(Modifier.height(8.dp))
          Text(
            "«${phrase.text}»",
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
          )
          phrase.author?.let {
            Text("— $it", style = MaterialTheme.typography.bodySmall)
          }
        }
      }
      IconButton(onClick = onClose) {
        Icon(Icons.Filled.Close, contentDescription = "Скрыть на сегодня")
      }
    }
  }
}

/**
 * Подробности «Настроения дня» в bottom sheet: все праздники дня с описаниями, фраза и «Поделиться».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GreetingSheet(greeting: Greeting, onDismiss: () -> Unit) {
  val context = LocalContext.current
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .padding(bottom = 32.dp)
    ) {
      Text(
        "Настроение дня",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
      Spacer(Modifier.height(16.dp))

      greeting.holidays.forEach { holiday ->
        Text(
          holiday.name,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        if (holiday.description.isNotBlank()) {
          Text(holiday.description, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))
      }

      greeting.phrase?.let { phrase ->
        Text(
          "«${phrase.text}»",
          style = MaterialTheme.typography.bodyLarge,
          fontStyle = FontStyle.Italic,
        )
        phrase.author?.let {
          Text("— $it", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
      }

      Button(onClick = { shareGreeting(context, greeting) }) {
        Icon(Icons.Filled.Share, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Поделиться")
      }
    }
  }
}

/** Отправляет текст «Настроения дня» в системный share sheet (мессенджеры и т.п.). */
private fun shareGreeting(context: Context, greeting: Greeting) {
  val send = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_TEXT, GreetingShareText.build(greeting))
  }
  context.startActivity(Intent.createChooser(send, "Поделиться"))
}
