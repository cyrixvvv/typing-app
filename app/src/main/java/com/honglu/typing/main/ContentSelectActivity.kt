package com.honglu.typing.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.app.BrowseFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import com.honglu.typing.R
import com.honglu.typing.data.ContentItem
import com.honglu.typing.data.ContentRepository

/**
 * Content selection page: browse by language and difficulty.
 * Uses LeanBack ListRow + ListRowPresenter for TV compatibility.
 */
class ContentSelectActivity : BrowseFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        headersState = HEADERS_ENABLED

        // Create adapter
        val mainAdapter = ArrayObjectAdapter(ListRowPresenter())

        // English content row
        val enItems = ArrayObjectAdapter(CardPresenter())
        val enContent = ContentRepository.listAvailableContent(this).filter { it.lang == "English" }
        enContent.forEach { enItems.add(it) }
        mainAdapter.add(ListRow(HeaderItem("ENGLISH"), enItems))

        // Chinese content row
        val cnItems = ArrayObjectAdapter(CardPresenter())
        val cnContent = ContentRepository.listAvailableContent(this).filter { it.lang == "Chinese" }
        cnContent.forEach { cnItems.add(it) }
        mainAdapter.add(ListRow(HeaderItem("中文"), cnItems))

        adapter = mainAdapter

        onItemClickedListener = androidx.leanback.widget.OnItemViewClickedListener { item, _, rowPosition, _ ->
            item as? ContentItem ?: return@OnItemViewClickedListener
            // Navigate to typing mode with selected content
            val intent = Intent(this, PrimaryModeActivity::class.java)
            intent.putExtra("content_id", item.id)
            intent.putExtra("content_lang", item.lang)
            startActivity(intent)
        }
    }
}
