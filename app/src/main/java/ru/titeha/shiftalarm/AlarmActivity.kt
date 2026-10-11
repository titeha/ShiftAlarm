package ru.titeha.shiftalarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import ru.titeha.shiftalarm.alarm.AlarmFireValidator
import ru.titeha.shiftalarm.alarm.AlarmScheduler
import ru.titeha.shiftalarm.alarm.AlarmService
import ru.titeha.shiftalarm.alarm.AlarmSignalState
import ru.titeha.shiftalarm.alarm.DismissMode
import ru.titeha.shiftalarm.alarm.FeatureFlags
import ru.titeha.shiftalarm.alarm.RingGroupMember
import ru.titeha.shiftalarm.alarm.RingSessionController
import ru.titeha.shiftalarm.alarm.ringGroupCanJoin
import ru.titeha.shiftalarm.alarm.ringGroupDisplayNames
import ru.titeha.shiftalarm.alarm.ringGroupWith
import ru.titeha.shiftalarm.data.SettingsStore
import ru.titeha.shiftalarm.ui.MathChallengeView
import ru.titeha.shiftalarm.ui.ShakeChallengeView
import ru.titeha.shiftalarm.ui.StepsChallengeView
import ru.titeha.shiftalarm.ui.theme.AppTheme
import ru.titeha.shiftalarm.ui.theme.ThemeMode

/**
 * Полноэкранный экран активного сигнала.
 *
 * Сам звук и вибрацию обслуживает [AlarmService]. Экран наблюдает
 * [AlarmSignalState] и закрывается, когда сервис остановлен из любого места:
 * кнопкой на экране, действием в уведомлении или смахиванием уведомления.
 */
class AlarmActivity : ComponentActivity() {
    /**
     * AUD-17 (BKL1): группа совпадающих звонков. `onNewIntent` ДОПОЛНЯЕТ группу, а не затирает.
     */
    private var groupMembers by mutableStateOf<List<RingGroupMember>>(emptyList())

    /** Остаток снузов — минимум по участникам (консервативная подпись «осталось N»). */
    private var snoozeRemaining by mutableStateOf(0)

    /** Снуз доступен хотя бы одному участнику (кнопка одна на группу). */
    private var snoozeAvailable by mutableStateOf(false)
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

            // Тема экрана звонка следует настройкам пользователя, как MainActivity, — но только когда
            // устройство разблокировано. Экран может срабатывать залоченным (Direct Boot), где
            // CE-настройки недоступны и любое обращение к ним роняет процесс; тогда берём безопасный
            // системный фолбэк (тема по системе + системные динамические цвета, без чтения CE).
            // Раньше здесь был голый MaterialTheme{} — дефолтная светлая тема, выбивавшаяся из приложения.
            val unlocked = remember {
                this@AlarmActivity.getSystemService(UserManager::class.java)?.isUserUnlocked ?: true
            }
            val settings = remember { if (unlocked) SettingsStore(applicationContext) else null }
            val darkTheme = when (settings?.themeMode() ?: ThemeMode.SYSTEM) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            AppTheme(
                darkTheme = darkTheme,
                dynamicColor = settings?.dynamicColor() ?: true,
                fontScale = settings?.fontScale() ?: 1f,
            ) {
              // Surface (а не .background) задаёт и фон, и цвет контента onBackground — иначе голый
              // Text берёт дефолтный чёрный и на тёмной теме сливается с фоном.
              Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
              ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // AUD-17: уникальные названия всех участников группы (пустые схлопнуты в один
                    // дефолтный текст, повторы убраны — RingGroup).
                    Text(
                        ringGroupDisplayNames(groupMembers, AlarmService.DEFAULT_TEXT)
                            .joinToString(", "),
                        style = MaterialTheme.typography.displaySmall,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(32.dp))

                    if (snoozeAvailable) {
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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateFromIntent(intent)
    }

    private fun updateFromIntent(intent: Intent) {
        val id = intent.getLongExtra(AlarmService.EXTRA_ALARM_ID, AlarmScheduler.NO_ID)
        if (id == AlarmScheduler.NO_ID) return
        val label = intent.getStringExtra(AlarmService.EXTRA_LABEL).orEmpty()
        val at = intent
            .getLongExtra(AlarmScheduler.EXTRA_TRIGGER_AT_MILLIS, AlarmScheduler.NO_TRIGGER_AT_MILLIS)
            .takeIf { it != AlarmScheduler.NO_TRIGGER_AT_MILLIS }
            ?: System.currentTimeMillis()
        val member = RingGroupMember(alarmId = id, label = label, scheduledTriggerAtMillis = at)

        // Присоединяем к текущей группе в пределах окна; иначе начинаем новую (как в сервисе).
        val window = AlarmFireValidator.DefaultTolerance.toMillis()
        groupMembers = if (groupMembers.isEmpty() || ringGroupCanJoin(groupMembers, member, window)) {
            ringGroupWith(groupMembers, member)
        } else {
            listOf(member)
        }

        // Кнопка «Отложить»: доступна, если снуз есть хотя бы у одного; «осталось N» — минимум
        // по участникам (консервативная оценка, решение владельца).
        val remainings = groupMembers.map { RingSessionController.remainingSnoozes(this, it.alarmId) }
        snoozeRemaining = remainings.minOrNull() ?: 0
        snoozeAvailable = remainings.any { it > 0 }
        snoozeIntervalMinutes = RingSessionController.snoozeIntervalMinutes(this)
    }

    private fun snoozeAlarm() {
        val primary = groupMembers.firstOrNull() ?: return
        AlarmService.snooze(this, primary.alarmId, primary.label)
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
