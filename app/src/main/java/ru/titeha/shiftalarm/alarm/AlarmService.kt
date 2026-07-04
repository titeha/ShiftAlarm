package ru.titeha.shiftalarm.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import ru.titeha.shiftalarm.AlarmActivity

/**
 * Foreground-сервис, который проигрывает сигнал будильника независимо от UI.
 * Стартует из [AlarmReceiver] в момент срабатывания; останавливается из экрана «Стоп».
 *
 * Не «липкий» ([START_NOT_STICKY]): надёжность звонка держит AlarmManager+[AlarmReceiver],
 * поэтому если ОС убьёт сервис под нехваткой памяти — сам он не воскресает. Приходящий при
 * воскрешении пустой (`null`) интент трактуется как стоп — иначе сервис зазвенел бы без
 * привязки к будильнику (в т.ч. после уже нажатого «Стоп»).
 */
class AlarmService : Service() {

  private var player: MediaPlayer? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // intent == null приходит, когда ОС воскрешает убитый сервис (при START_STICKY).
    // Нам это не нужно: надёжность звонка держит AlarmManager+AlarmReceiver, а не «липучесть»
    // сервиса. Пустой интент трактуем как стоп, чтобы не зазвенеть без привязки к будильнику
    // (в т.ч. после уже нажатого «Стоп»).
    if (intent == null || intent.action == ACTION_STOP) {
      stopRingingAndSelf()
      return START_NOT_STICKY
    }
    goForeground()
    startRinging()
    launchScreen()
    // START_NOT_STICKY: если ОС убьёт сервис — не воскрешать самому (см. проверку intent == null).
    return START_NOT_STICKY
  }

  /**
   * Явно поднять экран «Стоп». Full-screen intent из уведомления система сама показывает
   * только при заблокированном экране; когда приложение видимо/телефон разблокирован —
   * запускаем Activity отсюда (видимому приложению это разрешено). В фоне на части прошивок
   * запуск заблокируют — тогда остаётся heads-up с кнопкой «Стоп».
   */
  private fun launchScreen() {
    try {
      startActivity(
        Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
    } catch (_: Exception) {
      // Фоновый запуск Activity заблокирован ОС — звонок глушится из уведомления.
    }
  }

  private fun goForeground() {
    ensureChannel(this)
    val openScreen = PendingIntent.getActivity(
      this, 0,
      Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    // Запасной выход: если полноэкранный экран не всплыл (телефон разблокирован →
    // heads-up вместо Activity, либо OEM-ограничения), звонок всё равно можно заглушить
    // кнопкой в самом уведомлении и смахиванием.
    val stopPending = PendingIntent.getService(
      this, 1,
      Intent(this, AlarmService::class.java).setAction(ACTION_STOP),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val notification = NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
      .setContentTitle("Будильник")
      .setContentText("Подъём!")
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setCategory(NotificationCompat.CATEGORY_ALARM)
      .setFullScreenIntent(openScreen, true)
      .setContentIntent(openScreen)
      .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Стоп", stopPending)
      .setDeleteIntent(stopPending)
      .setOngoing(true)
      .build()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(
        NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun startRinging() {
    if (player != null) return
    val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
      ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
      ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
    try {
      player = MediaPlayer().apply {
        setDataSource(this@AlarmService, uri)
        setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        )
        isLooping = true
        prepare()
        start()
      }
    } catch (_: Exception) {
      // Сигнал недоступен — сервис всё равно держит уведомление/экран.
    }
  }

  private fun stopRingingAndSelf() {
    player?.run { if (isPlaying) stop(); release() }
    player = null
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  override fun onDestroy() {
    super.onDestroy()
    player?.run { if (isPlaying) stop(); release() }
    player = null
  }

  companion object {
    const val CHANNEL_ID = "alarm_channel"
    const val NOTIFICATION_ID = 1
    const val ACTION_STOP = "ru.titeha.shiftalarm.action.STOP"

    /** Запустить звонок. Разрешено из ресивера будильника даже из фона. */
    fun start(context: Context) {
      val intent = Intent(context, AlarmService::class.java)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }

    fun stop(context: Context) {
      context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_STOP))
    }

    private fun ensureChannel(context: Context) {
      val channel = NotificationChannel(
        CHANNEL_ID, "Будильник", NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "Срабатывание будильника"
        setSound(null, null) // звук играет сервис
      }
      context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
  }
}
