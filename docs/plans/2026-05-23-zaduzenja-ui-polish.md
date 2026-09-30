# Zaduzenja UI Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-optimized:subagent-driven-development (recommended) or superpowers-optimized:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the existing Compose UI clearer, denser, more touch-accessible, and more icon-driven without changing assignment rules, notification scheduling, backup behavior, or stored data.

**Architecture:** Keep the app's current single-screen Compose structure and `SpaceGroteskFamily` theme. Add a small pure-Kotlin UI copy helper file so compact/full labels and status text are testable outside Compose. Keep visual components local to `App.kt` unless extraction is needed during implementation; do not move repository, notification, backup, or Room code.

**Tech Stack:** Kotlin, Android Jetpack Compose Material 3, Material Icons Extended through the existing Compose BOM, JUnit 4, Gradle wrapper from `android/`.

**Assumptions:**
- Assumes the approved scope is UI polish only -- will NOT work if assignment periods, first-shift rules, backup semantics, or persistence formats should change.
- Assumes adding `androidx.compose.material:material-icons-extended` is acceptable -- will NOT work as written if dependency additions are forbidden.
- Assumes visual verification can be done by running a debug build on a device/emulator after Gradle checks -- will NOT claim final visual approval from unit tests alone.
- Assumes current local workflow should avoid Git commands, per `project-map.md` -- will NOT include commit steps.

---

## File Structure

- Modify: `android/app/build.gradle.kts`
  - Add Compose Material Icons Extended dependency for icon buttons.
- Create: `android/app/src/main/java/com/zaduzenja/app/UiCopy.kt`
  - Own compact/full labels and UI status strings that should not be embedded repeatedly inside Compose code.
- Create: `android/app/src/test/java/com/zaduzenja/app/UiCopyTest.kt`
  - Verify compact labels, full labels, cloud action labels, and unavailable-date copy.
- Modify: `android/app/src/main/java/com/zaduzenja/app/Theme.kt`
  - Tune tokens only: background, accent surfaces, radius, and muted text if needed.
- Modify: `android/app/src/main/java/com/zaduzenja/app/App.kt`
  - Replace text-only pills with icon-backed actions, tighten cards, improve history filters/rows, raise touch target sizes, and reduce decorative background weight.

---

## Task 1: Add Testable UI Copy Helpers

**Files:**
- Create: `android/app/src/main/java/com/zaduzenja/app/UiCopy.kt`
- Create: `android/app/src/test/java/com/zaduzenja/app/UiCopyTest.kt`

**Security flag:** `none`

**Does NOT cover:** Does not change business rules, notification windows, backup state, or data storage. It only exposes display strings used by later UI tasks.

- [x] **Step 1: Write failing test**

Create `android/app/src/test/java/com/zaduzenja/app/UiCopyTest.kt`:

```kotlin
package com.zaduzenja.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class UiCopyTest {
    @Test
    fun compactHistoryFilterLabelsUseStableShortCodes() {
        assertEquals("RU", historyFilterLabel(ArticleType.RUKAVICE, isCompact = true))
        assertEquals("MC", historyFilterLabel(ArticleType.MAXICUT, isCompact = true))
        assertEquals("MA", historyFilterLabel(ArticleType.MAJICA, isCompact = true))
        assertEquals("CIP", historyFilterLabel(ArticleType.CIPELE, isCompact = true))
        assertEquals("OD", historyFilterLabel(ArticleType.ODELO, isCompact = true))
    }

    @Test
    fun fullHistoryFilterLabelsUseDisplayNames() {
        assertEquals("Rukavice", historyFilterLabel(ArticleType.RUKAVICE, isCompact = false))
        assertEquals("MaxiCut rukavice", historyFilterLabel(ArticleType.MAXICUT, isCompact = false))
        assertEquals("Majica", historyFilterLabel(ArticleType.MAJICA, isCompact = false))
        assertEquals("Cipele", historyFilterLabel(ArticleType.CIPELE, isCompact = false))
        assertEquals("Odelo", historyFilterLabel(ArticleType.ODELO, isCompact = false))
    }

    @Test
    fun cloudActionLabelReflectsConnectionState() {
        assertEquals("Sinhronizacija", cloudActionLabel(isSignedIn = true, isSyncing = true))
        assertEquals("Backup", cloudActionLabel(isSignedIn = true, isSyncing = false))
        assertEquals("Poveži", cloudActionLabel(isSignedIn = false, isSyncing = false))
    }

    @Test
    fun unavailableDateTextNamesTheNextDate() {
        assertEquals(
            "Dostupno od 03.12.2025",
            unavailableDateText(LocalDate.of(2025, 12, 3))
        )
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run from `android/`:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.zaduzenja.app.UiCopyTest"
```

