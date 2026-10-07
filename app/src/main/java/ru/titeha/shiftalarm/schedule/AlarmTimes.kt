package ru.titeha.shiftalarm.schedule

import ru.titeha.shiftalarm.data.AlarmEntity
import ru.titeha.shiftalarm.data.AlarmPeriod
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Чистый (без Android и I/O) расчёт ближайшего срабатывания одного будильника.
 * Смены делегируются [ShiftEngine]; режим «по дням недели» считается здесь.
 */
object AlarmTimes {

  /** Бит дня недели: Пн = бит 0 … Вс = бит 6. */
  fun bitOf(day: DayOfWeek): Int = 1 shl (day.value - 1)

  /** Маска из набора дней недели. */
  fun maskOf(vararg days: DayOfWeek): Int = days.fold(0) { acc, d -> acc or bitOf(d) }

  fun maskHas(mask: Int, day: DayOfWeek): Boolean = mask and bitOf(day) != 0

  /**
   * Ближайшее срабатывание строго после [from] для режима «по дням недели».
   *  - [daysMask] == 0 → разовый: сегодня в HH:MM, если ещё впереди, иначе завтра.
   *  - иначе → ближайший день из маски (включая сегодня), время которого ещё впереди.
   */
  fun nextWeekly(hour: Int, minute: Int, daysMask: Int, from: LocalDateTime): LocalDateTime {
    if (daysMask == 0) {
      val today = from.toLocalDate().atTime(hour, minute)
      return if (today.isAfter(from)) today else today.plusDays(1)
    }
    // 8 дней (сегодня + неделя), чтобы покрыть случай «единственный день маски = сегодня,
    // но время уже прошло»: тогда подходящим окажется тот же день недели ровно через неделю.
    var date: LocalDate = from.toLocalDate()
    repeat(8) {
      if (maskHas(daysMask, date.dayOfWeek)) {
        val candidate = date.atTime(hour, minute)
        if (candidate.isAfter(from)) return candidate
      }
      date = date.plusDays(1)
    }
    // Маска непустая → за 8 дней подходящий день гарантированно найден выше; сюда не дойдём.
    error("nextWeekly: не найден день для непустой маски $daysMask")
  }

  /**
   * Ближайшее срабатывание [alarm] строго после [from] с учётом периодов отпуска [periods] и
   * пер-дневных правок календаря [overrides] (подмены/исключения). Периоды глушат звонок во всех
   * режимах; правки — только в сменном. null — ставить нечего.
   *
   * [globalWeek] — глобальная рабочая неделя пользователя (Q1=1а: глобальная настройка;
   * подключение настройки — TC03). По умолчанию — стандартная неделя страны, поэтому поведение
   * существующих вызовов и сохранённых будильников не меняется до появления настройки.
   */
  fun next(
    alarm: AlarmEntity,
    periods: List<AlarmPeriod>,
    overrides: List<ScheduleOverrides.DayOverride>,
    from: LocalDateTime,
    globalWeek: WorkWeek = CountryProfile.RU.standardWeek,
  ): LocalDateTime? {
    // Производственный календарь — в режиме calendar всегда (D-1), иначе по флагу honorHolidays.
    // calendarFor() покрывает год отсчёта и следующий (поиск может уйти за границу года); берёт из
    // кэша (обновляемого с офиц. источника), иначе встроенные данные. Год без данных → лишь Сб/Вс.
    val usesCalendarAlways = effectiveMode(alarm) == AlarmEntity.MODE_CALENDAR
    val calendar = ProductionCalendars.calendarFor(
      if (usesCalendarAlways) true else alarm.honorHolidays,
      from.toLocalDate().year
    )

    val effectivePolarity = HolidayModePolicy.effectivePolarity(alarm)

    return when (effectiveMode(alarm)) {
      AlarmEntity.MODE_SHIFT -> shiftBase(alarm)?.let { base ->
        val schedule = ScheduleOverrides.apply(
          ShiftSchedule(base).copy(
            offPeriods = periods.map {
              OffPeriod(LocalDate.ofEpochDay(it.fromEpochDay), LocalDate.ofEpochDay(it.toEpochDay), it.reason)
            },
            freezeCycleDuringOff = alarm.freezeCycleDuringOff
          ),
          overrides
        )
        ShiftEngine.nextAlarm(from, schedule, calendar = calendar)
      }
      AlarmEntity.MODE_CALENDAR -> nextCalendarResolved(alarm, periods, from, calendar, globalWeek)
      else -> nextWeeklyResolved(alarm, periods, from, calendar, effectivePolarity, globalWeek)
    }
  }

