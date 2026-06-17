package com.honglu.typing.main

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.honglu.typing.R
import com.honglu.typing.databinding.ActivityAdvancedBinding
import com.honglu.typing.ui.viewmodel.AdvancedViewModel

class AdvancedModeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdvancedBinding
    private val viewModel: AdvancedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdvancedBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        binding.tvBack.setOnClickListener { finish() }

        observeViewModel()
    }

    private fun observeViewModel() {
        // Text
        viewModel.currentText.observe(this) { binding.tvDisplayText.text = it }
        // Stats
        viewModel.wpm.observe(this) { binding.tvWpmValue.text = "%.0f".format(it) }
        viewModel.cpm.observe(this) { /* optional */ }
        viewModel.accuracy.observe(this) { binding.tvAccuracyValue.text = "%.0f%%".format(it) }
        viewModel.score.observe(this) { binding.tvScoreValue.text = "%d".format(it) }
        viewModel.progress.observe(this) { binding.pbProgress.progress = (it * 100).toInt() }
        // Flash background
        viewModel.flashActive.observe(this) { active ->
            val color = if (active) R.color.flash_color1 else R.color.bg_primary
            binding.root.setBackgroundColor(resources.getColor(color))
        }
        // Encouragement
        viewModel.encouragement.observe(this) { binding.tvEncourage.text = it }
        // Hint
        viewModel.hintText.observe(this) { binding.tvHint.text = it }
        // Completion
        viewModel.completionEvent.observe(this) {
            showCompletionDialog()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event)
        }
        val handled = viewModel.onKeyDown(event.keyCode, event.metaState)
        if (handled) return true
        return super.dispatchKeyEvent(event)
    }

    private fun showCompletionDialog() {
        val wpm = viewModel.wpm.value
        val accuracy = viewModel.accuracy.value
        val score = viewModel.score.value
        val cpm = viewModel.cpm.value
        val isChinese = viewModel.currentText.value?.any { it in '一'..'鿿' } == true
        val typeStr = if (isChinese) "中文" else "英文"
        AlertDialog.Builder(this)
            .setTitle("练习完成！")
            .setMessage(
                "WPM: %.0f\\nCPM: %.0f\\n正确率: %.0f%%\\n得分: %d\\n类型: %s"
                    .format(wpm, cpm, accuracy, score, typeStr)
            )
            .setPositiveButton("再来一次") { _, _ ->
                viewModel.startNewSession()
            }
            .setNegativeButton("返回菜单") { _, _ -> finish() }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}