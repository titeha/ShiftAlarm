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
   * пер-дневных правок календаря [overrides] (подмены/исключения). Всё это только для режима смен;
   * «по дням недели» ни периоды, ни правки не использует. null — ставить нечего.
   */
  fun next(
    alarm: AlarmEntity,
    periods: List<AlarmPeriod>,
    overrides: List<ScheduleOverrides.DayOverride>,
    from: LocalDateTime
  ): LocalDateTime? {
    // Производственный календарь — только если будильник просит его учитывать. Страна пока РФ.
    // merged() покрывает текущий и следующий год (поиск может уйти за границу года); берёт из кэша
    // (обновляемого с офиц. источника), иначе встроенные данные. Год без данных → лишь Сб/Вс.
    val calendar = if (alarm.honorHolidays) ProductionCalendars.merged("RU", from.toLocalDate().year) else null

    val effectivePolarity = HolidayModePolicy.effectivePolarity(alarm)

    return when (alarm.mode) {
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
      else -> nextWeeklyResolved(alarm, periods, from, calendar, effectivePolarity)
    }
  }

  /**
   * Ближайшее срабатывание weekly-будильника через матрицу «личная неделя × госкалендарь»
   * ([PersonalDayResolver]). Разовый (маска 0, WORK) — ближайшее время без календаря. REST требует
   * календаря; без него ведёт себя как WORK. Личная неделя = отмеченные дни (маска); для REST с пустой
   * маской берём стандартную неделю страны, чтобы «по выходным» = Сб/Вс/праздники.
   */
  private fun nextWeeklyResolved(
    alarm: AlarmEntity,
    periods: List<AlarmPeriod>,
    from: LocalDateTime,
    calendar: ProductionCalendar?,
    polarity: AlarmPolarity,
  ): LocalDateTime? {
    val effPolarity = if (calendar == null) AlarmPolarity.WORK else polarity

    if (alarm.daysMask == 0 && effPolarity == AlarmPolarity.WORK) {
      val today = from.toLocalDate().atTime(alarm.hour, alarm.minute)
      return if (today.isAfter(from)) today else today.plusDays(1)
    }

    val workWeek = personalWeek(alarm.daysMask, effPolarity)
    val resolver = PersonalDayResolver(CountryProfile.RU, calendar, effPolarity)

    var date = from.toLocalDate()
    repeat(370) {
      // Периоды без будильника (отпуск/больничный/за свой счёт/сессия/…) глушат звонок и в режиме
      // «по дням недели» — как у смен.
      if (resolver.ringOn(date, workWeek) && !coveredByPeriod(periods, date)) {
        val candidate = date.atTime(alarm.hour, alarm.minute)
        if (candidate.isAfter(from)) return candidate
      }
      date = date.plusDays(1)
    }
    return null
  }

  /** Покрыт ли день [date] периодом без будильника. */
  private fun coveredByPeriod(periods: List<AlarmPeriod>, date: LocalDate): Boolean {
    val day = date.toEpochDay()
    return periods.any { day in it.fromEpochDay..it.toEpochDay }
  }

  /** Личная неделя из маски; для REST с пустой маской — стандартная неделя страны. */
  private fun personalWeek(daysMask: Int, polarity: AlarmPolarity): WorkWeek {
    val fromMask = WorkWeek.fromMask(daysMask)
    return if (polarity == AlarmPolarity.REST && fromMask.workingDays.isEmpty()) {
      CountryProfile.RU.standardWeek
    } else {
      fromMask
    }
  }

  /** Перегрузка без правок календаря — периоды учитывает, правки нет. */
  fun next(alarm: AlarmEntity, periods: List<AlarmPeriod>, from: LocalDateTime): LocalDateTime? =
    next(alarm, periods, emptyList(), from)

  /**
   * Звонит ли weekly-будильник в конкретный календарный день [date] — для наглядной визуализации в
   * календаре (совпадает с логикой [next]): полярность REST звонит по нерабочим (выходные/праздники),
   * иначе — по масочным дням, а при [AlarmEntity.honorHolidays] нерабочие глушатся. Разовый (маска 0)
   * как повтор не показываем.
   */
  fun weeklyFiresOn(
    alarm: AlarmEntity,
    date: LocalDate,
    calendar: ProductionCalendar?,
    periods: List<AlarmPeriod> = emptyList(),
  ): Boolean {
    if (alarm.mode != AlarmEntity.MODE_WEEKLY) return false
    if (coveredByPeriod(periods, date)) return false // отпуск/больничный/… глушат и weekly

    // REST требует календаря; без него (тумблер выключен) — как WORK. Разовый как повтор не показываем.
    val polarity = if (calendar == null) AlarmPolarity.WORK
      else HolidayModePolicy.effectivePolarity(alarm)
    if (alarm.daysMask == 0 && polarity == AlarmPolarity.WORK) return false

    val workWeek = personalWeek(alarm.daysMask, polarity)
    return PersonalDayResolver(CountryProfile.RU, calendar, polarity).ringOn(date, workWeek)
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
