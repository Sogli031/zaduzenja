package com.zaduzenja.app.data.db

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val TAG = "JsonToRoomMigration"
private const val PREFS_NAME = "zaduzenja_migration"
private const val KEY_MIGRATED = "json_to_room_done"

/**
 * Jednokratna migracija iz z.json u Room bazu.
 * Čuva flag u SharedPreferences da se ne izvršava ponovo.
 */
suspend fun migrateJsonToRoom(context: Context, dao: ArticleDao) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    if (prefs.getBoolean(KEY_MIGRATED, false)) {
        return // Već migrirano
    }

    val file = File(context.filesDir, "z.json")
    if (!file.exists()) {
        // Nema JSON fajla — kopiraj iz assets pa migriraj
        try {
            context.assets.open("z.json").use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Neuspelo kopiranje assets/z.json", e)
            initializeEmptyRoom(dao)
            prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
            return
        }
    }

    try {
        val raw = file.readText(Charsets.UTF_8)
        val json = JSONObject(raw)
        val articleTypes = listOf("rukavice", "maxicut", "majica", "cipele", "odelo")

        for (type in articleTypes) {
            val obj = json.optJSONObject(type)
            val lastStr = obj?.optString("last_assignment")
            val lastAssignment = if (lastStr.isNullOrBlank() || lastStr == "null") null else lastStr

            // Ubaci article (IGNORE: postojeći red se ne dira, da se ne obriše njegova istorija)
            dao.insertArticleIfMissing(ArticleEntity(
                articleType = type,
                lastAssignment = lastAssignment
            ))

            // Ubaci history entries
            val historyArray = obj?.optJSONArray("history") ?: JSONArray()
            val entries = mutableListOf<HistoryEntryEntity>()
            for (i in 0 until historyArray.length()) {
                val item = historyArray.get(i)
                if (item is JSONObject) {
                    val date = item.getString("date")
                    val size = item.optString("size", "").ifBlank { null }
                    entries.add(HistoryEntryEntity(
                        articleType = type,
                        date = date,
                        size = size
                    ))
                } else {
                    // Stari format — niz stringova
                    val dateStr = item.toString()
                    if (dateStr.isNotBlank() && dateStr != "null") {
                        entries.add(HistoryEntryEntity(
                            articleType = type,
                            date = dateStr
                        ))
                    }
                }
            }
            if (entries.isNotEmpty()) {
                dao.insertHistoryEntries(entries)
            }
        }

        prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
        Log.i(TAG, "Migracija JSON → Room završena uspešno")

    } catch (e: Exception) {
        Log.e(TAG, "Greška pri migraciji", e)
        // Ako je puklo, inicijalizuj prazno
        initializeEmptyRoom(dao)
        prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
    }
}

private suspend fun initializeEmptyRoom(dao: ArticleDao) {
    val types = listOf("rukavice", "maxicut", "majica", "cipele", "odelo")
    dao.insertArticlesIfMissing(types.map { ArticleEntity(articleType = it, lastAssignment = null) })
}
