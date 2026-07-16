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
| **P0-3** | **Direct Boot (ночной ребут залоченным)** | **✅ ПРОВЕРЕНО НА ЖЕЛЕЗЕ** | `DirectBootAlarmCache.kt`, `AlarmScheduler` (refreshDirectBootCache/reArmFromCache), `BootReceiver.kt`, `AlarmReceiver.handleAlarmLocked`, `ShiftAlarmApp.kt`, `AndroidManifest.xml` | **Слой A (перевыставление) — СДЕЛАНО:** device-protected кэш ближайших звонков (7 дней), `directBootAware` `BootReceiver` слепо перевыставляет из кэша по `LOCKED_BOOT_COMPLETED` (без Room), полный пересчёт по `USER_UNLOCKED`, юнит-тест кодека. См. `DIRECT_BOOT.md`. **Слой B (Этап 2 из `DIRECT_BOOT_FIX.md`) — СДЕЛАН:** `AlarmReceiver`/`AlarmService`/`AlarmActivity` помечены `directBootAware`; `AlarmReceiver` при `!isUserUnlocked()` звонит по метке из RingCache без Room (`handleAlarmLocked`), просроченный сверх грейса (30 мин) не звонит; `reArmFromCache` тоже с грейсом; журнал в сервисе под try/catch (CE недоступен залоченным). **★ Ключевой баг найден на устройстве:** `ShiftAlarmApp.onCreate` лез в CE-хранилище (`HolidayCalendarRepository`) → процесс падал при старте залоченным → `BootReceiver` не отрабатывал. Фикс: `onCreate` при `!isUserUnlocked()` пропускает CE-инициализацию. **ПРОВЕРЕНО на Poco F6 Pro (HyperOS):** будильник зазвонил на локскрине после ребута БЕЗ разблокировки. Условие — включённый автозапуск (P1-1). Осталось опц.: этап 3 (bundled raw-сигнал), этап 4 (буфер журнала в DPS). |
| P0-4 | Экран звонка сквозь блокировку | ✅ | `AlarmService.kt:107-108,268`, `AlarmActivity.kt:44-47`, `AndroidManifest.xml:62-63`, `AlarmPermissions.kt:35-38` | `CATEGORY_ALARM`, канал `IMPORTANCE_HIGH`, `setFullScreenIntent`; `showWhenLocked`/`turnScreenOn` в манифесте и коде; `canUseFullScreenIntent()` — пункт готовности. Деградация: звук/вибро из сервиса независимо; при заблокированном запуске Activity — `catch` и heads-up с «Стоп» (`:71-81`). `setFullScreenIntent` вызывается безусловно — это норма (система сама решает full-screen vs heads-up). |
| P0-5 | Foreground-сервис на 34+ | ✅ | `AlarmService.kt` (acquireWakeLock/releaseWakeLock), `AndroidManifest.xml:11,14,77` | `startForegroundService` + `startForeground(type=MEDIA_PLAYBACK)` + `goASync()` ✅. **Сделано:** `PARTIAL_WAKE_LOCK` (`shiftalarm:ringing`, страховочный таймаут 10 мин) держится, пока играет сигнал, освобождается в `stopRinging`/`onDestroy` — звук не «уснёт» при погашенном экране. **Выбор типа FGS:** оставлен `mediaPlayback` (сервис реально проигрывает звук — тип валиден и на 34+); `systemExempted` — латеральная альтернатива со своими требованиями, менять без нужды не стали. Требует проверки звука при погашенном экране на устройстве. |
| P0-6 | Звук гарантированно слышен | ✅ | `AlarmService.kt:166-172`, `AlarmVibration.kt`, `AlarmPermissions.alarmVolumeZero`, `AlarmReadiness.kt` | `USAGE_ALARM` + `isLooping` ✅; вибрация параллельно ✅; POST_NOTIFICATIONS в готовности ✅. **Сделано:** пункт готовности `ALARM_VOLUME` (severity RECOMMENDATION) — «Громкость будильника на нуле: сигнал только вибрацией», интент в `ACTION_SOUND_SETTINGS`; громкость кодом не поднимаем. Баннер + диагностика + юнит-тест. |

## P1 — «пользователь может сам себе навредить»

