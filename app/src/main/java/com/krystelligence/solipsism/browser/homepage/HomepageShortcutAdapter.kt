package com.krystelligence.solipsism.browser.homepage

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.browser.image.ImageLoader
import com.krystelligence.solipsism.database.Bookmark
import com.krystelligence.solipsism.databinding.ItemHomepageShortcutBinding

class HomepageShortcutAdapter(
    private val imageLoader: ImageLoader,
    private val onOpen: (String) -> Unit,
) : ListAdapter<Bookmark.Entry, HomepageShortcutAdapter.Holder>(DIFF) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        ItemHomepageShortcutBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(
        private val binding: ItemHomepageShortcutBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(bookmark: Bookmark.Entry) {
            val title = bookmark.title.ifBlank { bookmark.url }
            binding.shortcutTitle.text = title
            binding.shortcutInitial.text = title.trim().firstOrNull()?.uppercase().orEmpty()
            binding.root.contentDescription = binding.root.context.getString(
                R.string.homepage_open_bookmark,
                title,
            )
            binding.root.setOnClickListener { onOpen(bookmark.url) }
            imageLoader.loadImage(binding.shortcutIcon, bookmark)
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Bookmark.Entry>() {
            override fun areItemsTheSame(oldItem: Bookmark.Entry, newItem: Bookmark.Entry): Boolean =
                oldItem.url == newItem.url

            override fun areContentsTheSame(oldItem: Bookmark.Entry, newItem: Bookmark.Entry): Boolean =
                oldItem == newItem
        }
    }
}
