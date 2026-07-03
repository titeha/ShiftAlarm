package ru.titeha.shiftalarm.schedule

import java.time.LocalDate
import java.time.LocalTime

/**
 * Готовые графики смен. Опорная дата задаётся при выборе (обычно «сегодня» = первый день цикла).
 * Полноценный редактор произвольных циклов — отдельный шаг.
 */
object ShiftPresets {

  data class Preset(
    val id: String,
    val title: String,
    val build: (anchor: LocalDate) -> ShiftSchedule
  )

  val all: List<Preset> = listOf(
    Preset("2x2", "2/2 в 7:00") { anchor ->
      ShiftSchedule(
        ShiftPattern.workRest(2, 2, ShiftType("d", "Работа", LocalTime.of(7, 0)), anchor)
      )
    },
    Preset("sutki", "Сутки/трое") { anchor ->
      ShiftSchedule(
        ShiftPattern(
          listOf(
            ShiftType("s", "Сутки", LocalTime.of(6, 0)),
            ShiftType.off("Отсыпной"),
            ShiftType.off(),
            ShiftType.off()
          ),
          anchor
        )
      )
    },
    // Времена — это время БУДИЛЬНИКА (раньше старта смены на дорогу/сборы):
    // утро старт 7:00 → звонок 5:00; день старт 15:00 → 13:00; ночь старт 23:00 → звонок 21:00.
    // Вариант Б: ночная отрабатывается в эти сутки (метка «Ночь»), но звонок — вечером
    // предыдущего дня. Поэтому последний выходной перед ночами несёт звонок (вых*), а последняя
    // ночь — без своего звонка (он был накануне). Цикл 3/2, 15 дней.
    Preset("mdn", "Утро/День/Ночь 3×2") { anchor ->
      val morning = ShiftType("m", "Утро", LocalTime.of(5, 0), ShiftCategory.MORNING)
      val day = ShiftType("d", "День", LocalTime.of(13, 0), ShiftCategory.DAY)
      val night = ShiftType("n", "Ночь", LocalTime.of(21, 0), ShiftCategory.NIGHT)
      val nightLast = ShiftType("n", "Ночь", null, ShiftCategory.NIGHT)
      val depart = ShiftType("o", "Выходной", LocalTime.of(21, 0), ShiftCategory.OFF)
      val off = ShiftType.off()
      ShiftSchedule(
        ShiftPattern(
          List(3) { morning } + listOf(off, off) +
            List(3) { day } + listOf(off, depart) +
            listOf(night, night, nightLast) + listOf(off, off),
          anchor
        )
      )
    }
  )

  fun byId(id: String): Preset? = all.firstOrNull { it.id == id }
}
