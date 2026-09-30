package com.zaduzenja.app

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private var exactAlarmWarningShownThisForeground = false
    private var notificationPermissionRequestInFlight = false
    private var showExactAlarmPermissionDialog by mutableStateOf(false)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            notificationPermissionRequestInFlight = false
            requestExactAlarmPermissionIfNeeded()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleAvailabilityNotifications(applicationContext)
        requestNotificationPermissionIfNeeded()
        setContent {
            ZaduzenjaTheme {
                AppRoot()
                if (showExactAlarmPermissionDialog) {
                    ExactAlarmPermissionDialog(
                        onOpenSettings = ::openExactAlarmSettings,
                        onDismiss = { showExactAlarmPermissionDialog = false }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        exactAlarmWarningShownThisForeground = false
    }

    override fun onResume() {
        super.onResume()
        requestExactAlarmPermissionIfNeeded()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            return
        }

        val prefs = getSharedPreferences("zaduzenja_prefs", MODE_PRIVATE)
        val alreadyAsked = prefs.getBoolean("notification_permission_asked", false)
        if (!alreadyAsked) {
            prefs.edit().putBoolean("notification_permission_asked", true).apply()
            notificationPermissionRequestInFlight = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestExactAlarmPermissionIfNeeded() {
        if (notificationPermissionRequestInFlight || exactAlarmWarningShownThisForeground) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        val alarmManager = getSystemService(AlarmManager::class.java)
        if (alarmManager.canScheduleExactAlarms()) return

        exactAlarmWarningShownThisForeground = true
        showExactAlarmPermissionDialog = true
    }

    private fun openExactAlarmSettings() {
        showExactAlarmPermissionDialog = false
        runCatching { startActivity(exactAlarmSettingsIntent()) }
    }

    private fun exactAlarmSettingsIntent(): Intent {
        return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:$packageName")
        }
    }
}

@Composable
private fun ExactAlarmPermissionDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .wrapContentHeight(),
            color = AppColors.Surface,
            shape = RoundedCornerShape(AppDimens.BorderRadius),
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(AppDimens.Padding)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Margin),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Tačni alarmi nisu uključeni",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = AppColors.Warning,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Da bi obaveštenja stizala u tačno vreme, uključi dozvolu Alarmi i podsetnici za ovu aplikaciju.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.TextPrimary,
                    textAlign = TextAlign.Center
                )
                RoundedButton(
                    text = "OTVORI PODEŠAVANJE",
                    bgColor = AppColors.Warning,
                    textColor = AppColors.TextInvert,
                    radius = RoundedButtonRadius.Pill,
                    modifier = Modifier
                        .heightIn(min = AppDimens.ButtonHeight)
                        .fillMaxWidth(),
                    onClick = onOpenSettings
                )
                RoundedButton(
                    text = "KASNIJE",
                    bgColor = AppColors.SurfaceTint,
                    textColor = AppColors.TextSecondary,
                    radius = RoundedButtonRadius.Pill,
                    modifier = Modifier
                        .heightIn(min = AppDimens.ButtonHeight)
                        .fillMaxWidth(),
                    onClick = onDismiss
                )
            }
        }
    }
}
