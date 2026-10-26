package ru.titeha.shiftalarm.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DAO пропущенных срабатываний `skipped_alarm_occurrences` (вариант Б из плана): составной ключ
 * (alarmId, triggerAtMillis), идемпотентная вставка, каскадное удаление при удалении будильника.
 *
 * `AlarmRepository` — тонкая обёртка над этим DAO (методы addSkipped/removeSkipped/isSkipped/
 * skippedFor/cleanupSkippedOlderThan), поэтому контракт покрыт здесь, на уровне хранилища.
 */
@RunWith(AndroidJUnit4::class)
class SkippedAlarmOccurrenceDaoTest {
  private lateinit var db: AppDatabase
  private lateinit var alarmDao: AlarmDao
  private lateinit var dao: SkippedAlarmOccurrenceDao

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    alarmDao = db.alarmDao()
    dao = db.skippedAlarmOccurrenceDao()
  }

  @After
  @Throws(IOException::class)
  fun tearDown() {
    db.close()
  }

  @Test
  fun insert_thenExists_forAlarmOrderedByTime() = runBlocking {
    val alarmId = insertAlarm("График")
    dao.insert(SkippedAlarmOccurrence(alarmId, 3_000L))
    dao.insert(SkippedAlarmOccurrence(alarmId, 1_000L))
    dao.insert(SkippedAlarmOccurrence(alarmId, 2_000L))

    assertTrue(dao.exists(alarmId, 2_000L))
    assertFalse(dao.exists(alarmId, 9_999L))
    assertFalse(dao.exists(alarmId + 1, 2_000L))

    assertEquals(listOf(1_000L, 2_000L, 3_000L), dao.forAlarm(alarmId).map { it.triggerAtMillis })
  }

  @Test
  fun insert_isIdempotent_onConflictIgnore() = runBlocking {
    val alarmId = insertAlarm("График")
    dao.insert(SkippedAlarmOccurrence(alarmId, 5_000L))
    dao.insert(SkippedAlarmOccurrence(alarmId, 5_000L))

    assertEquals(1, dao.forAlarm(alarmId).size)
  }

  @Test
  fun delete_removesExactOccurrence() = runBlocking {
    val alarmId = insertAlarm("График")
    dao.insert(SkippedAlarmOccurrence(alarmId, 5_000L))
    dao.insert(SkippedAlarmOccurrence(alarmId, 6_000L))

    dao.delete(alarmId, 5_000L)

    assertEquals(listOf(6_000L), dao.forAlarm(alarmId).map { it.triggerAtMillis })
    // Повторное удаление несуществующей записи не падает.
    dao.delete(alarmId, 5_000L)
    assertEquals(1, dao.forAlarm(alarmId).size)
  }

  @Test
  fun deleteForAlarm_removesOnlyThatAlarm() = runBlocking {
    val first = insertAlarm("Первый")
    val second = insertAlarm("Второй")
    dao.insert(SkippedAlarmOccurrence(first, 5_000L))
    dao.insert(SkippedAlarmOccurrence(second, 6_000L))

    dao.deleteForAlarm(first)

    assertTrue(dao.forAlarm(first).isEmpty())
    assertEquals(listOf(6_000L), dao.forAlarm(second).map { it.triggerAtMillis })
  }

  @Test
  fun deleteOlderThan_removesPastOccurrences() = runBlocking {
    val alarmId = insertAlarm("График")
    dao.insert(SkippedAlarmOccurrence(alarmId, 999L))
    dao.insert(SkippedAlarmOccurrence(alarmId, 1_000L))
    dao.insert(SkippedAlarmOccurrence(alarmId, 2_000L))

    // Граница: строго меньше cutoff (включительно не удаляется).
    dao.deleteOlderThan(1_000L)

    assertEquals(listOf(1_000L, 2_000L), dao.forAlarm(alarmId).map { it.triggerAtMillis })
  }

  @Test
  fun deleteAlarm_cascadesSkippedOccurrences() = runBlocking {
    val alarmId = insertAlarm("График")
    dao.insert(SkippedAlarmOccurrence(alarmId, 5_000L))

    alarmDao.deleteById(alarmId)

    assertTrue(dao.forAlarm(alarmId).isEmpty())
  }

  private suspend fun insertAlarm(label: String): Long =
    alarmDao.insert(AlarmEntity(label = label, enabled = true, hour = 7, minute = 0))
}