  /**
   * Режим «по производственному календарю страны» (MODE_CALENDAR): звонит в официальные
   * нерабочие дни (Сб, Вс, праздники, перенесённые выходные). Календарь запрашивается всегда
   * (D-1), полярность фиксирована REST, маска [AlarmEntity.daysMask] не участвует (D-2),
   * периоды без будильника глушат звонок (D-4). При отсутствии данных на год деградирует
   * до Сб/Вс (как weekly-REST).
   */
  private fun nextCalendarResolved(
    alarm: AlarmEntity,
    periods: List<AlarmPeriod>,
    from: LocalDateTime,
    calendar: ProductionCalendar?,
    globalWeek: WorkWeek,
  ): LocalDateTime? {
    // Полярность в этом режиме фиксирована REST; календарь уже запрошен с honorHolidays=true.
    // Инвертируемая неделя — глобальная рабочая неделя пользователя (Q5; по умолчанию —
    // стандартная неделя страны). Пустой календарь (нет данных на год) → только Сб/Вс своей недели.
    return searchRing(
      alarm = alarm,
      periods = periods,
      from = from,
      workWeek = globalWeek,
      calendar = calendar,
      polarity = AlarmPolarity.REST,
      globalWeek = globalWeek,
    )
  }

  /**
   * Общий поиск ближайшего звонка по матрице «личная неделя × госкалендарь» для несменных
   * режимов: перебор дней вперёд, первый день, в который [PersonalDayResolver] разрешает
   * звонок и который не покрыт периодом без будильника, и время которого ещё впереди.
   * Горизонт — 370 дней (год с запасом); null — за горизонтом подходящего дня нет.
   * [workWeek] — неделя «дня звонка» (маска в weekly, [globalWeek] в calendar);
   * [globalWeek] передаётся резолверу для правила переносов (Q4=4a).
   */
  private fun searchRing(
    alarm: AlarmEntity,
    periods: List<AlarmPeriod>,
    from: LocalDateTime,
    workWeek: WorkWeek,
    calendar: ProductionCalendar?,
    polarity: AlarmPolarity,
    globalWeek: WorkWeek,
  ): LocalDateTime? {
    val resolver = PersonalDayResolver(CountryProfile.RU, calendar, polarity, globalWeek)
    var date = from.toLocalDate()
    repeat(370) {
      if (resolver.ringOn(date, workWeek) && !coveredByPeriod(periods, date)) {
        val candidate = date.atTime(alarm.hour, alarm.minute)
        if (candidate.isAfter(from)) return candidate
      }
      date = date.plusDays(1)
    }
    return null
  }

  /**
   * Ближайшее срабатывание weekly-будильника через матрицу «личная неделя × госкалендарь»
   * ([PersonalDayResolver]). Разовый (маска 0) — ближайшее время без календаря.
   * После TD03 полярность REST у weekly не действует (только у calendar); weekly всегда
   * считается как WORK (личная неделя = отмеченные дни из маски).
   */
  private fun nextWeeklyResolved(
    alarm: AlarmEntity,
    periods: List<AlarmPeriod>,
    from: LocalDateTime,
    calendar: ProductionCalendar?,
    polarity: AlarmPolarity,
    globalWeek: WorkWeek,
  ): LocalDateTime? {
    val effPolarity = if (calendar == null) AlarmPolarity.WORK else polarity

    if (alarm.daysMask == 0 && effPolarity == AlarmPolarity.WORK) {
      val today = from.toLocalDate().atTime(alarm.hour, alarm.minute)
      return if (today.isAfter(from)) today else today.plusDays(1)
    }

    val workWeek = personalWeek(alarm.daysMask, effPolarity, globalWeek)
    return searchRing(
      alarm = alarm,
      periods = periods,
      from = from,
      workWeek = workWeek,
      calendar = calendar,
      polarity = effPolarity,
      globalWeek = globalWeek,
    )
  }

