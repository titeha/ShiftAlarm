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
    Preset("mdn", "Утро/День/Ночь") { anchor ->
      val morning = ShiftType("m", "Утро", LocalTime.of(5, 0))
      val day = ShiftType("d", "День", LocalTime.of(11, 0))
      val night = ShiftType("n", "Ночь", LocalTime.of(18, 0))
      val off = ShiftType.off("Отсыпной")
      ShiftSchedule(
        ShiftPattern(List(3) { morning } + List(3) { day } + List(3) { night } + List(3) { off }, anchor)
      )
    }
  )

  fun byId(id: String): Preset? = all.firstOrNull { it.id == id }
}
