package com.honglu.typing.ui.viewmodel

import android.app.Application
import android.content.Context
import android.view.KeyEvent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.honglu.typing.R
import com.honglu.typing.data.ContentRepository
import com.honglu.typing.data.AppDatabase
import com.honglu.typing.data.RecordEntity
import com.honglu.typing.data.RecordDao
import com.honglu.typing.engine.ScoreManager
import com.honglu.typing.engine.SoundManager
import com.honglu.typing.engine.TypingEngine
import com.honglu.typing.input.PinyinInputEngine
import com.honglu.typing.util.DeviceUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * ViewModel for Primary mode (finger training).
 * Holds UI state as LiveData and handles input processing.
 */
class PrimaryViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()

    // Engine and managers
    private val engine = TypingEngine()
    private val scoreManager = ScoreManager(context)
    private val soundManager = SoundManager(context)
    private val pinyinInputEngine = PinyinInputEngine()
    private val recordDao: RecordDao by lazy {
        AppDatabase.getInstance(context).recordDao()
    }

    // UI State LiveData
    val currentText = MutableLiveData<String>("")
    val currentIndex = MutableLiveData<Int>(0)
    val isRunning = MutableLiveData<Boolean>(false)
    val wpm = MutableLiveData<Float>(0f)
    val cpm = MutableLiveData<Float>(0f)
    val accuracy = MutableLiveData<Float>(100f)
    val score = MutableLiveData<Int>(0)
    val highlightedKey = MutableLiveData<Char?>(null)
    val pressedKeys = MutableLiveData<Set<Char>>(emptySet())
    val progress = MutableLiveData<Float>(0f)
    val flashActive = MutableLiveData<Boolean>(false)
    val encouragement = MutableLiveData<String>("")
    val hintText = MutableLiveData<String>("Press SPACE to start")
    // Completion event
    val completionEvent = MutableLiveData<Unit>()
    // For pinyin candidate UI
    val selectingCandidates = MutableLiveData<Boolean>(false)
    val candidateList = MutableLiveData<List<String>>(emptyList())
    val candidateIndex = MutableLiveData<Int>(0)

    // Timeout handling
    private var lastActivityTime = 0L
    private val timeoutSeconds = 5L
    private var timeoutJob: Job? = null

    // Intent extras (set by Activity before startNewSession)
    private var pendingContentId: String? = null
    private var pendingContentLang: String? = null

    init {
        loadDictionary()
        startTimeoutWatcher()
        // Don't auto-start — wait for Activity to set content
    }

    fun setPendingContent(contentId: String, lang: String) {
        pendingContentId = contentId
        pendingContentLang = lang
        startNewSession()
    }

    private fun loadDictionary() {
        try {
            pinyinInputEngine.loadDictionary(context, "pinyin_dict.json")
        } catch (e: Exception) {
            // Dictionary load fails; pinyin features disabled
        }
    }

    /** Start a new typing session with random or selected content */
    fun startNewSession() {
        val text = if (pendingContentId != null) {
            ContentRepository.getTextById(context, pendingContentId!!)
        } else {
            ContentRepository.getRandomEnglishText(context)
        }
        pendingContentId = null
        pendingContentLang = null
        if (text.isNotEmpty()) {
            engine.start(text, TypingEngine.Mode.PRIMARY)
            resetPinyinState()
            updateUiFromEngine()
            hintText.value = context.getString(R.string.primary_hint)
        }
    }

    /** Reset pinyin-related UI state */
    private fun resetPinyinState() {
        pinyinAccumulator = ""
        selectingCandidates.value = false
        candidateList.value = emptyList()
        candidateIndex.value = 0
    }

    /** Call from Activity on key down event */
    fun onKeyDown(keyCode: Int, metaState: Int): Boolean {
        // Reset timeout timer
        lastActivityTime = System.currentTimeMillis()
        restartTimeoutWatcher()

        // Handle special keys BEFORE char mapping (SPACE not in keyCodeToChar)
        when (keyCode) {
            android.view.KeyEvent.KEYCODE_SPACE,
            android.view.KeyEvent.KEYCODE_ENTER,
            android.view.KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (!engine.isRunning && !engine.isComplete()) {
                    engine.markStarted()
                    hintText.value = ""
                }
                return true
            }
            android.view.KeyEvent.KEYCODE_DEL -> {
                if (engine.currentIndex > 0) {
                    engine.currentIndex--
                    updateUiFromEngine()
                }
                return true
            }
            android.view.KeyEvent.KEYCODE_ESCAPE, android.view.KeyEvent.KEYCODE_BACK -> {
                // Let Activity handle back press
                return false
            }
        }

        val shift = (metaState and KeyEvent.META_SHIFT_MASK) != 0
        val char = DeviceUtils.keyCodeToChar(keyCode, shift) ?: return false

        // Let ViewModel handle pinyin first
        if (tryHandlePinyinKey(char)) {
            return true
        }

        // Process as regular key
        val isCorrect = engine.processKeyPress(char)
        // Update keyboard UI
        highlightedKey.value = engine.getNextExpectedChar()
        val lower = char.lowercaseChar()
        pressedKeys.value = if (isCorrect) setOf(lower) else emptySet()
        // Play sound
        if (isCorrect) {
            soundManager.playCorrect()
        } else {
            soundManager.playWrong()
        }
        // Update UI
        updateUiFromEngine()
        // Check completion
        if (engine.isComplete()) {
            handleCompletion()
        }
        return true
    }

    /** Handle pinyin logic before falling back to English */
    private fun tryHandlePinyinKey(char: Char): Boolean {
        if (selectingCandidates.value != true && char in 'a'..'z') {
            pinyinAccumulator += char
            if (pinyinInputEngine.hasSuggestions(pinyinAccumulator)) {
                showCandidates()
                return true
            }
            // Accumulate but let English engine handle it too
        }
        return false
    }

    private var pinyinAccumulator = ""

    private fun showCandidates() {
        val suggestions = pinyinInputEngine.getSuggestions(pinyinAccumulator)
        if (suggestions.isNotEmpty()) {
            selectingCandidates.value = true
            candidateList.value = suggestions
            candidateIndex.value = 0
            updateCandidateHint()
        }
    }

    /** Handle DPAD/Enter/Number keys for candidate selection */
    fun onCandidateKey(keyCode: Int): Boolean {
        if (!(selectingCandidates.value ?: false)) return false
        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                candidateIndex.value = (candidateIndex.value ?: 0 - 1 + (candidateList.value ?: emptyList()).size) %
                        (candidateList.value ?: emptyList()).size
                updateCandidateHint()
                true
            }
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                candidateIndex.value = (candidateIndex.value ?: 0 + 1) %
                        (candidateList.value ?: emptyList()).size
                updateCandidateHint()
                true
            }
            android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent.KEYCODE_DPAD_CENTER -> {
                confirmCandidate()
                true
            }
            android.view.KeyEvent.KEYCODE_ESCAPE, android.view.KeyEvent.KEYCODE_BACK -> {
                cancelCandidateSelection()
                true
            }
            in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 -> {
                val idx = keyCode - android.view.KeyEvent.KEYCODE_0
                if (idx in 0 until (candidateList.value ?: emptyList()).size) {
                    candidateIndex.value = idx
                    confirmCandidate()
                }
                true
            }
            else -> false
        }
    }

    private fun updateCandidateHint() {
        val candidate = candidateList.value?.getOrNull(candidateIndex.value ?: 0) ?: ""
        hintText.value = "Select candidate: [$candidate] Enter to confirm"
    }

    private fun confirmCandidate() {
        val chosenChar = candidateList.value?.getOrNull(candidateIndex.value ?: 0) ?: return
        // Insert chosen char at current position
        val text = engine.currentText.toMutableList()
        if (engine.currentIndex < text.size) {
            text[engine.currentIndex] = chosenChar[0]
        } else {
            text.add(chosenChar[0])
        }
        engine.currentText = text.joinToString("")
        engine.currentIndex++
        engine.correctKeystrokes++
        engine.totalKeystrokes++
        soundManager.playCorrect()
        updateUiFromEngine()
        // Reset pinyin state
        resetPinyinState()
        hintText.value = ""
    }

    private fun cancelCandidateSelection() {
        resetPinyinState()
        hintText.value = ""
    }

    private fun updateUiFromEngine() {
        currentText.value = engine.currentText
        currentIndex.value = engine.currentIndex
        isRunning.value = engine.isRunning
        wpm.value = engine.calculateWpm()
        cpm.value = engine.calculateCpm()
        accuracy.value = engine.calculateAccuracy()
        score.value = calculateScore()
        highlightedKey.value = engine.getNextExpectedChar()
        progress.value = engine.getProgress()
        // No flash unless timeout
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

    private fun handleCompletion() {
        // Record result
        viewModelScope.launch {
            val record = RecordEntity(
                mode = "primary",
                contentType = "en_short",
                wpm = engine.calculateWpm(),
                cpm = 0f,
                accuracy = engine.calculateAccuracy(),
                score = calculateScore(),
                totalKeystrokes = engine.totalKeystrokes,
                correctKeystrokes = engine.correctKeystrokes,
                date = System.currentTimeMillis()
            )
            recordDao.insert(record)
        }
        completionEvent.value = Unit
    }

    // Timeout watcher
    private fun startTimeoutWatcher() {
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            while (true) {
                delay(500) // check every 0.5s
                val running = engine.isRunning && !engine.isComplete()
                val timeout = engine.isTimeout(lastActivityTime, timeoutSeconds)
                flashActive.value = running && timeout
                if (timeout && running) {
                    // Flash keyboard - we already have flashActive LiveData
                    // Could also trigger sound? Not needed.
                }
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