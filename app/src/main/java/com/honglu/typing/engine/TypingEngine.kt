package com.honglu.typing.engine

import kotlin.math.max

/**
 * Typing engine: core logic for tracking typing progress,
 * matching keystrokes, calculating WPM/CPM and accuracy.
 */
class TypingEngine {

    enum class Mode { PRIMARY, ADVANCED }

    var currentText: String = ""
        internal set
    var currentIndex: Int = 0
        internal set
    var startTime: Long = 0
        private set
    var isRunning: Boolean = false
        private set

    var mode: Mode = Mode.PRIMARY

    // Stats
    var totalKeystrokes = 0
        internal set
    var correctKeystrokes = 0
        internal set
    var wrongKeystrokes = 0
        private set
    var consecutiveCorrect = 0
        private set

    /**
     * Start typing with the given text.
     */
    fun start(text: String, mode: Mode) {
        currentText = text
        currentIndex = 0
        startTime = 0
        isRunning = false
        this.mode = mode
        totalKeystrokes = 0
        correctKeystrokes = 0
        wrongKeystrokes = 0
        consecutiveCorrect = 0
    }

    /**
     * Process a key press. Returns true if the key is correct for the current position.
     * For Chinese mode, this can also accept pinyin letter keys.
     */
    fun processKeyPress(key: Char): Boolean {
        if (currentIndex >= currentText.length) {
            return true // already finished
        }

        totalKeystrokes++

        val expected = currentText[currentIndex]
        val isCorrect = (key.lowercaseChar() == expected.lowercaseChar())

        if (isCorrect) {
            correctKeystrokes++
            consecutiveCorrect++
            currentIndex++
            return true
        } else {
            wrongKeystrokes++
            consecutiveCorrect = 0
            return false
        }
    }

    /**
     * Get the next expected character (null if finished).
     */
    fun getNextExpectedChar(): Char? {
        return if (currentIndex < currentText.length) currentText[currentIndex] else null
    }

    /**
     * Get progress ratio (0.0 to 1.0).
     */
    fun getProgress(): Float {
        if (currentText.isEmpty()) return 0f
        return currentIndex.toFloat() / currentText.length.toFloat()
    }

    /**
     * Check if time since last activity exceeds the timeout threshold.
     */
    fun isTimeout(lastActivityTime: Long, timeoutSeconds: Long): Boolean {
        if (lastActivityTime == 0L) return false
        return (System.currentTimeMillis() - lastActivityTime) >= timeoutSeconds * 1000
    }

    /**
     * Start the timer on first keypress.
     */
    fun markStarted() {
        if (!isRunning) {
            isRunning = true
            startTime = System.currentTimeMillis()
        }
    }

    /**
     * Calculate WPM (Words Per Minute).
     * Standard: 1 word = 5 characters.
     * Returns 0 if no time has elapsed.
     */
    fun calculateWpm(): Float {
        if (startTime == 0L || currentIndex == 0) return 0f
        val minutes = (System.currentTimeMillis() - startTime) / 60000f
        if (minutes <= 0) return 0f
        return (currentIndex / 5f) / minutes
    }

    /**
     * Calculate CPM (Characters Per Minute).
     * For Chinese typing.
     */
    fun calculateCpm(): Float {
        if (startTime == 0L || currentIndex == 0) return 0f
        val minutes = (System.currentTimeMillis() - startTime) / 60000f
        if (minutes <= 0) return 0f
        return currentIndex.toFloat() / minutes
    }

    /**
     * Calculate accuracy percentage.
     */
    fun calculateAccuracy(): Float {
        if (totalKeystrokes == 0) return 100f
        return (correctKeystrokes.toFloat() / totalKeystrokes) * 100f
    }

    /**
     * Check if typing is complete.
     */
    fun isComplete(): Boolean {
        return currentIndex >= currentText.length
    }

    /**
     * Reset everything.
     */
    fun reset() {
        currentText = ""
        currentIndex = 0
        startTime = 0
        isRunning = false
        totalKeystrokes = 0
        correctKeystrokes = 0
        wrongKeystrokes = 0
        consecutiveCorrect = 0
    }
}
