# Отчёт аудита надёжности звонка — ShiftAlarm

Аудит по чек-листу `RELIABILITY_AUDIT.md` (Fable). Дата: 16 июля 2026.
Метод: проверка каждого пункта по коду (file:line). Фиксы **не вносились** — это отчёт.

Статусы: ✅ подтверждено кодом · ⚠️ частично / иначе, но эквивалентно · ❌ отсутствует.

## Подтверждение заявок README (§1)

| Заявка | Где | Статус |
|---|---|---|
| `setAlarmClock` (Doze-стойкость) | `AlarmScheduler.kt:154-161` | ✅ |
| Перепланирование (5 системных событий) | `SystemRescheduleActions.kt:25-31`, `BootReceiver.kt:37-65` | ✅ |
| Foreground-сервис звонка, стоп из шторки/экрана/смахиванием | `AlarmService.kt:86-123,247-256` | ✅ |
| Вибрация как запасной сигнал | `AlarmService.kt:138`, `AlarmVibration.kt:19-41` | ✅ |
| Готовность (разрешения) + журнал событий | `AlarmPermissions.kt`, `AlarmEvent.kt`, `DiagnosticsScreen.kt` | ✅ |

## P0 — «проспал = катастрофа»

| № | Пункт | Статус | Где в коде | Комментарий / что делать |
|---|---|---|---|---|
| P0-1 | Право на точные будильники | ✅ | `AndroidManifest.xml:6-7`, `AlarmPermissions.kt:21-24,57-61`, `AlarmReadiness.kt:39-42`, `AlarmScheduler.scheduleAt` | Манифест и готовность — ✅ (USE_EXACT_ALARM + SCHEDULE_EXACT_ALARM maxSdk32, критичный баннер, интент в настройки). **Сделано:** `scheduleAt` ловит `SecurityException` (API 31-32 без разрешения), не роняет планировщик, возвращает false; `reschedule` пишет событие `ERROR` в журнал вместо `SCHEDULED`. Внутри `scheduleAt` только `Log.e` (безопасно и при locked boot). |
| P0-2 | Полный набор событий перепланирования | ✅ | `AndroidManifest.xml:83-89`, `SystemRescheduleActions.kt:25-31`, `BootReceiver.kt:37-65` | Все 5 событий (BOOT/MY_PACKAGE_REPLACED/TIME_SET/TIMEZONE_CHANGED/EXACT_ALARM_PERMISSION_STATE_CHANGED) → полный пересчёт `rescheduleAll` + `RESCHEDULED` в журнал. Все PendingIntent `FLAG_IMMUTABLE` (таблица ниже). |
| **P0-3** | **Direct Boot (ночной ребут залоченным)** | **⚠️ слой A сделан** | `DirectBootAlarmCache.kt`, `AlarmScheduler.kt` (refreshDirectBootCache/reArmFromCache), `BootReceiver.kt`, `AndroidManifest.xml:80-93` | **Слой A (перевыставление) — СДЕЛАНО:** device-protected кэш ближайших звонков (7 дней), `directBootAware` `BootReceiver` слепо перевыставляет из кэша по `LOCKED_BOOT_COMPLETED` (без Room), полный пересчёт по `USER_UNLOCKED`, юнит-тест кодека. См. `DIRECT_BOOT.md`. **Остаётся слой B:** путь срабатывания (`AlarmReceiver`→`AlarmService`) ещё читает Room и не `directBootAware` — если звонок наступает, пока устройство ВСЁ ЕЩЁ залочено, доставка откладывается до разблокировки (поздно). Полное закрытие = сделать путь сигнала direct-boot-aware (звонить из кэша/extras без Room). Требует ручной проверки (ночной ребут). |
| P0-4 | Экран звонка сквозь блокировку | ✅ | `AlarmService.kt:107-108,268`, `AlarmActivity.kt:44-47`, `AndroidManifest.xml:62-63`, `AlarmPermissions.kt:35-38` | `CATEGORY_ALARM`, канал `IMPORTANCE_HIGH`, `setFullScreenIntent`; `showWhenLocked`/`turnScreenOn` в манифесте и коде; `canUseFullScreenIntent()` — пункт готовности. Деградация: звук/вибро из сервиса независимо; при заблокированном запуске Activity — `catch` и heads-up с «Стоп» (`:71-81`). `setFullScreenIntent` вызывается безусловно — это норма (система сама решает full-screen vs heads-up). |
| P0-5 | Foreground-сервис на 34+ | ✅ | `AlarmService.kt` (acquireWakeLock/releaseWakeLock), `AndroidManifest.xml:11,14,77` | `startForegroundService` + `startForeground(type=MEDIA_PLAYBACK)` + `goASync()` ✅. **Сделано:** `PARTIAL_WAKE_LOCK` (`shiftalarm:ringing`, страховочный таймаут 10 мин) держится, пока играет сигнал, освобождается в `stopRinging`/`onDestroy` — звук не «уснёт» при погашенном экране. **Выбор типа FGS:** оставлен `mediaPlayback` (сервис реально проигрывает звук — тип валиден и на 34+); `systemExempted` — латеральная альтернатива со своими требованиями, менять без нужды не стали. Требует проверки звука при погашенном экране на устройстве. |
| P0-6 | Звук гарантированно слышен | ⚠️ | `AlarmService.kt:166-172`, `AlarmVibration.kt`, `AlarmPermissions.kt:27-32` | `USAGE_ALARM` + `isLooping` ✅; вибрация параллельно ✅; POST_NOTIFICATIONS в готовности ✅. **Пробел:** нет предупреждения готовности «громкость STREAM_ALARM = 0» (grep `STREAM_ALARM`=0). Вибрация как запас есть, но пользователя не предупреждаем. **Делать:** пункт готовности «громкость будильника на нуле» (громкость не поднимать самовольно). |

