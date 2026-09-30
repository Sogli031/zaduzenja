package com.zaduzenja.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import java.time.LocalDate
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.zaduzenja.app.data.backup.CloudBackupStatus
import com.zaduzenja.app.data.backup.GoogleDriveBackup
import com.zaduzenja.app.data.backup.SignInState
import com.zaduzenja.app.data.backup.SyncState
import com.zaduzenja.app.ui.components.AutoSizeText
import com.zaduzenja.app.ui.components.ResponsiveInfoRow
import kotlinx.coroutines.launch
import java.util.Locale

private val ArticleColorMap = mapOf(
    ArticleType.RUKAVICE to AppColors.Primary,
    ArticleType.MAXICUT to AppColors.Primary,
    ArticleType.MAJICA to AppColors.Majica,
    ArticleType.CIPELE to AppColors.Cipele,
    ArticleType.ODELO to AppColors.Odelo
)

private fun formatPeriodMonths(months: Int): String {
    val lastDigit = months % 10
    val lastTwoDigits = months % 100
    return when {
        lastTwoDigits in 11..19 -> "$months meseci"
        lastDigit == 1 -> "$months mesec"
        lastDigit in 2..4 -> "$months meseca"
        else -> "$months meseci"
    }
}

private fun formatSyncTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60 -> "Upravo sada"
        minutes < 60 -> "Pre $minutes min"
        hours < 24 -> "Pre $hours h"
        days == 1L -> "Juče"
        days < 7 -> "Pre $days dana"
        else -> {
            val date = java.text.SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault())
            date.format(java.util.Date(timestamp))
        }
    }
}

enum class Screen {
    Main,
    History
}

