package com.honglu.typing.main

import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.KeyEvent
import android.view.View
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

        // Accept content selection intent
        val contentId = intent.getStringExtra("content_id")
        val contentLang = intent.getStringExtra("content_lang")
        if (contentId != null) {
            viewModel.switchContentMode(
                if (contentLang == "Chinese") ContentMode.CHINESE else ContentMode.ENGLISH
            )
        }

        // Steal focus from tv_back — focus the root layout
        binding.root.isFocusable = true
        binding.root.isFocusableInTouchMode = true
        binding.root.requestFocus()

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
            // pinyin info is embedded in hintText via updateCandidateHint(), no override needed
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

        // ---- Pinyin candidate (ViewModel handles hintText, just restore when deselected) ----
        viewModel.selectingCandidates.observe(this) { selecting ->
            if (!selecting) binding.tvHint.text = viewModel.hintText.value ?: ""
        }

        // ---- Content mode selector ----
        setupModeTabs()
        viewModel.contentMode.observe(this) { updateTabHighlights() }

        // ---- Completion: auto-advance to next content (defer to avoid observer crash) ----
        viewModel.completionEvent.observe(this) {
            binding.root.post {
                try {
                    viewModel.nextContent()
                } catch (_: Exception) { }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.root.requestFocus()
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
    }

    // ==================== Key Events ====================

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // SPACE must always be intercepted before focus system sees it
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

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
