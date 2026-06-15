package com.honglu.typing.main

import android.os.Bundle
import android.view.KeyEvent
import androidx.lifecycle.lifecycleScope
import com.honglu.typing.TypingScreenActivity
import com.honglu.typing.databinding.ActivityAdvancedBinding
import com.honglu.typing.databinding.ActivityPrimaryBinding
import com.honglu.typing.engine.TypingEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Primary mode: finger training.
 * Shows text above, keyboard below. One character at a time.
 * Supports both English typing and Chinese pinyin input.
 */
class PrimaryModeActivity : TypingScreenActivity() {

    private lateinit var binding: ActivityPrimaryBinding
    override val mode: TypingEngine.Mode = TypingEngine.Mode.PRIMARY

    override val primaryBinding: ActivityPrimaryBinding? get() = if (::binding.isInitialized) binding else null
    override val advancedBinding: ActivityAdvancedBinding? get() = null

    // Pinyin input state
    private var pinyinAccumulator = ""
    private var selectingCandidates = false
    private var candidateList = emptyList<String>()
    private var candidateIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrimaryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        // Set initial content
        setContentFromRepository()
    }

    override fun bindViews() {
        binding.tvBack.setOnClickListener { finish() }
    }

    private fun setContentFromRepository() {
        val content = com.honglu.typing.data.ContentRepository.getRandomEnglishText(this)
        if (content.isNotEmpty()) {
            engine.start(content, mode)
            pinyinAccumulator = ""
            selectingCandidates = false
            updateDisplayText()
            updateKeyboardDisplay()
        }
    }

    override fun resetAndRestart() {
        setContentFromRepository()
    }

    override fun onTextComplete() {
        lifecycleScope.launch(Dispatchers.Main) {
            val wpm = engine.calculateWpm()
            val accuracy = engine.calculateAccuracy()
            val score = calculateScore()

            scoreManager.recordResult(
                mode = "primary",
                contentType = "en_short",
                wpm = wpm,
                cpm = 0f,
                accuracy = accuracy,
                score = score,
                totalKeystrokes = engine.totalKeystrokes,
                correctKeystrokes = engine.correctKeystrokes
            )

            showCompleteDialog(wpm, accuracy, score)
        }
    }

    override fun tryHandlePinyinKey(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        return when {
            // If selecting Chinese candidates, handle D-pad/Enter first
            selectingCandidates -> handleCandidateInput(event)
            // Pinyin letter keys (a-z)
            else -> handlePinyinLetter(event)
        }
    }

    /**
     * Handle a-z keys for pinyin accumulation.
     */
    private fun handlePinyinLetter(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val char = com.honglu.typing.util.DeviceUtils.keyCodeToChar(keyCode, false)
        if (char != null && char in 'a'..'z') {
            pinyinAccumulator += char
            return when {
                pinyinInputEngine.hasSuggestions(pinyinAccumulator) -> {
                    showCandidateList()
                    true
                }
                else -> false  // Let base class handle as English letter
            }
        }
        return false
    }

    /**
     * Handle D-pad/Enter for Chinese character candidate selection.
     */
    private fun handleCandidateInput(event: KeyEvent): Boolean {
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                // Cycle through candidates
                candidateIndex = if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    (candidateIndex - 1 + candidateList.size) % candidateList.size
                } else {
                    (candidateIndex + 1) % candidateList.size
                }
                showCandidateIndicator()
                true
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                confirmCandidate()
                true
            }
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK -> {
                // Cancel candidate selection
                pinyinAccumulator = ""
                selectingCandidates = false
                candidateList = emptyList()
                true
            }
            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3,
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5 -> {
                // Quick select by number key
                val idx = event.keyCode - KeyEvent.KEYCODE_1
                if (idx < candidateList.size) {
                    candidateIndex = idx
                    confirmCandidate()
                }
                true
            }
            else -> false
        }
    }

    private fun showCandidateList() {
        candidateList = pinyinInputEngine.getSuggestions(pinyinAccumulator)
        if (candidateList.isNotEmpty()) {
            selectingCandidates = true
            candidateIndex = 0
            showCandidateIndicator()
        }
    }

    private fun showCandidateIndicator() {
        if (candidateList.isNotEmpty()) {
            val candidate = candidateList[candidateIndex]
            binding.tvProgressHint.text = "选字: [$candidate]  Enter确认"
        }
    }

    private fun confirmCandidate() {
        if (candidateList.isNotEmpty() && candidateIndex in candidateList.indices) {
            val chosenChar = candidateList[candidateIndex]
            // Insert the chosen Chinese character at current position
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

            pinyinAccumulator = ""
            selectingCandidates = false
            candidateList = emptyList()

            soundManager.playCorrect()
            updateDisplayText()
            updateKeyboardDisplay()
        }
    }
}
