package com.zaduzenja.app

import android.content.Context
import android.util.Log
import com.zaduzenja.app.data.backup.BackupScheduler
import com.zaduzenja.app.data.db.ArticleDao
import com.zaduzenja.app.data.db.ArticleEntity
import com.zaduzenja.app.data.db.HistoryEntryEntity
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private const val TAG = "DataRepository"

enum class ArticleType(val displayName: String, val periodMonths: Int, val key: String) {
    RUKAVICE("Rukavice", 1, "rukavice"),
    MAXICUT("MaxiCut rukavice", 2, "maxicut"),
    MAJICA("Majica", 4, "majica"),
    CIPELE("Cipele", 12, "cipele"),
    ODELO("Odelo", 12, "odelo");

    val availableSizes: List<String>?
        get() = SizeOptions[this]

    val hasSizes: Boolean
        get() = availableSizes != null

    companion object {
        fun fromKey(key: String): ArticleType? {
            return values().firstOrNull { it.key == key }
        }
    }
}

private val SizeOptions = mapOf(
    ArticleType.CIPELE to listOf("39", "40", "41", "42", "43", "44", "45", "46"),
    ArticleType.ODELO to listOf("48", "50", "52", "54", "56", "58"),
    ArticleType.MAJICA to listOf("S", "M", "L", "XL", "XXL", "XXXL")
)

data class HistoryEntry(
    val date: LocalDate,
    val size: String? = null
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("date", date.format(DateUtils.isoFormatter))
        if (size != null) {
            obj.put("size", size)
        }
        return obj
    }

    companion object {
        // Neispravan zapis je greška (BackupFormatException) - nikad se ne zamenjuje današnjim datumom
        fun fromJson(obj: JSONObject): HistoryEntry {
            val dateStr = obj.opt("date") as? String
                ?: throw BackupFormatException("Stavka istorije nema datum.")
            val date = DateUtils.parseDate(dateStr)
                ?: throw BackupFormatException("Neispravan datum u istoriji: $dateStr")
            val size = if (obj.isNull("size")) null else obj.optString("size", "").ifBlank { null }
            return HistoryEntry(date = date, size = size)
        }
    }
}

/** Backup ne može da se pročita ili sačuva u celini. Poruka je namenjena korisniku. */
class BackupFormatException(message: String) : Exception(message)

data class ArticleRecord(
    val lastAssignment: LocalDate? = null,
    val history: List<HistoryEntry> = emptyList()
) {
    val lastSize: String?
        get() = history.maxByOrNull { it.date }?.size

    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("last_assignment", lastAssignment?.format(DateUtils.isoFormatter))
        val historyArray = JSONArray()
        history.forEach { historyArray.put(it.toJson()) }
        obj.put("history", historyArray)
        return obj
    }

    companion object {
        // Strogo parsiranje: sve što ne može da se pročita je greška, ništa se tiho ne preskače
        fun fromJson(obj: JSONObject): ArticleRecord {
            val last = if (obj.isNull("last_assignment")) {
                null
            } else {
                val lastStr = obj.opt("last_assignment") as? String
                    ?: throw BackupFormatException("Neispravan datum poslednjeg zaduženja.")
                if (lastStr.isBlank() || lastStr == "null") {
                    null
                } else {
                    DateUtils.parseDate(lastStr)
                        ?: throw BackupFormatException("Neispravan datum poslednjeg zaduženja: $lastStr")
                }
            }

            val historyArray = if (obj.isNull("history")) {
                JSONArray()
            } else {
                obj.optJSONArray("history")
                    ?: throw BackupFormatException("Istorija zaduženja nije u ispravnom obliku.")
            }
            val historyList = mutableListOf<HistoryEntry>()
            for (i in 0 until historyArray.length()) {
                val item = historyArray.get(i)
                if (item is JSONObject) {
                    historyList.add(HistoryEntry.fromJson(item))
                } else if (item is String) {
                    // Kompatibilnost sa starim formatom (niz stringova)
                    val date = DateUtils.parseDate(item)
                        ?: throw BackupFormatException("Neispravan datum u istoriji: $item")
                    historyList.add(HistoryEntry(date = date))
                } else {
                    throw BackupFormatException("Neispravna stavka istorije zaduženja.")
                }
            }

            return ArticleRecord(lastAssignment = last, history = historyList)
        }
    }
}

/**
 * Sve što ulazi u cloud backup. [hasSettings] je false samo za starije backup-e koji nemaju
 * odeljak "settings" - tada se podešavanja na uređaju ne diraju.
 */
data class BackupPayload(
    val records: Map<ArticleType, ArticleRecord>,
    val hasSettings: Boolean = false,
    val firstShiftReferenceDate: LocalDate? = null
) {
    val isEmpty: Boolean
        get() = records.values.all { it.lastAssignment == null && it.history.isEmpty() }
}