data class MessageDialogState(
    val title: String,
    val message: String,
    val color: Color
)

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val app = context.applicationContext as ZaduzenjaApplication
    val repository = app.dataRepository
    val backup = app.googleDriveBackup
    val viewModel = remember { TrackingViewModel(repository) }
    val scope = rememberCoroutineScope()

    var currentScreen by rememberSaveable { mutableStateOf(Screen.Main) }
    var selectedArticle by remember { mutableStateOf<ArticleType?>(null) }
    var statuses by remember { mutableStateOf(viewModel.getAllStatuses()) }
    var messageDialog by remember { mutableStateOf<MessageDialogState?>(null) }
    var assignDialogArticle by remember { mutableStateOf<ArticleType?>(null) }
    var dataVersion by remember { mutableIntStateOf(0) }
    var showCloudDialog by remember { mutableStateOf(false) }
    var showWelcomeDialog by remember { mutableStateOf(backup.isFirstLaunch()) }
    var showShiftReferenceDialog by remember { mutableStateOf(false) }
    var firstShiftReferenceDate by remember { mutableStateOf(getFirstShiftReferenceDate(context)) }
    var showRestoreOfferDialog by remember { mutableStateOf(false) }
    var overwriteConfirm by remember { mutableStateOf<SyncState.OverwriteNeedsConfirmation?>(null) }
    var pendingRestore by remember { mutableStateOf<BackupPayload?>(null) }

    val signInState by backup.signInState.collectAsState()
    val syncState by backup.syncState.collectAsState()
    val lastSyncTime by backup.lastSyncTime.collectAsState()

    val isCompact = rememberIsCompactLayout()

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        val account = try { task.result } catch (e: Exception) { null }
        backup.handleSignInResult(account)

        if (account != null) {
            if (showWelcomeDialog) {
                showWelcomeDialog = false
                backup.markFirstLaunchDone()
            }
            scope.launch {
                // Sveža instalacija + postojeći backup u oblaku -> ponudi restore
                val offerRestore = !backup.isCloudAdopted() &&
                    backup.cloudBackupStatus() == CloudBackupStatus.EXISTS
                if (offerRestore) {
                    showRestoreOfferDialog = true
                } else {
                    messageDialog = MessageDialogState("Uspeh", "Google nalog povezan.", AppColors.Success)
                }
            }
        }
    }

    // Primena već proverenog backup-a: sve ili ništa, uspeh se javlja tek kad je sve upisano
    fun applyRestore(payload: BackupPayload) {
        try {
            repository.applyBackup(payload)
            backup.markCloudAdopted()
            viewModel.refreshToday()
            statuses = viewModel.getAllStatuses()
            firstShiftReferenceDate = getFirstShiftReferenceDate(context)
            dataVersion += 1
            messageDialog = MessageDialogState("Uspeh", "Podaci vraćeni.", AppColors.Success)
        } catch (e: Exception) {
            messageDialog = MessageDialogState(
                "Greška",
                "Vraćanje nije uspelo. Podaci na uređaju nisu promenjeni.",
                AppColors.Error
            )
        }
    }

    fun startRestore() {
        scope.launch {
            val json = backup.restore()
            if (json == null) {
                messageDialog = if (backup.syncState.value is SyncState.Error) {
                    MessageDialogState(
                        "Greška",
                        "Preuzimanje backup-a nije uspelo. Proveri internet i pokušaj ponovo.",
                        AppColors.Error
                    )
                } else {
                    MessageDialogState("Info", "Nema backup-a na cloud-u.", AppColors.Primary)
                }
                return@launch
            }

            // Ceo backup se proverava pre prvog upisa - neispravan backup ne menja ništa
            val payload = try {
                repository.parseBackup(json)
            } catch (e: Exception) {
                val reason = (e as? BackupFormatException)?.message ?: "Backup nije u ispravnom formatu."
                messageDialog = MessageDialogState(
                    "Greška",
                    "$reason Ništa nije promenjeno.",
                    AppColors.Error
                )
                return@launch
            }

            if (repository.isEmpty()) {
                applyRestore(payload)
            } else {
                pendingRestore = payload
            }
        }
    }

    fun startBackup(confirmedOverwrite: Boolean) {
        scope.launch {
            val payload = try {
                repository.buildBackupPayload()
            } catch (e: Exception) {
                val reason = (e as? BackupFormatException)?.message ?: "Podaci nisu mogli da se pročitaju."
                messageDialog = MessageDialogState("Greška", "Backup nije napravljen. $reason", AppColors.Error)
                return@launch
            }

            val success = backup.backup(
                jsonData = BackupPayloadCodec.toJson(payload),
                localIsEmpty = payload.isEmpty,
                confirmedOverwrite = confirmedOverwrite
            )
            val state = backup.syncState.value
            when {
                success ->
                    messageDialog = MessageDialogState("Uspeh", "Backup sačuvan.", AppColors.Success)
                // U oblaku je backup koji bi bio pregažen - ne prepisuj bez pitanja
                state is SyncState.OverwriteNeedsConfirmation -> overwriteConfirm = state
                state is SyncState.EmptyData ->
                    messageDialog = MessageDialogState("Info", "Nema podataka za backup.", AppColors.Primary)
                else -> {
                    // Razlog mora da se vidi - bez njega se greška ne može ni prijaviti ni ispraviti
                    val reason = (state as? SyncState.Error)?.message
                    messageDialog = MessageDialogState(
                        "Greška",
                        "Backup nije uspeo." + (reason?.let { "\n$it" } ?: ""),
                        AppColors.Error
                    )
                }
            }
        }
    }

    LaunchedEffect(currentScreen) {
        viewModel.refreshToday()
        statuses = viewModel.getAllStatuses()
    }

    when (currentScreen) {
        Screen.Main -> {
            MainScreen(
                statuses = statuses,
                today = viewModel.todayDate,
                isCompact = isCompact,
                signInState = signInState,
                syncState = syncState,
                onAssign = { articleType ->
                    assignDialogArticle = articleType
                },
                onShowInfo = { msg ->
                    messageDialog = MessageDialogState("Info", msg, AppColors.Primary)
                },
                firstShiftReferenceDate = firstShiftReferenceDate,
                onShiftReferenceClick = {
                    showShiftReferenceDialog = true
                },
                onCloudClick = {
                    showCloudDialog = true
                },
                onNavigateHistory = {
                    currentScreen = Screen.History
                }
            )
        }
        Screen.History -> {
            HistoryScreen(
                viewModel = viewModel,
                selectedArticle = selectedArticle,
                onSelectArticle = { selectedArticle = it },
                onBack = { currentScreen = Screen.Main },
                onNavigateHistory = {},
                onDeleteHistory = { type, entry ->
                    if (viewModel.deleteHistoryEntry(type, entry)) {
                        statuses = viewModel.getAllStatuses()
                        dataVersion += 1
                    }
                },
                onEditHistory = { type, oldEntry, newDate, newSize ->
                    val normalizedSize = newSize?.trim()?.ifBlank { null }
                    val edited = viewModel.editHistoryEntry(type, oldEntry, newDate, normalizedSize)
                    if (edited) {
                        statuses = viewModel.getAllStatuses()
                        dataVersion += 1
                    }
                    edited
                },
                dataVersion = dataVersion,
                isCompact = isCompact
            )
        }
    }

    assignDialogArticle?.let { articleType ->
        val lastSize = statuses.find { it.articleType == articleType }?.lastSize
        AssignDialog(
            articleType = articleType,
            defaultDate = DateUtils.formatDate(LocalDate.now()),
            lastSize = lastSize,
            onDismiss = { assignDialogArticle = null },
            onConfirm = { dateText, size ->
                val assignmentDate = DateUtils.parseDate(dateText)
                if (assignmentDate == null) {
                    messageDialog = MessageDialogState("Greška", "Neispravan format datuma", AppColors.Error)
                    return@AssignDialog
                }

                val response = viewModel.attemptAssignment(articleType, assignmentDate, size)
                assignDialogArticle = null

                if (response.result == AssignmentResult.SUCCESS) {
                    messageDialog = MessageDialogState("Uspeh", response.message, AppColors.Success)
                    statuses = viewModel.getAllStatuses()
                    dataVersion += 1
                } else {
                    val msg = if (response.nextAllowed != null) {
                        "${response.message}\nSledeće od: ${DateUtils.formatDate(response.nextAllowed)}"
                    } else {
                        response.message
                    }
                    messageDialog = MessageDialogState("Greška", msg, AppColors.Error)
                }
            }
        )
    }

    messageDialog?.let { dialogState ->
        MessageDialog(dialogState) {
            messageDialog = null
        }
    }

    if (showShiftReferenceDialog) {
        ShiftReferenceDialog(
            defaultDate = DateUtils.formatDate(firstShiftReferenceDate),
            onDismiss = { showShiftReferenceDialog = false },
            onConfirm = { dateText ->
                val parsedDate = DateUtils.parseDate(dateText)
                if (parsedDate == null) {
                    messageDialog = MessageDialogState("Greška", "Neispravan format datuma", AppColors.Error)
                    return@ShiftReferenceDialog
                }

                setFirstShiftReferenceDate(context, parsedDate)
                firstShiftReferenceDate = parsedDate
                scheduleAvailabilityNotifications(context)
                showShiftReferenceDialog = false
                messageDialog = MessageDialogState(
                    "Uspeh",
                    "Prva smena podešena od ${DateUtils.formatDate(parsedDate)}.",
                    AppColors.Success
                )
            }
        )
    }

    if (showCloudDialog) {
        CloudBackupDialog(
            signInState = signInState,
            syncState = syncState,
            lastSyncTime = lastSyncTime,
            onDismiss = { showCloudDialog = false },
            onSignIn = {
                signInLauncher.launch(backup.getSignInIntent())
            },
            onSignOut = {
                scope.launch {
                    backup.signOut()
                }
            },
            onBackup = { startBackup(confirmedOverwrite = false) },
            onRestore = { startRestore() }
        )
    }

    overwriteConfirm?.let { confirm ->
        CloudChoiceDialog(
            title = "U oblaku već postoji backup",
            message = if (confirm.localIsEmpty) {
                "Na ovom uređaju nema zaduženja, a u oblaku postoji backup. " +
                    "Ako sada napraviš backup, on će biti zamenjen praznim podacima."
            } else {
                "U oblaku postoji backup koji na ovom uređaju još nije vraćen. " +
                    "Ako sada napraviš backup, on će biti zamenjen podacima sa ovog uređaja."
            },
            primaryText = "Vrati iz oblaka",
            onPrimary = {
                overwriteConfirm = null
                startRestore()
            },
            secondaryText = "Ipak napravi backup",
            onSecondary = {
                overwriteConfirm = null
                startBackup(confirmedOverwrite = true)
            },
            cancelText = "Otkaži",
            onCancel = { overwriteConfirm = null }
        )
    }

    if (showRestoreOfferDialog) {
        CloudChoiceDialog(
            title = "Pronađen backup u oblaku",
            message = "Na Google Drive-u postoji backup tvojih zaduženja. Želiš li da ga vratiš na ovaj uređaj?",
            primaryText = "Vrati iz oblaka",
            onPrimary = {
                showRestoreOfferDialog = false
                startRestore()
            },
            cancelText = "Ne sada",
            onCancel = { showRestoreOfferDialog = false }
        )
    }

    pendingRestore?.let { payload ->
        CloudChoiceDialog(
            title = "Vraćanje podataka",
            message = "Podaci iz oblaka će zameniti sva trenutna zaduženja i podešavanja na ovom uređaju.",
            primaryText = "Zameni",
            onPrimary = {
                pendingRestore = null
                applyRestore(payload)
            },
            cancelText = "Otkaži",
            onCancel = { pendingRestore = null }
        )
    }

    if (showWelcomeDialog) {
        WelcomeDialog(
            onSignIn = {
                signInLauncher.launch(backup.getSignInIntent())
            },
            onSkip = {
                showWelcomeDialog = false
                backup.markFirstLaunchDone()
            }
        )
    }
}

@Composable
private fun rememberIsCompactLayout(): Boolean {
    val config = LocalConfiguration.current
    val shortest = minOf(config.screenWidthDp, config.screenHeightDp)
    val isNarrow = shortest <= 420
    val isLargeText = config.fontScale >= 1.15f
    return isNarrow || isLargeText
}

