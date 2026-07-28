package com.krystelligence.solipsism.settings.fragment

import android.os.Bundle
import android.view.View
import androidx.annotation.XmlRes
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreferenceCompat
import androidx.recyclerview.widget.RecyclerView
import com.krystelligence.solipsism.R

/**
 * An abstract settings fragment which performs wiring for an instance of [PreferenceFragmentCompat].
 */
abstract class AbstractSettingsFragment : PreferenceFragmentCompat() {

    /**
     * Provide the XML resource which holds the preferences.
     */
    @XmlRes
    protected abstract fun providePreferencesXmlResource(): Int

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(providePreferencesXmlResource(), rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val horizontalPadding = resources.getDimensionPixelSize(R.dimen.material_grid_unit)
        val verticalPadding = resources.getDimensionPixelSize(R.dimen.material_grid_margin)
        setDivider(null)
        setDividerHeight(0)
        listView.apply {
            clipToPadding = false
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) {
                    updatePreferenceRowBackground(view)
                }

                override fun onChildViewDetachedFromWindow(view: View) = Unit
            })
            post { updateVisiblePreferenceRowBackgrounds() }
        }
    }

    open fun applySettingsSearch(query: String) {
        val normalizedQuery = query.trim().lowercase()
        preferenceScreen?.filterChildren(normalizedQuery)
        listView?.post { updateVisiblePreferenceRowBackgrounds() }
    }

    private fun Preference.matches(query: String): Boolean =
        query.isBlank() ||
            title?.toString()?.lowercase()?.contains(query) == true ||
            summary?.toString()?.lowercase()?.contains(query) == true

    private fun PreferenceGroup.filterChildren(query: String): Boolean {
        var hasVisibleChild = false
        for (index in 0 until preferenceCount) {
            val preference = getPreference(index)
            val isVisible = if (preference is PreferenceGroup) {
                preference.filterChildren(query) || preference.matches(query)
            } else {
                preference.matches(query)
            }
            preference.isVisible = isVisible
            hasVisibleChild = hasVisibleChild || isVisible
        }
        return hasVisibleChild
    }

    private fun updateVisiblePreferenceRowBackgrounds() {
        val recyclerView = listView ?: return
        for (index in 0 until recyclerView.childCount) {
            updatePreferenceRowBackground(recyclerView.getChildAt(index))
        }
    }

    private fun updatePreferenceRowBackground(view: View) {
        val adapterPosition = listView.getChildAdapterPosition(view)
        val preference = visiblePreferences().getOrNull(adapterPosition) ?: return
        if (preference is PreferenceCategory) {
            view.background = null
            return
        }

        val previous = visiblePreferences().getOrNull(adapterPosition - 1)
        val next = visiblePreferences().getOrNull(adapterPosition + 1)
        val startsCluster = previous == null || previous is PreferenceCategory
        val endsCluster = next == null || next is PreferenceCategory
        val background = when {
            startsCluster && endsCluster -> R.drawable.preference_group_item_background_single
            startsCluster -> R.drawable.preference_group_item_background_top
            endsCluster -> R.drawable.preference_group_item_background_bottom
            else -> R.drawable.preference_group_item_background_middle
        }
        view.setBackgroundResource(background)
    }

    private fun visiblePreferences(): List<Preference> {
        val preferences = mutableListOf<Preference>()
        preferenceScreen?.collectVisiblePreferences(preferences)
        return preferences
    }

    private fun PreferenceGroup.collectVisiblePreferences(output: MutableList<Preference>) {
        for (index in 0 until preferenceCount) {
            val preference = getPreference(index)
            if (!preference.isVisible) {
                continue
            }
            output += preference
            if (preference is PreferenceGroup) {
                preference.collectVisiblePreferences(output)
            }
        }
    }

    /**
     * Creates a [CheckBoxPreference] with the provided options and listener.
     *
     * @param preference the preference to create.
     * @param isChecked true if it should be initialized as checked, false otherwise.
     * @param isEnabled true if the preference should be enabled, false otherwise. Defaults to true.
     * @param summary the summary to display. Defaults to null, which results in no summary.
     * @param onCheckChange the function that should be called when the check box is toggled.
     */
    protected fun checkBoxPreference(
        preference: String,
        isChecked: Boolean,
        isEnabled: Boolean = true,
        summary: String? = null,
        onCheckChange: (Boolean) -> Unit
    ): CheckBoxPreference = findPreference<CheckBoxPreference>(preference)!!.apply {
        this.isChecked = isChecked
        this.isEnabled = isEnabled
        summary?.let {
            this.summary = summary
        }
        onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, any: Any ->
            onCheckChange(any as Boolean)
            true
        }
    }

    /**
     * Creates a simple [Preference] which reacts to clicks with the provided options and listener.
     *
     * @param preference the preference to create.
     * @param isEnabled true if the preference should be enabled, false otherwise. Defaults to true.
     * @param summary the summary to display. Defaults to null, which results in no summary.
     * @param onClick the function that should be called when the preference is clicked.
     */
    protected fun clickablePreference(
        preference: String,
        isEnabled: Boolean = true,
        summary: String? = null,
        onClick: () -> Unit
    ): Preference = clickableDynamicPreference(
        preference = preference,
        isEnabled = isEnabled,
        summary = summary,
        onClick = { onClick() }
    )

    /**
     * Creates a simple [Preference] which reacts to clicks with the provided options and listener.
     * It also allows its summary to be updated when clicked.
     *
     * @param preference the preference to create.
     * @param isEnabled true if the preference should be enabled, false otherwise. Defaults to true.
     * @param summary the summary to display. Defaults to null, which results in no summary.
     * @param onClick the function that should be called when the preference is clicked. The
     * function is supplied with a [SummaryUpdater] object so that it can update the summary if
     * desired.
     */
    protected fun clickableDynamicPreference(
        preference: String,
        isEnabled: Boolean = true,
        summary: String? = null,
        onClick: (SummaryUpdater) -> Unit
    ): Preference = findPreference<Preference>(preference)!!.apply {
        this.isEnabled = isEnabled
        summary?.let {
            this.summary = summary
        }
        val summaryUpdate = SummaryUpdater(this)
        onPreferenceClickListener = Preference.OnPreferenceClickListener {
            onClick(summaryUpdate)
            true
        }
    }

    /**
     * Creates a [SwitchPreferenceCompat] with the provided options and listener.
     *
     * @param preference the preference to create.
     * @param isChecked true if it should be initialized as checked, false otherwise.
     * @param isEnabled true if the preference should be enabled, false otherwise. Defaults to true.
     * @param onCheckChange the function that should be called when the toggle is toggled.
     */
    protected fun togglePreference(
        preference: String,
        isChecked: Boolean,
        isEnabled: Boolean = true,
        summary: String? = null,
        onCheckChange: (Boolean) -> Unit
    ): SwitchPreferenceCompat = findPreference<SwitchPreferenceCompat>(preference)!!.apply {
        this.isChecked = isChecked
        this.isEnabled = isEnabled
        summary?.let {
            this.summary = summary
        }
        onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, any: Any ->
            onCheckChange(any as Boolean)
            true
        }
    }

}
