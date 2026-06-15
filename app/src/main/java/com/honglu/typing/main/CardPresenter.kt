package com.honglu.typing.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.leanback.widget.Presenter
import com.honglu.typing.R
import com.honglu.typing.data.ContentItem
import com.honglu.typing.databinding.CardItemBinding

/**
 * Presenter for content cards in the LeanBack list row.
 */
class CardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val binding = CardItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, item: Any) {
        val binding = CardItemBinding.bind(holder.view)
        val contentItem = item as ContentItem

        binding.tvTitle.text = contentItem.title
        binding.tvLang.text = contentItem.lang
    }

    override fun onUnbindViewHolder(holder: ViewHolder) {
        // Nothing to clean up
    }
}
