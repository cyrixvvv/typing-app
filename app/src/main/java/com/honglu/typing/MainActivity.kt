package com.honglu.typing

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.honglu.typing.databinding.ActivityMainMenuBinding

/**
 * Main entry activity. Shows a simple menu and dispatches to sub-activities.
 * Designed for TV (LeanBack landscape) but works on any screen.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainMenuBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        binding.btnPrimary.setOnClickListener {
            startActivity(Intent(this, PrimaryModeActivity::class.java))
        }

        binding.btnAdvanced.setOnClickListener {
            startActivity(Intent(this, AdvancedModeActivity::class.java))
        }

        binding.btnContent.setOnClickListener {
            startActivity(Intent(this, ContentSelectActivity::class.java))
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
}
