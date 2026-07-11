package ru.titeha.shiftalarm.alarm

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmReadinessTest {

  @Test
  fun allGranted_noIssues() {
    assertEquals(
      emptyList<AlarmReadinessIssue>(),
      AlarmReadiness.issues(
        canScheduleExact = true, notificationsAllowed = true,
        fullScreenAllowed = true, batteryUnrestricted = true
      )
    )
  }

  @Test
  fun allMissing_reportedInPriorityOrder() {
    val issues = AlarmReadiness.issues(
      canScheduleExact = false, notificationsAllowed = false,
      fullScreenAllowed = false, batteryUnrestricted = false
    )
    assertEquals(
      listOf(
        AlarmReadinessIssue.EXACT_ALARM,
        AlarmReadinessIssue.NOTIFICATIONS,
        AlarmReadinessIssue.FULL_SCREEN,
        AlarmReadinessIssue.BATTERY
      ),
      issues
    )
  }

  @Test
  fun batteryNull_notReported() {
    assertEquals(
      emptyList<AlarmReadinessIssue>(),
      AlarmReadiness.issues(
        canScheduleExact = true, notificationsAllowed = true,
        fullScreenAllowed = true, batteryUnrestricted = null
      )
    )
  }

  @Test
  fun onlyFullScreen_missing() {
    assertEquals(
      listOf(AlarmReadinessIssue.FULL_SCREEN),
      AlarmReadiness.issues(
        canScheduleExact = true, notificationsAllowed = true,
        fullScreenAllowed = false, batteryUnrestricted = true
      )
    )
  }
}
