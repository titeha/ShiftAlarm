package ru.titeha.shiftalarm.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.titeha.shiftalarm.data.AlarmEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Пропуск текущего звонка из плашки PreRing (вариант Б): чистая логика [AlarmTimes.nextUnskipped]
 * — пропускает срабатывания, помеченные в skip-store, и считает следующее.
 */
class AlarmTimesSkipTest {

  // 2026-06-24 — среда.
  private val wed = LocalDate.of(2026, 6, 24)
  private fun at(date: LocalDate, h: Int, m: Int) = date.atTime(h, m)

  private val everyDay = AlarmTimes.maskOf(*DayOfWeek.entries.toTypedArray())

  private fun weekly(mask: Int = everyDay) =
    AlarmEntity(mode = AlarmEntity.MODE_WEEKLY, daysMask = mask, hour = 7, minute = 0)

  private fun skippedMillis(vararg times: LocalDateTime): Set<Long> =
    times.map { AlarmInstant.epochMilli(it) }.toSet()

  @Test
  fun `без пропусков возвращает ближайшее срабатывание`() {
    val next = AlarmTimes.nextUnskipped(weekly(), emptyList(), emptyList(), at(wed, 6, 0))
    assertEquals(at(wed, 7, 0), next)
  }

  @Test
  fun `пропущенное ближайшее срабатывание сдвигает на следующее`() {
    val next = AlarmTimes.nextUnskipped(
      weekly(), emptyList(), emptyList(), at(wed, 6, 0),
      skippedMillis = skippedMillis(at(wed, 7, 0))
    )
    assertEquals(at(wed.plusDays(1), 7, 0), next)
  }

  @Test
  fun `несколько подряд пропущенных срабатываний пропускаются`() {
    val next = AlarmTimes.nextUnskipped(
      weekly(), emptyList(), emptyList(), at(wed, 6, 0),
      skippedMillis = skippedMillis(at(wed, 7, 0), at(wed.plusDays(1), 7, 0))
    )
    assertEquals(at(wed.plusDays(2), 7, 0), next)
  }

  @Test
  fun `пропуск времени, не совпадающего со срабатыванием, не влияет`() {
    val next = AlarmTimes.nextUnskipped(
      weekly(), emptyList(), emptyList(), at(wed, 6, 0),
      skippedMillis = skippedMillis(at(wed, 8, 0))
    )
    assertEquals(at(wed, 7, 0), next)
  }

  @Test
  fun `разовый без пропуска возвращает единственное срабатывание`() {
    val next = AlarmTimes.nextUnskipped(weekly(mask = 0), emptyList(), emptyList(), at(wed, 6, 0))
    assertEquals(at(wed, 7, 0), next)
  }

  @Test
  fun `разовый при пропуске единственного срабатывания даёт null`() {
    val next = AlarmTimes.nextUnskipped(
      weekly(mask = 0), emptyList(), emptyList(), at(wed, 6, 0),
      skippedMillis = skippedMillis(at(wed, 7, 0))
    )
    assertNull(next)
  }

  @Test
  fun `сменный будильник пропускает помеченную смену`() {
    // 2x2 в 7:00, опора = среда (рабочий, как и четверг).
    val alarm = AlarmEntity(mode = AlarmEntity.MODE_SHIFT, presetId = "2x2", anchorEpochDay = wed.toEpochDay())
    val next = AlarmTimes.nextUnskipped(
      alarm, emptyList(), emptyList(), at(wed, 6, 0),
      skippedMillis = skippedMillis(at(wed, 7, 0))
    )
    assertEquals(at(wed.plusDays(1), 7, 0), next)
  }
}
