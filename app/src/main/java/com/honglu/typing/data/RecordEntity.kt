package com.honglu.typing.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Typing session record entity.
 */
@Entity(tableName = "records")
data class RecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mode: String,              // "primary" / "advanced"
    val contentType: String,       // "en_short" / "cn_paragraph" / "mixed"
    val wpm: Float,                // 0 if Chinese
    val cpm: Float,                // 0 if English
    val accuracy: Float,           // 0~100
    val score: Int,
    val totalKeystrokes: Int,
    val correctKeystrokes: Int,
    val date: Long                 // epoch millis
)