@Composable
fun ThemedBackground(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(color = AppColors.Background, size = size)

                val sizeBase = kotlin.math.min(size.width, size.height)
                val blobSize = kotlin.math.max(sizeBase * 0.58f, 180.dp.toPx())
                val glowSize = kotlin.math.max(sizeBase * 0.48f, 160.dp.toPx())

                val blobX = -blobSize * 0.35f
                val blobY = -blobSize * 0.4f
                val glowX = size.width - glowSize * 0.65f
                val glowY = size.height - glowSize * 0.65f

                drawRoundRect(
                    color = AppColors.BackgroundAccent,
                    topLeft = Offset(blobX, blobY),
                    size = Size(blobSize, blobSize),
                    cornerRadius = CornerRadius(blobSize * 0.5f, blobSize * 0.5f)
                )

                drawRoundRect(
                    color = AppColors.BackgroundGlow,
                    topLeft = Offset(glowX, glowY),
                    size = Size(glowSize, glowSize),
                    cornerRadius = CornerRadius(glowSize * 0.5f, glowSize * 0.5f)
                )
            }
    ) {
        Column(modifier = Modifier.fillMaxSize(), content = content)
    }
}

@Composable
fun MainScreen(
    statuses: List<ArticleStatus>,
    today: LocalDate,
    isCompact: Boolean,
    signInState: SignInState,
    syncState: SyncState,
    onAssign: (ArticleType) -> Unit,
    onShowInfo: (String) -> Unit,
    firstShiftReferenceDate: LocalDate,
    onShiftReferenceClick: () -> Unit,
    onCloudClick: () -> Unit,
    onNavigateHistory: () -> Unit
) {
    val pad = if (isCompact) AppDimens.Padding - 4.dp else AppDimens.Padding
    val spacing = if (isCompact) AppDimens.Margin - 2.dp else AppDimens.Margin
    val context = LocalContext.current

    ThemedBackground(
        modifier = Modifier.fillMaxSize()
    ) {
        val available = statuses.count { it.canAssignNow }
        val summaryText = if (isCompact) {
            "$available/${statuses.size}"
        } else {
            "Dostupno $available/${statuses.size}"
        }
        val cloudLabel = cloudActionLabel(
            isSignedIn = signInState is SignInState.SignedIn,
            isSyncing = syncState is SyncState.Syncing
        )
        val cloudColor = when {
            syncState is SyncState.Syncing -> AppColors.Warning
            signInState is SignInState.SignedIn -> AppColors.Success
            else -> AppColors.Primary
        }

        AppBar(
            title = "Praćenje zaduženja",
            subtitle = "Danas: ${DateUtils.formatDate(today)}",
            backAction = null,
            rightContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Badge(
                        text = summaryText,
                        bgColor = AppColors.SurfaceTint,
                        textColor = AppColors.TextSecondary
                    )
                    IconPillAction(
                        icon = Icons.Outlined.Notifications,
                        label = "Test",
                        tint = AppColors.TextSecondary,
                        showLabel = !isCompact,
                        onClick = {
                            val sent = sendTestNotification(context)
                            if (!sent) {
                                onShowInfo("Obaveštenja su isključena. Uključite ih u podešavanjima.")
                            }
                        }
                    )
                    IconPillAction(
                        icon = Icons.Outlined.Cloud,
                        label = cloudLabel,
                        tint = cloudColor,
                        showLabel = !isCompact,
                        onClick = onCloudClick
                    )
                }
            },
            isCompact = isCompact
        )

        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = pad, vertical = 0.dp)
                .verticalScroll(scrollState)
                .padding(top = 6.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            CardSurface(
                radius = AppDimens.CardRadius,
                padding = AppDimens.Padding,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppDimens.CardRadius))
                    .clickable(
                        onClickLabel = "Podesi prvu smenu",
                        role = Role.Button,
                        onClick = onShiftReferenceClick
                    )
            ) {
                Text(
                    text = "Prva smena kreće od ${DateUtils.formatDate(firstShiftReferenceDate)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.TextPrimary
                )
                Text(
                    text = firstShiftReferenceHintText(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary
                )
            }

            val rukaviceStatus = statuses.find { it.articleType == ArticleType.RUKAVICE }
            val maxicutStatus = statuses.find { it.articleType == ArticleType.MAXICUT }
            val otherStatuses = statuses.filter {
                it.articleType != ArticleType.RUKAVICE && it.articleType != ArticleType.MAXICUT
            }

            if (rukaviceStatus != null && maxicutStatus != null) {
                GlovesCardAnimated(
                    rukaviceStatus = rukaviceStatus,
                    maxicutStatus = maxicutStatus,
                    index = 0,
                    isCompact = isCompact,
                    onAssign = onAssign
                )
            }

            otherStatuses.forEachIndexed { index, status ->
                StatusCardAnimated(
                    status = status,
                    index = index + 1,
                    isCompact = isCompact,
                    onAssign = onAssign
                )
            }
        }

        BottomNav(
            active = "list",
            onList = { onShowInfo("Već ste na listi artikala.") },
            onHistory = onNavigateHistory
        )
    }
}

