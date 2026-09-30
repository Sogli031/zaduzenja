package com.zaduzenja.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import com.zaduzenja.app.data.db.AppDatabase
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

private const val AVAILABILITY_CHANNEL_ID = "availability"
private const val AVAILABILITY_CHANNEL_NAME = "Dostupna zaduženja"
private const val AVAILABILITY_ALARM_ACTION = "com.zaduzenja.app.action.AVAILABILITY_ALARM"
private const val AVAILABILITY_WORK_NAME = "daily_availability_check"
private const val AVAILABILITY_NOTIFICATION_ID = 1001
private const val TEST_NOTIFICATION_ID = 1002
private const val SCHEDULE_WINDOW_START_HOUR = 8
private const val SCHEDULE_WINDOW_END_HOUR = 11
private const val AVAILABILITY_FALLBACK_WINDOW_MILLIS = 10 * 60 * 1000L
private const val PREFS_NAME = "zaduzenja_prefs"
private const val FIRST_SHIFT_REFERENCE_DATE_KEY = "first_shift_reference_date"
private const val LAST_NOTIFICATION_DATE_KEY = "last_availability_notification_date"
private val DEFAULT_REFERENCE_FIRST_SHIFT_MONDAY = LocalDate.of(2026, 3, 16)

internal object ShiftNotificationSchedule {
    private val weeklyShiftCycle = listOf(3, 2, 1)
    private const val firstShift = 1
    private val referenceShiftIndex = weeklyShiftCycle.indexOf(firstShift)

    fun isFirstShiftWeekday(date: LocalDate, referenceFirstShiftMonday: LocalDate): Boolean {
        if (date.dayOfWeek.value > DayOfWeek.FRIDAY.value) {
            return false
        }

        val currentWeekMonday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weeksDiff = ChronoUnit.WEEKS.between(referenceFirstShiftMonday, currentWeekMonday).toInt()
        val cycleIndex = Math.floorMod(referenceShiftIndex + weeksDiff, weeklyShiftCycle.size)
        return weeklyShiftCycle[cycleIndex] == firstShift
    }

    fun isLastFirstShiftWeekOfMonth(date: LocalDate, referenceFirstShiftMonday: LocalDate): Boolean {
        val endOfMonth = date.withDayOfMonth(date.lengthOfMonth())
        var nextWeekMonday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(1)

        while (!nextWeekMonday.isAfter(endOfMonth)) {
            if (isFirstShiftWeekday(nextWeekMonday, referenceFirstShiftMonday)) {
                return false
            }
            nextWeekMonday = nextWeekMonday.plusWeeks(1)
        }

        return true
    }
}

internal data class NotificationWindow(
    val triggerAtMillis: Long,
    val windowLengthMillis: Long
)

private fun notificationPrefs(context: Context): SharedPreferences {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

internal fun getFirstShiftReferenceDate(context: Context): LocalDate {
    return getStoredFirstShiftReferenceDate(context) ?: DEFAULT_REFERENCE_FIRST_SHIFT_MONDAY
}

/** Datum koji je korisnik sam podesio, ili null ako se koristi podrazumevani. Ide u backup. */
internal fun getStoredFirstShiftReferenceDate(context: Context): LocalDate? {
    val raw = notificationPrefs(context).getString(FIRST_SHIFT_REFERENCE_DATE_KEY, null)
    return DateUtils.parseDate(raw)
}

internal fun setFirstShiftReferenceDate(context: Context, date: LocalDate) {
    storeFirstShiftReferenceDate(context, date)
}

/** Upisuje (ili uklanja, za null) datum prve smene. Vraća true tek kad je upis na disk uspeo. */
internal fun storeFirstShiftReferenceDate(context: Context, date: LocalDate?): Boolean {
    val editor = notificationPrefs(context).edit()
    if (date != null) {
        editor.putString(FIRST_SHIFT_REFERENCE_DATE_KEY, date.format(DateUtils.isoFormatter))
    } else {
        editor.remove(FIRST_SHIFT_REFERENCE_DATE_KEY)
    }
    return editor.commit()
}

fun scheduleAvailabilityNotifications(context: Context) {
    ensureAvailabilityNotificationChannel(context)
    WorkManager.getInstance(context).cancelUniqueWork(AVAILABILITY_WORK_NAME)

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val pendingIntent = availabilityAlarmPendingIntent(context)
    alarmManager.cancel(pendingIntent)

    val scheduleWindow = nextNotificationWindow()
    scheduleAvailabilityAlarm(alarmManager, scheduleWindow, pendingIntent)
}

fun sendTestNotification(context: Context): Boolean {
    if (!notificationsAllowed(context)) {
        return false
    }

    ensureAvailabilityNotificationChannel(context)
    val title = "Test obaveštenje"
    val text = "Notifikacije rade kako treba."
    showNotification(context, title, text, TEST_NOTIFICATION_ID)
    return true
}

private fun ensureAvailabilityNotificationChannel(context: Context) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        AVAILABILITY_CHANNEL_ID,
        AVAILABILITY_CHANNEL_NAME,
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        description = "Obaveštenja kada su zaduženja dostupna."
    }

    manager.createNotificationChannel(channel)
}