  /** Покрыт ли день [date] периодом без будильника. */
  private fun coveredByPeriod(periods: List<AlarmPeriod>, date: LocalDate): Boolean {
    val day = date.toEpochDay()
    return periods.any { day in it.fromEpochDay..it.toEpochDay }
  }

  /**
   * Режим будильника с защитным чтением: неизвестное или повреждённое значение трактуется как
   * «по дням недели» — так же, как [PeriodKind.fromReason] для типов периода. Лучше позвонить
   * по маске, чем не позвонить вовсе; явная нормализация нужна и для того, чтобы новое значение
   * режима не попало в молчаливую ветку `else`, и чтобы все потребители трактовали неизвестный
   * режим одинаково: расчёт [next], точки календаря [firesOn], участие производственного
   * календаря [usesProductionCalendar] и правило «разовый» [isOneShot].
   *
   * Различаются ровно три режима: [AlarmEntity.MODE_SHIFT], [AlarmEntity.MODE_CALENDAR] и
   * «остальное — weekly». Историческая форма `weekly + honorHolidays + REST` больше не
   * различается: полярность действует только в режиме calendar (TD03, инвариант D-5), а прежние
   * такие записи перенесены в calendar миграцией Room 6→7 (TD02).
   */
  private fun effectiveMode(alarm: AlarmEntity): String = when (alarm.mode) {
    AlarmEntity.MODE_SHIFT -> AlarmEntity.MODE_SHIFT
    AlarmEntity.MODE_CALENDAR -> AlarmEntity.MODE_CALENDAR
    else -> AlarmEntity.MODE_WEEKLY
  }

  /**
   * Разовый ли это будильник: сработает один раз и будет выключен (или удалён) приёмником.
   *
   * Правило одно на движок и приёмник: пустая маска дней имеет смысл только в режиме «по дням
   * недели». Сменный режим и режим «по производственному календарю страны» повторяющиеся, а
   * неизвестный режим трактуется как weekly ([effectiveMode]) — так же, как в расчёте [next].
   */
  fun isOneShot(alarm: AlarmEntity): Boolean =
    effectiveMode(alarm) == AlarmEntity.MODE_WEEKLY && alarm.daysMask == 0

  /**
   * Участвует ли производственный календарь в расчёте [alarm]. Если нет — отсутствие данных на год
   * расчёт не ухудшает, поэтому диагностика о деградации таким будильникам не пишется.
   *
   * Праздники не запрошены — календарь не читается вовсе. Смены учитывают его как фильтр: официальные
   * нерабочие дни глушат звонки графика. «По дням недели» календарь читается всегда, кроме разового
   * (пустая маска при полярности WORK): у него ближайшее время возвращается до резолвера.
   * Режим calendar — всегда с календарём (инвариант D-1 фазы D), независимо от флага honorHolidays.
   */
  fun usesProductionCalendar(alarm: AlarmEntity): Boolean {
    if (effectiveMode(alarm) == AlarmEntity.MODE_CALENDAR) return true // D-1: всегда
    if (!alarm.honorHolidays) return false
    return when (effectiveMode(alarm)) {
      AlarmEntity.MODE_SHIFT -> true
      else -> alarm.daysMask != 0 || HolidayModePolicy.effectivePolarity(alarm) == AlarmPolarity.REST
    }
  }

  /**
   * Неделя для [PersonalDayResolver]. После TD03 личная неделя (из маски будильника) используется
   * только для недельного режима (полярность всегда WORK); для режима calendar — глобальная
   * рабочая неделя пользователя [globalWeek] (Q5 фазы C; по умолчанию — стандартная неделя
   * страны), а «по выходным» означает её инверсию плюс официальные нерабочие дни (праздники,
   * перенесённые выходные — когда переносы применимы). Маска в calendar на расчёт не влияет
   * (D-2); см. `docs/SHIFT_HOLIDAY_POLICY.md` и `docs/WORK_WEEK_AND_STUDY.md` §1.
   */
  private fun personalWeek(daysMask: Int, polarity: AlarmPolarity, globalWeek: WorkWeek): WorkWeek {
    if (polarity == AlarmPolarity.REST) return globalWeek
    return WorkWeek.fromMask(daysMask)
  }