@Composable
fun HistoryScreen(
    viewModel: TrackingViewModel,
    selectedArticle: ArticleType?,
    onSelectArticle: (ArticleType?) -> Unit,
    onBack: () -> Unit,
    onNavigateHistory: () -> Unit,
    onDeleteHistory: (ArticleType, HistoryEntry) -> Unit,
    onEditHistory: (ArticleType, HistoryEntry, LocalDate, String?) -> Boolean,
    dataVersion: Int,
    isCompact: Boolean
) {
    val pad = if (isCompact) AppDimens.Padding - 4.dp else AppDimens.Padding
    val spacing = if (isCompact) AppDimens.Margin - 2.dp else AppDimens.Margin
    var pendingDeleteRequest by remember { mutableStateOf<PendingHistoryDelete?>(null) }
    var pendingEditRequest by remember { mutableStateOf<PendingHistoryEdit?>(null) }

    val sections = remember(selectedArticle, dataVersion) {
        if (selectedArticle != null) {
            listOf(
                HistorySection(
                    articleType = selectedArticle,
                    history = viewModel.getHistory(selectedArticle)
                )
            )
        } else {
            ArticleType.values().map { type ->
                HistorySection(
                    articleType = type,
                    history = viewModel.getHistory(type)
                )
            }
        }
    }

    val totalCount = sections.sumOf { it.history.size }

    ThemedBackground(modifier = Modifier.fillMaxSize()) {
        AppBar(
            title = "Istorija",
            subtitle = if (selectedArticle == null) {
                "Filter: svi artikli"
            } else {
                "Filter: ${selectedArticle.displayName}"
            },
            backAction = onBack,
            rightContent = {
                Badge(
                    text = "$totalCount zapisa",
                    bgColor = AppColors.SurfaceTint,
                    textColor = AppColors.TextSecondary
                )
            },
            isCompact = isCompact
        )

        val filterHeight = if (isCompact) 40.dp else 44.dp
        val filterSpacing = if (isCompact) 6.dp else 8.dp
        val filterTextStyle = if (isCompact) {
            MaterialTheme.typography.bodyMedium
        } else {
            MaterialTheme.typography.bodyLarge
        }
        val filterPadding = if (isCompact) {
            PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        } else {
            PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        }

        Row(
            modifier = Modifier
                .padding(horizontal = pad)
                .heightIn(min = filterHeight)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(filterSpacing)
        ) {
            RoundedButton(
                text = if (isCompact) "SVE" else "Svi artikli",
                bgColor = if (selectedArticle == null) AppColors.Primary else AppColors.SurfaceTint,
                textColor = if (selectedArticle == null) AppColors.TextInvert else AppColors.TextSecondary,
                radius = RoundedButtonRadius.Pill,
                contentPadding = filterPadding,
                textStyle = filterTextStyle,
                modifier = Modifier
                    .heightIn(min = filterHeight),
                onClick = { onSelectArticle(null) }
            )

            ArticleType.values().forEach { type ->
                val isSelected = selectedArticle == type
                RoundedButton(
                    text = historyFilterLabel(type, isCompact),
                    bgColor = if (isSelected) AppColors.Primary else AppColors.SurfaceTint,
                    textColor = if (isSelected) AppColors.TextInvert else AppColors.TextSecondary,
                    radius = RoundedButtonRadius.Pill,
                    contentPadding = filterPadding,
                    textStyle = filterTextStyle,
                    modifier = Modifier
                        .heightIn(min = filterHeight),
                    onClick = { onSelectArticle(type) }
                )
            }
        }

        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = pad)
                .verticalScroll(scrollState)
                .padding(vertical = AppDimens.Margin),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            sections.forEach { section ->
                HistorySectionView(
                    section = section,
                    onEditHistory = { type, entry ->
                        pendingEditRequest = PendingHistoryEdit(type, entry)
                    },
                    onDeleteHistory = { type, entry ->
                        pendingDeleteRequest = PendingHistoryDelete(type, entry)
                    }
                )
            }
        }

        BottomNav(
            active = "history",
            onList = onBack,
            onHistory = onNavigateHistory
        )
    }

    pendingDeleteRequest?.let { request ->
        ConfirmDeleteHistoryDialog(
            articleType = request.articleType,
            entry = request.entry,
            onDismiss = { pendingDeleteRequest = null },
            onConfirm = {
                onDeleteHistory(request.articleType, request.entry)
                pendingDeleteRequest = null
            }
        )
    }

    pendingEditRequest?.let { request ->
        EditHistoryDialog(
            articleType = request.articleType,
            entry = request.entry,
            onDismiss = { pendingEditRequest = null },
            onConfirm = { dateText, newSize ->
                val parsedDate = DateUtils.parseDate(dateText)
                if (parsedDate == null) {
                    "Neispravan format datuma."
                } else if (parsedDate > LocalDate.now()) {
                    "Datum u budućnosti nije dozvoljen."
                } else {
                    val edited = onEditHistory(request.articleType, request.entry, parsedDate, newSize)
                    if (edited) {
                        pendingEditRequest = null
                        null
                    } else {
                        "Zapis nije pronađen."
                    }
                }
            }
        )
    }
}

data class HistorySection(
    val articleType: ArticleType,
    val history: List<HistoryEntry>
)

private data class PendingHistoryDelete(
    val articleType: ArticleType,
    val entry: HistoryEntry
)

private data class PendingHistoryEdit(
    val articleType: ArticleType,
    val entry: HistoryEntry
)

