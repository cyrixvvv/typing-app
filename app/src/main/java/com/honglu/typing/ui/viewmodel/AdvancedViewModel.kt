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
    val accuracy = MutableLiveData<Float>(100f)
    val score = MutableLiveData<Int>(0)
    val highlightedKey = MutableLiveData<Char?>(null)
    val pressedKeys = MutableLiveData<Set<Char>>(emptySet())
    val progress = MutableLiveData<Float>(0f)
    val flashActive = MutableLiveData<Boolean>(false)
    val encouragement = MutableLiveData<String>("")
    val hintText = MutableLiveData<String>(context.getString(R.string.primary_hint))
    val completionEvent = MutableLiveData<Unit>()

    val totalKeystrokes = MutableLiveData<Int>(0)
    val errorKeystrokes = MutableLiveData<Int>(0)
    val wrongKeyFlash = MutableLiveData<Boolean>(false)

    // Pinyin UI
    val selectingCandidates = MutableLiveData<Boolean>(false)
    val candidateList = MutableLiveData<List<String>>(emptyList())
    val candidateIndex = MutableLiveData<Int>(0)
    val pinyinBuffer = MutableLiveData<String>("")

    var autoAdvance = false

    private var lastActivityTime = 0L
    private val timeoutSeconds = 5L
    private var timeoutJob: Job? = null
    private var clearKeysJob: Job? = null
    private var wrongFlashJob: Job? = null
    private var isChineseContent = false
    private var pinyinAccumulator = ""
    private var pendingContentId: String? = null
    private var pendingContentLang: String? = null

    init {
        loadDictionary()
        startNewSession()
        startTimeoutWatcher()
    }

    private fun loadDictionary() {
        try { pinyinInputEngine.loadDictionary(context, "pinyin_dict.json") } catch (_: Exception) { }
    }

    fun setPendingContent(contentId: String, lang: String) {
        pendingContentId = contentId
        pendingContentLang = lang
        isChineseContent = (lang == "Chinese")
        autoAdvance = (contentId == "random_en" || contentId == "random_cn")
        startNewSession()
    }

    fun startNewSession() {
        val text = when {
            pendingContentId == "random_en" -> ContentRepository.getRandomEnglishText(context)
            pendingContentId == "random_cn" -> ContentRepository.getRandomChineseText(context)
            pendingContentId != null -> ContentRepository.getTextById(context, pendingContentId!!)
            else -> if ((0..1).random() == 0) ContentRepository.getRandomEnglishText(context)
                    else ContentRepository.getRandomChineseText(context)
        }
        pendingContentId = null
        pendingContentLang = null
        if (text.isNotEmpty()) {
            isChineseContent = text.any { it in '一'..'鿿' }
            engine.start(text, TypingEngine.Mode.ADVANCED)
            resetPinyinState()
            updateUiFromEngine()
            hintText.value = context.getString(R.string.primary_hint)
            wrongKeyFlash.value = false
            totalKeystrokes.value = 0
            errorKeystrokes.value = 0
        }
    }

    private fun resetPinyinState() {
        pinyinAccumulator = ""
        pinyinBuffer.value = ""
        selectingCandidates.value = false
        candidateList.value = emptyList()
        candidateIndex.value = 0
    }

    fun onKeyDown(keyCode: Int, metaState: Int): Boolean {
        lastActivityTime = System.currentTimeMillis()
        restartTimeoutWatcher()

        when (keyCode) {
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (selectingCandidates.value == true) {
                    confirmCandidate(); return true
                }
                if (!engine.isRunning && !engine.isComplete()) {
                    engine.markStarted()
                    hintText.value = ""
                }
                return true
            }
            KeyEvent.KEYCODE_DEL -> {
                if (selectingCandidates.value == true) {
                    if (pinyinAccumulator.isNotEmpty()) {
                        pinyinAccumulator = pinyinAccumulator.dropLast(1)
                        pinyinBuffer.value = pinyinAccumulator
                        if (pinyinAccumulator.isEmpty()) cancelCandidateSelection()
                    }
                    return true
                }
                if (engine.currentIndex > 0) { engine.currentIndex--; updateUiFromEngine() }
                return true
            }
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK -> return false
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (isChineseContent && pinyinAccumulator.length >= 1 &&
                    pinyinInputEngine.hasSuggestions(pinyinAccumulator)) {
                    showCandidates(); return true
                }
                return false
            }
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
                hintText.value = "拼音: $pinyinAccumulator ↑选字"
            }
            return true
        }

        // English: pass to engine
        val isCorrect = engine.processKeyPress(char)

        highlightedKey.value = engine.getNextExpectedChar()
        val lower = char.lowercaseChar()
        pressedKeys.value = setOf(lower)
        scheduleClearPressedKeys()
        totalKeystrokes.value = engine.totalKeystrokes
        errorKeystrokes.value = engine.wrongKeystrokes

        if (isCorrect) { soundManager.playCorrect(); wrongKeyFlash.value = false }
        else { soundManager.playWrong(); wrongKeyFlash.value = true; scheduleClearWrongFlash() }

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
                val s = (candidateList.value ?: emptyList()).size
                if (s > 0) candidateIndex.value = ((candidateIndex.value ?: 0) - 1 + s) % s
                updateCandidateHint(); true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val s = (candidateList.value ?: emptyList()).size
                if (s > 0) candidateIndex.value = ((candidateIndex.value ?: 0) + 1) % s
                updateCandidateHint(); true
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> { confirmCandidate(); true }
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK -> { cancelCandidateSelection(); true }
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                val idx = keyCode - KeyEvent.KEYCODE_0
                val list = candidateList.value ?: emptyList()
                if (idx in list.indices) { candidateIndex.value = idx; confirmCandidate() }
                true
            }
            else -> false
        }
    }

    private fun updateCandidateHint() {
        val candidate = candidateList.value?.getOrNull(candidateIndex.value ?: 0) ?: ""
        val total = (candidateList.value ?: emptyList()).size
        hintText.value = "拼音: $pinyinAccumulator → [$candidate] ($total选) ← → Enter确认"
    }

    private fun confirmCandidate() {
        val chosenChar = candidateList.value?.getOrNull(candidateIndex.value ?: 0) ?: return
        val textBuilder = StringBuilder(engine.currentText)
        if (engine.currentIndex < textBuilder.length) {
            textBuilder[engine.currentIndex] = chosenChar[0]
        } else { textBuilder.append(chosenChar[0]) }
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
        resetPinyinState(); hintText.value = ""
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
        return ((engine.calculateAccuracy() / 10 * 100).toInt() +
                (engine.calculateWpm() / 5 * 10).toInt() +
                engine.consecutiveCorrect * 2)
    }

    private fun updateEncouragement() {
        encouragement.value = when {
            engine.consecutiveCorrect >= 50 -> context.getString(R.string.encourage_excellent)
            engine.consecutiveCorrect >= 20 -> context.getString(R.string.encourage_good)
            engine.consecutiveCorrect >= 10 -> context.getString(R.string.encourage_keep)
            else -> ""
        }
    }

    private fun handleCompletion() {
        viewModelScope.launch {
            val isChinese = engine.currentText.any { it in '一'..'鿿' }
            recordDao.insert(RecordEntity(
                mode = "advanced",
                contentType = if (isChinese) "cn_paragraph" else "en_short",
                wpm = engine.calculateWpm(), cpm = engine.calculateCpm(),
                accuracy = engine.calculateAccuracy(), score = calculateScore(),
                totalKeystrokes = engine.totalKeystrokes,
                correctKeystrokes = engine.correctKeystrokes,
                date = System.currentTimeMillis()
            ))
        }
        completionEvent.value = Unit
    }

    fun nextContent() {
        if (autoAdvance) {
            pendingContentId = if (isChineseContent) "random_cn" else "random_en"
            pendingContentLang = if (isChineseContent) "Chinese" else "English"
        }
        startNewSession()
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