object BackupPayloadCodec {
    private const val SETTINGS_KEY = "settings"
    private const val FIRST_SHIFT_REFERENCE_KEY = "first_shift_reference_date"

    fun toJson(payload: BackupPayload, pretty: Boolean = true): String {
        val root = JSONObject()
        for (type in ArticleType.values()) {
            root.put(type.key, (payload.records[type] ?: ArticleRecord()).toJson())
        }
        // Stariji backup-i nemaju "settings"; stare verzije aplikacije ga ignorišu
        val settings = JSONObject()
        payload.firstShiftReferenceDate?.let {
            settings.put(FIRST_SHIFT_REFERENCE_KEY, it.format(DateUtils.isoFormatter))
        }
        root.put(SETTINGS_KEY, settings)
        return if (pretty) root.toString(2) else root.toString()
    }

    /** Parsira i proverava CEO backup pre bilo kakvog upisa. Baca [BackupFormatException]. */
    fun parse(raw: String): BackupPayload {
        val root = try {
            JSONObject(raw)
        } catch (e: org.json.JSONException) {
            throw BackupFormatException("Backup nije u ispravnom formatu.")
        }

        if (ArticleType.values().none { root.has(it.key) }) {
            throw BackupFormatException("Backup ne sadrži podatke o zaduženjima.")
        }

        val records = ArticleType.values().associateWith { type ->
            if (!root.has(type.key)) {
                ArticleRecord() // stariji backup napravljen pre uvođenja ovog artikla
            } else {
                val obj = root.optJSONObject(type.key)
                    ?: throw BackupFormatException("Neispravan odeljak \"${type.displayName}\" u backup-u.")
                try {
                    ArticleRecord.fromJson(obj)
                } catch (e: BackupFormatException) {
                    throw BackupFormatException("${type.displayName}: ${e.message}")
                }
            }
        }

        if (!root.has(SETTINGS_KEY)) {
            return BackupPayload(records = records)
        }
        val settings = root.optJSONObject(SETTINGS_KEY)
            ?: throw BackupFormatException("Neispravan odeljak podešavanja u backup-u.")
        val firstShift = if (settings.isNull(FIRST_SHIFT_REFERENCE_KEY)) {
            null
        } else {
            val value = settings.opt(FIRST_SHIFT_REFERENCE_KEY) as? String
            value?.let { DateUtils.parseDate(it) }
                ?: throw BackupFormatException("Neispravan datum prve smene u backup-u.")
        }
        return BackupPayload(records = records, hasSettings = true, firstShiftReferenceDate = firstShift)
    }
}

object DateUtils {
    private val displayFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val isoFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun formatDate(date: LocalDate?): String {
        return date?.format(displayFormatter) ?: "-"
    }

    fun parseDate(dateStr: String?): LocalDate? {
        if (dateStr.isNullOrBlank() || dateStr == "null") {
            return null
        }
        val formatters = listOf(isoFormatter, displayFormatter)
        for (formatter in formatters) {
            try {
                return LocalDate.parse(dateStr, formatter)
            } catch (_: DateTimeParseException) {
            }
        }
        return null
    }

    fun firstOfMonth(date: LocalDate): LocalDate {
        return date.withDayOfMonth(1)
    }

    fun addMonths(date: LocalDate, months: Int): LocalDate {
        return date.plusMonths(months.toLong()).withDayOfMonth(1)
    }

    fun calculateNextAllowed(last: LocalDate?, periodMonths: Int, today: LocalDate): LocalDate {
        return if (last == null) {
            firstOfMonth(today)
        } else {
            addMonths(last, periodMonths)
        }
    }
}

class DataRepository(private val context: Context, private val dao: ArticleDao) {
    private var onChangeCallback: (() -> Unit)? = null

    fun onChange(callback: () -> Unit) {
        onChangeCallback = callback
    }

    // --- Sinhroni metodi (za TrackingViewModel kompatibilnost) ---
    // TrackingViewModel poziva getRecord/getAllRecords/updateRecord sinhrono,
    // pa koristimo runBlocking za bridge.

    fun getRecord(articleType: ArticleType): ArticleRecord {
        return kotlinx.coroutines.runBlocking {
            val entity = dao.getArticle(articleType.key)
            val historyEntities = dao.getHistory(articleType.key)
            toArticleRecord(entity, historyEntities)
        }
    }

    fun getAllRecords(): Map<ArticleType, ArticleRecord> {
        return kotlinx.coroutines.runBlocking {
            val result = mutableMapOf<ArticleType, ArticleRecord>()
            for (type in ArticleType.values()) {
                val entity = dao.getArticle(type.key)
                val historyEntities = dao.getHistory(type.key)
                result[type] = toArticleRecord(entity, historyEntities)
            }
            result
        }
    }

