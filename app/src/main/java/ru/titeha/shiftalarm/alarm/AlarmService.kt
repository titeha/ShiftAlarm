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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import ru.titeha.shiftalarm.AlarmActivity
import ru.titeha.shiftalarm.R
import ru.titeha.shiftalarm.data.AlarmEventLog
import ru.titeha.shiftalarm.data.AlarmEventType

/**
 * Foreground-сервис, который проигрывает сигнал будильника независимо от UI.
 *
 * Стартует из [AlarmReceiver] в момент срабатывания; останавливается из экрана «Стоп».
 *
 * Не «липкий» ([START_NOT_STICKY]): надёжность звонка держит AlarmManager+[AlarmReceiver],
 * поэтому если ОС убьёт сервис под нехваткой памяти — сам он не воскресает. Приходящий при
 * воскрешении пустой (`null`) интент трактуется как стоп — иначе сервис зазвенел бы без
 * привязки к будильнику, в том числе после уже нажатого «Стоп».
 */
class AlarmService : Service() {
  private var player: MediaPlayer? = null
  private var wakeLock: PowerManager.WakeLock? = null
  private var signalStarted = false
  private var alarmLabel: String = ""
  private var alarmId: Long = AlarmScheduler.NO_ID

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val action = intent?.action

    /*
     * intent == null приходит, когда ОС воскрешает убитый сервис.
     * Нам это не нужно: надёжность звонка держит AlarmManager+AlarmReceiver,
     * а не «липучесть» сервиса. «Стоп» дополнительно закрывает сессию звонка.
     */
    if (intent == null || action == ACTION_STOP) {
      val id = intent?.getLongExtra(EXTRA_ALARM_ID, alarmId) ?: alarmId
      if (id != AlarmScheduler.NO_ID) {
        RingSessionController.onStopped(this, id)
      }
      stopRingingAndSelf()
      return START_NOT_STICKY
    }

    /*
     * «Отложить»: ставим снуз-звонок (через сессию) и глушим текущий сигнал. Будильник вернётся
     * по снуз-звонку через интервал.
     */
    if (action == ACTION_SNOOZE) {
      val id = intent.getLongExtra(EXTRA_ALARM_ID, alarmId)
      val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
      if (id != AlarmScheduler.NO_ID) {
        RingSessionController.onSnoozePressed(this, id, label)
      }
      stopRingingAndSelf()
      return START_NOT_STICKY
    }

    alarmId = intent.getLongExtra(EXTRA_ALARM_ID, AlarmScheduler.NO_ID)
    alarmLabel = intent.getStringExtra(EXTRA_LABEL).orEmpty()

    /*
    * Состояние устанавливается до foreground-уведомления:
    * full-screen PendingIntent может открыть Activity прямо во время goForeground().
    */
    AlarmSignalState.markStarted()

    goForeground()
    startRinging()
    launchScreen()

