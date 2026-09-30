package com.zaduzenja.app

import com.zaduzenja.app.data.backup.BackupFilePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

class BackupPayloadCodecTest {
    private val populated = BackupPayload(
        records = mapOf(
            ArticleType.RUKAVICE to ArticleRecord(
                lastAssignment = LocalDate.of(2026, 9, 1),
                history = listOf(
                    HistoryEntry(LocalDate.of(2026, 8, 3)),
                    HistoryEntry(LocalDate.of(2026, 9, 1))
                )
            ),
            ArticleType.MAXICUT to ArticleRecord(),
            ArticleType.MAJICA to ArticleRecord(
                lastAssignment = LocalDate.of(2026, 5, 4),
                history = listOf(HistoryEntry(LocalDate.of(2026, 5, 4), size = "XL"))
            ),
            ArticleType.CIPELE to ArticleRecord(
                lastAssignment = LocalDate.of(2025, 12, 1),
                history = listOf(
                    HistoryEntry(LocalDate.of(2024, 12, 2), size = "43"),
                    HistoryEntry(LocalDate.of(2025, 12, 1), size = "44")
                )
            ),
            ArticleType.ODELO to ArticleRecord(
                // dva zapisa istog dana: redosled mora da ostane isti
                lastAssignment = LocalDate.of(2026, 1, 5),
                history = listOf(
                    HistoryEntry(LocalDate.of(2026, 1, 5), size = "52"),
                    HistoryEntry(LocalDate.of(2026, 1, 5), size = "54")
                )
            )
        ),
        hasSettings = true,
        firstShiftReferenceDate = LocalDate.of(2026, 3, 23)
    )

    @Test
    fun `export - import - export gives identical state`() {
        val firstExport = BackupPayloadCodec.toJson(populated)
        val imported = BackupPayloadCodec.parse(firstExport)
        val secondExport = BackupPayloadCodec.toJson(imported)

        assertEquals(populated, imported)
        assertEquals(firstExport, secondExport)
    }

    @Test
    fun `unset first shift date round-trips as unset`() {
        val payload = populated.copy(firstShiftReferenceDate = null)

        val imported = BackupPayloadCodec.parse(BackupPayloadCodec.toJson(payload))

        assertTrue(imported.hasSettings)
        assertNull(imported.firstShiftReferenceDate)
        assertEquals(payload, imported)
    }

    @Test
    fun `old backup without settings still restores and leaves settings alone`() {
        val oldFormat = """
            {
              "rukavice": {"last_assignment": "2026-09-01", "history": [{"date": "2026-09-01"}]},
              "maxicut": {"history": []},
              "majica": {"last_assignment": "2026-05-04", "history": [{"date": "2026-05-04", "size": "XL"}]},
              "cipele": {"last_assignment": null, "history": []},
              "odelo": {"last_assignment": "05.01.2026", "history": ["05.01.2026"]}
            }
        """.trimIndent()

        val imported = BackupPayloadCodec.parse(oldFormat)

        assertFalse(imported.hasSettings)
        assertNull(imported.firstShiftReferenceDate)
        assertEquals(LocalDate.of(2026, 9, 1), imported.records.getValue(ArticleType.RUKAVICE).lastAssignment)
        assertEquals(
            listOf(HistoryEntry(LocalDate.of(2026, 5, 4), size = "XL")),
            imported.records.getValue(ArticleType.MAJICA).history
        )
        assertNull(imported.records.getValue(ArticleType.CIPELE).lastAssignment)
        // stari format: datumi kao dd.MM.yyyy i istorija kao niz stringova
        assertEquals(
            ArticleRecord(LocalDate.of(2026, 1, 5), listOf(HistoryEntry(LocalDate.of(2026, 1, 5)))),
            imported.records.getValue(ArticleType.ODELO)
        )
    }

    @Test
    fun `backup missing one article type restores it as empty`() {
        val imported = BackupPayloadCodec.parse(
            """{"rukavice": {"last_assignment": "2026-09-01", "history": [{"date": "2026-09-01"}]}}"""
        )

        assertEquals(ArticleRecord(), imported.records.getValue(ArticleType.ODELO))
        assertEquals(1, imported.records.getValue(ArticleType.RUKAVICE).history.size)
    }

    @Test
    fun `invalid payloads fail as a whole instead of being partially imported`() {
        val invalid = listOf(
            "ovo nije json",
            "[]",
            "{}",
            """{"error": {"code": 401}}""",
            """{"rukavice": "nije objekat"}""",
            """{"rukavice": {"history": "nije niz"}}""",
            """{"rukavice": {"last_assignment": "nije-datum", "history": []}}""",
            """{"rukavice": {"history": [{"date": "2026-09-01"}, {"date": "31.02.2026x"}]}}""",
            """{"rukavice": {"history": [{"size": "43"}]}}""",
            """{"rukavice": {"history": ["nije-datum"]}}""",
            """{"rukavice": {"history": [42]}}""",
            """{"rukavice": {"history": []}, "settings": "nije objekat"}""",
            """{"rukavice": {"history": []}, "settings": {"first_shift_reference_date": "nije-datum"}}"""
        )

        invalid.forEach { raw ->
            try {
                BackupPayloadCodec.parse(raw)
                fail("Ocekivan neuspeh za: $raw")
            } catch (expected: BackupFormatException) {
                assertFalse(expected.message.isNullOrBlank())
            }
        }
    }

    @Test
    fun `empty payload is recognised as empty`() {
        val empty = BackupPayload(records = ArticleType.values().associateWith { ArticleRecord() })

        assertTrue(empty.isEmpty)
        assertTrue(BackupPayloadCodec.parse(BackupPayloadCodec.toJson(empty)).isEmpty)
        assertFalse(populated.isEmpty)
    }

    @Test
    fun `prev copy names round-trip and only oldest copies are pruned`() {
        val main = "zaduzenja_backup.json"
        val name = BackupFilePolicy.prevCopyName(main, 1700000000000L)

        assertEquals("zaduzenja_backup_prev_1700000000000.json", name)
        assertEquals(1700000000000L, BackupFilePolicy.prevCopyTimestamp(main, name))
        assertNull(BackupFilePolicy.prevCopyTimestamp(main, main))
        assertNull(BackupFilePolicy.prevCopyTimestamp(main, "zaduzenja_backup_prev_x.json"))

        val files = (1..8).map { "id$it" to BackupFilePolicy.prevCopyName(main, it * 1000L) } + ("main" to main)
        assertEquals(setOf("id1", "id2", "id3"), BackupFilePolicy.prevCopiesToDelete(main, files.shuffled()).toSet())
        assertTrue(BackupFilePolicy.prevCopiesToDelete(main, files.take(5)).isEmpty())
    }
}
