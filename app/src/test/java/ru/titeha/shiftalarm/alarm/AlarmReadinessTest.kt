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
  fun severity_batteryAndVolumeAreRecommendation_othersCritical() {
    assertEquals(AlarmReadinessSeverity.RECOMMENDATION, AlarmReadiness.severityOf(AlarmReadinessIssue.BATTERY))
    assertEquals(AlarmReadinessSeverity.RECOMMENDATION, AlarmReadiness.severityOf(AlarmReadinessIssue.ALARM_VOLUME))
    assertEquals(AlarmReadinessSeverity.CRITICAL, AlarmReadiness.severityOf(AlarmReadinessIssue.EXACT_ALARM))
    assertEquals(AlarmReadinessSeverity.CRITICAL, AlarmReadiness.severityOf(AlarmReadinessIssue.NOTIFICATIONS))
    assertEquals(AlarmReadinessSeverity.CRITICAL, AlarmReadiness.severityOf(AlarmReadinessIssue.FULL_SCREEN))
  }

  @Test
  fun alarmVolumeZero_reported() {
    assertEquals(
      listOf(AlarmReadinessIssue.ALARM_VOLUME),
      AlarmReadiness.issues(
        canScheduleExact = true, notificationsAllowed = true,
        fullScreenAllowed = true, batteryUnrestricted = true,
        alarmVolumeZero = true
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
