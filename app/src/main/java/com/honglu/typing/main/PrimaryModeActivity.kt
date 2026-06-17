package com.honglu.typing.main

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.honglu.typing.R
import com.honglu.typing.databinding.ActivityPrimaryBinding
import com.honglu.typing.ui.viewmodel.PrimaryViewModel

class PrimaryModeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrimaryBinding
    private val viewModel: PrimaryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrimaryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        // Pass content from ContentSelectActivity if specified
        val contentId = intent.getStringExtra("content_id")
        val contentLang = intent.getStringExtra("content_lang")
        if (contentId != null) {
            viewModel.setPendingContent(contentId, contentLang ?: "English")
        } else {
            viewModel.startNewSession()
        }

        // Back button
        binding.tvBack.setOnClickListener { finish() }

        // Observe LiveData and update UI
        observeViewModel()

        // KeyboardView bindings
        binding.keyboardView.highlightedKey = viewModel.highlightedKey.value ?: null
        binding.keyboardView.pressedKeys = viewModel.pressedKeys.value ?: emptySet()
        viewModel.highlightedKey.observe(this) { binding.keyboardView.highlightedKey = it }
        viewModel.pressedKeys.observe(this) { binding.keyboardView.pressedKeys = it ?: emptySet() }
        // Flash active
        viewModel.flashActive.observe(this) { active ->
            if (active) {
                binding.keyboardView.startFlashAnimation()
            } else {
                binding.keyboardView.stopFlashAnimation()
            }
        }

        // Text display
        viewModel.currentText.observe(this) { binding.tvDisplayText.text = it }
        viewModel.currentIndex.observe(this) { /* not needed directly */ }
        viewModel.wpm.observe(this) { binding.tvWpm.text = "WPM: %.0f".format(it) }
        viewModel.accuracy.observe(this) { binding.tvAccuracy.text = "正确率: %.0f%%".format(it) }
        viewModel.score.observe(this) { binding.tvScore.text = "得分: %.0f".format(it) }
        viewModel.hintText.observe(this) { binding.tvHint.text = it }
        viewModel.encouragement.observe(this) { /* not used in primary */ }

        // Pinyin candidate UI
        viewModel.selectingCandidates.observe(this) { selecting ->
            // maybe show/hide candidate indicator
            if (selecting) {
                updateCandidateHint()
            }
        }
        viewModel.candidateList.observe(this) { list ->
            if (list.isNotEmpty()) {
                updateCandidateHint()
            }
        }
        viewModel.candidateIndex.observe(this) { updateCandidateHint() }

        // Completion event
        viewModel.completionEvent.observe(this) {
            showCompletionDialog()
        }
    }

    private fun observeViewModel() {
        // Already set up observers above
    }

    private fun updateCandidateHint() {
        val index = viewModel.candidateIndex.value ?: 0
        val list = viewModel.candidateList.value ?: emptyList()
        val candidate = if (list.isNotEmpty()) list[index] else ""
        binding.tvProgressHint.text = "选字: [$candidate] Enter确认"
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event)
        }
        // Let ViewModel handle key
        val handled = viewModel.onKeyDown(event.keyCode, event.metaState)
        if (handled) return true
        // Handle candidate keys if selecting
        if (viewModel.selectingCandidates.value ?: false) {
            val candHandled = viewModel.onCandidateKey(event.keyCode)
            if (candHandled) return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun showCompletionDialog() {
        val wpm = viewModel.wpm.value
        val accuracy = viewModel.accuracy.value
        val score = viewModel.score.value
        AlertDialog.Builder(this)
            .setTitle("练习完成！")
            .setMessage("WPM: %.0f\\n正确率: %.0f%%\\n得分: %d".format(wpm, accuracy, score))
            .setPositiveButton("再来一次") { _, _ ->
                viewModel.startNewSession()
            }
            .setNegativeButton("返回菜单") { _, _ -> finish() }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // ViewModel will be cleared automatically
    }
}