| № | Пункт | Статус | Где в коде | Комментарий / что делать |
|---|---|---|---|---|
| P1-1 | Вендорские киллеры фона | ✅ | `alarm/VendorSetup.kt` (+тест), `ui/VendorSetupScreen.kt`, `MainActivity` карточка, `SettingsScreen` | **Сделано и проверено на устройстве.** `VendorSetup.forManufacturer` (Xiaomi/Huawei/Oppo/Vivo/Samsung → инструкция + кандидаты экрана автозапуска) → экран «Настроить телефон» (best-effort интенты автозапуска с откатом, энергосбережение, dontkillmyapp.com). Подача: скрываемая карточка на списке + Настройки→«Надёжность». **★ Находка с железа (Xiaomi/HyperOS):** «Автозапуск» был ВЫКЛЮЧЕН по умолчанию → после ребута будильник не вставал вообще (проверено `adb reboot`); включение вернуло перепланирование. На Xiaomi это критичнее Direct Boot. См. `VENDOR_SETUP.md`. |
| P1-2 | Игнор батарейной оптимизации | ✅ | `AlarmPermissions.kt:41-44,80-81`, `AlarmReadiness.kt:40` | `isIgnoringBatteryOptimizations()` — пункт готовности, severity `RECOMMENDATION`. Интент — `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (список), выбран осознанно как Play-safe вместо прямого `ACTION_REQUEST_…`. Эквивалентно требованию. (Опц.: прямой запрос под RuStore.) |
| P1-3 | Самотест «Проверить будильник» | ✅ | `AlarmListViewModel.runSelfTest`, `SettingsScreen` (Надёжность) | **Сделано.** Настройки → «Надёжность» → «Проверить будильник»: создаёт разовый будильник ~через минуту и планирует боевым путём (Scheduler→Receiver→Service→экран), удаляется после срабатывания (deleteAfterFiring). Пользователь может заблокировать экран и проверить полный путь. Проверка звонка — на устройстве. |
| P1-4 | План/факт срабатываний | ✅ | `AlarmScheduler.refreshDirectBootCache`, `DirectBootAlarmStore` (missed), `MainActivity` (MissedAlarmCard) | **Сделано.** Детект пропуска переиспользует device-protected кэш: если в СТАРОМ кэше остался будильник, чьё время прошло > 90 сек (при штатном звонке AlarmReceiver перепланировал бы и обновил кэш), — считаем пропущенным, копим (последние 10). Карточка «Звонок мог не прозвенеть» на списке: список пропусков + «Настроить телефон» (для агрессивных прошивок) + «Понятно». Обычно причина — OEM-выгрузка; карточка ведёт в вендор-настройку. |
| P1-5 | Устойчивость AlarmReceiver | ✅ | `AlarmReceiver.kt:41-63,77-92,108-110`, `AlarmFireValidator.kt:34-73` | `goAsync()`+`Dispatchers.IO`; следующий звонок перевыставляется в момент срабатывания (не после действия пользователя); `AlarmFireValidator` проверяет актуальность до сигнала. |

## P2 — страховки

| № | Пункт | Статус | Где в коде | Комментарий / что делать |
|---|---|---|---|---|
| P2-1 | Сторож `nextAlarmClock` | ⚠️ | `ShiftAlarmApp.onCreate` (reschedule на каждом старте) | Явной сверки `alarmManager.nextAlarmClock` нет, НО дрейф лечится иначе-эквивалентно: `ShiftAlarmApp` перепланирует ВСЕ будильники при каждом старте приложения — открыл приложение → всё перевыставлено. Точная сверка с `nextAlarmClock` малоценна и нечётка (значение системное — может быть будильник другого приложения). Оставлено как есть; отдельный диагностический дифф — низкий приоритет. |
| P2-2 | Force-stop задокументирован | ✅ | (ниже, раздел «Известные ограничения») | Ограничение описано в этом отчёте. |
| P2-3 | Тесты пересчёта по зоне/DST | ✅ | `AlarmInstantTest.kt:16-52`, `docs/DST_POLICY.md` | Покрыты gap/overlap/зависимость от зоны; смена пояса — через `TIMEZONE_CHANGED` (P0-2). |

## Аудит PendingIntent

| # | file:line | Тип | Флаги |
|---|---|---|---|
| 1 | `AlarmScheduler.kt:144-149` | getActivity (AlarmClockInfo show) | `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT` |
| 2 | `AlarmScheduler.kt:204-209` | getBroadcast (fire→AlarmReceiver) | `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT` |
| 3 | `AlarmService.kt:86-93` | getActivity (openScreen) | `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT` |
| 4 | `AlarmService.kt:95-100` | getService (stop) | `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT` |

**Все PendingIntent — `FLAG_IMMUTABLE`. Не-immutable нет.** ✅

## Прогресс фиксов

**Сделано (P0 закрыт):**
- ✅ P0-1 guard `SecurityException` в планировщике;
- ✅ P0-3 **слой A** Direct Boot (DE-кэш + directBootAware re-arm) — слой B (звонок залоченным) остаётся;
- ✅ P0-5 wake lock во время звонка;
- ✅ P0-6 предупреждение «громкость 0».

**Сделано (P1 страховки):**
- ✅ P1-1 вендор-раздел «Настроить телефон» (проверено на Xiaomi: автозапуск был выключен — корень пропусков после ребута);
- ✅ P1-3 самотест «Проверить будильник»;
- ✅ P1-4 детект пропуска + карточка «звонок мог не прозвенеть».

**Осталось:**
- **P0-3 слой B** — direct-boot-aware путь сигнала (звонок точно в срок при залоченном устройстве). Крупное; на MIUI упирается ещё и в OEM-ограничения (`LOCKED_BOOT` фактически не отрабатывает). Артемий делегировал поиск решения отдельно.
- P2-1 — низкий приоритет (дрейф лечится reschedule-на-старте).

Пункты ✅ (P0-1..6, P1-1..5, P2-2, P2-3) — сделаны/не требуют доработок.

## Известные ограничения Android (задокументированы намеренно)

- **Force-stop.** Если пользователь нажал «Остановить» в системных настройках приложения
  (или это сделал агрессивный вендорский «менеджер»), все компоненты, включая AlarmManager-таймеры
  и ресиверы, деактивируются до **следующего ручного запуска** приложения. Это поведение самой
  Android, обойти его нельзя. Смягчение: `ShiftAlarmApp` перепланирует всё при каждом старте, а
  вендорский раздел «Настроить телефон» (P1-1, планируется) снизит вероятность таких убийств.
- **Direct Boot, слой B.** После ночного ребута залоченным будильники перевыставляются (слой A), но
  если звонок наступает, пока устройство всё ещё залочено, доставка откладывается до разблокировки —
  см. `DIRECT_BOOT.md`.

## Итог

Ядро планирования и путь сигнала — крепкие: `setAlarmClock`, полный набор событий перепланирования, immutable-PendingIntent, устойчивый ресивер с валидатором, foreground-сервис, вибро-запас, покрытие готовности/диагностики. **Главный реальный риск — P0-3 Direct Boot** (ночной ребут залоченным). Остальные пробелы — страховочные и предупреждающие (wake lock, громкость 0, детект пропуска, самотест, вендор-киллеры), важны, но менее катастрофичны.
