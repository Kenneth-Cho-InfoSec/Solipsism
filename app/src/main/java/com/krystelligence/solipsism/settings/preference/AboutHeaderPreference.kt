package com.krystelligence.solipsism.settings.preference

import android.content.Context
import android.util.AttributeSet
import android.content.Intent
import android.graphics.Color
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.krystelligence.solipsism.BuildConfig
import com.krystelligence.solipsism.R

class AboutHeaderPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {
    init {
        layoutResource = R.layout.preference_about_header
        isSelectable = true
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        // The preference row is only a host for the two About cards. Remove the standard
        // preference surface so it cannot create a third card behind them.
        holder.itemView.setBackgroundColor(Color.TRANSPARENT)
        holder.itemView.setPadding(0, 0, 0, 0)
        holder.itemView.findViewById<android.widget.TextView>(R.id.about_version_value).text =
            BuildConfig.VERSION_NAME
        holder.itemView.findViewById<android.widget.TextView>(R.id.about_developer_value).text =
            context.getString(R.string.developer_name)
    }

    override fun onClick() {
        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://ko-fi.com/kennethchoinfosec")))
    }
}