Expected: FAIL because `UiCopy.kt` and its functions do not exist.

- [x] **Step 3: Implement helper file**

Create `android/app/src/main/java/com/zaduzenja/app/UiCopy.kt`:

```kotlin
package com.zaduzenja.app

import java.time.LocalDate

fun historyFilterLabel(type: ArticleType, isCompact: Boolean): String {
    if (!isCompact) return type.displayName

    return when (type) {
        ArticleType.RUKAVICE -> "RU"
        ArticleType.MAXICUT -> "MC"
        ArticleType.MAJICA -> "MA"
        ArticleType.CIPELE -> "CIP"
        ArticleType.ODELO -> "OD"
    }
}

fun cloudActionLabel(isSignedIn: Boolean, isSyncing: Boolean): String {
    return when {
        isSyncing -> "Sinhronizacija"
        isSignedIn -> "Backup"
        else -> "Poveži"
    }
}

fun unavailableDateText(nextAllowed: LocalDate): String {
    return "Dostupno od ${DateUtils.formatDate(nextAllowed)}"
}
```

- [x] **Step 4: Run focused test**

Run from `android/`:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.zaduzenja.app.UiCopyTest"
```

Expected: PASS.

---

## Task 2: Add Icon Dependency And Shared Icon Controls

**Files:**
- Modify: `android/app/build.gradle.kts`
- Modify: `android/app/src/main/java/com/zaduzenja/app/App.kt`

**Security flag:** `none`

**Does NOT cover:** Does not change any click destination or action callback. This task only makes icon rendering available and adds reusable Compose controls.

- [x] **Step 1: Add icon dependency**

In `android/app/build.gradle.kts`, inside `dependencies`, add this line after `implementation("androidx.compose.material3:material3")`:

```kotlin
implementation("androidx.compose.material:material-icons-extended")
```

- [x] **Step 2: Add imports to `App.kt`**

Add these imports near the existing Compose imports:

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
```

- [x] **Step 3: Add reusable icon action composables**

In `App.kt`, place these helpers after `Badge(...)` and before `Avatar(...)`:

```kotlin
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
            .clip(shape)
            .heightIn(min = 44.dp)
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
        modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp),
        onClick = onClick
    )
}
```

- [x] **Step 4: Compile to verify dependency and imports**

Run from `android/`:

```powershell
.\gradlew.bat compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

---

## Task 3: Rework Main Header And Cloud Action

**Files:**
- Modify: `android/app/src/main/java/com/zaduzenja/app/App.kt`

**Security flag:** `none`

**Does NOT cover:** Does not change `sendTestNotification`, `onShiftReferenceClick`, `onCloudClick`, sign-in, backup, restore, or notification permission handling.

- [x] **Step 1: Move cloud display state before `AppBar(...)`**

In `MainScreen(...)`, place this block immediately after `summaryText` is calculated and before `AppBar(...)`:

```kotlin
val cloudLabel = cloudActionLabel(
    isSignedIn = signInState is SignInState.SignedIn,
    isSyncing = syncState is SyncState.Syncing
)
val cloudColor = when {
    syncState is SyncState.Syncing -> AppColors.Warning
    signInState is SignInState.SignedIn -> AppColors.Success
    else -> AppColors.Primary
}
```

- [x] **Step 2: Replace top-right text pills with icon actions**

In `MainScreen(...)`, replace the `rightContent` `Row` inside `AppBar(...)` with:

```kotlin
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
        icon = Icons.Outlined.CalendarMonth,
        label = "Prva",
        tint = AppColors.Primary,
        showLabel = !isCompact,
        onClick = onShiftReferenceClick
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
```

- [x] **Step 3: Remove centered cloud button row**

In `MainScreen(...)`, delete the `Row` block that currently renders `RoundedButton(text = cloudText, ...)` below `AppBar(...)`. Remove the later `cloudText` and duplicate `cloudColor` block so the only cloud display state is the block from Step 1. Remove `actionTextStyle` and `actionPadding` if no longer referenced.

- [x] **Step 4: Replace back text with arrow icon**

In both `AppBar(...)` branches where `backAction != null`, replace the `RoundedButton(text = "<", ...)` with:

```kotlin
IconPillAction(
    icon = Icons.Outlined.ArrowBack,
    label = "Nazad",
    tint = AppColors.TextPrimary,
    showLabel = false,
    modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp),
    onClick = backAction
)
```

- [x] **Step 5: Compile changed header**

Run from `android/`:

```powershell
.\gradlew.bat compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