## P1 — «пользователь может сам себе навредить»

| № | Пункт | Статус | Где в коде | Комментарий / что делать |
|---|---|---|---|---|
| P1-1 | Вендорские киллеры фона | ❌ | grep `Build.MANUFACTURER`/`Xiaomi`=0 | Экрана инструкций по производителю нет (только упоминания в обзорах). **Делать:** раздел «Настроить телефон» по `Build.MANUFACTURER` (автозапуск + исключение из энергосбережения, best-effort интенты в try-catch + текстовый fallback, ссылка dontkillmyapp.com), доступен из готовности. |
| P1-2 | Игнор батарейной оптимизации | ✅ | `AlarmPermissions.kt:41-44,80-81`, `AlarmReadiness.kt:40` | `isIgnoringBatteryOptimizations()` — пункт готовности, severity `RECOMMENDATION`. Интент — `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (список), выбран осознанно как Play-safe вместо прямого `ACTION_REQUEST_…`. Эквивалентно требованию. (Опц.: прямой запрос под RuStore.) |
| P1-3 | Самотест «Проверить будильник» | ❌ | — | Прогона через ~60 сек по реальному пути нет. **Делать:** «Проверить будильник» в готовности/настройках — боевой путь Scheduler→Receiver→Service→экран. |
| P1-4 | План/факт срабатываний | ⚠️ | `AlarmScheduler.kt:77-83`, `AlarmReceiver.kt:84-96`, `ShiftAlarmApp.kt:29-45` | Пишутся `SCHEDULED`/`FIRED`; `SKIPPED` — только когда intent пришёл, но устарел. **Пробел:** нет детекта ПОЛНОГО пропуска при старте и карточки «звонок не прозвенел». **Делать:** при старте сверять последний план с фактом, при пропуске — карточка со ссылкой на готовность. |
| P1-5 | Устойчивость AlarmReceiver | ✅ | `AlarmReceiver.kt:41-63,77-92,108-110`, `AlarmFireValidator.kt:34-73` | `goAsync()`+`Dispatchers.IO`; следующий звонок перевыставляется в момент срабатывания (не после действия пользователя); `AlarmFireValidator` проверяет актуальность до сигнала. |

## P2 — страховки

| № | Пункт | Статус | Где в коде | Комментарий / что делать |
|---|---|---|---|---|
| P2-1 | Сторож `nextAlarmClock` | ❌ | grep `nextAlarmClock`=0 | Сверки `alarmManager.nextAlarmClock` с ожиданием движка при открытии нет. **Делать:** при старте сверять и при расхождении — журнал + перевыставить. |
| P2-2 | Force-stop задокументирован | ❌ | `docs/EDITOR_PROCESS_RESTORE.md:166` (не про надёжность) | Ограничение «после force-stop будильники мертвы до ручного запуска» в docs/ не описано. **Делать:** заметка в docs/ + связать с детектом план/факт (P1-4). |
| P2-3 | Тесты пересчёта по зоне/DST | ✅ | `AlarmInstantTest.kt:16-52`, `docs/DST_POLICY.md` | Покрыты gap/overlap/зависимость от зоны; смена пояса — через `TIMEZONE_CHANGED` (P0-2). |

## Аудит PendingIntent

| # | file:line | Тип | Флаги |
|---|---|---|---|
| 1 | `AlarmScheduler.kt:144-149` | getActivity (AlarmClockInfo show) | `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT` |
| 2 | `AlarmScheduler.kt:204-209` | getBroadcast (fire→AlarmReceiver) | `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT` |
| 3 | `AlarmService.kt:86-93` | getActivity (openScreen) | `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT` |
| 4 | `AlarmService.kt:95-100` | getService (stop) | `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT` |

**Все PendingIntent — `FLAG_IMMUTABLE`. Не-immutable нет.** ✅

## Предлагаемый порядок фиксов (риск × усилие)

Внутри P0 сортировка по риску «пропущенный звонок»:

1. **P0-3 Direct Boot** — самый вероятный катастрофический сценарий (ночной OTA-ребут → проспанная смена). Усилие: среднее-большое (DE-кэш + directBootAware-ресивер + тест). **Наивысший приоритет.**
2. **P0-5 wake lock во время звонка** — звук может оборваться при погашенном экране. Усилие: малое, ценность высокая. (Заодно зафиксировать тип FGS.)
3. **P0-1 guard/`try-catch` в планировщике** — защита от `SecurityException` на 31-32. Усилие: малое.
4. **P0-6 предупреждение «громкость 0»** — пункт готовности. Усилие: малое.
5. **P1-4 детект пропуска + карточка «не прозвенел»** — усилие: среднее (опирается на журнал).
6. **P1-3 самотест «Проверить будильник»** — усилие: среднее.
7. **P1-1 вендор-инструкции по производителю** — усилие: среднее (контент + интенты).
8. **P2-1 сторож `nextAlarmClock`** — усилие: малое-среднее.
9. **P2-2 док force-stop** — усилие: тривиальное (заодно с P1-4).

Пункты ✅ (P0-2, P0-4, P1-2, P1-5, P2-3) — доработок не требуют.

## Итог

Ядро планирования и путь сигнала — крепкие: `setAlarmClock`, полный набор событий перепланирования, immutable-PendingIntent, устойчивый ресивер с валидатором, foreground-сервис, вибро-запас, покрытие готовности/диагностики. **Главный реальный риск — P0-3 Direct Boot** (ночной ребут залоченным). Остальные пробелы — страховочные и предупреждающие (wake lock, громкость 0, детект пропуска, самотест, вендор-киллеры), важны, но менее катастрофичны.
