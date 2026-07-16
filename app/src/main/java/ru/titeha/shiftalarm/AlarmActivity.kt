package ru.titeha.shiftalarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.OutlinedButton
import ru.titeha.shiftalarm.alarm.AlarmScheduler
import ru.titeha.shiftalarm.alarm.AlarmService
import ru.titeha.shiftalarm.alarm.AlarmSignalState
import ru.titeha.shiftalarm.alarm.DismissMode
import ru.titeha.shiftalarm.alarm.FeatureFlags
import ru.titeha.shiftalarm.alarm.RingSessionController
import ru.titeha.shiftalarm.data.SettingsStore
import ru.titeha.shiftalarm.ui.MathChallengeView
import ru.titeha.shiftalarm.ui.ShakeChallengeView
import ru.titeha.shiftalarm.ui.StepsChallengeView

/**
 * Полноэкранный экран активного сигнала.
 *
 * Сам звук и вибрацию обслуживает [AlarmService]. Экран наблюдает
 * [AlarmSignalState] и закрывается, когда сервис остановлен из любого места:
 * кнопкой на экране, действием в уведомлении или смахиванием уведомления.
 */
class AlarmActivity : ComponentActivity() {
    /** Название может обновиться, если новый сигнал пришёл в существующую singleTask Activity. */
    private var label by mutableStateOf("")
    private var alarmId by mutableStateOf(AlarmScheduler.NO_ID)

    /** Остаток снузов на момент показа кнопки (пересчитывается при обновлении интента). */
    private var snoozeRemaining by mutableStateOf(0)
    private var snoozeIntervalMinutes by mutableStateOf(0)

    /** Способ выключения (жёсткий режим): задание усложняет «Стоп». «Отложить» работает всегда. */
    private var dismissMode by mutableStateOf(DismissMode.NORMAL)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Показать поверх блокировки и разбудить экран.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        updateFromIntent(intent)
        dismissMode =
            if (FeatureFlags.HARD_MODE) SettingsStore(this).dismissMode() else DismissMode.NORMAL

        setContent {
            val isRinging by
                AlarmSignalState.isRinging.collectAsStateWithLifecycle()
            var challengeActive by remember { mutableStateOf(false) }

            /*
             * Уведомление останавливает сервис напрямую. Когда сервис сообщает,
             * что сигнал больше не активен, отдельный экран «Стоп» больше не нужен.
             */
            LaunchedEffect(isRinging) {
                if (!isRinging) {
                    finish()
                }
            }

            MaterialTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        AlarmService.displayText(label),
                        style = MaterialTheme.typography.displaySmall,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(32.dp))

                    if (snoozeRemaining > 0) {
                        OutlinedButton(onClick = ::snoozeAlarm) {
                            Text("Отложить на $snoozeIntervalMinutes мин  ·  осталось $snoozeRemaining")
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    if (challengeActive) {
                        // Задание жёсткого режима вместо простого «Стоп»; «Отложить» выше работает всегда.
                        when (dismissMode) {
                            DismissMode.MATH -> MathChallengeView(
                                onSuccess = ::stopAlarm,
                                onCancel = { challengeActive = false }
                            )

                            DismissMode.STEPS -> StepsChallengeView(
                                onSuccess = ::stopAlarm,
                                onCancel = { challengeActive = false }
                            )

                            DismissMode.SHAKE -> ShakeChallengeView(
                                onSuccess = ::stopAlarm,
                                onCancel = { challengeActive = false }
                            )

                            DismissMode.NORMAL -> LaunchedEffect(Unit) { stopAlarm() }
                        }
                    } else {
                        Button(
                            onClick = {
                                if (dismissMode == DismissMode.NORMAL) stopAlarm() else challengeActive = true
                            }
                        ) {
                            Text("Стоп")
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateFromIntent(intent)
    }

    private fun updateFromIntent(intent: Intent) {
        label = intent
            .getStringExtra(AlarmService.EXTRA_LABEL)
            .orEmpty()
        alarmId = intent.getLongExtra(AlarmService.EXTRA_ALARM_ID, AlarmScheduler.NO_ID)
        if (alarmId != AlarmScheduler.NO_ID) {
            snoozeRemaining = RingSessionController.remainingSnoozes(this, alarmId)
            snoozeIntervalMinutes = RingSessionController.snoozeIntervalMinutes(this)
        } else {
            snoozeRemaining = 0
        }
    }

    private fun snoozeAlarm() {
        AlarmService.snooze(this, alarmId, label)
        finish()
    }

    private fun stopAlarm() {
        /*
         * finish() оставлен здесь для мгновенной реакции кнопки.
         * AlarmSignalState дополнительно закрывает Activity при остановке
         * из уведомления или при завершении сервиса.
         */
        AlarmService.stop(this)
        finish()
    }
}