---

## Task 4: Rework Assignment Cards Around Status And Next Date

**Files:**
- Modify: `android/app/src/main/java/com/zaduzenja/app/App.kt`

**Security flag:** `none`

**Does NOT cover:** Does not change `ArticleStatus.canAssignNow`, `ArticleStatus.nextAllowed`, `attemptAssignment`, period calculations, or assignment dialog behavior.

- [x] **Step 1: Update unavailable action behavior in `StatusCard(...)`**

In `StatusCard(...)`, keep the existing `RoundedButton(text = "ZADUŽI", ...)` only inside the `if (status.canAssignNow)` branch. Replace the disabled button in the `else` branch with:

```kotlin
Text(
    text = unavailableDateText(status.nextAllowed),
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
```

- [x] **Step 2: Apply the same unavailable behavior in `GloveSubRow(...)`**

In `GloveSubRow(...)`, replace its disabled `RoundedButton` branch with the same `Text(...)` block from Step 1, using `status.nextAllowed`.

- [x] **Step 3: Make the next-date row visually stronger**

In `StatusCard(...)` and `GloveSubRow(...)`, change the `ResponsiveInfoRow` for `"Sledeće od"` into a direct row:

```kotlin
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
        text = DateUtils.formatDate(status.nextAllowed),
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        color = AppColors.TextPrimary,
        textAlign = TextAlign.End,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
```

- [x] **Step 4: Run focused UI-copy tests and compile**

Run from `android/`:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.zaduzenja.app.UiCopyTest"
.\gradlew.bat compileDebugKotlin
```

Expected: Both commands pass.

---

## Task 5: Improve History Filters And Row Actions

**Files:**
- Modify: `android/app/src/main/java/com/zaduzenja/app/App.kt`

**Security flag:** `none`

**Does NOT cover:** Does not change history ordering, edit logic, delete confirmation logic, or `viewModel.getHistory(...)`.

- [x] **Step 1: Use tested labels in filter chips**

In `HistoryScreen(...)`, replace:

```kotlin
text = type.displayName.uppercase()
```

with:

```kotlin
text = historyFilterLabel(type, isCompact)
```

Also replace the first filter label:

```kotlin
text = "SVE"
```

with:

```kotlin
text = if (isCompact) "SVE" else "Svi artikli"
```

- [x] **Step 2: Replace compact delete text with icon action**

In `HistoryItem(...)`, remove these local values:

```kotlin
val deleteText = if (isCompact) "X" else "OBRIŠI"
val deleteMinWidth = if (isCompact) 28.dp else 64.dp
val deleteMinHeight = if (isCompact) 28.dp else 32.dp
val deletePadding = if (isCompact) PaddingValues(0.dp) else PaddingValues(horizontal = 10.dp, vertical = 6.dp)
val deleteTextStyle = MaterialTheme.typography.labelMedium
```

Replace the `RoundedButton(...)` delete control with:

```kotlin
DeleteIconAction(onClick = onDelete)
```

- [x] **Step 3: Make each history row easier to scan**

In `HistoryItem(...)`, keep the index badge, date, and optional size. Set the clickable edit row minimum height to at least `44.dp`:

```kotlin
.heightIn(min = 44.dp)
```

Place it before `.clip(RoundedCornerShape(10.dp))` on the inner editable `Row`.

- [x] **Step 4: Run focused tests and compile**

Run from `android/`:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.zaduzenja.app.UiCopyTest"
.\gradlew.bat compileDebugKotlin
```

Expected: Both commands pass.

---

## Task 6: Add Icon Bottom Navigation And Calm The Background

