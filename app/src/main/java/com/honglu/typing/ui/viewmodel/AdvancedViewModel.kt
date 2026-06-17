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
import com.honglu.typing.engine.SoundManager
import com.honglu.typing.engine.TypingEngine
import com.honglu.typing.input.PinyinInputEngine
import com.honglu.typing.util.DeviceUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

enum class ContentMode(val label: String) {
    ENGLISH("英文"),
    ENGLISH_NUMBERS("英数"),
    MIXED_CASE("大小写"),
    CHINESE("中文")
}

class AdvancedViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val prefs = context.getSharedPreferences("advanced_prefs", Context.MODE_PRIVATE)

    private val engine = TypingEngine()
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
    val progress = MutableLiveData<Float>(0f)
    val hintText = MutableLiveData<String>(context.getString(R.string.primary_hint))
    val completionEvent = MutableLiveData<Unit>()
    val wrongKeyFlash = MutableLiveData<Boolean>(false)

    // Content mode
    val contentMode = MutableLiveData(ContentMode.ENGLISH)

    // Total score (accumulated across sessions, persisted)
    val totalScore = MutableLiveData(prefs.getInt("total_score", 0))

    // Pinyin UI
    val selectingCandidates = MutableLiveData<Boolean>(false)
    val candidateList = MutableLiveData<List<String>>(emptyList())
    val candidateIndex = MutableLiveData<Int>(0)
    val pinyinBuffer = MutableLiveData<String>("")

    private var lastActivityTime = 0L
    private val timeoutSeconds = 5L
    private var timeoutJob: Job? = null
    private var wrongFlashJob: Job? = null
    private var isChineseContent = false
    private var pinyinAccumulator = ""

    init {
        loadDictionary()
        startNewSession()
        startTimeoutWatcher()
    }

    private fun loadDictionary() {
        try { pinyinInputEngine.loadDictionary(context, "pinyin_dict.json") } catch (_: Exception) { }
    }

    fun switchContentMode(mode: ContentMode) {
        if (contentMode.value == mode) return
        contentMode.value = mode
        isChineseContent = (mode == ContentMode.CHINESE)
        startNewSession()
    }

    fun startNewSession() {
        val text = generateContent(contentMode.value ?: ContentMode.ENGLISH)
        if (text.isNotEmpty()) {
            isChineseContent = (contentMode.value == ContentMode.CHINESE)
            // Enable case-sensitive matching for mixed case mode
            engine.caseSensitive = (contentMode.value == ContentMode.MIXED_CASE)
            engine.start(text, TypingEngine.Mode.ADVANCED)
            resetPinyinState()
            updateUiFromEngine()
            hintText.value = "按空格键开始"
            wrongKeyFlash.value = false
        }
    }

    private fun generateContent(mode: ContentMode): String {
        return when (mode) {
            ContentMode.ENGLISH -> ContentRepository.getRandomEnglishText(context)
            ContentMode.ENGLISH_NUMBERS -> numberTexts.random()
            ContentMode.MIXED_CASE -> mixedCaseTexts.random()
            ContentMode.CHINESE -> ContentRepository.getRandomChineseText(context)
        }
    }

    fun onKeyDown(keyCode: Int, metaState: Int): Boolean {
        lastActivityTime = System.currentTimeMillis()
        restartTimeoutWatcher()

        when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> {
                if (selectingCandidates.value == true) {
                    confirmCandidate(); return true
                }
                if (!engine.isRunning && !engine.isComplete()) {
                    engine.markStarted()
                    hintText.value = ""
                    return true
                }
                val isCorrect = engine.processKeyPress(' ')
                if (isCorrect) { soundManager.playCorrect(); wrongKeyFlash.value = false }
                else { soundManager.playWrong(); wrongKeyFlash.value = true; scheduleClearWrongFlash() }
                updateUiFromEngine()
                if (engine.isComplete()) handleCompletion()
                return true
            }
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (selectingCandidates.value == true) { confirmCandidate(); return true }
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

        // Normal typing
        val isCorrect = engine.processKeyPress(char)
        if (isCorrect) { soundManager.playCorrect(); wrongKeyFlash.value = false }
        else { soundManager.playWrong(); wrongKeyFlash.value = true; scheduleClearWrongFlash() }
        updateUiFromEngine()
        if (engine.isComplete()) handleCompletion()
        return true
    }

    private fun scheduleClearWrongFlash() {
        wrongFlashJob?.cancel()
        wrongFlashJob = viewModelScope.launch { delay(300); wrongKeyFlash.value = false }
    }

    // --- Pinyin ---

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
        hintText.value = "拼音: $pinyinAccumulator → [$candidate] (${total}选) ← → Enter确认"
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

    private fun resetPinyinState() {
        pinyinAccumulator = ""
        pinyinBuffer.value = ""
        selectingCandidates.value = false
        candidateList.value = emptyList()
        candidateIndex.value = 0
    }

    // --- Scoring ---

    private fun updateUiFromEngine() {
        currentText.value = engine.currentText
        currentIndex.value = engine.currentIndex
        isRunning.value = engine.isRunning
        wpm.value = engine.calculateWpm()
        cpm.value = engine.calculateCpm()
        accuracy.value = engine.calculateAccuracy()
        score.value = calculateScore()
        progress.value = engine.getProgress()
    }

    private fun calculateScore(): Int {
        return ((engine.calculateAccuracy() / 10 * 100).toInt() +
                (engine.calculateWpm() / 5 * 10).toInt() +
                engine.consecutiveCorrect * 2)
    }

    private fun addToTotalScore(sessionScore: Int) {
        val current = prefs.getInt("total_score", 0)
        val newTotal = current + sessionScore
        prefs.edit().putInt("total_score", newTotal).apply()
        totalScore.value = newTotal
    }

    private fun handleCompletion() {
        val sessionScore = calculateScore()
        addToTotalScore(sessionScore)
        viewModelScope.launch {
            val isChinese = engine.currentText.any { it in '一'..'鿿' }
            recordDao.insert(RecordEntity(
                mode = "advanced",
                contentType = if (isChinese) "cn_paragraph" else "en_short",
                wpm = engine.calculateWpm(), cpm = engine.calculateCpm(),
                accuracy = engine.calculateAccuracy(), score = sessionScore,
                totalKeystrokes = engine.totalKeystrokes,
                correctKeystrokes = engine.correctKeystrokes,
                date = System.currentTimeMillis()
            ))
        }
        completionEvent.value = Unit
    }

    fun nextContent() {
        startNewSession()
    }

    // --- Timeout ---

    private fun startTimeoutWatcher() {
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            while (true) {
                delay(500)
            }
        }
    }

    private fun restartTimeoutWatcher() { lastActivityTime = System.currentTimeMillis() }

    override fun onCleared() {
        super.onCleared()
        timeoutJob?.cancel(); wrongFlashJob?.cancel()
        soundManager.cleanup()
    }

    // --- Content Pools ---

    companion object {
        private val numberTexts = listOf(
            "Room 304 is on the 2nd floor with 15 chairs and 3 tables.",
            "The access code is 5421 and the backup pin is 9876.",
            "Chapter 7 has 3 sections covering pages 42 to 89.",
            "Mix 2 cups of flour with 3 eggs and 1 cup of sugar.",
            "Flight AC2016 departs at 4:30 PM from terminal 12.",
            "Population grew from 7.8 billion in 2020 to 8.5 billion in 2030.",
            "Score 98 out of 100 needs at least 36 correct answers out of 40.",
            "Version 2.0 was released in 2024 with 3 new features and 12 bug fixes.",
            "The package weighs 2.5 kg and costs 45 dollars and 99 cents.",
            "Call 911 in an emergency or dial 0 for the operator.",
            "The meeting starts at 3 PM in room 405 on the 4th floor.",
            "There are 365 days in a year and 52 weeks in a year.",
            "My phone number is 138 5555 0192 please call after 6 PM.",
            "The answer to question 5 is on page 28 in section 3.",
            "A decathlon has 10 events spread over 2 days of competition.",
            "Store at negative 18 degrees Celsius for up to 6 months.",
            "The marathon is 42 point 195 kilometers or 26 point 2 miles.",
            "Line 9 of the code on page 3 contains a bug in the 4th parameter.",
            "Train 127 departs from platform 5 at 7:45 AM every weekday.",
            "The 3 little pigs built houses of straw sticks and bricks."
        )

        private val mixedCaseTexts = listOf(
            "The Great Wall of China is one of the Seven Wonders of the World.",
            "Shakespeare wrote Hamlet in London at the Globe Theatre.",
            "Mount Everest is the tallest mountain on Earth at 8848 meters.",
            "Einstein developed the Theory of Relativity in 1915 in Berlin.",
            "Amazon River flows through Brazil Peru and Colombia in South America.",
            "Microsoft Windows was first released in 1985 by Bill Gates.",
            "Da Vinci painted the Mona Lisa in the Louvre Museum in Paris.",
            "The Pacific Ocean is the largest and deepest ocean on Earth.",
            "Harry Potter was written by J.K. Rowling in the United Kingdom.",
            "Mars is called the Red Planet with the tallest volcano Olympus Mons.",
            "Newton discovered Gravity when an Apple fell from a Tree.",
            "The Sahara Desert in Africa is the largest hot Desert on Earth.",
            "Beethoven composed the Fifth Symphony while completely deaf.",
            "Tokyo is the Capital of Japan and the largest City in the World.",
            "The Titanic sank on its First Voyage in April 1912.",
            "Galileo used his Telescope to discover the Moons of Jupiter.",
            "The Nile River in Egypt is the longest River in the World.",
            "Einstein won the Nobel Prize in Physics for his work on Light.",
            "The Amazon Rainforest produces about 20 percent of the World Oxygen.",
            "Leonardo da Vinci was a Painter Inventor and Engineer from Italy."
        )
    }
}