internal fun nextNotificationWindow(
    now: ZonedDateTime = ZonedDateTime.now()
): NotificationWindow {
    val todayStart = now.withHour(SCHEDULE_WINDOW_START_HOUR).withMinute(0).withSecond(0).withNano(0)
    val todayEnd = now.withHour(SCHEDULE_WINDOW_END_HOUR).withMinute(0).withSecond(0).withNano(0)

    val triggerAt = when {
        now.isBefore(todayStart) -> todayStart
        now.isBefore(todayEnd) -> now.plusMinutes(1).withSecond(0).withNano(0)
        else -> todayStart.plusDays(1)
    }
    val windowEnd = if (triggerAt.toLocalDate() == now.toLocalDate() && triggerAt.isBefore(todayEnd)) {
        todayEnd
    } else {
        triggerAt.withHour(SCHEDULE_WINDOW_END_HOUR).withMinute(0).withSecond(0).withNano(0)
    }
    val remainingWindowMillis = Duration.between(triggerAt, windowEnd).toMillis().coerceAtLeast(1L)
    val windowLengthMillis = minOf(AVAILABILITY_FALLBACK_WINDOW_MILLIS, remainingWindowMillis)

    return NotificationWindow(
        triggerAtMillis = triggerAt.toInstant().toEpochMilli(),
        windowLengthMillis = windowLengthMillis
    )
}

private fun scheduleAvailabilityAlarm(
    alarmManager: AlarmManager,
    scheduleWindow: NotificationWindow,
    pendingIntent: PendingIntent
) {
    if (canScheduleAvailabilityExactAlarms(alarmManager)) {
        val exactScheduled = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    scheduleWindow.triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, scheduleWindow.triggerAtMillis, pendingIntent)
            }
        }.isSuccess
        if (exactScheduled) return
    }
    alarmManager.setWindow(
        AlarmManager.RTC_WAKEUP,
        scheduleWindow.triggerAtMillis,
        scheduleWindow.windowLengthMillis,
        pendingIntent
    )
}

private fun canScheduleAvailabilityExactAlarms(alarmManager: AlarmManager): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return alarmManager.canScheduleExactAlarms()
}

internal fun isAvailabilityNotificationWindowOpen(
    now: ZonedDateTime = ZonedDateTime.now()
): Boolean {
    val time = now.toLocalTime()
    val windowStart = LocalTime.of(SCHEDULE_WINDOW_START_HOUR, 0)
    val windowEnd = LocalTime.of(SCHEDULE_WINDOW_END_HOUR, 0)
    return !time.isBefore(windowStart) && time.isBefore(windowEnd)
}

private fun availabilityAlarmPendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, AvailabilityAlarmReceiver::class.java).apply {
        action = AVAILABILITY_ALARM_ACTION
    }
    return PendingIntent.getBroadcast(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

private fun notificationsAllowed(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return false
    }

    return NotificationManagerCompat.from(context).areNotificationsEnabled()
}

internal fun maybeSendAvailabilityNotification(
    context: Context,
    now: ZonedDateTime = ZonedDateTime.now()
) {
    if (!isAvailabilityNotificationWindowOpen(now)) {
        return
    }

    val today = now.toLocalDate()
    val referenceFirstShiftMonday = getFirstShiftReferenceDate(context)

    if (!notificationsAllowed(context)) {
        return
    }

    if (!ShiftNotificationSchedule.isFirstShiftWeekday(today, referenceFirstShiftMonday)) {
        return
    }

    if (wasAvailabilityNotificationSentToday(context, today)) {
        return
    }

    val database = AppDatabase.getInstance(context)
    val dao = database.articleDao()
    val repository = DataRepository(context, dao)
    val viewModel = TrackingViewModel(repository)
    viewModel.refreshToday()
    val available = viewModel.getAllStatuses().filter { it.canAssignNow }
    if (available.isEmpty()) {
        return
    }

    ensureAvailabilityNotificationChannel(context)

    val names = available.map { it.articleType.displayName }
    val title = if (names.size == 1) "Dostupno zaduženje" else "Dostupna zaduženja"
    val suffix = if (ShiftNotificationSchedule.isLastFirstShiftWeekOfMonth(today, referenceFirstShiftMonday)) {
        " Samo još ove sedmice možeš da preuzmeš ovog meseca."
    } else {
        ""
    }
    val text = "Dostupno: ${names.joinToString(", ")}.$suffix"

    showNotification(context, title, text, AVAILABILITY_NOTIFICATION_ID)
    markAvailabilityNotificationSent(context, today)
}

private fun wasAvailabilityNotificationSentToday(context: Context, today: LocalDate): Boolean {
    val raw = notificationPrefs(context).getString(LAST_NOTIFICATION_DATE_KEY, null)
    return DateUtils.parseDate(raw) == today
}

private fun markAvailabilityNotificationSent(context: Context, today: LocalDate) {
    notificationPrefs(context)
        .edit()
        .putString(LAST_NOTIFICATION_DATE_KEY, today.format(DateUtils.isoFormatter))
        .apply()
}

class AvailabilityAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != AVAILABILITY_ALARM_ACTION) {
            return
        }

        val pendingResult = goAsync()
        Thread {
            try {
                maybeSendAvailabilityNotification(context.applicationContext)
            } finally {
                scheduleAvailabilityNotifications(context.applicationContext)
                pendingResult.finish()
            }
        }.start()
    }
}

class AvailabilityRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                scheduleAvailabilityNotifications(context.applicationContext)
            }
        }
    }
}

private fun showNotification(
    context: Context,
    title: String,
    text: String,
    notificationId: Int
) {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, AVAILABILITY_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_popup_reminder)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }

    NotificationManagerCompat.from(context)
        .notify(notificationId, notification)
}
