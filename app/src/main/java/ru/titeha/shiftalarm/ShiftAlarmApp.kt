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
     * разблокировки. Процессу, поднятому до разблокировки и дожившему до неё, календарь подключит
     * BootReceiver на USER_UNLOCKED (см. BootReceiver — иначе расчёт шёл бы мимо кэша, F9).
     */
    val userManager = getSystemService(UserManager::class.java)
    if (userManager != null && !userManager.isUserUnlocked) {
      return
    }

    val holidays = HolidayCalendarRepository(this)
    holidays.install() // движок теперь читает календарь из кэша (иначе — встроенные данные)

    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
      val alarms = AlarmRepository(this@ShiftAlarmApp)
      val year = LocalDate.now().year

      // TD10 (AO-8): физически убираем из кэша плейсхолдер источника (нули за ещё не
      // опубликованный год), если он успел туда попасть до защиты в парсере. Делается ДО
      // перепланирования и до refreshIfStale: движок, настройки и hasData() не должны видеть
      // фантомные «данные на год», а автообновление обязано повторять попытку каждый старт.
      holidays.purgeInvalid("RU", year)
      holidays.purgeInvalid("RU", year + 1)

      // Перепланировать при каждом старте: AlarmManager не хранит расписание между процессами и
      // устройствами, поэтому это покрывает восстановление из backup, обновление приложения и
      // первый запуск — будильники из БД снова попадают в систему.
      // AUD-10 (R-02): reconcile отметок разовых и актуальное чтение enabled — внутри rescheduleAll
      // (единая граница под общим Mutex); список-аргумент игнорируется — см. KDoc rescheduleAll.
      AlarmScheduler.rescheduleAll(this@ShiftAlarmApp, alarms, emptyList())

      // Фоновое обновление календаря: текущий И следующий год (поиск звонка уходит за границу года),
      // но не чаще раза в сутки (throttle). Если данные обновились — перепланировать с их учётом.
      // Ошибка/оффлайн — тихо, планировщик уже работает на кэше/встроенном.
      val updated = holidays.refreshIfStale("RU", year) or holidays.refreshIfStale("RU", year + 1)
      if (updated) {
        AlarmScheduler.rescheduleAll(this@ShiftAlarmApp, alarms, emptyList())
      }
    }
  }
}
