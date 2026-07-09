package ru.titeha.shiftalarm.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.titeha.shiftalarm.schedule.ShiftCategory

@RunWith(AndroidJUnit4::class)
class AlarmDaoRelationTest {
  private lateinit var db: AppDatabase
  private lateinit var alarmDao: AlarmDao
  private lateinit var periodDao: AlarmPeriodDao
  private lateinit var overrideDao: AlarmOverrideDao

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    db = Room.inMemoryDatabaseBuilder(
      context,
      AppDatabase::class.java
    )
      .allowMainThreadQueries()
      .build()

    alarmDao = db.alarmDao()
    periodDao = db.alarmPeriodDao()
    overrideDao = db.alarmOverrideDao()
  }

  @After
  @Throws(IOException::class)
  fun tearDown() {
    db.close()
  }

  @Test
  fun updateAlarm_keepsPeriodsAndOverrides() = runBlocking {
    val alarmId = alarmDao.insert(
      AlarmEntity(
        label = "График",
        enabled = true,
        hour = 7,
        minute = 0,
        mode = AlarmEntity.MODE_SHIFT
      )
    )

    periodDao.upsert(
      AlarmPeriod(
        alarmId = alarmId,
        fromEpochDay = 20,
        toEpochDay = 22,
        reason = "Отгул"
      )
    )

    overrideDao.upsert(
      AlarmOverride(
        alarmId = alarmId,
        fromEpochDay = 25,
        toEpochDay = 25,
        category = ShiftCategory.OFF.name,
        wakeMinutes = null,
        name = "Выходной"
      )
    )

    val original = alarmDao.byId(alarmId)!!
    alarmDao.update(
      original.copy(
        label = "Обновлённый график",
        enabled = false
      )
    )

    val updated = alarmDao.byId(alarmId)!!
    val periods = periodDao.forAlarm(alarmId)
    val overrides = overrideDao.forAlarm(alarmId)

    assertEquals("Обновлённый график", updated.label)
    assertEquals(false, updated.enabled)

    assertEquals(1, periods.size)
    assertEquals("Отгул", periods.single().reason)

    assertEquals(1, overrides.size)
    assertEquals(ShiftCategory.OFF.name, overrides.single().category)
  }

  @Test
  fun deleteAlarm_removesPeriodsAndOverridesByCascade() = runBlocking {
    val alarmId = alarmDao.insert(
      AlarmEntity(
        label = "График",
        enabled = true,
        hour = 7,
        minute = 0,
        mode = AlarmEntity.MODE_SHIFT
      )
    )

    periodDao.upsert(
      AlarmPeriod(
        alarmId = alarmId,
        fromEpochDay = 20,
        toEpochDay = 22,
        reason = "Отгул"
      )
    )

    overrideDao.upsert(
      AlarmOverride(
        alarmId = alarmId,
        fromEpochDay = 25,
        toEpochDay = 25,
        category = ShiftCategory.OFF.name,
        wakeMinutes = null,
        name = "Выходной"
      )
    )

    alarmDao.deleteById(alarmId)

    assertTrue(periodDao.forAlarm(alarmId).isEmpty())
    assertTrue(overrideDao.forAlarm(alarmId).isEmpty())
  }
}