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

    // Полярность REST («буди по выходным»): звонок в hh:mm на нерабочих днях, независимо от режима
    // и маски. Без данных календаря — хотя бы обычные Сб/Вс (пустой ProductionCalendar).
    if (alarm.honorHolidays && alarm.polarity == AlarmEntity.POLARITY_REST) {
      return HolidayAlarms.next(
        from, LocalTime.of(alarm.hour, alarm.minute), AlarmPolarity.REST, calendar ?: ProductionCalendar()
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