    fun updateRecord(articleType: ArticleType, record: ArticleRecord) {
        kotlinx.coroutines.runBlocking {
            // Jedna transakcija: article + kompletna istorija za ovaj tip
            dao.replaceRecord(toArticleEntity(articleType, record), toHistoryEntities(articleType, record))
        }
        onChangeCallback?.invoke()
        // Svaka izmena zakazuje i backup u oblak; REPLACE politika spoji niz brzih
        // izmena u jedan posao. applyBackup() namerno ne zakazuje - vraćanje iz
        // oblaka ne treba da se odmah vrati nazad u oblak.
        BackupScheduler.scheduleAfterChange(context)
    }

    // save() i saveIfDirty() više nisu potrebni jer Room automatski čuva,
    // ali zadržavamo ih kao prazne metode za kompatibilnost
    fun save() { /* Room auto-persist */ }
    fun saveIfDirty() { /* Room auto-persist */ }

    // --- JSON export/import za Google Drive backup ---

    /** Nema nijednog zaduženja (sveža instalacija) - restore tada ne traži potvrdu. */
    fun isEmpty(): Boolean {
        return getAllRecords().values.all { it.lastAssignment == null && it.history.isEmpty() }
    }

    /** Sve što ide u backup: zaduženja iz baze + podešavanja iz SharedPreferences. */
    fun buildBackupPayload(): BackupPayload {
        val records = kotlinx.coroutines.runBlocking {
            ArticleType.values().associateWith { type ->
                // strict: neispravan datum u bazi obara backup umesto da se tiho zameni
                toArticleRecord(dao.getArticle(type.key), dao.getHistory(type.key), strict = true)
            }
        }
        return BackupPayload(
            records = records,
            hasSettings = true,
            firstShiftReferenceDate = getStoredFirstShiftReferenceDate(context)
        )
    }

    fun exportJson(pretty: Boolean = true): String {
        return BackupPayloadCodec.toJson(buildBackupPayload(), pretty)
    }

    fun parseBackup(raw: String): BackupPayload = BackupPayloadCodec.parse(raw)

    fun importJson(raw: String) {
        applyBackup(parseBackup(raw))
    }

    /**
     * Zamenjuje lokalno stanje sadržajem backup-a: sve ili ništa. Baza se menja u jednoj
     * transakciji; ako ona ne uspe, vraća se i prethodno podešavanje prve smene.
     * Baca izuzetak ako bilo šta ne uspe.
     */
    fun applyBackup(payload: BackupPayload) {
        val previousReference = getStoredFirstShiftReferenceDate(context)
        if (payload.hasSettings &&
            !storeFirstShiftReferenceDate(context, payload.firstShiftReferenceDate)
        ) {
            throw BackupFormatException("Podešavanje prve smene nije sačuvano.")
        }

        try {
            kotlinx.coroutines.runBlocking {
                dao.replaceAll(
                    articles = ArticleType.values().map { type ->
                        toArticleEntity(type, payload.records[type] ?: ArticleRecord())
                    },
                    history = ArticleType.values().flatMap { type ->
                        toHistoryEntities(type, payload.records[type] ?: ArticleRecord())
                    }
                )
            }
        } catch (e: Exception) {
            if (payload.hasSettings) {
                storeFirstShiftReferenceDate(context, previousReference)
            }
            throw e
        }

        // Obaveštenja zavise od prve smene i od vraćenih zaduženja
        scheduleAvailabilityNotifications(context)
        onChangeCallback?.invoke()
    }

    // --- Pomoćne funkcije ---

    private fun toArticleEntity(type: ArticleType, record: ArticleRecord): ArticleEntity {
        return ArticleEntity(
            articleType = type.key,
            lastAssignment = record.lastAssignment?.format(DateUtils.isoFormatter)
        )
    }

    private fun toHistoryEntities(type: ArticleType, record: ArticleRecord): List<HistoryEntryEntity> {
        return record.history.map { entry ->
            HistoryEntryEntity(
                articleType = type.key,
                date = entry.date.format(DateUtils.isoFormatter),
                size = entry.size
            )
        }
    }

    private fun toArticleRecord(
        entity: ArticleEntity?,
        historyEntities: List<HistoryEntryEntity>,
        strict: Boolean = false
    ): ArticleRecord {
        val lastAssignment = entity?.lastAssignment?.takeUnless { it.isBlank() || it == "null" }?.let { raw ->
            DateUtils.parseDate(raw)
                ?: if (strict) throw BackupFormatException("Neispravan datum u bazi: $raw") else null
        }
        val history = historyEntities.map { e ->
            val date = DateUtils.parseDate(e.date)
                ?: if (strict) {
                    throw BackupFormatException("Neispravan datum u bazi: ${e.date}")
                } else {
                    // Samo za prikaz; backup (strict) ovakav zapis prijavljuje kao grešku
                    LocalDate.now()
                }
            HistoryEntry(date = date, size = e.size)
        }
        return ArticleRecord(lastAssignment = lastAssignment, history = history)
    }
}

