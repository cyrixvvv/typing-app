package com.honglu.typing.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.leanback.app.BrowseFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
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
        val enContent = ContentRepository.listAvailableContent(requireContext()).filter { it.lang == "English" }
        enContent.forEach { enItems.add(it) }
        mainAdapter.add(ListRow(HeaderItem("ENGLISH"), enItems))

        // Chinese content row
        val cnItems = ArrayObjectAdapter(CardPresenter())
        val cnContent = ContentRepository.listAvailableContent(requireContext()).filter { it.lang == "Chinese" }
        cnContent.forEach { cnItems.add(it) }
        mainAdapter.add(ListRow(HeaderItem("中文"), cnItems))

        adapter = mainAdapter

        onItemViewClickedListener = OnItemViewClickedListener { _: Presenter.ViewHolder, item: Any, _: Row, _: Long ->
            (item as? ContentItem)?.let {
                val intent = Intent(requireContext(), PrimaryModeActivity::class.java)
                intent.putExtra("content_id", it.id)
                intent.putExtra("content_lang", it.lang)
                startActivity(intent)
            }
        }
    }
}