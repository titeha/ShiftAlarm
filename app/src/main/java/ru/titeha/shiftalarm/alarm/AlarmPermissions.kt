package ru.titeha.shiftalarm.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Проверка разрешений/ограничений, влияющих на срабатывание будильника, и интенты для их настройки.
 * Чистая логика «какие проблемы» — в [AlarmReadiness]; здесь только чтение реальных статусов Android.
 */
object AlarmPermissions {

  /** Точные будильники (Android 12+ можно отозвать). На младших версиях — всегда разрешены. */
  fun canScheduleExact(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
  }

  /** Уведомления (Android 13+ runtime-разрешение). На младших версиях — всегда разрешены. */
  fun notificationsAllowed(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
      context, android.Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
  }

  /** Полноэкранные уведомления (Android 14+ можно отозвать). На младших версиях — всегда можно. */
  fun canUseFullScreen(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
    return context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
  }

  /** Приложение вне ограничений энергосбережения (в белом списке Doze). */
  fun batteryUnrestricted(context: Context): Boolean {
    val pm = context.getSystemService(PowerManager::class.java)
    return pm.isIgnoringBatteryOptimizations(context.packageName)
  }

  /** Громкость канала будильника на нуле — сигнал прозвучит только вибрацией. */
  fun alarmVolumeZero(context: Context): Boolean {
    val audio = context.getSystemService(AudioManager::class.java)
    return audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0
  }

  /** Актуальные проблемы готовности (по приоритету). Пустой список — всё в порядке. */
  fun issues(context: Context): List<AlarmReadinessIssue> = AlarmReadiness.issues(
    canScheduleExact = canScheduleExact(context),
    notificationsAllowed = notificationsAllowed(context),
    fullScreenAllowed = canUseFullScreen(context),
    batteryUnrestricted = batteryUnrestricted(context),
    alarmVolumeZero = alarmVolumeZero(context),
  )

  /** Интент в настройки для устранения [issue]. */
  fun settingsIntent(context: Context, issue: AlarmReadinessIssue): Intent {
    val intent = when (issue) {
      AlarmReadinessIssue.EXACT_ALARM ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
          Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
        else
          appDetailsIntent(context)

      AlarmReadinessIssue.NOTIFICATIONS ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
          Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        else
          appDetailsIntent(context)

      AlarmReadinessIssue.FULL_SCREEN ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
          Intent(
            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            Uri.parse("package:${context.packageName}")
          )
        else
          appDetailsIntent(context)

      // Список приложений энергосбережения (без прямого запроса — безопасно для Google Play).
      AlarmReadinessIssue.BATTERY ->
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

      // Настройки звука: громкость будильника пользователь поднимает сам (не трогаем её кодом).
      AlarmReadinessIssue.ALARM_VOLUME ->
        Intent(Settings.ACTION_SOUND_SETTINGS)
    }
    return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  }

  private fun appDetailsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
}