@Composable
fun HistorySectionView(
    section: HistorySection,
    onEditHistory: (ArticleType, HistoryEntry) -> Unit,
    onDeleteHistory: (ArticleType, HistoryEntry) -> Unit
) {
    val titleStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    Row(
        modifier = Modifier.heightIn(min = 36.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AutoSizeText(
            text = section.articleType.displayName,
            style = titleStyle,
            maxFontSize = titleStyle.fontSize,
            minFontSize = MaterialTheme.typography.bodyMedium.fontSize,
            color = AppColors.TextPrimary,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Badge(
            text = section.history.size.toString(),
            bgColor = AppColors.SurfaceTint,
            textColor = AppColors.TextSecondary
        )
    }

    if (section.history.isEmpty()) {
        Text(
            text = "Nema zapisa",
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .heightIn(min = 32.dp)
                .fillMaxWidth(),
            textAlign = TextAlign.Start
        )
    } else {
        section.history.forEachIndexed { index, entry ->
            HistoryItemAnimated(
                index = index + 1,
                entry = entry,
                onEdit = { onEditHistory(section.articleType, entry) },
                onDelete = { onDeleteHistory(section.articleType, entry) }
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
fun ConfirmDeleteHistoryDialog(
    articleType: ArticleType,
    entry: HistoryEntry,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val details = if (entry.size != null) {
        "Datum: ${DateUtils.formatDate(entry.date)}\nVeličina: ${entry.size}"
    } else {
        "Datum: ${DateUtils.formatDate(entry.date)}"
    }

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
                modifier = Modifier.padding(AppDimens.Padding),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Margin)
            ) {
                Text(
                    text = "Potvrda brisanja",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = AppColors.Error,
                    maxLines = 1
                )
                Text(
                    text = "Da li želiš da obrišeš zapis za ${articleType.displayName}?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.TextPrimary
                )
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.Margin)
                ) {
                    RoundedButton(
                        text = "OTKAŽI",
                        bgColor = AppColors.SurfaceTint,
                        textColor = AppColors.TextSecondary,
                        radius = RoundedButtonRadius.Pill,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = AppDimens.ButtonHeight),
                        onClick = onDismiss
                    )
                    RoundedButton(
                        text = "OBRIŠI",
                        bgColor = AppColors.Error,
                        textColor = AppColors.TextInvert,
                        radius = RoundedButtonRadius.Pill,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = AppDimens.ButtonHeight),
                        onClick = onConfirm
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditHistoryDialog(
    articleType: ArticleType,
    entry: HistoryEntry,
    onDismiss: () -> Unit,
    onConfirm: (dateText: String, size: String?) -> String?
) {
    var dateText by remember(entry) { mutableStateOf(DateUtils.formatDate(entry.date)) }
    var selectedSize by remember(entry, articleType) {
        mutableStateOf(entry.size?.takeIf { size -> articleType.availableSizes?.contains(size) == true })
    }
    var errorText by remember(entry) { mutableStateOf<String?>(null) }

    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.85f
    val canConfirm = !articleType.hasSizes || selectedSize != null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .heightIn(max = maxHeight)
                .wrapContentHeight(),
            color = AppColors.Surface,
            shape = RoundedCornerShape(AppDimens.BorderRadius),
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(AppDimens.Padding)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Margin)
            ) {
                Text(
                    text = "Izmeni zapis",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = AppColors.Primary
                )

                Text(
                    text = "Datum (dd.mm.yyyy):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                RoundedInput(
                    value = dateText,
                    onValueChange = {
                        dateText = it
                        errorText = null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                )

                articleType.availableSizes?.let { sizes ->
                    Text(
                        text = "Veličina:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sizes.forEach { sizeOption ->
                            val isSelected = selectedSize == sizeOption
                            RoundedButton(
                                text = sizeOption,
                                bgColor = if (isSelected) AppColors.Primary else AppColors.SurfaceTint,
                                textColor = if (isSelected) AppColors.TextInvert else AppColors.TextSecondary,
                                radius = RoundedButtonRadius.Pill,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.heightIn(min = 36.dp),
                                onClick = {
                                    selectedSize = sizeOption
                                    errorText = null
                                }
                            )
                        }
                    }
                }

                if (errorText != null) {
                    Text(
                        text = errorText ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.Error
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.Margin)
                ) {
                    RoundedButton(
                        text = "OTKAŽI",
                        bgColor = AppColors.SurfaceTint,
                        textColor = AppColors.TextSecondary,
                        radius = RoundedButtonRadius.Pill,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = AppDimens.ButtonHeight),
                        onClick = onDismiss
                    )
                    RoundedButton(
                        text = "SAČUVAJ",
                        bgColor = if (canConfirm) AppColors.Primary else AppColors.SurfaceTint,
                        textColor = if (canConfirm) AppColors.TextInvert else AppColors.TextSecondary,
                        radius = RoundedButtonRadius.Pill,
                        enabled = canConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = AppDimens.ButtonHeight),
                        onClick = {
                            errorText = onConfirm(dateText, selectedSize)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun StatusCardAnimated(status: ArticleStatus, index: Int, isCompact: Boolean, onAssign: (ArticleType) -> Unit) {
    val alpha = remember(status.articleType) { Animatable(0f) }

    LaunchedEffect(status.articleType) {
        alpha.snapTo(0f)
        delay(index * 50L)
        alpha.animateTo(1f, animationSpec = tween(250, easing = FastOutSlowInEasing))
    }

    StatusCard(
        status = status,
        isCompact = isCompact,
        modifier = Modifier.graphicsLayer(alpha = alpha.value),
        onAssign = onAssign
    )
}

@Composable
fun GlovesCardAnimated(
    rukaviceStatus: ArticleStatus,
    maxicutStatus: ArticleStatus,
    index: Int,
    isCompact: Boolean,
    onAssign: (ArticleType) -> Unit
) {
    val alpha = remember(Unit) { Animatable(0f) }

    LaunchedEffect(Unit) {
        alpha.snapTo(0f)
        delay(index * 50L)
        alpha.animateTo(1f, animationSpec = tween(250, easing = FastOutSlowInEasing))
    }

    GlovesCard(
        rukaviceStatus = rukaviceStatus,
        maxicutStatus = maxicutStatus,
        isCompact = isCompact,
        modifier = Modifier.graphicsLayer(alpha = alpha.value),
        onAssign = onAssign
    )
}

@Composable
fun GlovesCard(
    rukaviceStatus: ArticleStatus,
    maxicutStatus: ArticleStatus,
    isCompact: Boolean,
    modifier: Modifier = Modifier,
    onAssign: (ArticleType) -> Unit
) {
    val pad = if (isCompact) AppDimens.Padding - 4.dp else AppDimens.Padding
    val spacing = if (isCompact) 10.dp else 12.dp

    CardSurface(
        modifier = modifier.fillMaxWidth(),
        radius = AppDimens.CardRadius,
        padding = pad
    ) {
        GloveSubRow(status = rukaviceStatus, isCompact = isCompact, onAssign = onAssign)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing)
                .height(1.dp)
                .background(AppColors.Outline)
        )

        GloveSubRow(status = maxicutStatus, isCompact = isCompact, onAssign = onAssign)
    }
}

@Composable
private fun NextAllowedInfoRow(nextAllowed: LocalDate) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Sledeće od",
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = DateUtils.formatDate(nextAllowed),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = AppColors.TextPrimary,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun UnavailableAssignmentNotice(nextAllowed: LocalDate) {
    Text(
        text = unavailableDateText(nextAllowed),
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        color = AppColors.TextSecondary,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AppDimens.ButtonHeight)
            .background(AppColors.SurfaceTint, RoundedCornerShape(percent = 50))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    )
}

@Composable
private fun GloveSubRow(
    status: ArticleStatus,
    isCompact: Boolean,
    onAssign: (ArticleType) -> Unit
) {
    val spacing = if (isCompact) 10.dp else 12.dp
    val headerHeight = if (isCompact) 42.dp else 46.dp
    val infoHeight = if (isCompact) 42.dp else 46.dp
    val baseTitleStyle = if (isCompact) {
        MaterialTheme.typography.bodyLarge
    } else {
        MaterialTheme.typography.titleMedium
    }
    val titleStyle = baseTitleStyle.copy(fontWeight = FontWeight.SemiBold)
    val buttonTextStyle = if (isCompact) {
        MaterialTheme.typography.bodyMedium
    } else {
        MaterialTheme.typography.bodyLarge
    }

    Row(
        modifier = Modifier.heightIn(min = headerHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val accentColor = ArticleColorMap[status.articleType] ?: AppColors.Primary
        Avatar(text = status.articleType.displayName.first().toString(), bgColor = accentColor)

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = spacing, end = spacing)
        ) {
            AutoSizeText(
                text = status.articleType.displayName,
                style = titleStyle,
                maxFontSize = baseTitleStyle.fontSize,
                minFontSize = MaterialTheme.typography.bodyMedium.fontSize,
                color = AppColors.TextPrimary,
                maxLines = 1,
            )
            Text(
                text = "Period: ${formatPeriodMonths(status.articleType.periodMonths)}",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        val statusColor = if (status.canAssignNow) AppColors.Success else AppColors.Warning
        val statusText = if (status.canAssignNow) "DOSTUPNO" else "ZADUŽENO"
        Badge(text = statusText, bgColor = statusColor, textColor = AppColors.TextInvert)
    }

    Column(
        modifier = Modifier
            .heightIn(min = infoHeight)
            .padding(top = spacing)
    ) {
        ResponsiveInfoRow(
            label = "Poslednje",
            value = status.lastAssignment?.let { DateUtils.formatDate(it) } ?: "Nikad"
        )
        NextAllowedInfoRow(status.nextAllowed)
    }

    if (status.canAssignNow) {
        RoundedButton(
            text = "ZADUŽI",
            bgColor = AppColors.Primary,
            textColor = AppColors.TextInvert,
            radius = RoundedButtonRadius.Pill,
            textStyle = buttonTextStyle,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppDimens.ButtonHeight),
            onClick = { onAssign(status.articleType) }
        )
    } else {
        UnavailableAssignmentNotice(status.nextAllowed)
    }
}

@Composable
fun StatusCard(
    status: ArticleStatus,
    isCompact: Boolean,
    modifier: Modifier = Modifier,
    onAssign: (ArticleType) -> Unit
) {
    val pad = if (isCompact) AppDimens.Padding - 4.dp else AppDimens.Padding
    val spacing = if (isCompact) 10.dp else 12.dp
    val headerHeight = if (isCompact) 42.dp else 46.dp
    val infoHeight = if (isCompact) 42.dp else 46.dp
    val baseTitleStyle = if (isCompact) {
        MaterialTheme.typography.bodyLarge
    } else {
        MaterialTheme.typography.titleMedium
    }
    val titleStyle = baseTitleStyle.copy(fontWeight = FontWeight.SemiBold)
    val buttonTextStyle = if (isCompact) {
        MaterialTheme.typography.bodyMedium
    } else {
        MaterialTheme.typography.bodyLarge
    }

    CardSurface(
        modifier = modifier.fillMaxWidth(),
        radius = AppDimens.CardRadius,
        padding = pad
    ) {
        Row(
            modifier = Modifier.heightIn(min = headerHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val accentColor = ArticleColorMap[status.articleType] ?: AppColors.Primary
            Avatar(text = status.articleType.displayName.first().toString(), bgColor = accentColor)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = spacing, end = spacing)
            ) {
                AutoSizeText(
                    text = status.articleType.displayName,
                    style = titleStyle,
                    maxFontSize = baseTitleStyle.fontSize,
                    minFontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                )
                Text(
                    text = "Period: ${formatPeriodMonths(status.articleType.periodMonths)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val statusColor = if (status.canAssignNow) AppColors.Success else AppColors.Warning
            val statusText = if (status.canAssignNow) "DOSTUPNO" else "ZADUŽENO"
            Badge(text = statusText, bgColor = statusColor, textColor = AppColors.TextInvert)
        }

        Column(
            modifier = Modifier
                .heightIn(min = infoHeight)
                .padding(top = spacing)
        ) {
        ResponsiveInfoRow(
            label = "Poslednje",
            value = status.lastAssignment?.let { DateUtils.formatDate(it) } ?: "Nikad"
        )
        NextAllowedInfoRow(status.nextAllowed)
    }

    if (status.canAssignNow) {
            RoundedButton(
                text = "ZADUŽI",
                bgColor = AppColors.Primary,
                textColor = AppColors.TextInvert,
                radius = RoundedButtonRadius.Pill,
                textStyle = buttonTextStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AppDimens.ButtonHeight),
            onClick = { onAssign(status.articleType) }
        )
    } else {
        UnavailableAssignmentNotice(status.nextAllowed)
    }
}
}

@Composable
fun HistoryItemAnimated(
    index: Int,
    entry: HistoryEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val alpha = remember(index, entry) { Animatable(0f) }

    LaunchedEffect(index, entry) {
        alpha.snapTo(0f)
        delay(index * 30L)
        alpha.animateTo(1f, animationSpec = tween(200, easing = FastOutSlowInEasing))
    }

    HistoryItem(
        index = index,
        entry = entry,
        modifier = Modifier.graphicsLayer(alpha = alpha.value),
        onEdit = onEdit,
        onDelete = onDelete
    )
}

@Composable
fun HistoryItem(
    index: Int,
    entry: HistoryEntry,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val radius = 14.dp

    val dateText = DateUtils.formatDate(entry.date)
    val sizeText = entry.size?.let { "br. $it" }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .drawBehind {
                val shadowOffset = 2.dp.toPx()
                drawRoundRect(
                    color = AppColors.Shadow,
                    topLeft = Offset(0f, shadowOffset),
                    size = Size(size.width, size.height - shadowOffset),
                    cornerRadius = CornerRadius(radius.toPx(), radius.toPx())
                )
            }
            .background(AppColors.Surface, RoundedCornerShape(radius))
            .border(1.dp, AppColors.Outline, RoundedCornerShape(radius))
            .padding(start = AppDimens.Padding + 6.dp, end = AppDimens.Padding),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onEdit)
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Badge(text = index.toString(), bgColor = AppColors.SurfaceTint, textColor = AppColors.TextSecondary)
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (sizeText != null) {
                    Text(
                        text = sizeText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            DeleteIconAction(onClick = onDelete)
        }

    }
}

@Composable
fun AppBar(
    title: String,
    subtitle: String,
    backAction: (() -> Unit)?,
    rightContent: (@Composable () -> Unit)?,
    isCompact: Boolean
) {
    val minHeight = if (isCompact) 64.dp else AppDimens.AppBarMinHeight
    val spacing = if (isCompact) 8.dp else 12.dp
    val baseTitleStyle = if (isCompact) {
        MaterialTheme.typography.titleMedium
    } else {
        MaterialTheme.typography.titleLarge
    }
    val titleStyle = baseTitleStyle.copy(fontWeight = FontWeight.SemiBold)
    val subtitleStyle = if (isCompact) {
        MaterialTheme.typography.labelMedium
    } else {
        MaterialTheme.typography.bodyMedium
    }

    val containerModifier = Modifier
        .statusBarsPadding()
        .padding(horizontal = AppDimens.Padding, vertical = 4.dp)
        .heightIn(min = minHeight)

    if (isCompact && rightContent != null) {
        Column(
            modifier = containerModifier,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                if (backAction != null) {
                    IconPillAction(
                        icon = Icons.AutoMirrored.Outlined.ArrowBack,
                        label = "Nazad",
                        tint = AppColors.TextPrimary,
                        showLabel = false,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                        onClick = backAction
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    AutoSizeText(
                        text = title,
                        style = titleStyle,
                        maxFontSize = baseTitleStyle.fontSize,
                        minFontSize = MaterialTheme.typography.bodyLarge.fontSize,
                        color = AppColors.TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = subtitle,
                        style = subtitleStyle,
                        color = AppColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                rightContent()
            }
        }
    } else {
        Row(
            modifier = containerModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            if (backAction != null) {
                IconPillAction(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    label = "Nazad",
                    tint = AppColors.TextPrimary,
                    showLabel = false,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    onClick = backAction
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                AutoSizeText(
                    text = title,
                    style = titleStyle,
                    maxFontSize = baseTitleStyle.fontSize,
                    minFontSize = MaterialTheme.typography.bodyLarge.fontSize,
                    color = AppColors.TextPrimary,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = subtitleStyle,
                    color = AppColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (rightContent != null) {
                rightContent()
            }
        }
    }
}

@Composable
fun BottomNav(
    active: String,
    onList: () -> Unit,
    onHistory: () -> Unit
) {
    val radius = AppDimens.NavHeight / 2
    Row(
        modifier = Modifier
            .padding(8.dp)
            .navigationBarsPadding()
            .heightIn(min = AppDimens.NavHeight)
            .fillMaxWidth()
            .background(AppColors.Surface, RoundedCornerShape(radius))
            .border(1.dp, AppColors.Outline, RoundedCornerShape(radius))
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconPillAction(
            icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
            label = "Lista",
            tint = if (active == "list") AppColors.TextInvert else AppColors.TextSecondary,
            bgColor = if (active == "list") AppColors.Primary else AppColors.SurfaceTint,
            pressedBgColor = if (active == "list") AppColors.PrimaryDark else AppColors.SurfaceMuted,
            showLabel = true,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = AppDimens.ButtonHeight),
            onClick = onList
        )
        IconPillAction(
            icon = Icons.Outlined.History,
            label = "Istorija",
            tint = if (active == "history") AppColors.TextInvert else AppColors.TextSecondary,
            bgColor = if (active == "history") AppColors.Primary else AppColors.SurfaceTint,
            pressedBgColor = if (active == "history") AppColors.PrimaryDark else AppColors.SurfaceMuted,
            showLabel = true,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = AppDimens.ButtonHeight),
            onClick = onHistory
        )
    }
}

@Composable
fun Badge(
    text: String,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val minHeight = if (rememberIsCompactLayout()) 24.dp else 28.dp
    val padding = if (rememberIsCompactLayout()) {
        PaddingValues(10.dp, 4.dp)
    } else {
        PaddingValues(12.dp, 6.dp)
    }

    Box(
        modifier = Modifier
            .heightIn(min = minHeight)
            .background(bgColor, RoundedCornerShape(percent = 50))
            .padding(padding)
            .then(modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun IconPillAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    bgColor: Color = AppColors.SurfaceTint,
    pressedBgColor: Color = AppColors.SurfaceMuted,
    showLabel: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(percent = 50)
    val background = if (pressed) pressedBgColor else bgColor

    Surface(
        color = background,
        shape = shape,
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (showLabel) 12.dp else 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(if (showLabel) 6.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.sizeIn(minWidth = 20.dp, minHeight = 20.dp)
            )
            if (showLabel) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun DeleteIconAction(onClick: () -> Unit) {
    IconPillAction(
        icon = Icons.Outlined.Delete,
        label = "Obriši zapis",
        tint = AppColors.Error,
        showLabel = false,
        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
        onClick = onClick
    )
}

@Composable
fun Avatar(text: String, bgColor: Color) {
    val size = if (rememberIsCompactLayout()) 38.dp else 44.dp
    Box(
        modifier = Modifier
            .sizeIn(minWidth = size, minHeight = size)
            .background(bgColor, RoundedCornerShape(percent = 50)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.TextInvert,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

sealed class RoundedButtonRadius {
    data object Pill : RoundedButtonRadius()
    data class Fixed(val radius: Dp) : RoundedButtonRadius()
    data object Default : RoundedButtonRadius()
}

@Composable
fun RoundedButton(
    text: String,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    outline: Boolean = false,
    outlineColor: Color = bgColor,
    radius: RoundedButtonRadius = RoundedButtonRadius.Default,
    pressedColor: Color = AppColors.PrimaryDark,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    textStyle: TextStyle? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val resolvedTextStyle = (textStyle ?: MaterialTheme.typography.bodyLarge)
        .copy(fontWeight = FontWeight.SemiBold)

    val shape = when (radius) {
        RoundedButtonRadius.Pill -> RoundedCornerShape(percent = 50)
        is RoundedButtonRadius.Fixed -> RoundedCornerShape(radius.radius)
        RoundedButtonRadius.Default -> RoundedCornerShape(AppDimens.BorderRadius)
    }

    val backgroundColor = if (outline) {
        AppColors.Surface
    } else if (pressed && enabled) {
        pressedColor
    } else {
        bgColor
    }

    val border = if (outline) {
        BorderStroke(1.2f.dp, outlineColor)
    } else {
        null
    }

    Surface(
        color = backgroundColor,
        border = border,
        shape = shape,
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = resolvedTextStyle,
                color = if (enabled) textColor else AppColors.TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun RoundedInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor = if (focused) AppColors.Primary else AppColors.Outline

    Box(
        modifier = modifier
            .background(AppColors.Surface, RoundedCornerShape(AppDimens.BorderRadius))
            .border(1.1f.dp, borderColor, RoundedCornerShape(AppDimens.BorderRadius))
            .padding(horizontal = AppDimens.Padding, vertical = AppDimens.Margin)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = AppColors.TextPrimary
            ),
            keyboardOptions = keyboardOptions,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
        )
    }
}

@Composable
fun CardSurface(
    modifier: Modifier = Modifier,
    radius: Dp,
    padding: Dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .drawBehind {
                val shadowOffset = 2.dp.toPx()
                drawRoundRect(
                    color = AppColors.Shadow,
                    topLeft = Offset(0f, shadowOffset),
                    size = Size(size.width, size.height - shadowOffset),
                    cornerRadius = CornerRadius(radius.toPx(), radius.toPx())
                )
            }
            .background(AppColors.Surface, RoundedCornerShape(radius))
            .border(1.dp, AppColors.Outline, RoundedCornerShape(radius))
            .padding(padding)
    ) {
        Column(content = content)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssignDialog(
    articleType: ArticleType,
    defaultDate: String,
    lastSize: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (dateText: String, size: String?) -> Unit
) {
    var dateText by remember(defaultDate) { mutableStateOf(defaultDate) }
    var selectedSize by remember { mutableStateOf(lastSize) }
    val config = LocalConfiguration.current
    val maxHeight = config.screenHeightDp.dp * 0.85f
    val canConfirm = !articleType.hasSizes || selectedSize != null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = maxHeight)
                .wrapContentHeight(),
            color = AppColors.Surface,
            shape = RoundedCornerShape(AppDimens.BorderRadius),
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(AppDimens.Padding)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Margin)
            ) {
                AutoSizeText(
                    text = "Zaduži: ${articleType.displayName}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxFontSize = MaterialTheme.typography.titleMedium.fontSize,
                    minFontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    color = AppColors.TextPrimary,
                    maxLines = 1
                )

                Text(
                    text = "Datum (dd.mm.yyyy):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                RoundedInput(
                    value = dateText,
                    onValueChange = { dateText = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                )

                articleType.availableSizes?.let { sizes ->
                    Text(
                        text = "Veličina:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sizes.forEach { sizeOption ->
                            val isSelected = selectedSize == sizeOption
                            RoundedButton(
                                text = sizeOption,
                                bgColor = if (isSelected) AppColors.Primary else AppColors.SurfaceTint,
                                textColor = if (isSelected) AppColors.TextInvert else AppColors.TextSecondary,
                                radius = RoundedButtonRadius.Pill,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.heightIn(min = 36.dp),
                                onClick = { selectedSize = sizeOption }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.Margin)
                ) {
                    RoundedButton(
                        text = "OTKAŽI",
                        bgColor = AppColors.SurfaceTint,
                        textColor = AppColors.TextSecondary,
                        radius = RoundedButtonRadius.Pill,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = AppDimens.ButtonHeight),
                        onClick = onDismiss
                    )
                    RoundedButton(
                        text = "POTVRDI",
                        bgColor = if (canConfirm) AppColors.Primary else AppColors.SurfaceTint,
                        textColor = if (canConfirm) AppColors.TextInvert else AppColors.TextSecondary,
                        radius = RoundedButtonRadius.Pill,
                        enabled = canConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = AppDimens.ButtonHeight),
                        onClick = { onConfirm(dateText, selectedSize) }
                    )
                }
            }
        }
    }
}

@Composable
fun MessageDialog(state: MessageDialogState, onDismiss: () -> Unit) {
    val config = LocalConfiguration.current
    val maxHeight = config.screenHeightDp.dp * 0.7f

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .heightIn(max = maxHeight)
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
                AutoSizeText(
                    text = state.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxFontSize = MaterialTheme.typography.titleMedium.fontSize,
                    minFontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    color = state.color,
                    maxLines = 1
                )
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    // Poruke o grešci pri backup/restore su duže od dva reda
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
                RoundedButton(
                    text = "OK",
                    bgColor = state.color,
                    textColor = AppColors.TextInvert,
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

@Composable
fun ShiftReferenceDialog(
    defaultDate: String,
    onDismiss: () -> Unit,
    onConfirm: (dateText: String) -> Unit
) {
    var dateText by remember(defaultDate) { mutableStateOf(defaultDate) }

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
                modifier = Modifier.padding(AppDimens.Padding),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Margin)
            ) {
                Text(
                    text = "Prvi dan prve smene",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = AppColors.Primary
                )
                Text(
                    text = "Unesi ponedeljak kada ti počinje prva smena. Aplikacija dalje sama vrti 3-2-1.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary
                )
                Text(
                    text = "Datum (dd.mm.yyyy):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary
                )
                RoundedInput(
                    value = dateText,
                    onValueChange = { dateText = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                )
                RoundedButton(
                    text = "SAČUVAJ",
                    bgColor = AppColors.Primary,
                    textColor = AppColors.TextInvert,
                    radius = RoundedButtonRadius.Pill,
                    modifier = Modifier
                        .heightIn(min = AppDimens.ButtonHeight)
                        .fillMaxWidth(),
                    onClick = { onConfirm(dateText) }
                )
                RoundedButton(
                    text = "OTKAŽI",
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

@Composable
fun CloudBackupDialog(
    signInState: SignInState,
    syncState: SyncState,
    lastSyncTime: Long?,
    onDismiss: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit
) {
    val isSignedIn = signInState is SignInState.SignedIn
    val isSyncing = syncState is SyncState.Syncing
    val email = (signInState as? SignInState.SignedIn)?.email ?: "Nije povezano"

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
                modifier = Modifier.padding(AppDimens.Padding),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Margin),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Cloud Backup",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = AppColors.Primary
                )

                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSignedIn) AppColors.TextPrimary else AppColors.TextSecondary
                )

                if (isSyncing) {
                    Text(
                        text = "Sinhronizacija...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.Warning
                    )
                } else if (lastSyncTime != null) {
                    Text(
                        text = "Poslednji backup: ${formatSyncTime(lastSyncTime)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (!isSignedIn) {
                    RoundedButton(
                        text = "Poveži Google",
                        bgColor = AppColors.Primary,
                        textColor = AppColors.TextInvert,
                        radius = RoundedButtonRadius.Pill,
                        modifier = Modifier
                            .heightIn(min = AppDimens.ButtonHeight)
                            .fillMaxWidth(),
                        onClick = {
                            onDismiss()
                            onSignIn()
                        }
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.Margin)
                    ) {
                        RoundedButton(
                            text = "Backup",
                            bgColor = AppColors.Success,
                            textColor = AppColors.TextInvert,
                            radius = RoundedButtonRadius.Pill,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = AppDimens.ButtonHeight),
                            onClick = {
                                onDismiss()
                                onBackup()
                            }
                        )
                        RoundedButton(
                            text = "Restore",
                            bgColor = AppColors.Primary,
                            textColor = AppColors.TextInvert,
                            radius = RoundedButtonRadius.Pill,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = AppDimens.ButtonHeight),
                            onClick = {
                                onDismiss()
                                onRestore()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    RoundedButton(
                        text = "Odjavi se",
                        bgColor = AppColors.SurfaceTint,
                        textColor = AppColors.TextSecondary,
                        radius = RoundedButtonRadius.Pill,
                        modifier = Modifier
                            .heightIn(min = AppDimens.ButtonHeight)
                            .fillMaxWidth(),
                        onClick = {
                            onDismiss()
                            onSignOut()
                        }
                    )
                }

                RoundedButton(
                    text = "Zatvori",
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

/** Dijalog sa glavnom (preporučenom) akcijom, opcionom sporednom i otkazivanjem. */
@Composable
fun CloudChoiceDialog(
    title: String,
    message: String,
    primaryText: String,
    onPrimary: () -> Unit,
    cancelText: String,
    onCancel: () -> Unit,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onCancel,
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
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = AppColors.Primary,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                RoundedButton(
                    text = primaryText,
                    bgColor = AppColors.Primary,
                    textColor = AppColors.TextInvert,
                    radius = RoundedButtonRadius.Pill,
                    modifier = Modifier
                        .heightIn(min = AppDimens.ButtonHeight)
                        .fillMaxWidth(),
                    onClick = onPrimary
                )

                if (secondaryText != null && onSecondary != null) {
                    RoundedButton(
                        text = secondaryText,
                        bgColor = AppColors.SurfaceTint,
                        textColor = AppColors.Warning,
                        radius = RoundedButtonRadius.Pill,
                        modifier = Modifier
                            .heightIn(min = AppDimens.ButtonHeight)
                            .fillMaxWidth(),
                        onClick = onSecondary
                    )
                }

                RoundedButton(
                    text = cancelText,
                    bgColor = AppColors.SurfaceTint,
                    textColor = AppColors.TextSecondary,
                    radius = RoundedButtonRadius.Pill,
                    modifier = Modifier
                        .heightIn(min = AppDimens.ButtonHeight)
                        .fillMaxWidth(),
                    onClick = onCancel
                )
            }
        }
    }
}

@Composable
fun WelcomeDialog(
    onSignIn: () -> Unit,
    onSkip: () -> Unit
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
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
                modifier = Modifier.padding(AppDimens.Padding),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Margin),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Dobrodošli!",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = AppColors.Primary
                )

                Text(
                    text = "Poveži Google nalog da možeš ručno da sačuvaš i vratiš podatke iz cloud-a.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                RoundedButton(
                    text = "Poveži Google",
                    bgColor = AppColors.Primary,
                    textColor = AppColors.TextInvert,
                    radius = RoundedButtonRadius.Pill,
                    modifier = Modifier
                        .heightIn(min = AppDimens.ButtonHeight)
                        .fillMaxWidth(),
                    onClick = onSignIn
                )

                RoundedButton(
                    text = "Preskoči",
                    bgColor = AppColors.SurfaceTint,
                    textColor = AppColors.TextSecondary,
                    radius = RoundedButtonRadius.Pill,
                    modifier = Modifier
                        .heightIn(min = AppDimens.ButtonHeight)
                        .fillMaxWidth(),
                    onClick = onSkip
                )
            }
        }
    }
}
