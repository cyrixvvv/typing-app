package com.honglu.typing.engine

import android.content.Context
import android.content.SharedPreferences
import androidx.room.*
import com.honglu.typing.data.AppDatabase
import com.honglu.typing.data.RecordEntity

/**
 * Score manager: tracks total score, streak, and persists records to Room DB.
 */
class ScoreManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("typing_stats", Context.MODE_PRIVATE)
    private val db: AppDatabase = AppDatabase.getInstance(context)
    private val dao = db.recordDao()

    /**
     * Record a typing session result.
     */
    suspend fun recordResult(
        mode: String,
        contentType: String,
        wpm: Float,
        cpm: Float,
        accuracy: Float,
        score: Int,
        totalKeystrokes: Int,
        correctKeystrokes: Int
    ) {
        val record = RecordEntity(
            mode = mode,
            contentType = contentType,
            wpm = wpm,
            cpm = cpm,
            accuracy = accuracy,
            score = score,
            totalKeystrokes = totalKeystrokes,
            correctKeystrokes = correctKeystrokes,
            date = System.currentTimeMillis()
        )
        dao.insert(record)

        // Update running totals
        val currentTotal = prefs.getInt("total_score", 0)
        prefs.edit().putInt("total_score", currentTotal + score).apply()

        // Track best streak
        val bestStreak = prefs.getInt("best_streak", 0)
        if (accuracy >= 95) {
            prefs.edit().putInt("best_streak", maxOf(bestStreak, correctKeystrokes)).apply()
        }

        // Track practice time (inferred from keystrokes, roughly 1 keystroke = 0.5 seconds)
        val totalTime = prefs.getLong("total_practice_time", 0)
        prefs.edit().putLong("total_practice_time", totalTime + (totalKeystrokes * 500L)).apply()
    }

    fun getTotalScore(): Int {
        return prefs.getInt("total_score", 0)
    }

    fun getBestStreak(): Int {
        return prefs.getInt("best_streak", 0)
    }

    /**
     * Clear all data: Room records + SharedPreferences stats.
     */
    suspend fun clearAllData() {
        dao.clearAll()
        prefs.edit().clear()
            .putInt("total_score", 0)
            .putInt("best_streak", 0)
            .putLong("total_practice_time", 0)
            .apply()
    }

    fun getAllRecords(): List<RecordEntity> {
        return dao.getAll()
    }

    fun avgAccuracyForMode(mode: String): Float {
        return dao.avgAccuracy(mode) ?: 0f
    }
}
