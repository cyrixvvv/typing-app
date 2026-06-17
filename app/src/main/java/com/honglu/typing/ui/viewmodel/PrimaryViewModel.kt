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

class PrimaryViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()

    private val engine = TypingEngine()
    private val scoreManager = ScoreManager(context)
    private val soundManager = SoundManager(context)
    private val pinyinInputEngine = PinyinInputEngine()
    private val recordDao: RecordDao by lazy {
        AppDatabase.getInstance(context).recordDao()
    }

    // UI State
    val currentText = MutableLiveData<String>("")
    val currentIndex = MutableLiveData<Int>(0)
    val isRunning = MutableLiveData<Boolean>(false)
    val wpm = MutableLiveData<Float>(0f)
    val cpm = MutableLiveData<Float>(0f)
    val accuracy = MutableLiveData<Float>(0f)
    val score = MutableLiveData<Int>(0)
    val highlightedKey = MutableLiveData<Char?>(null)
    val pressedKeys = MutableLiveData<Set<Char>>(emptySet())
    val progress = MutableLiveData<Float>(0f)
    val flashActive = MutableLiveData<Boolean>(false)
    val encouragement = MutableLiveData<String>("")
    val hintText = MutableLiveData<String>(context.getString(R.string.primary_hint))
    val completionEvent = MutableLiveData<Unit>()

    // Pinyin UI
    val selectingCandidates = MutableLiveData<Boolean>(false)
    val candidateList = MutableLiveData<List<String>>(emptyList())
    val candidateIndex = MutableLiveData<Int>(0)
    val pinyinBuffer = MutableLiveData<String>("")  // shows accumulated pinyin

    val wrongKeyFlash = MutableLiveData<Boolean>(false)

    // Auto-advance: when true, completion auto-loads next random content
    var autoAdvance = false

    private var lastActivityTime = 0L
    private val timeoutSeconds = 5L
    private var timeoutJob: Job? = null
    private var clearKeysJob: Job? = null
    private var wrongFlashJob: Job? = null

    private var pinyinAccumulator = ""
    private var isChineseContent = false
    private var pendingContentId: String? = null
    private var pendingContentLang: String? = null
    private var candidatePageOffset = 0

    init {
        loadDictionary()
        startTimeoutWatcher()
    }

    fun setPendingContent(contentId: String, lang: String) {
        pendingContentId = contentId
        pendingContentLang = lang
        isChineseContent = (lang == "Chinese")
        autoAdvance = (contentId == "random_en" || contentId == "random_cn")
        startNewSession()
    }

    private fun loadDictionary() {
        try { pinyinInputEngine.loadDictionary(context, "pinyin_dict.json") } catch (_: Exception) { }
    }

    fun startNewSession() {
        val text = when {
            pendingContentId == "random_en" -> ContentRepository.getRandomEnglishText(context)
            pendingContentId == "random_cn" -> ContentRepository.getRandomChineseText(context)
            pendingContentId != null -> ContentRepository.getTextById(context, pendingContentId!!)
            else -> ContentRepository.getRandomEnglishText(context)
        }
        pendingContentId = null
        pendingContentLang = null
        if (text.isNotEmpty()) {
            isChineseContent = text.any { it in '一'..'鿿' }
            engine.start(text, TypingEngine.Mode.PRIMARY)
            resetPinyinState()
            updateUiFromEngine()
            hintText.value = context.getString(R.string.primary_hint)
            wrongKeyFlash.value = false
        }
    }

    private fun resetPinyinState() {
        pinyinAccumulator = ""
        pinyinBuffer.value = ""
        selectingCandidates.value = false
        candidateList.value = emptyList()
        candidateIndex.value = 0
        candidatePageOffset = 0
    }

    fun onKeyDown(keyCode: Int, metaState: Int): Boolean {
        lastActivityTime = System.currentTimeMillis()
        restartTimeoutWatcher()

        when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> {
                if (selectingCandidates.value == true) {
                    if (isChineseContent) candidateIndex.value = 0 // SPACE picks first
                    confirmCandidate()
                    return true
                }
                if (!engine.isRunning && !engine.isComplete()) {
                    engine.markStarted()
                    hintText.value = ""
                    return true
                }
                // Process space character
                val isCorrect = engine.processKeyPress(' ')
                highlightedKey.value = engine.getNextExpectedChar()
                pressedKeys.value = setOf(' ')
                scheduleClearPressedKeys()
                if (isCorrect) { soundManager.playCorrect(); wrongKeyFlash.value = false }
                else { soundManager.playWrong(); wrongKeyFlash.value = true; scheduleClearWrongFlash() }
                updateUiFromEngine()
                if (engine.isComplete()) handleCompletion()
                return true
            }
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (selectingCandidates.value == true) {
                    confirmCandidate()
                    return true
                }
                if (!engine.isRunning && !engine.isComplete()) {
                    engine.markStarted()
                    hintText.value = ""
                }
                return true
            }
            KeyEvent.KEYCODE_DEL -> {
                if (selectingCandidates.value == true) {
                    handleDelWithCandidates()
                    return true
                }
                // Chinese: drop from pinyin accumulator even when not selecting
                if (isChineseContent && pinyinAccumulator.isNotEmpty()) {
                    pinyinAccumulator = pinyinAccumulator.dropLast(1)
                    pinyinBuffer.value = pinyinAccumulator
                    hintText.value = if (pinyinAccumulator.isEmpty()) "" else "拼音: $pinyinAccumulator"
                    return true
                }
                if (engine.currentIndex > 0) {
                    engine.currentIndex--
                    if (isChineseContent) resetPinyinState()
                    updateUiFromEngine()
                }
                return true
            }
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK -> return false
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (isChineseContent && pinyinAccumulator.length >= 1 &&
                    pinyinInputEngine.hasSuggestions(pinyinAccumulator)) {
                    showCandidates()
                    return true
                }
                return false
            }
        }

        // Candidate pagination in Chinese mode: =/. next, -/, prev
        if (selectingCandidates.value == true && isChineseContent) {
            when (keyCode) {
                KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_PERIOD -> {
                    val total = (candidateList.value ?: emptyList()).size
                    if (total > 9) {
                        candidatePageOffset = (candidatePageOffset + 9).coerceAtMost(total - 1)
                        updateCandidateHint()
                    }
                    return true
                }
                KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_COMMA -> {
                    if (candidatePageOffset > 0) {
                        candidatePageOffset = (candidatePageOffset - 9).coerceAtLeast(0)
                        updateCandidateHint()
                    }
                    return true
                }
            }
        }

        // Chinese mode: number keys select candidates directly (key N → index N-1)
        if (selectingCandidates.value == true && isChineseContent &&
            keyCode in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9) {
            val idx = keyCode - KeyEvent.KEYCODE_1
            val list = candidateList.value ?: emptyList()
            if (idx in list.indices) {
                candidateIndex.value = idx
                confirmCandidate()
            }
            return true
        }

        val shift = (metaState and KeyEvent.META_SHIFT_MASK) != 0
        val char = DeviceUtils.keyCodeToChar(keyCode, shift) ?: return false

        // Chinese pinyin input
        if (isChineseContent && char in 'a'..'z') {
            pinyinAccumulator += char
            pinyinBuffer.value = pinyinAccumulator
            if (pinyinInputEngine.hasSuggestions(pinyinAccumulator)) {
                showCandidates()
            } else {
                hintText.value = "拼音: $pinyinAccumulator"
            }
            return true
        }

        // English: pass to engine
        val isCorrect = engine.processKeyPress(char)
        highlightedKey.value = engine.getNextExpectedChar()
        val lower = char.lowercaseChar()
        pressedKeys.value = setOf(lower)
        scheduleClearPressedKeys()

        if (isCorrect) {
            soundManager.playCorrect()
            wrongKeyFlash.value = false
        } else {
            soundManager.playWrong()
            wrongKeyFlash.value = true
            scheduleClearWrongFlash()
        }

        updateUiFromEngine()
        if (engine.isComplete()) handleCompletion()
        return true
    }

    private fun scheduleClearPressedKeys() {
        clearKeysJob?.cancel()
        clearKeysJob = viewModelScope.launch { delay(180); pressedKeys.value = emptySet() }
    }

    private fun scheduleClearWrongFlash() {
        wrongFlashJob?.cancel()
        wrongFlashJob = viewModelScope.launch { delay(300); wrongKeyFlash.value = false }
    }

    private fun handleDelWithCandidates() {
        if (pinyinAccumulator.isEmpty()) return
        pinyinAccumulator = pinyinAccumulator.dropLast(1)
        pinyinBuffer.value = pinyinAccumulator
        if (pinyinAccumulator.isEmpty()) {
            cancelCandidateSelection()
        } else if (pinyinInputEngine.hasSuggestions(pinyinAccumulator)) {
            candidatePageOffset = 0
            showCandidates()
        } else {
            cancelCandidateSelection()
            hintText.value = "拼音: $pinyinAccumulator"
        }
    }

    private fun showCandidates() {
        val suggestions = pinyinInputEngine.getSuggestions(pinyinAccumulator)
        if (suggestions.isNotEmpty()) {
            selectingCandidates.value = true
            candidateList.value = suggestions
            candidateIndex.value = 0
            updateCandidateHint()
        }
    }

    fun onCandidateKey(keyCode: Int): Boolean {
        if (selectingCandidates.value != true) return false
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                val size = (candidateList.value ?: emptyList()).size
                if (size > 0) candidateIndex.value = ((candidateIndex.value ?: 0) - 1 + size) % size
                updateCandidateHint(); true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val size = (candidateList.value ?: emptyList()).size
                if (size > 0) candidateIndex.value = ((candidateIndex.value ?: 0) + 1) % size
                updateCandidateHint(); true
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> { confirmCandidate(); true }
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK -> { cancelCandidateSelection(); true }
            in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> {
                val idx = keyCode - KeyEvent.KEYCODE_1
                val list = candidateList.value ?: emptyList()
                if (idx in list.indices) { candidateIndex.value = idx; confirmCandidate() }
                true
            }
            else -> false
        }
    }

    private fun updateCandidateHint() {
        val candidates = candidateList.value ?: emptyList()
        if (candidates.isEmpty()) return
        val pageSize = 9
        val offset = candidatePageOffset.coerceIn(0, (candidates.size - 1).coerceAtLeast(0))
        val page = candidates.drop(offset).take(pageSize)
        val items = page.mapIndexed { i, c -> "${offset + i + 1}.$c" }.joinToString("  ")
        val total = candidates.size
        val pageInfo = if (total > pageSize) " [${offset / pageSize + 1}/${(total + pageSize - 1) / pageSize}]" else ""
        hintText.value = "拼音: $pinyinAccumulator$pageInfo  $items"
    }

    private fun confirmCandidate() {
        val chosenChar = candidateList.value?.getOrNull(candidateIndex.value ?: 0) ?: return
        val textBuilder = StringBuilder(engine.currentText)
        if (engine.currentIndex < textBuilder.length) {
            textBuilder[engine.currentIndex] = chosenChar[0]
        } else {
            textBuilder.append(chosenChar[0])
        }
        engine.currentText = textBuilder.toString()
        engine.currentIndex++
        engine.correctKeystrokes++
        engine.totalKeystrokes++
        soundManager.playCorrect()
        updateUiFromEngine()
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
        // Show 0 when not started yet (engine.calculateAccuracy() defaults to 100)
        accuracy.value = if (engine.isRunning) engine.calculateAccuracy() else 0f
        score.value = if (engine.isRunning) calculateScore() else 0
        highlightedKey.value = engine.getNextExpectedChar()
        progress.value = engine.getProgress()
    }

    private fun calculateScore(): Int {
        val acc = engine.calculateAccuracy()
        val w = engine.calculateWpm()
        val streak = engine.consecutiveCorrect
        return (acc / 10 * 100).toInt() + (w / 5 * 10).toInt() + streak * 2
    }

    private fun handleCompletion() {
        // Capture engine state to local vars before firing completionEvent
        // (Activity observer may call startNewSession() which resets the engine)
        val capturedText = engine.currentText
        val capturedWpm = engine.calculateWpm()
        val capturedCpm = engine.calculateCpm()
        val capturedAccuracy = engine.calculateAccuracy()
        val capturedScore = calculateScore()
        val capturedTotalK = engine.totalKeystrokes
        val capturedCorrectK = engine.correctKeystrokes
        val contentType = if (capturedText.any { it in '一'..'鿿' }) "cn_short" else "en_short"

        viewModelScope.launch {
            try {
                val record = RecordEntity(
                    mode = "primary",
                    contentType = contentType,
                    wpm = capturedWpm, cpm = capturedCpm,
                    accuracy = capturedAccuracy, score = capturedScore,
                    totalKeystrokes = capturedTotalK,
                    correctKeystrokes = capturedCorrectK,
                    date = System.currentTimeMillis()
                )
                recordDao.insert(record)
            } catch (_: Exception) { /* silent */ }
        }
        completionEvent.value = Unit
    }

    // Called by Activity when dialog "再来一次" or auto-advance
    fun nextContent() {
        if (autoAdvance) {
            pendingContentId = if (isChineseContent) "random_cn" else "random_en"
            pendingContentLang = if (isChineseContent) "Chinese" else "English"
            startNewSession()
        } else {
            startNewSession()
        }
    }

    private fun startTimeoutWatcher() {
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            while (true) {
                delay(500)
                val running = engine.isRunning && !engine.isComplete()
                flashActive.value = running && engine.isTimeout(lastActivityTime, timeoutSeconds)
            }
        }
    }

    private fun restartTimeoutWatcher() { lastActivityTime = System.currentTimeMillis() }

    override fun onCleared() {
        super.onCleared()
        timeoutJob?.cancel(); clearKeysJob?.cancel(); wrongFlashJob?.cancel()
        soundManager.cleanup()
    }
}
