package com.honglu.typing.main

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.KeyEvent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.honglu.typing.R
import com.honglu.typing.databinding.ActivityAdvancedBinding
import com.honglu.typing.ui.viewmodel.AdvancedViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AdvancedModeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdvancedBinding
    private val viewModel: AdvancedViewModel by viewModels()
    private var autoClearWrongFlash: Job? = null
    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdvancedBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        // Accept content selection intent
        val contentId = intent.getStringExtra("content_id")
        val contentLang = intent.getStringExtra("content_lang")
        if (contentId != null) {
            viewModel.setPendingContent(contentId, contentLang ?: "English")
        }

        binding.tvBack.setOnClickListener { finish() }
        binding.keyboardView.requestFocus()

        // Keyboard
        binding.keyboardView.highlightedKey = viewModel.highlightedKey.value ?: null
        binding.keyboardView.pressedKeys = viewModel.pressedKeys.value ?: emptySet()
        viewModel.highlightedKey.observe(this) { binding.keyboardView.highlightedKey = it }
        viewModel.pressedKeys.observe(this) { binding.keyboardView.pressedKeys = it ?: emptySet() }

        // Flash
        viewModel.flashActive.observe(this) { active ->
            if (active) binding.keyboardView.startFlashAnimation()
            else binding.keyboardView.stopFlashAnimation()
        }

        // Text display
        viewModel.currentText.observe(this) { updateDisplayText() }
        viewModel.currentIndex.observe(this) { updateDisplayText() }

        // Stats
        viewModel.wpm.observe(this) { binding.tvWpmValue.text = "%.0f".format(it) }
        viewModel.cpm.observe(this) { binding.tvCpmValue.text = "%.0f".format(it) }
        viewModel.accuracy.observe(this) { binding.tvAccuracyValue.text = "%.0f%%".format(it) }
        viewModel.score.observe(this) { binding.tvScoreValue.text = "%d".format(it) }
        viewModel.errorKeystrokes.observe(this) { binding.tvErrorsValue.text = "%d".format(it) }
        viewModel.progress.observe(this) { binding.pbProgress.progress = (it * 100).toInt() }

        // Encouragement / hint
        viewModel.encouragement.observe(this) { binding.tvEncourage.text = it }
        viewModel.hintText.observe(this) { binding.tvHint.text = it }

        // Pinyin buffer
        viewModel.pinyinBuffer.observe(this) { buf ->
            binding.tvPinyinBuffer.text = buf
        }

        // Wrong key flash
        viewModel.wrongKeyFlash.observe(this) { flashing ->
            if (flashing) {
                binding.tvDisplayText.setTextColor(ContextCompat.getColor(this, R.color.color_wrong))
                autoClearWrongFlash?.cancel()
                autoClearWrongFlash = scope.launch {
                    kotlinx.coroutines.delay(300)
                    binding.tvDisplayText.setTextColor(
                        ContextCompat.getColor(this@AdvancedModeActivity, R.color.text_primary)
                    )
                }
            }
        }

        // Pinyin candidate
        viewModel.selectingCandidates.observe(this) { selecting ->
            if (!selecting) binding.tvHint.text = viewModel.hintText.value ?: ""
        }
        viewModel.candidateList.observe(this) { if (it.isNotEmpty()) updateCandidateHint() }
        viewModel.candidateIndex.observe(this) { updateCandidateHint() }

        // Completion
        viewModel.completionEvent.observe(this) {
            if (viewModel.autoAdvance) viewModel.nextContent()
            else showCompletionDialog()
        }
    }

    private fun updateDisplayText() {
        val fullText = viewModel.currentText.value ?: ""
        val index = viewModel.currentIndex.value ?: 0
        if (fullText.isEmpty()) { binding.tvDisplayText.text = "准备就绪"; return }

        val ssb = SpannableStringBuilder(fullText)
        if (index > 0) {
            ssb.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, R.color.text_hint)), 0, index, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.setSpan(StyleSpan(Typeface.ITALIC), 0, index, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (index < fullText.length) {
            ssb.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, R.color.text_primary)), index, index + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.setSpan(BackgroundColorSpan(ContextCompat.getColor(this, R.color.color_progress)), index, index + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.setSpan(StyleSpan(Typeface.BOLD), index, index + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (index + 1 < fullText.length) {
            ssb.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, R.color.text_secondary)), index + 1, fullText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        binding.tvDisplayText.text = ssb
    }

    private fun updateCandidateHint() {
        val idx = viewModel.candidateIndex.value ?: 0
        val list = viewModel.candidateList.value ?: emptyList()
        val c = if (list.isNotEmpty()) list.getOrElse(idx) { "" } else ""
        binding.tvHint.text = "候选: [$c] ← → 切换, Enter确认"
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        if (event.keyCode == KeyEvent.KEYCODE_ESCAPE) { finish(); return true }
        val handled = viewModel.onKeyDown(event.keyCode, event.metaState)
        if (handled) return true
        if (viewModel.selectingCandidates.value == true) return viewModel.onCandidateKey(event.keyCode)
        return super.dispatchKeyEvent(event)
    }

    private fun showCompletionDialog() {
        val wpm = viewModel.wpm.value ?: 0f
        val accuracy = viewModel.accuracy.value ?: 0f
        val score = viewModel.score.value ?: 0
        val cpm = viewModel.cpm.value ?: 0f
        val isChinese = viewModel.currentText.value?.any { it in '一'..'鿿' } == true
        AlertDialog.Builder(this)
            .setTitle("练习完成！")
            .setMessage("WPM: %.0f\nCPM: %.0f\n正确率: %.0f%%\n得分: %d\n类型: %s".format(wpm, cpm, accuracy, score, if (isChinese) "中文" else "英文"))
            .setPositiveButton("再来一次") { _, _ -> viewModel.nextContent() }
            .setNegativeButton("返回菜单") { _, _ -> finish() }
            .show()
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
