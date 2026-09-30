package com.zaduzenja.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ShiftNotificationScheduleTest {
    private val referenceDate = LocalDate.of(2026, 3, 16)

    @Test
    fun firstShiftNotificationsFollowThreeWeekCycle() {
        assertFalse(ShiftNotificationSchedule.isFirstShiftWeekday(LocalDate.of(2026, 3, 9), referenceDate))
        assertTrue(ShiftNotificationSchedule.isFirstShiftWeekday(LocalDate.of(2026, 3, 16), referenceDate))
        assertTrue(ShiftNotificationSchedule.isFirstShiftWeekday(LocalDate.of(2026, 3, 20), referenceDate))
        assertFalse(ShiftNotificationSchedule.isFirstShiftWeekday(LocalDate.of(2026, 3, 21), referenceDate))
        assertFalse(ShiftNotificationSchedule.isFirstShiftWeekday(LocalDate.of(2026, 3, 23), referenceDate))
        assertTrue(ShiftNotificationSchedule.isFirstShiftWeekday(LocalDate.of(2026, 4, 6), referenceDate))
    }

    @Test
    fun lastFirstShiftWeekIsDetectedPerMonth() {
        assertTrue(ShiftNotificationSchedule.isLastFirstShiftWeekOfMonth(LocalDate.of(2026, 3, 16), referenceDate))
        assertFalse(ShiftNotificationSchedule.isLastFirstShiftWeekOfMonth(LocalDate.of(2026, 4, 6), referenceDate))
        assertTrue(ShiftNotificationSchedule.isLastFirstShiftWeekOfMonth(LocalDate.of(2026, 4, 27), referenceDate))
    }

    @Test
    fun customReferenceDateChangesFirstShiftWeeks() {
        val customReferenceDate = LocalDate.of(2026, 4, 27)

        assertTrue(ShiftNotificationSchedule.isFirstShiftWeekday(LocalDate.of(2026, 4, 27), customReferenceDate))
        assertFalse(ShiftNotificationSchedule.isFirstShiftWeekday(LocalDate.of(2026, 5, 4), customReferenceDate))
        assertFalse(ShiftNotificationSchedule.isFirstShiftWeekday(LocalDate.of(2026, 5, 11), customReferenceDate))
        assertTrue(ShiftNotificationSchedule.isFirstShiftWeekday(LocalDate.of(2026, 5, 18), customReferenceDate))
    }
}
