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
import com.honglu.typing.databinding.ActivityPrimaryBinding
import com.honglu.typing.ui.viewmodel.PrimaryViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PrimaryModeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrimaryBinding
    private val viewModel: PrimaryViewModel by viewModels()
    private var autoClearWrongFlash: Job? = null
    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrimaryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        val contentId = intent.getStringExtra("content_id")
        val contentLang = intent.getStringExtra("content_lang")
        if (contentId != null) {
            viewModel.setPendingContent(contentId, contentLang ?: "English")
        } else {
            viewModel.setPendingContent("random_en", "English")
        }

        binding.tvBack.setOnClickListener { finish() }
        binding.keyboardView.requestFocus()

        // Keyboard bindings
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
        viewModel.wpm.observe(this) { binding.tvWpm.text = "WPM: %.0f".format(it) }
        viewModel.accuracy.observe(this) { binding.tvAccuracy.text = "正确率: %.0f%%".format(it) }
        viewModel.score.observe(this) { binding.tvScore.text = "得分: %d".format(it) }
        viewModel.progress.observe(this) { binding.pbProgress.progress = (it * 100).toInt() }

        // Hint / encouragement (visibility controlled by pinyin observer below)
        viewModel.hintText.observe(this) { binding.tvHint.text = it }

        // Pinyin buffer display (hidden when empty)
        binding.tvPinyinBuffer.visibility = android.view.View.GONE
        binding.tvEncourage.visibility = android.view.View.GONE
        viewModel.pinyinBuffer.observe(this) { buf ->
            if (buf.isNotEmpty()) {
                binding.tvPinyinBuffer.text = buf
                binding.tvPinyinBuffer.visibility = android.view.View.VISIBLE
            } else {
                binding.tvPinyinBuffer.visibility = android.view.View.GONE
            }
        }
        viewModel.encouragement.observe(this) { msg ->
            if (msg.isNotEmpty()) {
                binding.tvEncourage.text = msg
                binding.tvEncourage.visibility = android.view.View.VISIBLE
            } else {
                binding.tvEncourage.visibility = android.view.View.GONE
            }
        }

        // Wrong key flash
        viewModel.wrongKeyFlash.observe(this) { flashing ->
            if (flashing) {
                binding.tvDisplayText.setTextColor(ContextCompat.getColor(this, R.color.color_wrong))
                autoClearWrongFlash?.cancel()
                autoClearWrongFlash = scope.launch {
                    kotlinx.coroutines.delay(300)
                    binding.tvDisplayText.setTextColor(
                        ContextCompat.getColor(this@PrimaryModeActivity, R.color.text_primary)
                    )
                }
            }
        }

        // Pinyin candidate (ViewModel handles hintText, just restore when deselected)
        viewModel.selectingCandidates.observe(this) { selecting ->
            if (!selecting) binding.tvHint.text = viewModel.hintText.value ?: ""
        }

        // Completion (defer to next frame to avoid re-entrant LiveData observer crash)
        viewModel.completionEvent.observe(this) {
            binding.root.post {
                try {
                    if (viewModel.autoAdvance) {
                        viewModel.nextContent()
                    } else {
                        showCompletionDialog()
                    }
                } catch (_: Exception) { }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.keyboardView.requestFocus()
    }

    private fun updateDisplayText() {
        val fullText = viewModel.currentText.value ?: ""
        val index = viewModel.currentIndex.value ?: 0
        if (fullText.isEmpty()) { binding.tvDisplayText.text = getString(R.string.primary_waiting); return }

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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        // Intercept SPACE unconditionally to prevent Android TV focus system theft
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                val handled = viewModel.onKeyDown(keyCode, event.metaState)
                if (!handled && viewModel.selectingCandidates.value == true) {
                    viewModel.onCandidateKey(keyCode)
                }
            }
            return true
        }
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) { finish(); return true }
        val handled = viewModel.onKeyDown(keyCode, event.metaState)
        if (handled) return true
        if (viewModel.selectingCandidates.value == true) return viewModel.onCandidateKey(keyCode)
        return super.dispatchKeyEvent(event)
    }

    private fun showCompletionDialog() {
        AlertDialog.Builder(this)
            .setTitle("练习完成！")
            .setMessage("WPM: %.0f\n正确率: %.0f%%\n得分: %d".format(viewModel.wpm.value ?: 0f, viewModel.accuracy.value ?: 0f, viewModel.score.value ?: 0))
            .setPositiveButton("再来一次") { _, _ -> viewModel.nextContent() }
            .setNegativeButton("返回菜单") { _, _ -> finish() }
            .show()
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
