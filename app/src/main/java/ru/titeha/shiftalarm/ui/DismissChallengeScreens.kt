package ru.titeha.shiftalarm.ui

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.titeha.shiftalarm.alarm.DismissMode
import ru.titeha.shiftalarm.alarm.MathChallenge
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Задания жёсткого режима, усложняющие «Стоп». Звук продолжает играть (его держит AlarmService).
 * [onSuccess] — задание выполнено (пора глушить), [onCancel] — вернуться к экрану звонка (звонок звенит).
 */

@Composable
fun MathChallengeView(onSuccess: () -> Unit, onCancel: () -> Unit) {
    // Разовая генерация на показ: nanoTime лишь как источник разнообразия, логика — в чистом MathChallenge.
    val problems = remember { MathChallenge.generate(Random(SystemClock.elapsedRealtimeNanos())) }
    var index by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Реши, чтобы выключить (${index + 1}/${problems.size})")
        Spacer(Modifier.height(8.dp))
        Text(
            "${problems[index].text} = ?",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { new -> input = new.filter { it.isDigit() || it == '-' }; error = false },
            isError = error,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            label = { Text(if (error) "Неверно, ещё раз" else "Ответ") }
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onCancel) { Text("Назад") }
            Button(onClick = {
                val answer = input.toIntOrNull()
                if (answer != null && answer == problems[index].answer) {
                    if (index == problems.lastIndex) {
                        onSuccess()
                    } else {
                        index++
                        input = ""
                    }
                } else {
                    error = true
                }
            }) { Text("Проверить") }
        }
    }
}

@Composable
fun StepsChallengeView(onSuccess: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var count by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // TYPE_STEP_DETECTOR — одно событие на шаг.
                count += 1
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) {
            // maxReportLatencyUs=0 — без батчинга: иначе датчик копит шаги и отдаёт пачкой позже,
            // и счётчик на экране не растёт по ходу ходьбы.
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI, 0)
        }
        onDispose { manager?.unregisterListener(listener) }
    }

    LaunchedEffect(count) {
        if (count >= DismissMode.STEP_TARGET) onSuccess()
    }

    ChallengeProgress(
        title = "Пройди ${DismissMode.STEP_TARGET} шагов",
        progress = "$count / ${DismissMode.STEP_TARGET}",
        onCancel = onCancel
    )
}

@Composable
fun ShakeChallengeView(onSuccess: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var count by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var lastShakeAt = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val gForce = sqrt(
                    event.values[0] * event.values[0] +
                        event.values[1] * event.values[1] +
                        event.values[2] * event.values[2]
                ) / SensorManager.GRAVITY_EARTH
                val now = SystemClock.elapsedRealtime()
                if (gForce > SHAKE_THRESHOLD_G && now - lastShakeAt > SHAKE_DEBOUNCE_MS) {
                    lastShakeAt = now
                    count += 1
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { manager?.unregisterListener(listener) }
    }

    LaunchedEffect(count) {
        if (count >= DismissMode.SHAKE_TARGET) onSuccess()
    }

    ChallengeProgress(
        title = "Встряхни ${DismissMode.SHAKE_TARGET} раз",
        progress = "$count / ${DismissMode.SHAKE_TARGET}",
        onCancel = onCancel
    )
}

@Composable
private fun ChallengeProgress(title: String, progress: String, onCancel: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title)
        Spacer(Modifier.height(8.dp))
        Text(progress, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onCancel) { Text("Назад") }
    }
}

private const val SHAKE_THRESHOLD_G = 2.7f
private const val SHAKE_DEBOUNCE_MS = 300L
