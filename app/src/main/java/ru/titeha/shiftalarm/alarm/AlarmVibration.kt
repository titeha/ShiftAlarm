package ru.titeha.shiftalarm.alarm

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Управляет вибрацией во время звонка будильника.
 *
 * Вибрация используется как дополнительный канал сигнала. Если системная мелодия
 * недоступна или не смогла запуститься, пользователь всё равно получает физический
 * сигнал, если устройство поддерживает вибрацию.
 */
object AlarmVibration {
  /** Запустить повторяющуюся вибрацию будильника. */
  fun start(context: Context) {
    val vibrator = defaultVibrator(context) ?: return

    if (!vibrator.hasVibrator()) {
      return
    }

    val effect = VibrationEffect.createWaveform(
      AlarmVibrationPattern.timingsMillis(),
      AlarmVibrationPattern.repeatIndex()
    )

    val attributes = AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_ALARM)
      .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
      .build()

    try {
      vibrator.vibrate(effect, attributes)
    } catch (_: Exception) {
      // Вибрация недоступна или запрещена прошивкой — продолжаем звонок звуком/экраном.
    }
  }

  /** Остановить вибрацию будильника. */
  fun stop(context: Context) {
    try {
      defaultVibrator(context)?.cancel()
    } catch (_: Exception) {
      // Нечего останавливать или вибратор недоступен.
    }
  }

  @Suppress("DEPRECATION")
  private fun defaultVibrator(context: Context): Vibrator? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
      context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
  }
}