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
    // Производственный календарь — только если будильник просит его учитывать. Страна пока РФ
    // (мультистрана и онлайн-источник — отдельный слой).
    val calendar = if (alarm.honorHolidays) ProductionCalendars.bundled("RU") else null

    // Полярность REST («буди по выходным»): звонок в hh:mm на нерабочих днях, независимо от
    // режима и маски — календарь задаёт дни (выходные/праздники/переносы).
    if (calendar != null && alarm.polarity == AlarmEntity.POLARITY_REST) {
      return HolidayAlarms.next(
        from, LocalTime.of(alarm.hour, alarm.minute), AlarmPolarity.REST, calendar
      )
    }

    // Иначе — обычный расчёт по режиму; при honorHolidays (WORK) нерабочие дни глушатся.
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
      else ->
        if (calendar != null) nextWeeklyWorking(alarm.hour, alarm.minute, alarm.daysMask, from, calendar)
        else nextWeekly(alarm.hour, alarm.minute, alarm.daysMask, from)
    }
  }

  /**
   * Как [nextWeekly], но пропускает официально нерабочие дни по [calendar] (полярность WORK:
   * «буди по масочным дням, но не в праздник/выходной»). Разовый (маска 0) календарь не фильтрует.
   * Горизонт шире, т.к. масочные дни могут выпасть на длинные праздники; null — за год не нашлось.
   */
  private fun nextWeeklyWorking(
    hour: Int, minute: Int, daysMask: Int, from: LocalDateTime, calendar: ProductionCalendar
  ): LocalDateTime? {
    if (daysMask == 0) {
      val today = from.toLocalDate().atTime(hour, minute)
      return if (today.isAfter(from)) today else today.plusDays(1)
    }
    var date = from.toLocalDate()
    repeat(370) {
      if (maskHas(daysMask, date.dayOfWeek) && calendar.isWorking(date)) {
        val candidate = date.atTime(hour, minute)
        if (candidate.isAfter(from)) return candidate
      }
      date = date.plusDays(1)
    }
    return null
  }

  /** Перегрузка без правок календаря — периоды учитывает, правки нет. */
  fun next(alarm: AlarmEntity, periods: List<AlarmPeriod>, from: LocalDateTime): LocalDateTime? =
    next(alarm, periods, emptyList(), from)

  /**
   * Базовая ротация будильника-смены: произвольный цикл из [AlarmEntity.cycleSpec], если он задан
   * и непустой, иначе встроенный пресет [AlarmEntity.presetId]. null — цикл задать нечем.
   */
  fun shiftBase(alarm: AlarmEntity): ShiftPattern? {
    val anchor = LocalDate.ofEpochDay(alarm.anchorEpochDay)
    alarm.cycleSpec?.let { spec ->
      val slots = ShiftCycleCodec.decode(spec)
      if (slots.isNotEmpty()) return ShiftPattern(slots, anchor)
    }
    return ShiftPresets.byId(alarm.presetId)?.build(anchor)?.base
  }

  /**
   * Без периодов отпуска — удобная перегрузка для режима «по дням недели», который периоды
   * не использует. Для смен передавайте периоды через [next] с тремя аргументами, иначе
   * отпускные дни не будут заглушены.
   */
  fun next(alarm: AlarmEntity, from: LocalDateTime): LocalDateTime? =
    next(alarm, emptyList(), emptyList(), from)
}
