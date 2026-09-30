package com.zaduzenja.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey
    @ColumnInfo(name = "article_type")
    val articleType: String,     // "rukavice", "maxicut", "majica", "cipele", "odelo"

    @ColumnInfo(name = "last_assignment")
    val lastAssignment: String?  // ISO format "yyyy-MM-dd" ili null
)