  /** Перегрузка без правок календаря — периоды учитывает, правки нет. */
  fun next(
    alarm: AlarmEntity,
    periods: List<AlarmPeriod>,
    from: LocalDateTime,
    globalWeek: WorkWeek = CountryProfile.RU.standardWeek,
  ): LocalDateTime? = next(alarm, periods, emptyList(), from, globalWeek)

  /**
   * Звонит ли будильник в конкретный календарный день [date] — для наглядной визуализации в
   * календаре (совпадает с логикой [next]). Сменный режим сюда не попадает (у него свой расчёт
   * через [ShiftEngine]).
   *
   * Режим calendar звонит по официальным нерабочим дням; календарь в нём обязателен (D-1), маска
   * не участвует (D-2), инвертируемая неделя — [globalWeek] (Q5). Для weekly: полярность REST
   * после TD03 не действует (только calendar); weekly всегда считается по масочным дням, а при
   * [AlarmEntity.honorHolidays] нерабочие глушатся (правило переносов — от глобальной недели
   * [globalWeek], Q4=4a).
   * Разовый (маска 0) как повтор не показываем.
   */
  fun firesOn(
    alarm: AlarmEntity,
    date: LocalDate,
    calendar: ProductionCalendar?,
    periods: List<AlarmPeriod> = emptyList(),
    globalWeek: WorkWeek = CountryProfile.RU.standardWeek,
  ): Boolean {
    val mode = effectiveMode(alarm)
    if (mode == AlarmEntity.MODE_SHIFT) return false
    if (coveredByPeriod(periods, date)) return false // отпуск/больничный/… глушат звонок

    if (mode == AlarmEntity.MODE_CALENDAR) {
      // Полярность фиксирована REST, инвертируемая неделя — глобальная неделя пользователя
      // (Q5; по умолчанию — стандартная неделя страны), маска игнорируется (D-2).
      return PersonalDayResolver(CountryProfile.RU, calendar, AlarmPolarity.REST, globalWeek)
        .ringOn(date, globalWeek)
    }

    // REST требует календаря; без него (тумблер выключен) — как WORK. Разовый как повтор не показываем.
    val polarity = if (calendar == null) AlarmPolarity.WORK
      else HolidayModePolicy.effectivePolarity(alarm)
    if (alarm.daysMask == 0 && polarity == AlarmPolarity.WORK) return false

    val workWeek = personalWeek(alarm.daysMask, polarity, globalWeek)
    return PersonalDayResolver(CountryProfile.RU, calendar, polarity, globalWeek).ringOn(date, workWeek)
  }

  /**
   * Базовая ротация будильника-смены: произвольный цикл из [AlarmEntity.cycleSpec], если он задан
   * и корректен, иначе встроенный пресет [AlarmEntity.presetId]. null — цикл задать нечем.
   *
   * Повреждённый `cycleSpec` не должен ронять планировщик. В таком случае откатываемся
   * на пресет, если он задан.
   */
  fun shiftBase(alarm: AlarmEntity): ShiftPattern? {
    val anchor = LocalDate.ofEpochDay(alarm.anchorEpochDay)

    alarm.cycleSpec?.let { spec ->
      val slots = ShiftCycleCodec.decodeOrNull(spec)

      if (!slots.isNullOrEmpty()) {
        return ShiftPattern(normalizeCustomNightAlarms(slots), anchor)
      }
    }

    return ShiftPresets.byId(alarm.presetId)?.build(anchor)?.base
  }

