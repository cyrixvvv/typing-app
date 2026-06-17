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

class AdvancedViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()

    private val engine = TypingEngine()
    private val scoreManager = ScoreManager(context)
    private val soundManager = SoundManager(context)
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
    val hintText = MutableLiveData<String>(context.getString(R.string.primary_hint))
    val completionEvent = MutableLiveData<Unit>()

    // Extra stats for advanced mode
    val totalKeystrokes = MutableLiveData<Int>(0)
    val errorKeystrokes = MutableLiveData<Int>(0)

    val wrongKeyFlash = MutableLiveData<Boolean>(false)

    private var lastActivityTime = 0L
    private val timeoutSeconds = 5L
    private var timeoutJob: Job? = null
    private var clearKeysJob: Job? = null
    private var wrongFlashJob: Job? = null
    private var isChineseContent = false

    init {
        startNewSession()
        startTimeoutWatcher()
    }

    fun startNewSession() {
        val text = if ((0..1).random() == 0) {
            ContentRepository.getRandomEnglishText(context)
        } else {
            ContentRepository.getRandomChineseText(context)
        }
        if (text.isNotEmpty()) {
            isChineseContent = text.any { it in '一'..'鿿' }
            engine.start(text, TypingEngine.Mode.ADVANCED)
            updateUiFromEngine()
            hintText.value = context.getString(R.string.primary_hint)
            wrongKeyFlash.value = false
            totalKeystrokes.value = 0
            errorKeystrokes.value = 0
        }
    }

    fun onKeyDown(keyCode: Int, metaState: Int): Boolean {
        lastActivityTime = System.currentTimeMillis()
        restartTimeoutWatcher()

        when (keyCode) {
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (!engine.isRunning && !engine.isComplete()) {
                    engine.markStarted()
                    hintText.value = ""
                }
                return true
            }
            KeyEvent.KEYCODE_DEL -> {
                if (engine.currentIndex > 0) {
                    engine.currentIndex--
                    updateUiFromEngine()
                }
                return true
            }
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK -> {
                return false
            }
        }

        val shift = (metaState and KeyEvent.META_SHIFT_MASK) != 0
        val char = DeviceUtils.keyCodeToChar(keyCode, shift) ?: return false

        val isCorrect = engine.processKeyPress(char)

        // Keyboard visual feedback
        highlightedKey.value = engine.getNextExpectedChar()
        val lower = char.lowercaseChar()
        pressedKeys.value = setOf(lower)
        scheduleClearPressedKeys()

        // Stats
        totalKeystrokes.value = engine.totalKeystrokes
        errorKeystrokes.value = engine.wrongKeystrokes

        if (isCorrect) {
            soundManager.playCorrect()
            wrongKeyFlash.value = false
        } else {
            soundManager.playWrong()
            wrongKeyFlash.value = true
            scheduleClearWrongFlash()
        }

        updateUiFromEngine()

        if (engine.isComplete()) {
            handleCompletion()
        }
        return true
    }

    private fun scheduleClearPressedKeys() {
        clearKeysJob?.cancel()
        clearKeysJob = viewModelScope.launch {
            delay(180)
            pressedKeys.value = emptySet()
        }
    }

    private fun scheduleClearWrongFlash() {
        wrongFlashJob?.cancel()
        wrongFlashJob = viewModelScope.launch {
            delay(300)
            wrongKeyFlash.value = false
        }
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
        encouragement.value = when {
            streak >= 50 -> context.getString(R.string.encourage_excellent)
            streak >= 20 -> context.getString(R.string.encourage_good)
            streak >= 10 -> context.getString(R.string.encourage_keep)
            else -> ""
        }
    }

    private fun handleCompletion() {
        viewModelScope.launch {
            val record = RecordEntity(
                mode = "advanced",
                contentType = if (isChineseContent) "cn_paragraph" else "en_short",
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
        completionEvent.value = Unit
    }

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
        clearKeysJob?.cancel()
        wrongFlashJob?.cancel()
        soundManager.cleanup()
    }
}