data class ArticleStatus(
    val articleType: ArticleType,
    val lastAssignment: LocalDate?,
    val nextAllowed: LocalDate,
    val canAssignNow: Boolean,
    val history: List<HistoryEntry>,
    val lastSize: String? = null
)

enum class AssignmentResult {
    SUCCESS,
    NOT_ALLOWED_YET,
    INVALID_DATE
}

data class AssignmentResponse(
    val result: AssignmentResult,
    val message: String,
    val nextAllowed: LocalDate? = null
)

class TrackingViewModel(private val repository: DataRepository) {
    private var today: LocalDate = LocalDate.now()

    val todayDate: LocalDate
        get() = today

    fun refreshToday() {
        today = LocalDate.now()
    }

    fun getAllStatuses(): List<ArticleStatus> {
        val statuses = mutableListOf<ArticleStatus>()
        val records = repository.getAllRecords()
        for ((type, record) in records) {
            val nextAllowed = DateUtils.calculateNextAllowed(
                record.lastAssignment,
                type.periodMonths,
                today
            )
            statuses.add(
                ArticleStatus(
                    articleType = type,
                    lastAssignment = record.lastAssignment,
                    nextAllowed = nextAllowed,
                    canAssignNow = today >= nextAllowed,
                    history = record.history.toList(),
                    lastSize = record.lastSize
                )
            )
        }
        return statuses
    }

    fun getStatus(articleType: ArticleType): ArticleStatus {
        val record = repository.getRecord(articleType)
        val nextAllowed = DateUtils.calculateNextAllowed(
            record.lastAssignment,
            articleType.periodMonths,
            today
        )
        return ArticleStatus(
            articleType = articleType,
            lastAssignment = record.lastAssignment,
            nextAllowed = nextAllowed,
            canAssignNow = today >= nextAllowed,
            history = record.history.toList(),
            lastSize = record.lastSize
        )
    }

    fun attemptAssignment(articleType: ArticleType, assignmentDate: LocalDate? = null, size: String? = null): AssignmentResponse {
        refreshToday()
        val dateToUse = assignmentDate ?: today

        if (dateToUse > today) {
            return AssignmentResponse(
                result = AssignmentResult.INVALID_DATE,
                message = "Datum u budućnosti nije dozvoljen."
            )
        }

        val record = repository.getRecord(articleType)
        val nextAllowed = DateUtils.calculateNextAllowed(
            record.lastAssignment,
            articleType.periodMonths,
            dateToUse
        )

        if (today < nextAllowed || dateToUse < nextAllowed) {
            return AssignmentResponse(
                result = AssignmentResult.NOT_ALLOWED_YET,
                message = "Zaduženje za ${articleType.displayName} nije moguće.",
                nextAllowed = nextAllowed
            )
        }

        val entry = HistoryEntry(date = dateToUse, size = size)
        val newRecord = ArticleRecord(
            lastAssignment = dateToUse,
            history = record.history + entry
        )
        repository.updateRecord(articleType, newRecord)
        repository.save()

        return AssignmentResponse(
            result = AssignmentResult.SUCCESS,
            message = "Uspešno zaduženo: ${articleType.displayName}"
        )
    }

    fun getHistory(articleType: ArticleType): List<HistoryEntry> {
        val record = repository.getRecord(articleType)
        return record.history.sortedByDescending { it.date }
    }

    fun deleteHistoryEntry(articleType: ArticleType, entry: HistoryEntry): Boolean {
        val record = repository.getRecord(articleType)
        val history = record.history.toMutableList()
        val removed = history.remove(entry)
        if (!removed) {
            return false
        }

        val newLast = history.maxByOrNull { it.date }?.date
        val newRecord = ArticleRecord(
            lastAssignment = newLast,
            history = history
        )
        repository.updateRecord(articleType, newRecord)
        repository.save()
        return true
    }

    fun editHistoryEntry(
        articleType: ArticleType,
        oldEntry: HistoryEntry,
        newDate: LocalDate,
        newSize: String?
    ): Boolean {
        val record = repository.getRecord(articleType)
        val history = record.history.toMutableList()
        val index = history.indexOf(oldEntry)
        if (index == -1) {
            return false
        }

        history[index] = HistoryEntry(date = newDate, size = newSize)
        val newLast = history.maxByOrNull { it.date }?.date
        val newRecord = ArticleRecord(
            lastAssignment = newLast,
            history = history
        )
        repository.updateRecord(articleType, newRecord)
        repository.save()
        return true
    }
}
