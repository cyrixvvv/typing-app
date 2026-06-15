package com.honglu.typing.main

import android.app.AlertDialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.honglu.typing.R
import com.honglu.typing.databinding.ActivitySettingsBinding
import com.honglu.typing.engine.ScoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings activity: sound toggle, timeout, clear data.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var scoreManager: ScoreManager

    private val prefs by lazy {
        getSharedPreferences("typing_config", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        scoreManager = ScoreManager(this)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.menu_settings)

        // Load saved settings
        binding.cbSound.isChecked = prefs.getBoolean("sound_enabled", true)
        binding.etTimeout.setText(prefs.getInt("timeout_seconds", 5).toString())

        binding.cbSound.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("sound_enabled", isChecked).apply()
        }

        binding.etTimeout.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val seconds = v.text.toString().toIntOrNull() ?: 5
                prefs.edit().putInt("timeout_seconds", seconds).apply()
                true
            } else {
                false
            }
        }

        binding.btnClear.setOnClickListener {
            showClearConfirmation()
        }
    }

    private fun showClearConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_clear)
            .setMessage(R.string.settings_clear_confirm)
            .setPositiveButton(R.string.common_ok) { _, _ ->
                lifecycleScope.launch {
                    scoreManager.clearAllData()
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            this@SettingsActivity,
                            getString(R.string.settings_cleared),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
