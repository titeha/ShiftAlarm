package ru.titeha.shiftalarm.schedule

import ru.titeha.shiftalarm.data.AlarmEntity
import ru.titeha.shiftalarm.data.AlarmPeriod
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

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
    var date: LocalDate = from.toLocalDate()
    repeat(7) {
      if (maskHas(daysMask, date.dayOfWeek)) {
        val candidate = date.atTime(hour, minute)
        if (candidate.isAfter(from)) return candidate
      }
      date = date.plusDays(1)
    }
    // Маска непустая, значит подходящий день всегда найдётся в пределах недели — сюда не дойдём,
    // но на всякий случай возвращаем следующий подходящий день по тому же правилу.
    return nextWeekly(hour, minute, daysMask, from.plusDays(7))
  }

  /**
   * Ближайшее срабатывание [alarm] строго после [from] с учётом периодов отпуска [periods]
   * (для режима смен; «по дням недели» периоды не использует). null — ставить нечего.
   */
  fun next(alarm: AlarmEntity, periods: List<AlarmPeriod>, from: LocalDateTime): LocalDateTime? =
    when (alarm.mode) {
      AlarmEntity.MODE_SHIFT -> ShiftPresets.byId(alarm.presetId)?.let { preset ->
        val schedule = preset.build(LocalDate.ofEpochDay(alarm.anchorEpochDay)).copy(
          offPeriods = periods.map {
            OffPeriod(LocalDate.ofEpochDay(it.fromEpochDay), LocalDate.ofEpochDay(it.toEpochDay), it.reason)
          },
          freezeCycleDuringOff = alarm.freezeCycleDuringOff
        )
        ShiftEngine.nextAlarm(from, schedule)
      }
      else -> nextWeekly(alarm.hour, alarm.minute, alarm.daysMask, from)
    }

  /**
   * Без периодов отпуска. Используется для превью «след:» в списке (приблизительно — не глушит
   * отпускные дни) и для режима «по дням недели», который периоды не использует.
   */
  fun next(alarm: AlarmEntity, from: LocalDateTime): LocalDateTime? = next(alarm, emptyList(), from)
}
