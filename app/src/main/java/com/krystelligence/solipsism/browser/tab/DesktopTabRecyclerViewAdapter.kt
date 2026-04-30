package com.krystelligence.solipsism.browser.tab

import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.extensions.desaturate
import com.krystelligence.solipsism.extensions.inflater
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter

/**
 * The adapter that renders tabs in the desktop form.
 *
 * @param onClick Invoked when the tab is clicked.
 * @param onLongClick Invoked when the tab is long pressed.
 * @param onCloseClick Invoked when the tab's close button is clicked.
 */
class DesktopTabRecyclerViewAdapter(
    context: Context,
    private val onClick: (Int) -> Unit,
    private val onLongClick: (Int) -> Unit,
    private val onCloseClick: (Int) -> Unit,
) : ListAdapter<TabViewState, TabViewHolder>(
    object : DiffUtil.ItemCallback<TabViewState>() {
        override fun areItemsTheSame(oldItem: TabViewState, newItem: TabViewState): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: TabViewState, newItem: TabViewState): Boolean =
            oldItem == newItem
    }
) {
    private val backgroundTabDrawable: Drawable
    private val foregroundTabDrawable: Drawable
    private var foregroundLayout: View? = null

    init {
        backgroundTabDrawable =
            requireNotNull(AppCompatResources.getDrawable(context, R.drawable.tab_background))
        foregroundTabDrawable =
            requireNotNull(AppCompatResources.getDrawable(context, R.drawable.tab_background_selected))
    }

    fun updateForegroundTabColor(color: Int) {
        foregroundLayout?.invalidate()
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): TabViewHolder {
        val view =
            viewGroup.context.inflater.inflate(R.layout.tab_list_item_horizontal, viewGroup, false)
        return TabViewHolder(
            view,
            onClick = onClick,
            onLongClick = onLongClick,
            onCloseClick = onCloseClick
        )
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        holder.exitButton.tag = position

        val tab = getItem(position)

        holder.txtTitle.text = tab.title
        updateViewHolderAppearance(holder, tab.isSelected)
        updateViewHolderFavicon(holder, tab.icon, tab.isSelected)
        updateViewHolderBackground(holder, tab.isSelected)
    }

    private fun updateViewHolderFavicon(
        viewHolder: TabViewHolder,
        favicon: Bitmap?,
        isForeground: Boolean
    ) {
        favicon?.let {
            if (isForeground) {
                viewHolder.favicon.setImageBitmap(it)
            } else {
                viewHolder.favicon.setImageBitmap(it.desaturate())
            }
        } ?: viewHolder.favicon.setImageResource(R.drawable.ic_webpage)
    }

    private fun updateViewHolderBackground(viewHolder: TabViewHolder, isForeground: Boolean) {
        if (isForeground) {
            foregroundLayout = viewHolder.layout
            viewHolder.layout.background = foregroundTabDrawable
        } else {
            viewHolder.layout.background = backgroundTabDrawable
        }
    }

    private fun updateViewHolderAppearance(
        viewHolder: TabViewHolder,
        isForeground: Boolean
    ) {
        if (isForeground) {
            TextViewCompat.setTextAppearance(
                viewHolder.txtTitle,
                R.style.TextAppearance_Solipsism_TabTitle_Selected
            )
            viewHolder.txtTitle.setTextColor(
                ContextCompat.getColor(viewHolder.itemView.context, R.color.md3_on_surface)
            )
        } else {
            TextViewCompat.setTextAppearance(
                viewHolder.txtTitle,
                R.style.TextAppearance_Solipsism_TabTitle
            )
            viewHolder.txtTitle.setTextColor(
                ContextCompat.getColor(viewHolder.itemView.context, R.color.md3_on_surface_variant)
            )
        }
    }
}