    return START_NOT_STICKY
  }

  /**
   * Явно поднять экран «Стоп».
   *
   * Full-screen intent из уведомления система сама показывает только при заблокированном
   * экране. Когда приложение видимо или телефон разблокирован, пробуем запустить Activity
   * отсюда. В фоне на части прошивок запуск будет заблокирован — тогда остаётся heads-up
   * уведомление с кнопкой «Стоп».
   */
  private fun launchScreen() {
    try {
      startActivity(
        Intent(this, AlarmActivity::class.java)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          .putExtra(EXTRA_LABEL, alarmLabel)
          .putExtra(EXTRA_ALARM_ID, alarmId)
      )
    } catch (_: Exception) {
      // Фоновый запуск Activity заблокирован ОС — звонок глушится из уведомления.
    }
  }

  private fun goForeground() {
    ensureChannel(this)

    val openScreen = PendingIntent.getActivity(
      this,
      0,
      Intent(this, AlarmActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .putExtra(EXTRA_LABEL, alarmLabel)
        .putExtra(EXTRA_ALARM_ID, alarmId),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    val stopPending = PendingIntent.getService(
      this,
      1,
      Intent(this, AlarmService::class.java)
        .setAction(ACTION_STOP)
        .putExtra(EXTRA_ALARM_ID, alarmId),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    val builder = NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
      .setContentTitle("Будильник")
      .setContentText(displayText(alarmLabel))
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setCategory(NotificationCompat.CATEGORY_ALARM)
      .setFullScreenIntent(openScreen, true)
      .setContentIntent(openScreen)

    // «Отложить» — только пока лимит снуза не исчерпан (0 = снуз выключен). Порядок: снуз, затем стоп.
    if (alarmId != AlarmScheduler.NO_ID && RingSessionController.remainingSnoozes(this, alarmId) > 0) {
      val minutes = RingSessionController.snoozeIntervalMinutes(this)
      val snoozePending = PendingIntent.getService(
        this,
        2,
        Intent(this, AlarmService::class.java)
          .setAction(ACTION_SNOOZE)
          .putExtra(EXTRA_ALARM_ID, alarmId)
          .putExtra(EXTRA_LABEL, alarmLabel),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
      )
      builder.addAction(android.R.drawable.ic_lock_idle_alarm, "Отложить $minutes мин", snoozePending)
    }

    val notification = builder
      .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Стоп", stopPending)
      .setDeleteIntent(stopPending)
      .setOngoing(true)
      .build()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  /** Запустить все доступные каналы сигнала. */
  private fun startRinging() {
    if (signalStarted) {
      return
    }

    signalStarted = true

    /*
     * Держим CPU включённым, пока играет сигнал: foreground-сервиса и USAGE_ALARM недостаточно на
     * части прошивок — при погашенном экране звук/вибрация могут «уснуть». Wake lock со страховочным
     * таймаутом, чтобы никогда не зависнуть навсегда.
     */
    acquireWakeLock()

    /*
     * Вибрация стартует независимо от звука.
     * Если MediaPlayer не сможет открыть мелодию, вибрация останется запасным сигналом.
     */
    AlarmVibration.start(this)

    // Если звук не завёлся (нет мелодии/ошибка) — сигнал остаётся только вибрацией. Фиксируем это
    // в диагностическом журнале, чтобы «почему тихо» можно было понять постфактум. До разблокировки
    // журнал (CE) недоступен — тогда просто не пишем (звонок важнее записи).
    if (!startSound()) {
      val now = System.currentTimeMillis()
      try {
        AlarmEventLog(this).record(
          AlarmEventType.SIGNAL_DEGRADED,
          "звук недоступен — сигнал только вибрацией",
          now
        )
      } catch (_: Exception) {
        // Direct Boot: credential-encrypted журнал недоступен до разблокировки — пишем в DPS-буфер,
        // BootReceiver перельёт его в основной журнал после разблокировки.
        try {
          DirectBootEventBuffer(this).add(
            AlarmEventType.SIGNAL_DEGRADED.name,
            "звук недоступен — сигнал только вибрацией (locked)",
            now
          )
        } catch (_: Exception) {
          // Даже DPS-буфер недоступен — звонок важнее записи, молча продолжаем.
        }
      }
    }
  }

  /**
   * Запустить звуковой сигнал; true — играет, false — ни один источник не завёлся (запас — вибрация).
   *
   * Порядок fallback: (1) системная мелодия будильника → (2) любой рингтон → (3) встроенный в APK
   * raw-сигнал. Последний важен для Direct Boot: системная мелодия может лежать в credential-encrypted
   * хранилище и до разблокировки быть нечитаемой, а raw-ресурс из APK доступен всегда.
   */
  private fun startSound(): Boolean {
    val uri = RingtoneManager.getActualDefaultRingtoneUri(
      this,
      RingtoneManager.TYPE_ALARM
    )
      ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
      ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    if (uri != null && playUri(uri)) {
      return true
    }

    return playBundled()
  }

  private val alarmAudioAttributes: AudioAttributes
    get() = AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_ALARM)
      .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
      .build()

  /** Проиграть мелодию по URI. */
  private fun playUri(uri: android.net.Uri): Boolean {
    var createdPlayer: MediaPlayer? = null
    return try {
      createdPlayer = MediaPlayer().apply {
        setDataSource(this@AlarmService, uri)
        setAudioAttributes(alarmAudioAttributes)
        isLooping = true
        prepare()
        start()
      }
      player = createdPlayer
      true
    } catch (_: Exception) {
      releaseQuietly(createdPlayer)
      player = null
      false
    }
  }

  /** Проиграть встроенный в APK запасной сигнал (доступен даже до разблокировки). */
  private fun playBundled(): Boolean {
    var createdPlayer: MediaPlayer? = null
    return try {
      val afd = resources.openRawResourceFd(R.raw.fallback_alarm) ?: return false
      createdPlayer = MediaPlayer().apply {
        afd.use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
        setAudioAttributes(alarmAudioAttributes)
        isLooping = true
        prepare()
        start()
      }
      player = createdPlayer
      true
    } catch (_: Exception) {
      /*
       * Сигнал недоступен. Не падаем: сервис уже держит foreground-уведомление,
       * экран «Стоп» и вибрацию, если устройство её поддерживает.
       */
      releaseQuietly(createdPlayer)
      player = null
      false
    }
  }

  private fun releaseQuietly(mediaPlayer: MediaPlayer?) {
    try {
      mediaPlayer?.release()
    } catch (_: Exception) {
      // Плеер уже в некорректном состоянии — освобождение не критично.
    }
  }

  private fun stopRingingAndSelf() {
    stopRinging()

    /*
     * Экран наблюдает это состояние и закрывается независимо от того,
     * где пользователь остановил сигнал.
     */
    AlarmSignalState.markStopped()

    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  private fun stopRinging() {
    player?.let { currentPlayer ->
      try {
        if (currentPlayer.isPlaying) {
          currentPlayer.stop()
        }
      } catch (_: Exception) {
        // Плеер уже остановлен или в ошибочном состоянии.
      }

      try {
        currentPlayer.release()
      } catch (_: Exception) {
        // Освобождение ресурса уже не удалось, повторять нечего.
      }
    }

    player = null
    AlarmVibration.stop(this)
    releaseWakeLock()
    signalStarted = false
  }

  private fun acquireWakeLock() {
    if (wakeLock != null) {
      return
    }

    wakeLock = getSystemService(PowerManager::class.java)
      .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
      .apply {
        setReferenceCounted(false)
        acquire(WAKE_LOCK_TIMEOUT_MS)
      }
  }

  private fun releaseWakeLock() {
    wakeLock?.let { lock ->
      try {
        if (lock.isHeld) {
          lock.release()
        }
      } catch (_: Exception) {
        // Уже освобождён или в ошибочном состоянии — повторять нечего.
      }
    }
    wakeLock = null
  }

  override fun onDestroy() {
    stopRinging()
    AlarmSignalState.markStopped()
    super.onDestroy()
  }

  companion object {
    const val CHANNEL_ID = "alarm_channel"
    const val NOTIFICATION_ID = 1
    const val ACTION_STOP = "ru.titeha.shiftalarm.action.STOP"
    const val ACTION_SNOOZE = "ru.titeha.shiftalarm.action.SNOOZE"
    const val EXTRA_LABEL = "alarm_label"
    const val EXTRA_ALARM_ID = AlarmScheduler.EXTRA_ALARM_ID

    private const val WAKE_LOCK_TAG = "shiftalarm:ringing"

    /** Страховочный таймаут wake lock: звонок столько не длится, но лок не должен зависнуть. */
    private const val WAKE_LOCK_TIMEOUT_MS = 10L * 60L * 1000L

    /** Текст на экране/в уведомлении звонка: название будильника, иначе — дефолт. */
    const val DEFAULT_TEXT = "Подъём!"
    fun displayText(label: String): String = label.ifBlank { DEFAULT_TEXT }

    /** Запустить звонок будильника [alarmId] с названием [label]. Разрешено из ресивера даже из фона. */
    fun start(context: Context, alarmId: Long, label: String = "") {
      val intent = Intent(context, AlarmService::class.java)
        .putExtra(EXTRA_ALARM_ID, alarmId)
        .putExtra(EXTRA_LABEL, label)

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }

    fun stop(context: Context) {
      context.startService(
        Intent(context, AlarmService::class.java).setAction(ACTION_STOP)
      )
    }

    /** Отложить текущий звонок будильника [alarmId] (из экрана «Стоп»). */
    fun snooze(context: Context, alarmId: Long, label: String = "") {
      context.startService(
        Intent(context, AlarmService::class.java)
          .setAction(ACTION_SNOOZE)
          .putExtra(EXTRA_ALARM_ID, alarmId)
          .putExtra(EXTRA_LABEL, label)
      )
    }

    private fun ensureChannel(context: Context) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "Будильник",
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "Срабатывание будильника"
        setSound(null, null) // Звук играет сервис.
      }

      context
        .getSystemService(NotificationManager::class.java)
        .createNotificationChannel(channel)
    }
  }
}