package com.zaduzenja.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class NotificationsTest {
    private val zone: ZoneId = ZoneId.of("Europe/Belgrade")

    @Test
    fun `evening schedules next morning window`() {
        val now = ZonedDateTime.of(2026, 3, 16, 19, 31, 0, 0, zone)

        val window = nextNotificationWindow(now)

        assertEquals(
            ZonedDateTime.of(2026, 3, 17, 8, 0, 0, 0, zone).toInstant().toEpochMilli(),
            window.triggerAtMillis
        )
        assertEquals(Duration.ofMinutes(10).toMillis(), window.windowLengthMillis)
    }

    @Test
    fun `before morning schedules same day at eight`() {
        val now = ZonedDateTime.of(2026, 3, 16, 7, 15, 0, 0, zone)

        val window = nextNotificationWindow(now)

        assertEquals(
            ZonedDateTime.of(2026, 3, 16, 8, 0, 0, 0, zone).toInstant().toEpochMilli(),
            window.triggerAtMillis
        )
        assertEquals(Duration.ofMinutes(10).toMillis(), window.windowLengthMillis)
    }

    @Test
    fun `inside morning window schedules remaining time only`() {
        val now = ZonedDateTime.of(2026, 3, 16, 9, 30, 45, 0, zone)

        val window = nextNotificationWindow(now)

        assertEquals(
            ZonedDateTime.of(2026, 3, 16, 9, 31, 0, 0, zone).toInstant().toEpochMilli(),
            window.triggerAtMillis
        )
        assertEquals(Duration.ofMinutes(10).toMillis(), window.windowLengthMillis)
    }

    @Test
    fun `fallback window never escapes eleven`() {
        val now = ZonedDateTime.of(2026, 3, 16, 10, 55, 45, 0, zone)

        val window = nextNotificationWindow(now)

        assertEquals(
            ZonedDateTime.of(2026, 3, 16, 10, 56, 0, 0, zone).toInstant().toEpochMilli(),
            window.triggerAtMillis
        )
        assertEquals(Duration.ofMinutes(4).toMillis(), window.windowLengthMillis)
    }

    @Test
    fun `notification delivery is allowed only from eight until eleven`() {
        assertFalse(
            isAvailabilityNotificationWindowOpen(
                ZonedDateTime.of(2026, 3, 16, 7, 59, 59, 0, zone)
            )
        )
        assertTrue(
            isAvailabilityNotificationWindowOpen(
                ZonedDateTime.of(2026, 3, 16, 8, 0, 0, 0, zone)
            )
        )
        assertTrue(
            isAvailabilityNotificationWindowOpen(
                ZonedDateTime.of(2026, 3, 16, 10, 59, 59, 0, zone)
            )
        )
        assertFalse(
            isAvailabilityNotificationWindowOpen(
                ZonedDateTime.of(2026, 3, 16, 11, 0, 0, 0, zone)
            )
        )
    }

    @Test
    fun `manifest and scheduler support exact alarms`() {
        val manifest = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        ).first { it.exists() }.readText()
        val source = listOf(
            File("src/main/java/com/zaduzenja/app/Notifications.kt"),
            File("app/src/main/java/com/zaduzenja/app/Notifications.kt")
        ).first { it.exists() }.readText()

        assertTrue(manifest.contains("android.permission.SCHEDULE_EXACT_ALARM"))
        assertTrue(source.contains("setExactAndAllowWhileIdle"))
        assertTrue(source.contains("AVAILABILITY_FALLBACK_WINDOW_MILLIS"))
    }

    @Test
    fun `main activity warns about exact alarm permission on every startup`() {
        val source = listOf(
            File("src/main/java/com/zaduzenja/app/MainActivity.kt"),
            File("app/src/main/java/com/zaduzenja/app/MainActivity.kt")
        ).first { it.exists() }.readText()

        assertTrue(source.contains("override fun onResume()"))
        assertTrue(source.contains("requestExactAlarmPermissionIfNeeded()"))
        assertTrue(source.contains("ExactAlarmPermissionDialog("))
        assertTrue(source.contains("mutableStateOf(false)"))
        assertTrue(source.contains("Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM"))
        assertFalse(source.contains("AlertDialog.Builder(this)"))
        assertFalse(source.contains("exact_alarm_permission_asked"))
    }
}
