package com.zaduzenja.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "history_entries",
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["article_type"],
            childColumns = ["article_type"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["article_type"])]
)
data class HistoryEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "article_type")
    val articleType: String,     // FK ka articles tabeli

    @ColumnInfo(name = "date")
    val date: String,            // ISO format "yyyy-MM-dd"

    @ColumnInfo(name = "size")
    val size: String? = null     // npr. "43", "XL", null
)