  /**
   * Времена звонка для заголовка списка. В сменном режиме время живёт в цикле (у каждой смены
   * свой [ShiftType.wakeTime]); поля [AlarmEntity.hour]/[AlarmEntity.minute] для смен не
   * используются вовсе — поэтому заголовок нельзя брать из них. Возвращаем различимые времена
   * цикла по возрастанию (обычно одно; у «сутки-трое» и т.п. — несколько). Для остальных режимов —
   * одно время [AlarmEntity.hour]:[AlarmEntity.minute]. Пустой/битый цикл → откат на hour:minute.
   */
  fun headlineTimes(alarm: AlarmEntity): List<LocalTime> {
    if (alarm.mode == AlarmEntity.MODE_SHIFT) {
      val times = shiftBase(alarm)?.slots?.mapNotNull { it.wakeTime }?.distinct()?.sorted()
      if (!times.isNullOrEmpty()) return times
    }
    return listOf(LocalTime.of(alarm.hour, alarm.minute))
  }

  /**
   * Готовая строка времени для заголовка списка. Одно время → «HH:MM». Несколько (многосменный
   * цикл) → диапазон «самое раннее–самое позднее» («HH:MM–HH:MM»), чтобы заголовок не мельтешил
   * всеми временами. [headlineTimes] уже отсортирован по возрастанию.
   */
  fun headlineTimeLabel(alarm: AlarmEntity): String {
    val times = headlineTimes(alarm)
    fun fmt(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)
    return if (times.size <= 1) fmt(times.first())
    else "${fmt(times.first())}–${fmt(times.last())}"
  }

  /**
   * Расписание смены из будильника + периодов отпуска + пер-дневных правок — общий сборщик для
   * превью «Проверка» и календаря редактора. Периоды → [OffPeriod] (движок глушит по дню
   * обслуживаемой смены), правки применяются поверх. null — цикл задать нечем. Тот же материал,
   * что использует [next] для смен, поэтому превью совпадает с реальным планированием.
   */
  fun shiftScheduleOf(
    alarm: AlarmEntity,
    periods: List<AlarmPeriod>,
    overrides: List<ScheduleOverrides.DayOverride>,
  ): ShiftSchedule? = shiftBase(alarm)?.let { base ->
    ScheduleOverrides.apply(
      ShiftSchedule(base).copy(
        offPeriods = periods.map {
          OffPeriod(LocalDate.ofEpochDay(it.fromEpochDay), LocalDate.ofEpochDay(it.toEpochDay), it.reason)
        },
        freezeCycleDuringOff = alarm.freezeCycleDuringOff
      ),
      overrides
    )
  }

  /**
   * Приводит пользовательский цикл к календарной модели движка.
   *
   * В редакторе пользователь задаёт время будильника у самой ночной смены:
   * «Ночь, 21:00». Для движка это должно означать звонок накануне,
   * потому что ночная смена отмечена днём её отработки, а будить нужно вечером
   * предыдущего календарного дня.
   *
   * Пример:
   *   Выходной, Ночь(21:00), Ночь(21:00), Ночь(21:00), Выходной
   *
   * превращается в:
   *   Выходной(21:00), Ночь(21:00), Ночь(21:00), Ночь без звонка, Выходной
   *
   * Если предыдущий слот уже содержит звонок, мы его не перезаписываем:
   * текущая модель поддерживает только один звонок на календарный день.
   */
  private fun normalizeCustomNightAlarms(slots: List<ShiftType>): List<ShiftType> {
    if (slots.isEmpty()) return slots
    if (slots.none { it.category == ShiftCategory.NIGHT && it.wakeTime != null }) return slots

    val normalized = slots.toMutableList()

    for (index in slots.indices) {
      val slot = slots[index]

      if (slot.category != ShiftCategory.NIGHT || slot.wakeTime == null) {
        continue
      }

      val previousIndex = if (index == 0) slots.lastIndex else index - 1
      val previousSlot = normalized[previousIndex]

      if (previousSlot.wakeTime == null) {
        normalized[previousIndex] = previousSlot.copy(wakeTime = slot.wakeTime)
        normalized[index] = normalized[index].copy(wakeTime = null)
      }
    }

    return normalized
  }

  /**
   * Без периодов отпуска — удобная перегрузка для режима «по дням недели», который периоды
   * не использует. Для смен передавайте периоды через [next] с тремя аргументами, иначе
   * отпускные дни не будут заглушены.
   */
  fun next(alarm: AlarmEntity, from: LocalDateTime): LocalDateTime? =
    next(alarm, emptyList(), emptyList(), from)
}
