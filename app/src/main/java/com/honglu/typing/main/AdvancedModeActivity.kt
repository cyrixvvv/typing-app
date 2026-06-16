package com.honglu.typing.main

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.honglu.typing.R
import com.honglu.typing.TypingScreenActivity
import com.honglu.typing.data.ContentRepository
import com.honglu.typing.databinding.ActivityAdvancedBinding
import com.honglu.typing.databinding.ActivityPrimaryBinding
import com.honglu.typing.engine.TypingEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask

/**
 * Advanced mode: WPM/CPM test with large text display and statistics.
 */
class AdvancedModeActivity : TypingScreenActivity() {

    private lateinit var binding: ActivityAdvancedBinding
    private var updateTimer: Timer? = null

    override val mode: TypingEngine.Mode = TypingEngine.Mode.ADVANCED

    override val primaryBinding: ActivityPrimaryBinding? get() = null
    override val advancedBinding: ActivityAdvancedBinding? get() = if (::binding.isInitialized) binding else null

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityAdvancedBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        supportActionBar?.hide()
        bindViews()
        startProgressUpdate()
        setContentFromRepository()
    }

    override fun bindViews() {
        binding.tvBack.setOnClickListener { finish() }
    }

    private fun setContentFromRepository() {
        // Alternate between English and Chinese content
        val text = if ((0..1).random() == 0) {
            ContentRepository.getRandomEnglishText(this)
        } else {
            ContentRepository.getRandomChineseText(this)
        }
        if (text.isNotEmpty()) {
            engine.start(text, mode)
            updateDisplayText()
        }
    }

    override fun onTextComplete() {
        lifecycleScope.launch(Dispatchers.Main) {
            val wpm = engine.calculateWpm()
            val cpm = engine.calculateCpm()
            val accuracy = engine.calculateAccuracy()
            val score = calculateScore()

            scoreManager.recordResult(
                mode = "advanced",
                contentType = if (engine.currentText.any { it in '一'..'鿿' }) "cn_paragraph" else "en_short",
                wpm = wpm,
                cpm = cpm,
                accuracy = accuracy,
                score = score,
                totalKeystrokes = engine.totalKeystrokes,
                correctKeystrokes = engine.correctKeystrokes
            )

            showCompleteDialog(wpm, accuracy, score)
        }
    }

    override fun resetAndRestart() {
        setContentFromRepository()
    }

    override fun onStop() {
        super.onStop()
        updateTimer?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        updateTimer?.cancel()
    }

    private fun startProgressUpdate() {
        updateTimer = Timer("progress_update", false)
        updateTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    if (engine.isRunning && !engine.isComplete()) {
                        binding.tvWpmValue.text = String.format("%.0f", engine.calculateWpm())
                        binding.tvAccuracyValue.text = String.format("%.0f%%", engine.calculateAccuracy())

                        val progress = (engine.getProgress() * 100).toInt()
                        binding.pbProgress.progress = progress

                        updateEncouragement()
                    }
                }
            }
        }, 0L, 500L)
    }

    private fun updateEncouragement() {
        val streak = engine.consecutiveCorrect
        val encourage = if (streak >= 50) {
            getString(R.string.encourage_excellent)
        } else if (streak >= 20) {
            getString(R.string.encourage_good)
        } else if (streak >= 10) {
            getString(R.string.encourage_keep)
        } else {
            ""
        }
        binding.tvEncourage.text = encourage
    }
}
