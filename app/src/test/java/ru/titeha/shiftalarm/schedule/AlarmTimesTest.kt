package ru.titeha.shiftalarm.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.titeha.shiftalarm.data.AlarmEntity
import ru.titeha.shiftalarm.data.AlarmPeriod
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class AlarmTimesTest {

  // 2026-06-24 — среда.
  private val wed = LocalDate.of(2026, 6, 24)
  private fun at(date: LocalDate, h: Int, m: Int) = date.atTime(h, m)

  @Test
  fun `разовый — сегодня, если время ещё впереди`() {
    val from = at(wed, 6, 0)
    val next = AlarmTimes.nextWeekly(7, 0, daysMask = 0, from = from)
    assertEquals(at(wed, 7, 0), next)
  }

  @Test
  fun `разовый — завтра, если время уже прошло`() {
    val from = at(wed, 8, 0)
    val next = AlarmTimes.nextWeekly(7, 0, daysMask = 0, from = from)
    assertEquals(at(wed.plusDays(1), 7, 0), next)
  }

  @Test
  fun `разовый — ровно текущее время считается прошедшим`() {
    val from = at(wed, 7, 0)
    val next = AlarmTimes.nextWeekly(7, 0, daysMask = 0, from = from)
    assertEquals(at(wed.plusDays(1), 7, 0), next)
  }

  @Test
  fun `по дням недели — сегодня среда в маске и время впереди`() {
    val mask = AlarmTimes.maskOf(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
    val next = AlarmTimes.nextWeekly(7, 0, mask, at(wed, 6, 0))
    assertEquals(at(wed, 7, 0), next)
  }

  @Test
  fun `по дням недели — сегодня в маске, но время прошло, берём следующий день маски`() {
    // Среда и пятница; в среду уже 8:00 → ближайшее в пятницу.
    val mask = AlarmTimes.maskOf(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
    val next = AlarmTimes.nextWeekly(7, 0, mask, at(wed, 8, 0))
    assertEquals(at(wed.plusDays(2), 7, 0), next) // пятница 26-го
  }

  @Test
  fun `по дням недели — только понедельник, со среды до следующего понедельника`() {
    val mask = AlarmTimes.maskOf(DayOfWeek.MONDAY)
    val next = AlarmTimes.nextWeekly(9, 30, mask, at(wed, 12, 0))
    assertEquals(at(LocalDate.of(2026, 6, 29), 9, 30), next) // ближайший понедельник
  }

  @Test
  fun `по дням недели — единственный день = сегодня, время прошло, берём этот же день через неделю`() {
    // Регрессия StackOverflowError: маска = только среда, сегодня среда, 8:00 уже позже 7:00.
    // Раньше fallback рекурсивно прибавлял +7 дней с тем же временем суток и не сходился.
    val mask = AlarmTimes.maskOf(DayOfWeek.WEDNESDAY)
    val next = AlarmTimes.nextWeekly(7, 0, mask, at(wed, 8, 0))
    assertEquals(at(wed.plusDays(7), 7, 0), next) // следующая среда
  }

  @Test
  fun `по дням недели — единственный день = сегодня, ровно текущее время считается прошедшим`() {
    val mask = AlarmTimes.maskOf(DayOfWeek.WEDNESDAY)
    val next = AlarmTimes.nextWeekly(7, 0, mask, at(wed, 7, 0))
    assertEquals(at(wed.plusDays(7), 7, 0), next)
  }

  @Test
  fun `каждый день — маска из всех семи дней даёт завтра, если время прошло`() {
    val all = AlarmTimes.maskOf(*DayOfWeek.entries.toTypedArray())
    val next = AlarmTimes.nextWeekly(7, 0, all, at(wed, 9, 0))
    assertEquals(at(wed.plusDays(1), 7, 0), next)
  }

  @Test
  fun `next — режим weekly делегирует расчёт по маске`() {
    val alarm = AlarmEntity(hour = 7, minute = 0, mode = AlarmEntity.MODE_WEEKLY, daysMask = 0)
    val next = AlarmTimes.next(alarm, at(wed, 6, 0))
    assertEquals(at(wed, 7, 0), next)
  }

  @Test
  fun `next — режим shift делегирует движку смен`() {
    // Пресет 2x2 в 7:00, опорная дата = среда (рабочий день).
    val alarm = AlarmEntity(
      mode = AlarmEntity.MODE_SHIFT,
      presetId = "2x2",
      anchorEpochDay = wed.toEpochDay()
    )
    val next = AlarmTimes.next(alarm, at(wed, 6, 0))
    assertEquals(at(wed, 7, 0), next)
  }

  @Test
  fun `next — период отпуска глушит день и сдвигает звонок на следующую смену`() {
    // Пресет 2x2 в 7:00, опора = среда (рабочий день, как и четверг).
    val alarm = AlarmEntity(
      mode = AlarmEntity.MODE_SHIFT,
      presetId = "2x2",
      anchorEpochDay = wed.toEpochDay()
    )
    // Отпуск ровно на среду → ближайший звонок переезжает на четверг.
    val period = AlarmPeriod(
      alarmId = 1,
      fromEpochDay = wed.toEpochDay(),
      toEpochDay = wed.toEpochDay()
    )
    val next = AlarmTimes.next(alarm, listOf(period), at(wed, 6, 0))
    assertEquals(at(wed.plusDays(1), 7, 0), next)
  }

  @Test
  fun `next — неизвестный пресет даёт null`() {
    val alarm = AlarmEntity(mode = AlarmEntity.MODE_SHIFT, presetId = "нет-такого")
    assertNull(AlarmTimes.next(alarm, at(wed, 6, 0)))
  }

  @Test
  fun `next — произвольный цикл из cycleSpec перекрывает пресет`() {
    // Цикл 1/1 с побудкой в 9:00; presetId оставлен «2x2» (7:00) — должен игнорироваться.
    val spec = ShiftCycleCodec.encode(
      listOf(ShiftType("w", "Работа", LocalTime.of(9, 0)), ShiftType.off())
    )
    val alarm = AlarmEntity(
      mode = AlarmEntity.MODE_SHIFT,
      presetId = "2x2",
      cycleSpec = spec,
      anchorEpochDay = wed.toEpochDay()
    )
    val next = AlarmTimes.next(alarm, at(wed, 6, 0))
    assertEquals(at(wed, 9, 0), next) // из цикла, не из пресета (7:00)
  }

  @Test
  fun `next — пустой cycleSpec откатывается на пресет`() {
    val alarm = AlarmEntity(
      mode = AlarmEntity.MODE_SHIFT,
      presetId = "2x2",
      cycleSpec = "",
      anchorEpochDay = wed.toEpochDay()
    )
    val next = AlarmTimes.next(alarm, at(wed, 6, 0))
    assertEquals(at(wed, 7, 0), next) // пресет 2x2 в 7:00
  }

  @Test
  fun `next — точечная правка «выходной» глушит рабочий день смены`() {
    // Пресет 2x2 в 7:00: среда и четверг — рабочие. Сдаём среду (правка на выходной).
    val alarm = AlarmEntity(
      mode = AlarmEntity.MODE_SHIFT,
      presetId = "2x2",
      anchorEpochDay = wed.toEpochDay()
    )
    val ovr = ScheduleOverrides.DayOverride(wed, wed, ShiftType.off())
    val next = AlarmTimes.next(alarm, emptyList(), listOf(ovr), at(wed, 6, 0))
    assertEquals(at(wed.plusDays(1), 7, 0), next) // звонок переехал на четверг
  }

  // --- Производственный календарь (honorHolidays) ---

  @Test
  fun `next — смена с учётом праздников пропускает праздник и выходные`() {
    // Цикл «звонок каждый день 7:00», honorHolidays. 12 июня 2026 — праздник (Пт), 13-14 — выходные.
    val spec = ShiftCycleCodec.encode(listOf(ShiftType("w", "Работа", LocalTime.of(7, 0))))
    val alarm = AlarmEntity(
      mode = AlarmEntity.MODE_SHIFT,
      cycleSpec = spec,
      anchorEpochDay = LocalDate.of(2026, 6, 1).toEpochDay(),
      honorHolidays = true
    )
    val from = LocalDate.of(2026, 6, 11).atTime(8, 0) // четверг, звонок 7:00 прошёл
    val next = AlarmTimes.next(alarm, emptyList(), emptyList(), from)
    assertEquals(LocalDate.of(2026, 6, 15).atTime(7, 0), next) // понедельник
  }

  @Test
  fun `next — weekly с учётом праздников пропускает праздничную пятницу`() {
    val alarm = AlarmEntity(
      hour = 7, minute = 0,
      mode = AlarmEntity.MODE_WEEKLY,
      daysMask = AlarmTimes.maskOf(DayOfWeek.FRIDAY),
      honorHolidays = true
    )
    val from = LocalDate.of(2026, 6, 12).atTime(6, 0) // праздничная пятница, до 7:00
    val next = AlarmTimes.next(alarm, emptyList(), emptyList(), from)
    assertEquals(LocalDate.of(2026, 6, 19).atTime(7, 0), next) // следующая пятница (рабочая)
  }

  @Test
  fun `next — полярность REST будит в праздник и выходные`() {
    val alarm = AlarmEntity(
      hour = 9, minute = 0,
      mode = AlarmEntity.MODE_WEEKLY,
      daysMask = 0,
      honorHolidays = true,
      polarity = AlarmEntity.POLARITY_REST
    )
    val from = LocalDate.of(2026, 6, 11).atTime(12, 0) // четверг
    val next = AlarmTimes.next(alarm, emptyList(), emptyList(), from)
    assertEquals(LocalDate.of(2026, 6, 12).atTime(9, 0), next) // праздник = выходной → звонит
  }

  @Test
  fun `next — точечная правка «рабочий» будит в выходной смены`() {
    // Пятница по 2x2 — выходной; «отрабатываем» её в 8:00 точечной правкой.
    val alarm = AlarmEntity(
      mode = AlarmEntity.MODE_SHIFT,
      presetId = "2x2",
      anchorEpochDay = wed.toEpochDay()
    )
    val fri = wed.plusDays(2)
    val ovr = ScheduleOverrides.DayOverride(
      fri, fri, ShiftType("w2", "Отработка", LocalTime.of(8, 0), ShiftCategory.DAY)
    )
    // Отсчёт с четверга 8:00 (чт 7:00 уже прошёл) → ближайший звонок = пятница 8:00.
    val next = AlarmTimes.next(alarm, emptyList(), listOf(ovr), at(wed.plusDays(1), 8, 0))
    assertEquals(at(fri, 8, 0), next)
  }
}
