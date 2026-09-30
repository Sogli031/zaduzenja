package com.zaduzenja.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ArticleDao {

    @Query("SELECT * FROM articles WHERE article_type = :type")
    suspend fun getArticle(type: String): ArticleEntity?

    @Query("SELECT * FROM articles")
    suspend fun getAllArticles(): List<ArticleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticle(article: ArticleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticles(articles: List<ArticleEntity>)

    // Za migraciju: nikad REPLACE, jer REPLACE briše red pa CASCADE briše i njegovu istoriju
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArticleIfMissing(article: ArticleEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArticlesIfMissing(articles: List<ArticleEntity>)

    @Query("UPDATE articles SET last_assignment = :lastAssignment WHERE article_type = :type")
    suspend fun updateLastAssignment(type: String, lastAssignment: String?)

    // History entries

    @Query("SELECT * FROM history_entries WHERE article_type = :type ORDER BY date ASC")
    suspend fun getHistory(type: String): List<HistoryEntryEntity>

    @Query("SELECT * FROM history_entries ORDER BY date ASC")
    suspend fun getAllHistory(): List<HistoryEntryEntity>

    @Insert
    suspend fun insertHistoryEntry(entry: HistoryEntryEntity): Long

    @Insert
    suspend fun insertHistoryEntries(entries: List<HistoryEntryEntity>)

    @Query("DELETE FROM history_entries WHERE id = :id")
    suspend fun deleteHistoryEntry(id: Long)

    @Query("DELETE FROM history_entries WHERE article_type = :type AND date = :date AND (size = :size OR (size IS NULL AND :size IS NULL))")
    suspend fun deleteHistoryEntryByContent(type: String, date: String, size: String?)

    @Query("SELECT * FROM history_entries WHERE article_type = :type ORDER BY date DESC LIMIT 1")
    suspend fun getLastHistoryEntry(type: String): HistoryEntryEntity?

    @Query("DELETE FROM history_entries")
    suspend fun deleteAllHistory()

    @Query("DELETE FROM history_entries WHERE article_type = :type")
    suspend fun deleteHistoryForType(type: String)

    /** Zamena jednog artikla i cele njegove istorije - sve ili ništa. */
    @Transaction
    suspend fun replaceRecord(article: ArticleEntity, history: List<HistoryEntryEntity>) {
        deleteHistoryForType(article.articleType)
        insertArticle(article)
        if (history.isNotEmpty()) {
            insertHistoryEntries(history)
        }
    }

    /** Restore iz backup-a: zamena kompletnog stanja u jednoj transakciji. */
    @Transaction
    suspend fun replaceAll(articles: List<ArticleEntity>, history: List<HistoryEntryEntity>) {
        deleteAllHistory()
        insertArticles(articles)
        if (history.isNotEmpty()) {
            insertHistoryEntries(history)
        }
    }

    // Za migraciju - provera da li baza već ima podatke
    @Query("SELECT COUNT(*) FROM articles")
    suspend fun getArticleCount(): Int
}
