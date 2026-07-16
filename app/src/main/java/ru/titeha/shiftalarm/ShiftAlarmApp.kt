package ru.titeha.shiftalarm

import android.app.Application
import android.os.UserManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.titeha.shiftalarm.alarm.AlarmScheduler
import ru.titeha.shiftalarm.data.AlarmRepository
import ru.titeha.shiftalarm.data.HolidayCalendarRepository
import ru.titeha.shiftalarm.data.SettingsStore
import ru.titeha.shiftalarm.schedule.ProductionCalendars
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

    /*
     * Direct Boot: если процесс поднят до разблокировки (например, ради directBootAware BootReceiver
     * после ночного ребута), credential-encrypted хранилище — Room и обычные SharedPreferences —
     * НЕДОСТУПНО. Любое обращение к нему здесь роняет весь процесс на onCreate, и тогда не отработает
     * даже BootReceiver. Поэтому до разблокировки пропускаем всю CE-инициализацию: перевыставление
     * из device-protected кэша сделает сам BootReceiver, а полная инициализация — при старте после
     * разблокировки.
     */
    val userManager = getSystemService(UserManager::class.java)
    if (userManager != null && !userManager.isUserUnlocked) {
      return
    }

    // Рабочая неделя (глобально) — до перепланирования, чтобы движок сразу считал по ней выходные.
    ProductionCalendars.workWeek = SettingsStore(this).workWeek()

    val holidays = HolidayCalendarRepository(this)
    holidays.install() // движок теперь читает календарь из кэша (иначе — встроенные данные)

    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
      val alarms = AlarmRepository(this@ShiftAlarmApp)

      // Перепланировать при каждом старте: AlarmManager не хранит расписание между процессами и
      // устройствами, поэтому это покрывает восстановление из backup, обновление приложения и
      // первый запуск — будильники из БД снова попадают в систему.
      AlarmScheduler.rescheduleAll(this@ShiftAlarmApp, alarms, alarms.enabled())

      // Фоновое обновление календаря: текущий И следующий год (поиск звонка уходит за границу года),
      // но не чаще раза в сутки (throttle). Если данные обновились — перепланировать с их учётом.
      // Ошибка/оффлайн — тихо, планировщик уже работает на кэше/встроенном.
      val year = LocalDate.now().year
      val updated = holidays.refreshIfStale("RU", year) or holidays.refreshIfStale("RU", year + 1)
      if (updated) {
        AlarmScheduler.rescheduleAll(this@ShiftAlarmApp, alarms, alarms.enabled())
      }
    }
  }
}
