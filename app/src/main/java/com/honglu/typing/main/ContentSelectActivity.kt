package com.honglu.typing.main

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.fragment.app.FragmentActivity
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.app.BrowseFragment
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import com.honglu.typing.R
import com.honglu.typing.data.ContentItem
import com.honglu.typing.data.ContentRepository

/**
 * Content selection page: browse by language and difficulty.
 * Uses LeanBack BrowseFragment for TV compatibility.
 */
class ContentSelectActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_content_select)

        if (savedInstanceState != null) return

        // Note: BrowseFragment() constructor is public, no newInstance() needed
        val fragment = BrowseFragment()

        val adapter = ArrayObjectAdapter(ListRowPresenter())

        val enAdapter = ArrayObjectAdapter(CardPresenter())
        ContentRepository.listAvailableContent(this)
            .filter { it.lang == "English" }
            .forEach { enAdapter.add(it) }
        adapter.add(ListRow(HeaderItem(0, "ENGLISH"), enAdapter))

        val cnAdapter = ArrayObjectAdapter(CardPresenter())
        ContentRepository.listAvailableContent(this)
            .filter { it.lang == "Chinese" }
            .forEach { cnAdapter.add(it) }
        adapter.add(ListRow(HeaderItem(1, "中文"), cnAdapter))

        fragment.headersState = BrowseFragment.HEADERS_ENABLED
        fragment.title = getString(R.string.menu_content)
        fragment.adapter = adapter

        fragment.onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            (item as? ContentItem)?.let { contentItem ->
                val intent = Intent(this, PrimaryModeActivity::class.java)
                intent.putExtra("content_id", contentItem.id)
                intent.putExtra("content_lang", contentItem.lang)
                startActivity(intent)
            }
        }

        // R.id.browse_fragment_container is a FrameLayout, so pass it as the container View ID
        supportFragmentManager.beginTransaction()
            .replace(R.id.browse_fragment_container, fragment)
            .commit()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}