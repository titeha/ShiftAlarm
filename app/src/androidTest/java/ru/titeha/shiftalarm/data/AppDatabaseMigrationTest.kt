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
}
