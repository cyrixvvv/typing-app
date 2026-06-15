package com.honglu.typing.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.leanback.app.BrowseFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
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

        val ctx: Context = context ?: return

        // Create adapter
        val mainAdapter = ArrayObjectAdapter(ListRowPresenter())

        // English content row
        val enItems = ArrayObjectAdapter(CardPresenter())
        val enContent = ContentRepository.listAvailableContent(ctx).filter { it.lang == "English" }
        enContent.forEach { enItems.add(it) }
        mainAdapter.add(ListRow(HeaderItem("ENGLISH"), enItems))

        // Chinese content row
        val cnItems = ArrayObjectAdapter(CardPresenter())
        val cnContent = ContentRepository.listAvailableContent(ctx).filter { it.lang == "Chinese" }
        cnContent.forEach { cnItems.add(it) }
        mainAdapter.add(ListRow(HeaderItem("中文"), cnItems))

        adapter = mainAdapter

        onItemViewClickedListener = OnItemViewClickedListener { viewHolder: Presenter.ViewHolder, item: Any, rowViewHolder: RowPresenter.ViewHolder, row: Row ->
            (item as? ContentItem)?.let {
                val intent = Intent(ctx, PrimaryModeActivity::class.java)
                intent.putExtra("content_id", it.id)
                intent.putExtra("content_lang", it.lang)
                startActivity(intent)
            }
        }
    }
}