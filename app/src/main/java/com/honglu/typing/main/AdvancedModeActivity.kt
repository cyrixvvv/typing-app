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
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.honglu.typing.R
import com.honglu.typing.databinding.ActivityAdvancedBinding
import com.honglu.typing.ui.viewmodel.AdvancedViewModel
import com.honglu.typing.ui.viewmodel.ContentMode
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

class AdvancedModeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdvancedBinding
    private val viewModel: AdvancedViewModel by viewModels()
    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdvancedBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        binding.tvBack.setOnClickListener { finish() }

        // Accept content selection intent (from external callers)
        val contentId = intent.getStringExtra("content_id")
        val contentLang = intent.getStringExtra("content_lang")
        if (contentId != null) {
            viewModel.switchContentMode(
                if (contentLang == "Chinese") ContentMode.CHINESE else ContentMode.ENGLISH
            )
        }

        // ---- Text display ----
        viewModel.currentText.observe(this) { updateDisplayText() }
        viewModel.currentIndex.observe(this) { updateDisplayText() }

        // ---- Stats ----
        viewModel.wpm.observe(this) { binding.tvWpmValue.text = "%.0f".format(it) }
        viewModel.cpm.observe(this) { binding.tvCpmValue.text = "%.0f".format(it) }
        viewModel.accuracy.observe(this) { binding.tvAccuracyValue.text = "%.0f%%".format(it) }
        viewModel.score.observe(this) { binding.tvScoreValue.text = "%d".format(it) }
        viewModel.totalScore.observe(this) { binding.tvTotalScoreValue.text = "%d".format(it) }
        viewModel.progress.observe(this) { binding.pbProgress.progress = (it * 100).toInt() }

        // ---- Hints ----
        viewModel.hintText.observe(this) { binding.tvHint.text = it }
        viewModel.pinyinBuffer.observe(this) { buf ->
            if (buf.isNotEmpty()) {
                binding.tvHint.text = "拼音: $buf"
            }
        }

        // ---- Wrong flash ----
        viewModel.wrongKeyFlash.observe(this) { flashing ->
            if (flashing) {
                binding.tvDisplayText.setTextColor(
                    ContextCompat.getColor(this, R.color.color_wrong))
                binding.tvDisplayText.postDelayed({
                    binding.tvDisplayText.setTextColor(
                        ContextCompat.getColor(this, R.color.text_primary))
                }, 300)
            }
        }

        // ---- Pinyin candidate ----
        viewModel.selectingCandidates.observe(this) { selecting ->
            if (!selecting) binding.tvHint.text = viewModel.hintText.value ?: ""
        }
        viewModel.candidateList.observe(this) { if (it.isNotEmpty()) updateCandidateHint() }
        viewModel.candidateIndex.observe(this) { updateCandidateHint() }

        // ---- Content mode selector ----
        setupModeTabs()
        viewModel.contentMode.observe(this) { updateTabHighlights() }

        // ---- Completion ----
        viewModel.completionEvent.observe(this) { showCompletionDialog() }
    }

    // ==================== Content Mode Tabs ====================

    private val modeTabs: Map<ContentMode, TextView> get() = mapOf(
        ContentMode.ENGLISH to binding.tvModeEn,
        ContentMode.ENGLISH_NUMBERS to binding.tvModeEnNum,
        ContentMode.MIXED_CASE to binding.tvModeMixed,
        ContentMode.CHINESE to binding.tvModeCn
    )

    private fun setupModeTabs() {
        modeTabs.forEach { (mode, view) ->
            view.setOnClickListener { viewModel.switchContentMode(mode) }
        }
    }

    private fun updateTabHighlights() {
        val active = viewModel.contentMode.value ?: ContentMode.ENGLISH
        modeTabs.forEach { (mode, view) ->
            if (mode == active) {
                view.setTextColor(ContextCompat.getColor(this, R.color.color_gold))
                view.setBackgroundResource(R.drawable.selector_tab_active)
            } else {
                view.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                view.setBackgroundResource(R.drawable.selector_tab_inactive)
            }
        }
    }

    // ==================== Text Display ====================

    private fun updateDisplayText() {
        val fullText = viewModel.currentText.value ?: ""
        val index = viewModel.currentIndex.value ?: 0
        if (fullText.isEmpty()) { binding.tvDisplayText.text = "准备就绪"; return }

        val ssb = SpannableStringBuilder(fullText)
        if (index > 0) {
            ssb.setSpan(ForegroundColorSpan(
                ContextCompat.getColor(this, R.color.text_hint)), 0, index, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.setSpan(StyleSpan(Typeface.ITALIC), 0, index, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (index < fullText.length) {
            ssb.setSpan(ForegroundColorSpan(
                ContextCompat.getColor(this, R.color.text_primary)), index, index + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.setSpan(BackgroundColorSpan(
                ContextCompat.getColor(this, R.color.color_progress)), index, index + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.setSpan(StyleSpan(Typeface.BOLD), index, index + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (index + 1 < fullText.length) {
            ssb.setSpan(ForegroundColorSpan(
                ContextCompat.getColor(this, R.color.text_secondary)), index + 1, fullText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        binding.tvDisplayText.text = ssb

        // Auto-scroll: center current character
        binding.tvDisplayText.post {
            val layout = binding.tvDisplayText.layout ?: return@post
            val lastIdx = index.coerceAtMost(layout.text.length - 1)
            val x = layout.getPrimaryHorizontal(lastIdx)
            val target = (x - binding.tvDisplayText.width / 2f).toInt().coerceAtLeast(0)
            if (target != binding.tvDisplayText.scrollX) {
                binding.tvDisplayText.scrollTo(target, 0)
            }
        }
    }

    // ==================== Pinyin Candidate ====================

    private fun updateCandidateHint() {
        val idx = viewModel.candidateIndex.value ?: 0
        val list = viewModel.candidateList.value ?: emptyList()
        val c = if (list.isNotEmpty()) list.getOrElse(idx) { "" } else ""
        binding.tvHint.text = "候选: [$c] ← → 切换, Enter确认"
    }

    // ==================== Key Events ====================

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_SPACE) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                val handled = viewModel.onKeyDown(KeyEvent.KEYCODE_SPACE, event.metaState)
                if (!handled && viewModel.selectingCandidates.value == true) {
                    viewModel.onCandidateKey(KeyEvent.KEYCODE_SPACE)
                }
            }
            return true
        }
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        if (event.keyCode == KeyEvent.KEYCODE_ESCAPE) { finish(); return true }
        val handled = viewModel.onKeyDown(event.keyCode, event.metaState)
        if (handled) return true
        if (viewModel.selectingCandidates.value == true) {
            return viewModel.onCandidateKey(event.keyCode)
        }
        return super.dispatchKeyEvent(event)
    }

    // ==================== Completion ====================

    private fun showCompletionDialog() {
        val wpm = viewModel.wpm.value ?: 0f
        val cpm = viewModel.cpm.value ?: 0f
        val accuracy = viewModel.accuracy.value ?: 0f
        val score = viewModel.score.value ?: 0
        val total = viewModel.totalScore.value ?: 0
        val mode = viewModel.contentMode.value?.label ?: "英文"

        AlertDialog.Builder(this)
            .setTitle("练习完成！")
            .setMessage("""
                类型: $mode
                WPM: %.0f
                CPM: %.0f
                正确率: %.0f%%
                得分: $score
                总分: $total
            """.trimIndent().format(wpm, cpm, accuracy))
            .setPositiveButton("再来一次") { _, _ -> viewModel.nextContent() }
            .setNegativeButton("返回菜单") { _, _ -> finish() }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