**Files:**
- Modify: `android/app/src/main/java/com/zaduzenja/app/App.kt`
- Modify: `android/app/src/main/java/com/zaduzenja/app/Theme.kt`

**Security flag:** `none`

**Does NOT cover:** Does not add routes, remove routes, change back behavior, or change selected screen state.

- [x] **Step 1: Tune background tokens**

In `Theme.kt`, update only these values:

```kotlin
val Background = Color(0xFFFAF7F1)
val BackgroundAccent = Color(0x66DFF3EF)
val BackgroundGlow = Color(0x55FFE1C2)
val SurfaceTint = Color(0xFFF3F5F6)
val Outline = Color(0xFFE1D9CD)
val TextMuted = Color(0xFF767676)
```

- [x] **Step 2: Reduce decorative shape dominance**

In `ThemedBackground(...)`, change:

```kotlin
val blobSize = kotlin.math.max(sizeBase * 0.85f, 240.dp.toPx())
val glowSize = kotlin.math.max(sizeBase * 0.7f, 200.dp.toPx())
```

to:

```kotlin
val blobSize = kotlin.math.max(sizeBase * 0.58f, 180.dp.toPx())
val glowSize = kotlin.math.max(sizeBase * 0.48f, 160.dp.toPx())
```

- [x] **Step 3: Replace bottom nav text-only buttons with icon actions**

In `BottomNav(...)`, replace each `RoundedButton(...)` with `IconPillAction(...)`:

```kotlin
IconPillAction(
    icon = Icons.Outlined.FormatListBulleted,
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
```

- [x] **Step 4: Compile themed navigation**

Run from `android/`:

```powershell
.\gradlew.bat compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

---

## Task 7: Final Verification And Visual Smoke Check

**Files:**
- Verify: `android/app/build.gradle.kts`
- Verify: `android/app/src/main/java/com/zaduzenja/app/UiCopy.kt`
- Verify: `android/app/src/test/java/com/zaduzenja/app/UiCopyTest.kt`
- Verify: `android/app/src/main/java/com/zaduzenja/app/Theme.kt`
- Verify: `android/app/src/main/java/com/zaduzenja/app/App.kt`

**Security flag:** `none`

**Does NOT cover:** Does not validate live Google Drive backup, live notification delivery, or user-specific device settings.

- [x] **Step 1: Run full local verification**

Run from `android/`:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [x] **Step 2: Check target files for accidental business-logic edits**

Run from repository root:

```powershell
rg -n "nextNotificationWindow|isAvailabilityNotificationWindowOpen|scheduleAvailabilityNotifications|attemptAssignment|importJson|exportJson|GoogleDriveBackup" android/app/src/main/java/com/zaduzenja/app
```

Expected: Matches may appear in existing files, but implementation should not have changed `Notifications.kt`, `Data.kt`, or `GoogleDriveBackup.kt` for this plan.

- [x] **Step 3: Manual visual smoke check on device or emulator**

Run from `android/`:

```powershell
.\gradlew.bat installDebug
```

Expected if a device/emulator is connected: install succeeds. Open the app and verify:
- Main header shows count plus icon actions for first shift, test notification, and backup.
- Cloud action still opens the backup dialog.
- First-shift action still opens the first-shift dialog.
- Notification test still shows the existing info dialog when notifications are disabled.
- Assignment cards show `ZADUŽI` only when available, and otherwise show `Dostupno od dd.mm.yyyy`.
- History filters show short labels on compact layout and full names on non-compact layout.
- Delete action is at least 44dp and uses a trash icon.
- Bottom navigation has list/history icons and preserves current navigation behavior.
- Decorative background does not overpower the list content.

If no device/emulator is connected, record that visual smoke check was not run; do not claim visual verification.

Result in this run: `.\gradlew.bat installDebug` was attempted and failed with `No connected devices!`, so live visual smoke testing was not performed.

---

## Self-Review

- Spec coverage: Header, cloud action, card hierarchy, disabled assignment action, history filters, delete touch target, bottom navigation icons, and background reduction are each covered by a task.
- Data safety: No task modifies `Data.kt`, `Notifications.kt`, Room entities, backup code, assets, or notification tests.
- Test coverage: New string/label helpers have JUnit coverage; visual Compose changes are covered by compile/lint/build plus manual smoke check.
- Scope scan: Plan does not add new features, routes, persistence fields, notification timing, or backup behavior.
