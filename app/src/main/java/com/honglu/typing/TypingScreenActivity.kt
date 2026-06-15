package com.honglu.typing

import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import com.honglu.typing.databinding.ActivityAdvancedBinding
import com.honglu.typing.databinding.ActivityPrimaryBinding
import com.honglu.typing.engine.ScoreManager
import com.honglu.typing.engine.SoundManager
import com.honglu.typing.engine.TypingEngine
import com.honglu.typing.input.PinyinInputEngine
import com.honglu.typing.util.DeviceUtils

/**
 * Base class for typing screen activities (Primary + Advanced modes).
 * Handles common keyboard event dispatch, display updates, and result recording.
 * Subclasses only need to implement: bindViews(), onTextComplete(), resetAndRestart().
 */
abstract class TypingScreenActivity : AppCompatActivity() {

    /** Subclass sets binding in onCreate() — safe cast in base class methods. */
    protected abstract val primaryBinding: ActivityPrimaryBinding?
    protected abstract val advancedBinding: ActivityAdvancedBinding?
    protected abstract val mode: TypingEngine.Mode

    // Core engine
    protected val engine = TypingEngine()
    protected val soundManager = SoundManager(this)
    protected val scoreManager = ScoreManager(this)
    protected val pinyinInputEngine = PinyinInputEngine()
    protected val hintHandler = Handler(Looper.getMainLooper())

    // Timeout handling
    private var lastActivityTime = 0L
    private var timeoutRunnable: Runnable? = null
    private var timeoutSeconds = 5L

