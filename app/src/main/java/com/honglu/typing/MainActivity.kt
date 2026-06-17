package com.honglu.typing

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.honglu.typing.databinding.ActivityMainMenuBinding
import com.honglu.typing.main.AdvancedModeActivity
import com.honglu.typing.main.PrimaryModeActivity
import com.honglu.typing.main.SettingsActivity

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

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnExit.setOnClickListener {
            finishAffinity()
        }

        // Version display
        binding.tvVersion.text = "Ver ${BuildConfig.VERSION_NAME}"
    }
}
