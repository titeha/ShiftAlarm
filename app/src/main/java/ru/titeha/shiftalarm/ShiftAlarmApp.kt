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

    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
      val alarms = AlarmRepository(this@ShiftAlarmApp)

      // Перепланировать при каждом старте: AlarmManager не хранит расписание между процессами и
      // устройствами, поэтому это покрывает восстановление из backup, обновление приложения и
      // первый запуск — будильники из БД снова попадают в систему.
      AlarmScheduler.rescheduleAll(this@ShiftAlarmApp, alarms, alarms.enabled())

      // Фоновое обновление календаря праздников; если данные пришли — перепланировать с их учётом.
      // Ошибка/оффлайн — тихо, планировщик уже работает на кэше/встроенном.
      if (holidays.refresh("RU", LocalDate.now().year)) {
        AlarmScheduler.rescheduleAll(this@ShiftAlarmApp, alarms, alarms.enabled())
      }
    }
  }
}
