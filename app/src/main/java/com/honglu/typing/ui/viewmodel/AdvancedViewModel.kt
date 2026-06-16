package com.honglu.typing.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.honglu.typing.R
import com.honglu.typing.data.ContentRepository
import com.honglu.typing.data.RecordEntity
import com.honglu.typing.data.RecordDao
import com.honglu.typing.engine.ScoreManager
import com.honglu.typing.engine.SoundManager
import com.honglu.typing.engine.TypingEngine
import com.honglu.typing.input.PinyinInputEngine
import com.honglu.typing.util.DeviceUtils
import kotlinx.coroutines.launch
import kotlinx.coroutines.sleep

/**
 * ViewModel for Advanced mode (WPM/CPM test).
 */
class AdvancedViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()

    // Engine and managers
    private val engine = TypingEngine()
    private val scoreManager = ScoreManager(context)
    private val soundManager = SoundManager(context)
    private val pinyinInputEngine = PinyinInputEngine()
    private val recordDao: RecordDao by lazy {
        com.honglu.typing.data.AppDatabase.getInstance(context).recordDao()
    }

    // UI State LiveData
    val currentText = MutableLiveData<String>("")
    val currentIndex = MutableLiveData<Int>(0)
    val isRunning = MutableLiveData<Boolean>(false)
    val wpm = MutableLiveData<Float>(0f)
    val cpm = MutableLiveData<Float>(0f)
    val accuracy = MutableLiveData<Float>(100f)
    val score = MutableLiveData<Int>(0)
    val progress = MutableLiveData<Float>(0f)
    val flashActive = MutableLiveData<Boolean>(false)
    val encouragement = MutableLiveData<String>("")
    val hintText = MutableLiveData<String>("Press SPACE to start")
    // Completion event
    val completionEvent = MutableLiveData<Unit>()

    // Timeout handling
    private var lastActivityTime = 0L
    private val timeoutSeconds = 5L
    private var timeoutJob: kotlinx.coroutines.Job? = null

    init {
        loadDictionary()
        startNewSession()
        startTimeoutWatcher()
    }

    private fun loadDictionary() {
        try {
            pinyinInputEngine.loadDictionary(context, "pinyin_dict.json")
        } catch (e: Exception) {
            // Dictionary load fails; pinyin features disabled
        }
    }

    /** Start a new typing session with random English or Chinese text */
    fun startNewSession() {
        val text = if ((0..1).random() == 0) {
            ContentRepository.getRandomEnglishText(context)
        } else {
            ContentRepository.getRandomChineseText(context)
        }
        if (text.isNotEmpty()) {
            engine.start(text, TypingEngine.Mode.ADVANCED)
            updateUiFromEngine()
            hintText.value = getString(R.string.primary_hint) // reuse same hint
        }
    }

    /** Call from Activity on key down event */
    fun onKeyDown(keyCode: Int, metaState: Int): Boolean {
        // Reset timeout timer
        lastActivityTime = System.currentTimeMillis()
        restartTimeoutWatcher()

        val shift = (metaState and DeviceUtils.META_SHIFT_MASK) != 0
        val char = DeviceUtils.keyCodeToChar(keyCode, shift) ?: return false

        // Handle special keys first
        when (keyCode) {
            android.view.KeyEvent.KEYCODE_SPACE -> {
                if (!engine.isRunning && !engine.isComplete()) {
                    engine.markStarted()
                    hintText.value = ""
                }
                return true
            }
            android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (!engine.isRunning && !engine.isComplete()) {
                    engine.markStarted()
                    hintText.value = ""
                }
                return true
            }
            android.view.KeyEvent.KEYCODE_DEL -> {
                if (engine.currentIndex > 0) {
                    engine.currentIndex--
                    engine.correctKeystrokes++
                    engine.totalKeystrokes--
                    updateUiFromEngine()
                }
                return true
            }
            android.view.KeyEvent.KEYCODE_ESCAPE, android.view.KeyEvent.KEYCODE_BACK -> {
                // Let Activity handle back press
                return false
            }
        }

        // Process as regular key
        val isCorrect = engine.processKeyPress(char)
        // Update UI
        updateUiFromEngine()
        // Play sound
        if (isCorrect) {
            soundManager.playCorrect()
        } else {
            soundManager.playWrong()
        }
        // Check completion
        if (engine.isComplete()) {
            handleCompletion()
        }
        return true
    }

    private fun updateUiFromEngine() {
        currentText.value = engine.currentText
        currentIndex.value = engine.currentIndex
        isRunning.value = engine.isRunning
        wpm.value = engine.calculateWpm()
        cpm.value = engine.calculateCpm()
        accuracy.value = engine.calculateAccuracy()
        score.value = calculateScore()
        progress.value = engine.getProgress()
        updateEncouragement()
    }

    private fun calculateScore(): Int {
        val acc = engine.calculateAccuracy()
        val w = engine.calculateWpm()
        val streak = engine.consecutiveCorrect
        var s = 0
        s += (acc / 10 * 100).toInt()
        s += (w / 5 * 10).toInt()
        s += streak * 2
        return s
    }

    private fun updateEncouragement() {
        val streak = engine.consecutiveCorrect
        val encourage = when {
            streak >= 50 -> getString(R.string.encourage_excellent)
            streak >= 20 -> getString(R.string.encourage_good)
            streak >= 10 -> getString(R.string.encourage_keep)
            else -> ""
        }
        encouragement.value = encourage
    }

    private fun handleCompletion() {
        // Record result
        viewModelScope.launch {
            val isChinese = engine.currentText.any { it in '一'..'鿿' }
            val record = RecordEntity(
                mode = "advanced",
                contentType = if (isChinese) "cn_paragraph" else "en_short",
                wpm = engine.calculateWpm(),
                cpm = engine.calculateCpm(),
                accuracy = engine.calculateAccuracy(),
                score = calculateScore(),
                totalKeystrokes = engine.totalKeystrokes,
                correctKeystrokes = engine.correctKeystrokes,
                date = System.currentTimeMillis()
            )
            recordDao.insert(record)
        }
    }

    // Timeout watcher
    private fun startTimeoutWatcher() {
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            while (true) {
                delay(500)
                val running = engine.isRunning && !engine.isComplete()
                val timeout = engine.isTimeout(lastActivityTime, timeoutSeconds)
                flashActive.value = running && timeout
            }
        }
    }

    private fun restartTimeoutWatcher() {
        lastActivityTime = System.currentTimeMillis()
    }

    override fun onCleared() {
        super.onCleared()
        timeoutJob?.cancel()
        soundManager.cleanup()
    }
}