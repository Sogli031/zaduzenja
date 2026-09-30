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

fun firstShiftReferenceHintText(): String {
    return "Ciklus je fiksno 3-2-1. Dodirni ovde ako ti promene raspored."
}
