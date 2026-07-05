package ru.titeha.shiftalarm.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Тест миграции БД 1→2 (фича «отпуск»). Инструментальный — нужен реальный SQLite,
 * поэтому гоняется на устройстве: `./gradlew connectedDebugAndroidTest`.
 *
 * Проверяет: данные старого будильника переживают апгрейд, появляется колонка
 * `freezeCycleDuringOff` (с дефолтом 0) и рабочая таблица `alarm_periods`.
 * Конечную схему [MigrationTestHelper.runMigrationsAndValidate] сверяет с эталоном
 * `app/schemas/.../2.json` — если SQL миграции разойдётся со схемой Room, тест упадёт.
 *
 * `1.json` восстановлен вручную: экспорт схем включили только начиная с v2.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

  private val dbName = "migration-test"

  @get:Rule
  val helper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    AppDatabase::class.java
  )

  @Test
  fun migrate1To2_preservesAlarms_andAddsPeriods() {
    // v1: один будильник-смена (колонки freezeCycleDuringOff ещё нет).
    helper.createDatabase(dbName, 1).apply {
      execSQL(
        "INSERT INTO alarms " +
          "(label, enabled, hour, minute, mode, daysMask, presetId, anchorEpochDay, deleteAfterFiring) " +
          "VALUES ('Смена', 1, 7, 30, 'shift', 0, '2x2', 20000, 0)"
      )
      close()
    }

    // Прогон миграции + валидация конечной схемы против 2.json.
    val db = helper.runMigrationsAndValidate(dbName, 2, true, AppDatabase.MIGRATION_1_2)

    // Старые данные на месте, новая колонка получила дефолт 0.
    db.query("SELECT label, hour, minute, freezeCycleDuringOff FROM alarms").use { c ->
      assertTrue("ожидалась сохранённая строка будильника", c.moveToFirst())
      assertEquals("Смена", c.getString(0))
      assertEquals(7, c.getInt(1))
      assertEquals(30, c.getInt(2))
      assertEquals(0, c.getInt(3))
    }

    // Новая таблица периодов существует и принимает запись, привязанную к будильнику.
    db.execSQL(
      "INSERT INTO alarm_periods (alarmId, fromEpochDay, toEpochDay, reason) " +
        "SELECT id, 20100, 20110, 'Отпуск' FROM alarms LIMIT 1"
    )
    db.query("SELECT COUNT(*) FROM alarm_periods").use { c ->
      assertTrue(c.moveToFirst())
      assertEquals(1, c.getInt(0))
    }
    db.close()
  }

  @Test
  fun migrate2To3_addsCycleSpec_andPreservesData() {
    // v2: будильник-смена (колонки cycleSpec ещё нет).
    helper.createDatabase(dbName, 2).apply {
      execSQL(
        "INSERT INTO alarms " +
          "(label, enabled, hour, minute, mode, daysMask, presetId, anchorEpochDay, " +
          "deleteAfterFiring, freezeCycleDuringOff) " +
          "VALUES ('Смена', 1, 7, 0, 'shift', 0, '2x2', 20000, 0, 0)"
      )
      close()
    }

    val db = helper.runMigrationsAndValidate(dbName, 3, true, AppDatabase.MIGRATION_2_3)

    // Данные на месте, новая колонка существует и по умолчанию NULL (= использовать пресет).
    db.query("SELECT label, presetId, cycleSpec FROM alarms").use { c ->
      assertTrue(c.moveToFirst())
      assertEquals("Смена", c.getString(0))
      assertEquals("2x2", c.getString(1))
      assertTrue("cycleSpec должен быть NULL после миграции", c.isNull(2))
    }
    db.close()
  }

  @Test
  fun migrate3To4_addsOverrides_andPreservesData() {
    // v3: будильник-смена (таблицы alarm_overrides ещё нет).
    helper.createDatabase(dbName, 3).apply {
      execSQL(
        "INSERT INTO alarms " +
          "(label, enabled, hour, minute, mode, daysMask, presetId, anchorEpochDay, " +
          "deleteAfterFiring, freezeCycleDuringOff, cycleSpec) " +
          "VALUES ('Смена', 1, 7, 0, 'shift', 0, '2x2', 20000, 0, 0, NULL)"
      )
      close()
    }

    val db = helper.runMigrationsAndValidate(dbName, 4, true, AppDatabase.MIGRATION_3_4)

    // Старый будильник на месте.
    db.query("SELECT label FROM alarms").use { c ->
      assertTrue(c.moveToFirst())
      assertEquals("Смена", c.getString(0))
    }

    // Новая таблица правок существует и принимает запись, привязанную к будильнику.
    db.execSQL(
      "INSERT INTO alarm_overrides (alarmId, fromEpochDay, toEpochDay, category, wakeMinutes, name) " +
        "SELECT id, 20050, 20050, 'NIGHT', 1260, 'Ночь' FROM alarms LIMIT 1"
    )
    db.query("SELECT category, wakeMinutes FROM alarm_overrides").use { c ->
      assertTrue(c.moveToFirst())
      assertEquals("NIGHT", c.getString(0))
      assertEquals(1260, c.getInt(1))
    }
    db.close()
  }

  @Test
  fun migrate4To5_addsHolidayColumns_withDefaults() {
    // v4: будильник-смена (колонок honorHolidays/polarity ещё нет).
    helper.createDatabase(dbName, 4).apply {
      execSQL(
        "INSERT INTO alarms " +
          "(label, enabled, hour, minute, mode, daysMask, presetId, anchorEpochDay, " +
          "deleteAfterFiring, freezeCycleDuringOff, cycleSpec) " +
          "VALUES ('Смена', 1, 7, 0, 'shift', 0, '2x2', 20000, 0, 0, NULL)"
      )
      close()
    }

    val db = helper.runMigrationsAndValidate(dbName, 5, true, AppDatabase.MIGRATION_4_5)

    // Данные на месте, новые колонки получили дефолты (0 и 'WORK').
    db.query("SELECT label, honorHolidays, polarity FROM alarms").use { c ->
      assertTrue(c.moveToFirst())
      assertEquals("Смена", c.getString(0))
      assertEquals(0, c.getInt(1))
      assertEquals("WORK", c.getString(2))
    }
    db.close()
  }
}
