package ru.titeha.shiftalarm

import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ru.titeha.shiftalarm.alarm.AlarmScheduler
import ru.titeha.shiftalarm.data.AlarmRepository
import ru.titeha.shiftalarm.schedule.SetAlarmSpec

/**
 * Обработчик системного действия `ACTION_SET_ALARM` (Ассистент/сторонние приложения ставят
 * будильник «через нас»). Читает extras, создаёт будильник и планирует его.
 *
 * AUD-5: после успешного создания ВСЕГДА открывается список (C) — `EXTRA_SKIP_UI` больше не даёт
 * молчаливого создания будильника сторонним вызывающим. Видимость результата НЕ является согласием
 * пользователя ДО записи и не защищает от приложения, имеющего `SET_ALARM`; от скрытой установки
 * защищает объявленное в манифесте `android:permission="com.android.alarm.permission.SET_ALARM"` (A).
 *
 * Разбор extras → [AlarmEntity] делает чистый [SetAlarmSpec]; здесь только Android-обвязка.
 */
class SetAlarmActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    if (intent?.action != AlarmClock.ACTION_SET_ALARM) {
      openList(); finish(); return
    }

    val hour = if (intent.hasExtra(AlarmClock.EXTRA_HOUR)) {
      intent.getIntExtra(AlarmClock.EXTRA_HOUR, 0)
    } else null
    val minute = intent.getIntExtra(AlarmClock.EXTRA_MINUTES, 0)
    val message = intent.getStringExtra(AlarmClock.EXTRA_MESSAGE).orEmpty()
    val days = intent.getIntegerArrayListExtra(AlarmClock.EXTRA_DAYS).orEmpty()

    val alarm = SetAlarmSpec.toAlarm(hour, minute, message, days)
    if (alarm == null) {
      // Часа в интенте нет — открыть список, пусть пользователь заведёт вручную.
      openList(); finish(); return
    }

    lifecycleScope.launch {
      val repo = AlarmRepository(applicationContext)
      val id = repo.upsert(alarm)
      AlarmScheduler.reschedule(this@SetAlarmActivity, repo, alarm.copy(id = id))
      Toast.makeText(
        this@SetAlarmActivity,
        "Будильник поставлен: %02d:%02d".format(alarm.hour, alarm.minute),
        Toast.LENGTH_SHORT
      ).show()
      // AUD-5 (C): список открывается ВСЕГДА после создания, независимо от EXTRA_SKIP_UI.
      openList()
      finish()
    }
  }

  private fun openList() {
    startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  }
}
