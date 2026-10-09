package ru.titeha.shiftalarm.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.DayOfWeek

/**
 * Пресеты рабочей недели и защитное построение [WorkWeek]. Рабочая неделя — не маска будильника
 * (`docs/WORK_WEEK_AND_STUDY.md` §1), поэтому проверяются и наборы дней, и откаты на повреждённых
 * данных.
 */
class WorkWeekPresetsTest {

  private fun days(vararg d: DayOfWeek): Set<DayOfWeek> = d.toSet()

  @Test
  fun `пресеты дают ожидаемые наборы дней`() {
    assertEquals(
      days(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
      ),
      WorkWeekPreset.FIVE_DAY_MON_FRI.days
    )
    assertEquals(
      days(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
      ),
      WorkWeekPreset.SIX_DAY_MON_SAT.days
    )
    assertEquals(
      days(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY),
      WorkWeekPreset.FOUR_DAY_MON_THU.days
    )
    assertEquals(emptySet<DayOfWeek>(), WorkWeekPreset.CUSTOM.days)
  }

  @Test
  fun `пресет по умолчанию совпадает со стандартной неделей страны`() {
    assertEquals(WorkWeekPreset.FIVE_DAY_MON_FRI, WorkWeekPreset.DEFAULT)
    assertEquals(CountryProfile.RU.standardWeek, WorkWeek(WorkWeekPreset.DEFAULT.days))
  }

  @Test
  fun `шестидневка отличается от стандартной недели — переносы к ней неприменимы`() {
    assertNotEquals(CountryProfile.RU.standardWeek, WorkWeek(WorkWeekPreset.SIX_DAY_MON_SAT.days))
  }

  @Test
  fun `свой набор строится из маски в формате будильника`() {
    val mask = AlarmTimes.maskOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)
    assertEquals(
      WorkWeek(days(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)),
      workWeekOf(WorkWeekPreset.CUSTOM, mask)
    )
  }

  @Test
  fun `свой набор с пустой маской — откат на неделю по умолчанию`() {
    assertEquals(WorkWeek(WorkWeekPreset.DEFAULT.days), workWeekOf(WorkWeekPreset.CUSTOM, 0))
  }

  @Test
  fun `неизвестное или пустое имя пресета — откат на значение по умолчанию`() {
    assertEquals(WorkWeekPreset.DEFAULT, WorkWeekPreset.byName("SIX_DAY_MON_SAT_WRONG"))
    assertEquals(WorkWeekPreset.DEFAULT, WorkWeekPreset.byName(""))
    assertEquals(WorkWeekPreset.DEFAULT, WorkWeekPreset.byName(null))
    assertEquals(WorkWeekPreset.SIX_DAY_MON_SAT, WorkWeekPreset.byName(" SIX_DAY_MON_SAT "))
  }
}
