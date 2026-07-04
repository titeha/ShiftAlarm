package ru.titeha.shiftalarm.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * База приложения (Room).
 *
 * Версии схемы экспортируются в `app/schemas/` (`exportSchema = true`) — по ним пишутся
 * миграции и тест миграции. Переходы регистрируются через [addMigrations]; пересоздания
 * БД (`fallbackToDestructiveMigration`) нет — данные пользователя сохраняются при апгрейде.
 *
 * История версий:
 *  - v1 — таблица `alarms`.
 *  - v2 — фича «отпуск»: колонка `alarms.freezeCycleDuringOff` + таблица `alarm_periods`
 *    (см. [MIGRATION_1_2]).
 *  - v3 — редактор смен: колонка `alarms.cycleSpec` (произвольный цикл, см. [MIGRATION_2_3]).
 *  - v4 — интерактивный календарь: таблица `alarm_overrides` (пер-дневные правки/подмены,
 *    см. [MIGRATION_3_4]).
 */
@Database(
  entities = [AlarmEntity::class, AlarmPeriod::class, AlarmOverride::class],
  version = 4,
  exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

  abstract fun alarmDao(): AlarmDao
  abstract fun alarmPeriodDao(): AlarmPeriodDao
  abstract fun alarmOverrideDao(): AlarmOverrideDao

  companion object {
    @Volatile private var instance: AppDatabase? = null

    /**
     * Миграция 1→2 (фича «отпуск»): добавлена колонка цикла и таблица периодов.
     *
     * SQL таблицы/индекса скопирован дословно из эталонной схемы Room
     * (`app/schemas/.../2.json`, поле `createSql`) — иначе хэш схемы разойдётся
     * и Room упадёт на старте. Проверяется инструментальным тестом
     * `AppDatabaseMigrationTest` через `MigrationTestHelper.runMigrationsAndValidate`.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        // alarms: новая колонка режима цикла во время периодов без будильника.
        // DEFAULT 0 нужен только для существующих строк при ALTER; в схеме Room
        // колонка без default — на валидацию это не влияет (проверяются тип/NOT NULL).
        db.execSQL(
          "ALTER TABLE `alarms` ADD COLUMN `freezeCycleDuringOff` INTEGER NOT NULL DEFAULT 0"
        )
        // Новая таблица периодов отпуска + индекс по alarmId (FK на alarms, CASCADE).
        db.execSQL(
          "CREATE TABLE IF NOT EXISTS `alarm_periods` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`alarmId` INTEGER NOT NULL, " +
            "`fromEpochDay` INTEGER NOT NULL, " +
            "`toEpochDay` INTEGER NOT NULL, " +
            "`reason` TEXT NOT NULL, " +
            "FOREIGN KEY(`alarmId`) REFERENCES `alarms`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS `index_alarm_periods_alarmId` " +
            "ON `alarm_periods` (`alarmId`)"
        )
      }
    }

    /**
     * Миграция 2→3 (редактор смен): добавлена nullable-колонка `cycleSpec` для произвольного
     * цикла. Простой ALTER без дефолта (колонка допускает NULL = «использовать пресет»).
     * Проверяется в `AppDatabaseMigrationTest`.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `alarms` ADD COLUMN `cycleSpec` TEXT")
      }
    }

    /**
     * Миграция 3→4 (интерактивный календарь): таблица `alarm_overrides` с пер-дневными правками
     * (подмены/исключения). SQL таблицы/индекса скопирован дословно из `app/schemas/.../4.json`
     * (поле `createSql`) — иначе хэш схемы разойдётся и Room упадёт на старте. Проверяется в
     * `AppDatabaseMigrationTest`.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          "CREATE TABLE IF NOT EXISTS `alarm_overrides` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`alarmId` INTEGER NOT NULL, " +
            "`fromEpochDay` INTEGER NOT NULL, " +
            "`toEpochDay` INTEGER NOT NULL, " +
            "`category` TEXT NOT NULL, " +
            "`wakeMinutes` INTEGER, " +
            "`name` TEXT NOT NULL, " +
            "FOREIGN KEY(`alarmId`) REFERENCES `alarms`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS `index_alarm_overrides_alarmId` " +
            "ON `alarm_overrides` (`alarmId`)"
        )
      }
    }

    fun get(context: Context): AppDatabase =
      instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "shiftalarm.db"
        )
          .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
          .build().also { instance = it }
      }
  }
}