    private val prefs by lazy {
        getSharedPreferences("typing_config", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadSettings()

        // Load pinyin dictionary
        try {
            pinyinInputEngine.loadDictionary(this, "pinyin_dict.json")
        } catch (e: Exception) {
            // Dictionary not loaded, pinyin features disabled
        }

        // Set up view references
        bindViews()
    }

    private fun loadSettings() {
        timeoutSeconds = prefs.getInt("timeout_seconds", 5).toLong()
        soundManager.setEnabled(prefs.getBoolean("sound_enabled", true))
    }

    /**
     * Subclasses implement this to set up their specific view bindings.
     */
    protected abstract fun bindViews()

    /**
     * Update the keyboard to show the expected key.
     * Only valid for primary mode.
     */
    protected fun updateKeyboardDisplay() {
        val expected = engine.getNextExpectedChar()
        primaryBinding?.let {
            it.keyboardView.highlightedKey = expected
        }
        updateDisplayText()
    }

    /**
     * Update the text display area with current typing progress.
     * Dispatches to mode-specific rendering.
     */
    protected fun updateDisplayText() {
        val formatted = when {
            primaryBinding != null -> formatDisplay(engine.currentText, engine.currentIndex, false)
            advancedBinding != null -> formatDisplay(engine.currentText, engine.currentIndex, true)
            else -> ""
        }

        when {
            primaryBinding != null -> primaryBinding!!.tvDisplayText.text = formatted
            advancedBinding != null -> advancedBinding!!.tvDisplayText.text = formatted
        }
    }

    /**
     * Format text with progress indicators.
     * @param isAdvanced true for advanced mode layout
     */
    private fun formatDisplay(text: String, currentIndex: Int, isAdvanced: Boolean): String {
        if (text.isEmpty()) return ""

        val builder = StringBuilder()
        for (i in text.indices) {
            when {
                i == currentIndex -> builder.append("【${text[i]}】")
                i < currentIndex -> builder.append(text[i])
                else -> builder.append(if (isAdvanced) text[i] else "_ ")
            }
            if (!isAdvanced) builder.append(" ")
        }
        return builder.toString().trim()
    }

    /**
     * Hook for subclasses to handle pinyin letter keys before English processing.
     * Return true if the key was consumed as a pinyin key.
     */
    protected open fun tryHandlePinyinKey(event: KeyEvent): Boolean = false

    /**
     * Main key event handler: dispatches to physical keyboard or remote.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event)
        }

        return when {
            // Allow subclass to handle pinyin first
            tryHandlePinyinKey(event) -> true
            // Physical keyboard (OTG)
            DeviceUtils.isPhysicalKeyboard(event) -> {
                handlePhysicalKey(event)
            }
            // Remote control D-pad
            DeviceUtils.isRemoteControl(event) -> {
                handleRemote(event)
            }
            else -> super.dispatchKeyEvent(event)
        }
    }

    private fun handlePhysicalKey(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val shift = event.hasModifiers(KeyEvent.META_SHIFT_LEFT or KeyEvent.META_SHIFT_RIGHT)

        // Special keys
        when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> {
                if (!engine.isRunning && !engine.isComplete()) {
                    engine.markStarted()
                    // Hide hint text in both modes
                    primaryBinding?.tvHint?.visibility = android.view.View.GONE
                    advancedBinding?.tvHint?.visibility = android.view.View.GONE
                }
                return true
            }
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK -> {
                if (engine.currentIndex > 0 && engine.currentIndex < engine.currentText.length) {
                    AlertDialog.Builder(this)
                        .setMessage("确定要退出吗？当前进度将保留。")
                        .setPositiveButton("退出") { _, _ -> finish() }
                        .setNegativeButton("取消", null)
                        .show()
                } else {
                    finish()
                }
                return true
            }
            KeyEvent.KEYCODE_TAB -> return true // prevent focus change
            KeyEvent.KEYCODE_DEL -> {
                if (engine.currentIndex > 0) {
                    engine.currentIndex--
                    engine.correctKeystrokes++
                    engine.totalKeystrokes--
                    updateDisplayText()
                    updateKeyboardDisplay()
                    soundManager.playClick()
                }
                return true
            }
        }

        // Letter keys (a-z, A-Z)
        val char = DeviceUtils.keyCodeToChar(keyCode, shift)
        if (char != null) {
            soundManager.playClick()

            // Reset timeout timer
            lastActivityTime = System.currentTimeMillis()
            resetTimeoutWarning()

            if (!engine.isRunning) {
                engine.markStarted()
            }

            val isCorrect = engine.processKeyPress(char)

            // Update keyboard state (primary mode only)
            primaryBinding?.keyboardView?.pressedKeys = setOf(char.lowercaseChar())

            // Play feedback sound
            if (isCorrect) {
                soundManager.playCorrect()
            } else {
                soundManager.playWrong()
            }

            // Update display
            updateDisplayText()
            updateKeyboardDisplay()

            // Check completion
            if (engine.isComplete()) {
                onTextComplete()
            }

            return true
        }

        return super.dispatchKeyEvent(event)
    }

    private fun handleRemote(event: KeyEvent): Boolean {
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                // D-pad navigation for menus (not for typing)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (!engine.isRunning && !engine.isComplete()) {
                    engine.markStarted()
                }
                true
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                finish()
                true
            }
            else -> super.dispatchKeyEvent(event)
        }
    }

    /**
     * Reset the timeout warning flash animation.
     */
    private fun resetTimeoutWarning() {
        timeoutRunnable?.let { hintHandler.removeCallbacks(it) }

        timeoutRunnable = Runnable {
            primaryBinding?.keyboardView?.let { kbView ->
                if (engine.isRunning && !engine.isComplete() &&
                    engine.isTimeout(lastActivityTime, timeoutSeconds)) {
                    kbView.startFlashAnimation()
                }
            }
        }
        hintHandler.postDelayed(timeoutRunnable!!, timeoutSeconds * 1000)
    }

    /**
     * Calculate score based on accuracy and speed.
     */
    protected fun calculateScore(): Int {
        val accuracy = engine.calculateAccuracy()
        val wpm = engine.calculateWpm()
        val streak = engine.consecutiveCorrect

        var score = 0
        score += (accuracy / 10 * 100).toInt()            // Accuracy contribution
        score += (wpm / 5 * 10).toInt()                   // Speed contribution
        score += streak * 2                                // Streak bonus

        return score
    }

    /**
     * Called when typing is complete. Implement in subclasses.
     */
    protected abstract fun onTextComplete()

    /**
     * Show the completion dialog with results.
     */
    protected fun showCompleteDialog(wpm: Float, accuracy: Float, score: Int) {
        AlertDialog.Builder(this)
            .setTitle("练习完成！")
            .setMessage("WPM: ${String.format("%.0f", wpm)}\n正确率: ${String.format("%.0f", accuracy)}%\n得分: $score")
            .setPositiveButton("再来一次") { _, _ ->
                resetAndRestart()
            }
            .setNegativeButton("返回菜单") { _, _ -> finish() }
            .show()
    }

    /**
     * Subclass provides appropriate content for restart.
     */
    protected abstract fun resetAndRestart()

    override fun onDestroy() {
        super.onDestroy()
        timeoutRunnable?.let { hintHandler.removeCallbacks(it) }
        soundManager.cleanup()
        pinyinInputEngine.reset()
    }
}
