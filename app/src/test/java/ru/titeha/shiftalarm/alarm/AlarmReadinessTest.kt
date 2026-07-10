package ru.titeha.shiftalarm.alarm

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmReadinessTest {

  @Test
  fun allGranted_noIssues() {
    assertEquals(
      emptyList<AlarmReadinessIssue>(),
      AlarmReadiness.issues(canScheduleExact = true, notificationsAllowed = true, batteryUnrestricted = true)
    )
  }

  @Test
  fun exactAlarmMissing_reportedFirst() {
    val issues = AlarmReadiness.issues(false, notificationsAllowed = false, batteryUnrestricted = false)
    assertEquals(
      listOf(
        AlarmReadinessIssue.EXACT_ALARM,
        AlarmReadinessIssue.NOTIFICATIONS,
        AlarmReadinessIssue.BATTERY
      ),
      issues
    )
  }

  @Test
  fun batteryNull_notReported() {
    assertEquals(
      emptyList<AlarmReadinessIssue>(),
      AlarmReadiness.issues(canScheduleExact = true, notificationsAllowed = true, batteryUnrestricted = null)
    )
  }

  @Test
  fun onlyNotifications_missing() {
    assertEquals(
      listOf(AlarmReadinessIssue.NOTIFICATIONS),
      AlarmReadiness.issues(canScheduleExact = true, notificationsAllowed = false, batteryUnrestricted = true)
    )
  }
}
