package com.honglu.typing.data

import androidx.room.*

/**
 * Room DAO for typing records.
 */
@Dao
interface RecordDao {
    @Insert
    suspend fun insert(record: RecordEntity): Long

    @Query("SELECT * FROM records ORDER BY date DESC")
    suspend fun getAll(): List<RecordEntity>

    @Query("DELETE FROM records")
    suspend fun clearAll()

    @Query("SELECT AVG(accuracy) FROM records WHERE mode = :mode")
    suspend fun avgAccuracy(mode: String): Float?
}
