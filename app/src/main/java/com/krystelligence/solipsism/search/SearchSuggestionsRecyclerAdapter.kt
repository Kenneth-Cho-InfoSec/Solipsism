package com.krystelligence.solipsism.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.database.Bookmark
import com.krystelligence.solipsism.database.HistoryEntry
import com.krystelligence.solipsism.database.SearchSuggestion
import com.krystelligence.solipsism.database.WebPage

/**
 * Renders suggestion rows inline as part of the search card, replacing the
 * framework dropdown popup whose ListView silently drops the row margins.
 */
class SearchSuggestionsRecyclerAdapter(
    private val onItemClick: (WebPage) -> Unit,
    private val onInsertClick: (WebPage) -> Unit
) : RecyclerView.Adapter<SearchSuggestionsRecyclerAdapter.ViewHolder>() {

    var items: List<WebPage> = emptyList()
        set(value) {
            field = value.take(MAX_VISIBLE)
            notifyDataSetChanged()
        }

    /** When true, newly bound rows play a staggered enter animation once. */
    var playEnterAnimation: Boolean = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.two_line_autocomplete, parent, false)
        return ViewHolder(view, onItemClick, onInsertClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(items[position], playEnterAnimation, position)

    override fun getItemCount(): Int = items.size

    class ViewHolder(
        view: View,
        private val onItemClick: (WebPage) -> Unit,
        private val onInsertClick: (WebPage) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val titleView: TextView = view.findViewById(R.id.title)
        private val urlView: TextView = view.findViewById(R.id.url)
        private val iconView: ImageView = view.findViewById(R.id.suggestionIcon)
        private val insertButton: FrameLayout = view.findViewById(R.id.complete_search)

        fun bind(page: WebPage, animateEnter: Boolean, position: Int) {
            titleView.text = page.title
            urlView.text = page.url
            iconView.setImageResource(when (page) {
                is Bookmark -> R.drawable.ic_bookmark
                is SearchSuggestion -> R.drawable.ic_search
                is HistoryEntry -> R.drawable.ic_history
                else -> R.drawable.ic_search
            })
            itemView.setOnClickListener { onItemClick(page) }
            insertButton.setOnClickListener { onInsertClick(page) }
            if (animateEnter) {
                itemView.alpha = 0f
                itemView.translationY = -12f
                itemView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(220)
                    .setStartDelay((position * STAGGER_STEP_MS).toLong())
                    .withEndAction {
                        itemView.alpha = 1f
                        itemView.translationY = 0f
                    }
                    .start()
            } else {
                itemView.alpha = 1f
                itemView.translationY = 0f
            }
        }
    }

    companion object {
        private const val MAX_VISIBLE = 5
        private const val STAGGER_STEP_MS = 45
    }
}
