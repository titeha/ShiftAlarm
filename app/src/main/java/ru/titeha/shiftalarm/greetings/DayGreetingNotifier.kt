package ru.titeha.shiftalarm.greetings

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import ru.titeha.shiftalarm.MainActivity
import ru.titeha.shiftalarm.data.SettingsStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Уведомление «Настроение дня» на ПЕРВЫЙ «Стоп» за день (если включено в настройках).
 *
 * Строго отдельный канал [CHANNEL_ID] (IMPORTANCE_DEFAULT), НИКОГДА — канал будильника: пользователь
 * может замьютить «Настроение дня», и на звонок это не повлияет. Вызывается из пути звонка, поэтому
 * максимально защищён — любая ошибка глушится и не мешает выключению будильника.
 */
object DayGreetingNotifier {
  const val CHANNEL_ID = "day_greeting"
  private const val NOTIFICATION_ID = 3000

  /** Чистое решение: уведомление включено, за сегодня ещё не показывали, есть что показать. */
  fun shouldPost(
    enabled: Boolean,
    today: LocalDate,
    postedEpochDay: Long,
    greeting: Greeting,
  ): Boolean = enabled && postedEpochDay != today.toEpochDay() && !greeting.isEmpty

  fun maybePost(context: Context, nowMillis: Long) {
    try {
      val settings = SettingsStore(context)
      val enabled = settings.dayGreetingNotificationEnabled()
      if (!enabled) return // не грузим датасет зря

      val today = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).toLocalDate()
      val greeting = DayGreetingResolver(GreetingsLoader.fromAssets(context)).forDate(today)
      if (!shouldPost(enabled, today, settings.greetingNotifPostedEpochDay(), greeting)) return

      val manager = context.getSystemService(NotificationManager::class.java) ?: return
      manager.createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "Настроение дня", NotificationManager.IMPORTANCE_DEFAULT)
          .apply { description = "Праздник дня и фраза дня после выключения будильника" }
      )

      val top = greeting.holidays.firstOrNull()
      val title = if (top != null) "Сегодня — ${top.name}" else "Фраза дня"
      val text = greeting.phrase?.text ?: top?.description.orEmpty()

      val open = PendingIntent.getActivity(
        context,
        NOTIFICATION_ID,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )

      val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setCategory(NotificationCompat.CATEGORY_SOCIAL)
        .setAutoCancel(true)
        .setContentIntent(open)
        .build()

      manager.notify(NOTIFICATION_ID, notification)
      settings.setGreetingNotifPosted(today.toEpochDay())
    } catch (_: Exception) {
      // Информационная фича — любая ошибка не должна влиять на путь звонка.
    }
  }
}
