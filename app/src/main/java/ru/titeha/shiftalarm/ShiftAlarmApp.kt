package ru.titeha.shiftalarm

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.titeha.shiftalarm.alarm.AlarmScheduler
import ru.titeha.shiftalarm.data.AlarmRepository
import ru.titeha.shiftalarm.data.HolidayCalendarRepository
import java.time.LocalDate

/**
 * Точка инициализации приложения.
 *
 * Подключает локальный кэш производственного календаря к движку и фоново обновляет его с офиц.
 * источника (isDayOff.ru). Звонок от сети не зависит: движок читает кэш/встроенные данные, а
 * обновление лишь освежает кэш. Если данные обновились — перепланируем будильники, чтобы они учли
 * актуальные праздники/переносы.
 */
class ShiftAlarmApp : Application() {

  override fun onCreate() {
    super.onCreate()

    val holidays = HolidayCalendarRepository(this)
    holidays.install() // движок теперь читает календарь из кэша (иначе — встроенные данные)

    // Фоновое обновление кэша. Ошибка/оффлайн — тихо, планировщик работает на кэше/встроенном.
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
      val year = LocalDate.now().year
      if (holidays.refresh("RU", year)) {
        val alarms = AlarmRepository(this@ShiftAlarmApp)
        AlarmScheduler.rescheduleAll(this@ShiftAlarmApp, alarms, alarms.enabled())
      }
    }
  }
}
