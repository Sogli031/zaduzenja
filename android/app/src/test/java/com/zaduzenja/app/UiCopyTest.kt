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

    @Test
    fun firstShiftReferenceHintInvitesTappingCurrentCard() {
        assertEquals(
            "Ciklus je fiksno 3-2-1. Dodirni ovde ako ti promene raspored.",
            firstShiftReferenceHintText()
        )
    }
}
