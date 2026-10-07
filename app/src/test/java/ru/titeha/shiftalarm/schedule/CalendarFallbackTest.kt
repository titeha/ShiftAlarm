package ru.titeha.shiftalarm.schedule

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.titeha.shiftalarm.data.AlarmEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Деградация производственного календаря: при включённом «Учитывать праздники», но отсутствии
 * данных на год, «По выходным» обязана звонить в Сб/Вс, а не превращаться в «По рабочим» (K1/E1).
 *
 * Год без данных во встроенном календаре — 2028 (есть RU-2026 и RU-2027). Даты: 14.01.2028 —
 * пятница, 15.01 — суббота, 17.01 — понедельник.
 */
class CalendarFallbackTest {

  @Before
  fun setUp() {
    ProductionCalendars.source = null
  }

  @After
  fun tearDown() {
    ProductionCalendars.source = null
  }

  private val from = LocalDate.of(2028, 1, 14).atTime(8, 0) // пятница
  private val sat = LocalDate.of(2028, 1, 15)
  private val mon = LocalDate.of(2028, 1, 17)

  private val weekdays = AlarmTimes.maskOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
  )

  private fun alarm(polarity: String, honor: Boolean, mask: Int) = AlarmEntity(
    hour = 7,
    minute = 0,
    mode = if (polarity == AlarmEntity.POLARITY_REST) AlarmEntity.MODE_CALENDAR else AlarmEntity.MODE_WEEKLY,
    daysMask = mask,
    honorHolidays = honor,
    polarity = polarity
  )

  @Test
  fun `нет данных на год — по выходным звонит в субботу, а не по маске будней`() {
    val a = alarm(AlarmEntity.POLARITY_REST, honor = true, mask = weekdays)
    assertEquals(sat.atTime(7, 0), AlarmTimes.next(a, from))
  }

  @Test
  fun `нет данных на год — по рабочим не меняется и звонит по маске`() {
    val a = alarm(AlarmEntity.POLARITY_WORK, honor = true, mask = weekdays)
    assertEquals(mon.atTime(7, 0), AlarmTimes.next(a, from))
  }

  @Test
  fun `calendar игнорирует выключенный тумблер — звонит по нерабочим (D-1)`() {
    // После TD03 REST существует только в режиме calendar, а он всегда использует календарь
    // (инвариант D-1), независимо от honorHolidays. Тумблер больше не «выключает» календарь
    // для этого режима — старое поведение «REST + honor=false → как WORK» недостижимо.
    val a = alarm(AlarmEntity.POLARITY_REST, honor = false, mask = weekdays)
    assertEquals(sat.atTime(7, 0), AlarmTimes.next(a, from))
  }

  @Test
  fun `разовый будильник не зависит от деградации календаря`() {
    val a = alarm(AlarmEntity.POLARITY_WORK, honor = true, mask = 0)
    assertEquals(sat.atTime(7, 0), AlarmTimes.next(a, from))
  }

  @Test
  fun `год с данными — поведение прежнее`() {
    val a = alarm(AlarmEntity.POLARITY_REST, honor = true, mask = weekdays)
    assertEquals(
      LocalDate.of(2026, 6, 27).atTime(7, 0),
      AlarmTimes.next(a, LocalDate.of(2026, 6, 24).atTime(8, 0))
    )
  }

  @Test
  fun `calendarFor — null при выключенном тумблере, пустой календарь при включённом без данных`() {
    assertNull(ProductionCalendars.calendarFor(honorHolidays = false, year = 2028))

    val fallback = requireNotNull(ProductionCalendars.calendarFor(honorHolidays = true, year = 2028))
    assertTrue(fallback.kinds.isEmpty())
    assertTrue(fallback.holidays.isEmpty())
    assertTrue(fallback.workingWeekends.isEmpty())

    // Год с данными: календарь не пуст — то есть проверки выше отличают деградацию от подстановки
    // календаря чужого года (проект такое запрещает).
    val known = requireNotNull(ProductionCalendars.calendarFor(honorHolidays = true, year = 2026))
    assertTrue(known.kinds.isNotEmpty())
  }

  @Test
  fun `hasData — различает встроенный год, год без данных и год из внешнего источника`() {
    assertTrue(ProductionCalendars.hasData(2026))
    assertFalse(ProductionCalendars.hasData(2028))

    ProductionCalendars.source = { _, year -> if (year == 2028) ProductionCalendar() else null }
    assertTrue(ProductionCalendars.hasData(2028))
    assertTrue(ProductionCalendars.hasData(2026))
  }

  @Test
  fun `пустой календарь эквивалентен null для сменного режима`() {
    val anchor = LocalDate.of(2028, 1, 1)
    val everyDay = ShiftSchedule(
      ShiftPattern(listOf(ShiftType("w", "Работа", LocalTime.of(7, 0))), anchor)
    )
    val fallback = requireNotNull(ProductionCalendars.calendarFor(honorHolidays = true, year = 2028))
    val from = anchor.atTime(0, 0)

    // Горизонт поиска совпадает: пустой календарь смены не глушит.
    assertEquals(
      ShiftEngine.nextAlarm(from, everyDay, calendar = null),
      ShiftEngine.nextAlarm(from, everyDay, calendar = fallback)
    )

    // И по каждому дню года: время звонка то же, что без календаря вовсе.
    var date = anchor
    while (date.year == 2028) {
      assertEquals(
        ShiftEngine.wakeTimeOn(date, everyDay, null),
        ShiftEngine.wakeTimeOn(date, everyDay, fallback)
      )
      date = date.plusDays(1)
    }
  }

  @Test
  fun `календарь участвует в расчёте — кроме разового будильника (D-1)`() {
    // Предикат решает, писать ли диагностику о деградации (F2 ревью TE2): разовому WORK-будильнику
    // календарь не нужен вовсе, а смене он нужен как фильтр праздников. Для calendar (REST) календарь
    // участвует всегда (D-1), даже при honor=false.
    assertTrue(AlarmTimes.usesProductionCalendar(alarm(AlarmEntity.POLARITY_REST, honor = true, mask = weekdays)))
    assertTrue(AlarmTimes.usesProductionCalendar(alarm(AlarmEntity.POLARITY_REST, honor = false, mask = weekdays)))
    assertTrue(AlarmTimes.usesProductionCalendar(alarm(AlarmEntity.POLARITY_REST, honor = true, mask = 0)))
    assertTrue(AlarmTimes.usesProductionCalendar(alarm(AlarmEntity.POLARITY_WORK, honor = true, mask = weekdays)))
    assertTrue(
      AlarmTimes.usesProductionCalendar(
        AlarmEntity(
          mode = AlarmEntity.MODE_SHIFT,
          presetId = "2x2",
          anchorEpochDay = mon.toEpochDay(),
          daysMask = 0,
          honorHolidays = true
        )
      )
    )

    assertFalse(AlarmTimes.usesProductionCalendar(alarm(AlarmEntity.POLARITY_WORK, honor = true, mask = 0)))
    assertFalse(AlarmTimes.usesProductionCalendar(alarm(AlarmEntity.POLARITY_WORK, honor = false, mask = weekdays)))
  }

  @Test
  fun `предикат участия календаря совпадает с чувствительностью расчёта`() {
    // Связывающий тест (N7 и F-6 повторных ревью): недостаточно проверить сам предикат — он должен
    // совпадать с тем, меняет ли появление данных на год результат расчёта. Меняем два дня:
    // нерабочий понедельник (глушит недельный рабочий день и смену) и рабочую субботу (глушит «по выходным»).
    val oneShot = alarm(AlarmEntity.POLARITY_WORK, honor = true, mask = 0)
    val weekly = alarm(AlarmEntity.POLARITY_WORK, honor = true, mask = weekdays)
    val restOneShot = alarm(AlarmEntity.POLARITY_REST, honor = true, mask = 0)
    val shift = AlarmEntity(
      mode = AlarmEntity.MODE_SHIFT,
      cycleSpec = ShiftCycleCodec.encode(listOf(ShiftType("w", "Работа", LocalTime.of(7, 0)))),
      anchorEpochDay = sat.toEpochDay(),
      honorHolidays = true
    )
    val shiftFrom = LocalDate.of(2028, 1, 16).atTime(8, 0) // воскресенье

    // Без данных на год — расчёт по личной неделе, по Сб/Вс и по циклу смен.
    assertFalse(AlarmTimes.usesProductionCalendar(oneShot))
    assertTrue(AlarmTimes.usesProductionCalendar(weekly))
    assertTrue(AlarmTimes.usesProductionCalendar(restOneShot))
    assertTrue(AlarmTimes.usesProductionCalendar(shift))

    assertEquals(sat.atTime(7, 0), AlarmTimes.next(oneShot, from))
    assertEquals(mon.atTime(7, 0), AlarmTimes.next(weekly, from))
    assertEquals(sat.atTime(7, 0), AlarmTimes.next(restOneShot, from))
    assertEquals(mon.atTime(7, 0), AlarmTimes.next(shift, shiftFrom))

    // Появляются данные на 2028: нерабочий праздник в понедельник (глушит недельный рабочий день и смену).
    ProductionCalendars.source = { _, year ->
      if (year == 2028) ProductionCalendar(kinds = mapOf(mon to StateDayKind.HOLIDAY)) else null
    }
    assertTrue(ProductionCalendars.hasData(2028))

    // Разовый будильник календарь не читает — результат прежний; у остальных звонок сдвигается.
    assertEquals(sat.atTime(7, 0), AlarmTimes.next(oneShot, from))
    assertEquals(mon.plusDays(1).atTime(7, 0), AlarmTimes.next(weekly, from))
    assertEquals(mon.plusDays(1).atTime(7, 0), AlarmTimes.next(shift, shiftFrom))

    // Добавляется рабочая суббота: «по выходным» перестаёт звонить в субботу — проверяем на REST
    // (у WORK-будильника со стандартной неделей рабочая суббота, наоборот, становится рабочим днём).
    ProductionCalendars.source = { _, year ->
      if (year == 2028) {
        ProductionCalendar(
          kinds = mapOf(
            mon to StateDayKind.HOLIDAY,
            sat to StateDayKind.TRANSFER_WORK
          )
        )
      } else null
    }
    assertEquals(sat.plusDays(1).atTime(7, 0), AlarmTimes.next(restOneShot, from))

    // Неизвестный режим нормализуется в weekly тем же effectiveMode, что и в предикате.
    assertTrue(AlarmTimes.usesProductionCalendar(weekly.copy(mode = "weekly_v2")))
  }

  @Test
  fun `точка календаря согласована с расчётом в год без данных`() {
    val a = alarm(AlarmEntity.POLARITY_REST, honor = true, mask = weekdays)
    val cal = requireNotNull(ProductionCalendars.calendarFor(honorHolidays = true, year = 2028))
    assertTrue(AlarmTimes.firesOn(a, sat, cal))
    assertFalse(AlarmTimes.firesOn(a, mon, cal))
  }

  @Test
  fun `граница года — данных на следующий год нет, событие деградации срабатывает`() {
    // 31.12.2027 — пятница (нерабочая по переносу с вс 03.01.2027 — данные на 2027 есть), но
    // звонок 07:00 уже «прошёл» относительно отсчёта 08:00, поэтому горизонт поиска уходит
    // в 2028 год БЕЗ данных: 01.01.2028 (суббота) трактуется как обычный выходной — ближайший
    // звонок. Это сценарий находки F4 ревью TE1: условие записи события должно смотреть
    // на год ЗВОНКА, а не на год отсчёта.
    val a = alarm(AlarmEntity.POLARITY_REST, honor = true, mask = weekdays)
    val next = requireNotNull(AlarmTimes.next(a, LocalDate.of(2027, 12, 31).atTime(8, 0)))
    assertEquals(LocalDate.of(2028, 1, 1).atTime(7, 0), next)
    assertFalse(ProductionCalendars.hasData(next.year))
  }
}
