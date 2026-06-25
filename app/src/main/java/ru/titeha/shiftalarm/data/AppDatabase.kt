package ru.titeha.shiftalarm.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [AlarmEntity::class, AlarmPeriod::class],
  version = 2,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

  abstract fun alarmDao(): AlarmDao
  abstract fun alarmPeriodDao(): AlarmPeriodDao

  companion object {
    @Volatile private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase =
      instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "shiftalarm.db"
        )
          // TODO(перед публичным релизом): заменить на нормальную миграцию 1→2
          // с инструментальным тестом. Сейчас приложение предрелизное — допускаем
          // пересоздание БД при смене схемы (теряются лишь тестовые будильники).
          .fallbackToDestructiveMigration(dropAllTables = true)
          .build().also { instance = it }
      }
  }
}
