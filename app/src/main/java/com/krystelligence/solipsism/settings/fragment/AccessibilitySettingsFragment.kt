package com.krystelligence.solipsism.settings.fragment

import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.browser.di.injector
import com.krystelligence.solipsism.browser.view.RenderingMode
import com.krystelligence.solipsism.extensions.resizeAndShow
import com.krystelligence.solipsism.extensions.withSingleChoiceItems
import com.krystelligence.solipsism.preference.UserPreferences
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import javax.inject.Inject

class AccessibilitySettingsFragment : AbstractSettingsFragment() {

    @Inject internal lateinit var userPreferences: UserPreferences

    override fun providePreferencesXmlResource() = R.xml.preference_accessibility

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        injector.inject(this)

        clickablePreference(
            preference = SETTINGS_TEXT_SIZE,
            onClick = ::showTextSizePicker
        )

        clickableDynamicPreference(
            preference = SETTINGS_RENDERING_MODE,
            summary = userPreferences.renderingMode.toDisplayString(),
            onClick = ::showRenderingDialogPicker
        )

        togglePreference(
            preference = SETTINGS_REFLOW,
            isChecked = userPreferences.textReflowEnabled,
            onCheckChange = { userPreferences.textReflowEnabled = it }
        )

        togglePreference(
            preference = SETTINGS_VIEWPORT,
            isChecked = userPreferences.useWideViewPortEnabled,
            onCheckChange = { userPreferences.useWideViewPortEnabled = it }
        )

        togglePreference(
            preference = SETTINGS_ALLOW_ZOOM_RESTRICTED,
            isChecked = userPreferences.allowZoomOnRestrictedPages,
            onCheckChange = { userPreferences.allowZoomOnRestrictedPages = it }
        )

        togglePreference(
            preference = SETTINGS_OVERVIEW_MODE,
            isChecked = userPreferences.overviewModeEnabled,
            onCheckChange = { userPreferences.overviewModeEnabled = it }
        )

        togglePreference(
            preference = SETTINGS_REDUCED_MOTION,
            isChecked = userPreferences.reducedMotionEnabled,
            onCheckChange = { userPreferences.reducedMotionEnabled = it }
        )

        togglePreference(
            preference = SETTINGS_LARGE_TARGETS,
            isChecked = userPreferences.largeAccessibilityTargetsEnabled,
            onCheckChange = { userPreferences.largeAccessibilityTargetsEnabled = it }
        )

        togglePreference(
            preference = SETTINGS_ANNOUNCEMENTS,
            isChecked = userPreferences.accessibilityAnnouncementsEnabled,
            onCheckChange = { userPreferences.accessibilityAnnouncementsEnabled = it }
        )
    }

    private fun showTextSizePicker() {
        val maxValue = 5
        MaterialAlertDialogBuilder(requireActivity()).apply {
            val customView =
                (layoutInflater.inflate(R.layout.dialog_seek_bar, null) as LinearLayout).apply {
                    val text = TextView(activity).apply {
                        setText(R.string.untitled)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.WRAP_CONTENT
                        )
                        gravity = Gravity.CENTER_HORIZONTAL
                    }
                    addView(text)
                    findViewById<SeekBar>(R.id.text_size_seekbar).apply {
                        setOnSeekBarChangeListener(TextSeekBarListener(text))
                        max = maxValue
                        progress = maxValue - userPreferences.textSize
                    }
                }
            setView(customView)
            setTitle(R.string.title_text_size)
            setPositiveButton(android.R.string.ok) { _, _ ->
                val seekBar = customView.findViewById<SeekBar>(R.id.text_size_seekbar)
                userPreferences.textSize = maxValue - seekBar.progress
            }
        }.resizeAndShow()
    }

    private fun showRenderingDialogPicker(summaryUpdater: SummaryUpdater) {
        MaterialAlertDialogBuilder(requireActivity()).apply {
            setTitle(resources.getString(R.string.rendering_mode))
            val values = RenderingMode.entries.map { Pair(it, it.toDisplayString()) }
            withSingleChoiceItems(values, userPreferences.renderingMode) {
                userPreferences.renderingMode = it
                summaryUpdater.updateSummary(it.toDisplayString())
            }
            setPositiveButton(resources.getString(R.string.action_ok), null)
        }.resizeAndShow()
    }

    private fun RenderingMode.toDisplayString(): String = getString(
        when (this) {
            RenderingMode.NORMAL -> R.string.name_normal
            RenderingMode.INVERTED -> R.string.name_inverted
            RenderingMode.GRAYSCALE -> R.string.name_grayscale
            RenderingMode.INVERTED_GRAYSCALE -> R.string.name_inverted_grayscale
            RenderingMode.INCREASE_CONTRAST -> R.string.name_increase_contrast
        }
    )

    private class TextSeekBarListener(
        private val sampleText: TextView
    ) : SeekBar.OnSeekBarChangeListener {

        override fun onProgressChanged(view: SeekBar, size: Int, user: Boolean) {
            sampleText.textSize = when (size) {
                0 -> 10.0f
                1 -> 14.0f
                2 -> 18.0f
                3 -> 22.0f
                4 -> 26.0f
                5 -> 30.0f
                else -> 18.0f
            }
        }

        override fun onStartTrackingTouch(view: SeekBar) = Unit

        override fun onStopTrackingTouch(view: SeekBar) = Unit
    }

    companion object {
        private const val SETTINGS_TEXT_SIZE = "accessibility_text_size"
        private const val SETTINGS_RENDERING_MODE = "accessibility_rendering_mode"
        private const val SETTINGS_REFLOW = "accessibility_text_reflow"
        private const val SETTINGS_VIEWPORT = "accessibility_wide_viewport"
        private const val SETTINGS_ALLOW_ZOOM_RESTRICTED = "accessibility_allow_zoom_restricted"
        private const val SETTINGS_OVERVIEW_MODE = "accessibility_overview_mode"
        private const val SETTINGS_REDUCED_MOTION = "accessibility_reduced_motion"
        private const val SETTINGS_LARGE_TARGETS = "accessibility_large_targets"
        private const val SETTINGS_ANNOUNCEMENTS = "accessibility_announcements"
    }
}
