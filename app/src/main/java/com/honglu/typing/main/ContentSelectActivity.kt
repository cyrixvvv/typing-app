package com.honglu.typing.main

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.honglu.typing.R
import com.honglu.typing.data.ContentItem
import com.honglu.typing.data.ContentRepository

class ContentSelectActivity : AppCompatActivity() {

    private lateinit var enContainer: LinearLayout
    private lateinit var cnContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_content_select)

        enContainer = findViewById(R.id.en_container)
        cnContainer = findViewById(R.id.cn_container)

        findViewById<TextView>(R.id.tv_back).setOnClickListener { finish() }

        findViewById<TextView>(R.id.tv_random_en).setOnClickListener {
            launchRandom("random_en", "English")
        }
        findViewById<TextView>(R.id.tv_random_cn).setOnClickListener {
            launchRandom("random_cn", "Chinese")
        }

        populateContent()
    }

    private fun launchRandom(contentId: String, lang: String) {
        val intent = Intent(this, PrimaryModeActivity::class.java)
        intent.putExtra("content_id", contentId)
        intent.putExtra("content_lang", lang)
        startActivity(intent)
    }

    private fun populateContent() {
        val allContent = ContentRepository.listAvailableContent(this)

        allContent.filter { it.lang == "English" }.forEach { item ->
            enContainer.addView(makeCard(item))
        }

        allContent.filter { it.lang == "Chinese" }.forEach { item ->
            cnContainer.addView(makeCard(item))
        }
    }

    private fun makeCard(item: ContentItem): TextView {
        return TextView(this).apply {
            text = item.title
            textSize = 20f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setBackgroundColor(resources.getColor(R.color.bg_key, null))
            gravity = android.view.Gravity.CENTER
            val pad = (8 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad * 2)

            val lp = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            lp.setMargins(pad, 0, pad, 0)
            layoutParams = lp

            isFocusable = true
            isClickable = true
            setOnClickListener {
                val intent = Intent(this@ContentSelectActivity, PrimaryModeActivity::class.java)
                intent.putExtra("content_id", item.compositeId)
                intent.putExtra("content_lang", item.lang)
                startActivity(intent)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
