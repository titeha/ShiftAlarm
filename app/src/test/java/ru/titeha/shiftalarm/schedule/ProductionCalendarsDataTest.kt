package ru.titeha.shiftalarm.schedule

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Наличие данных производственного календаря по годам — основа сигнала о деградации (K13/TE2). */
class ProductionCalendarsDataTest {

  @Before
  fun setUp() {
    ProductionCalendars.source = null
  }

  @After
  fun tearDown() {
    ProductionCalendars.source = null
  }

  @Test
  fun `данные есть на встроенные годы и нет на следующий за ними`() {
    assertTrue(ProductionCalendars.hasData(2026))
    assertTrue(ProductionCalendars.hasData(2027))
    assertFalse(ProductionCalendars.hasData(2028))
  }

  @Test
  fun `внешний источник добавляет данные на год`() {
    ProductionCalendars.source = { _, year -> if (year == 2028) ProductionCalendar() else null }
    assertTrue(ProductionCalendars.hasData(2028))
    assertTrue(ProductionCalendars.hasData(2027))
  }
}
