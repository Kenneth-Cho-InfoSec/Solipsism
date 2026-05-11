/*
 * Copyright 2014 A.C.R. Development
 */
package com.krystelligence.solipsism.settings.fragment

import com.krystelligence.solipsism.AppTheme
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.browser.di.injector
import com.krystelligence.solipsism.extensions.resizeAndShow
import com.krystelligence.solipsism.extensions.withSingleChoiceItems
import com.krystelligence.solipsism.preference.UserPreferences
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import javax.inject.Inject

class DisplaySettingsFragment : AbstractSettingsFragment() {

    @Inject internal lateinit var userPreferences: UserPreferences
    private var wallpaperSummaryUpdater: SummaryUpdater? = null
    private val wallpaperPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::copyHomepageWallpaper)
    }

    override fun providePreferencesXmlResource() = R.xml.preference_display

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        injector.inject(this)

        // preferences storage
        clickableDynamicPreference(
            preference = SETTINGS_THEME,
            summary = userPreferences.useTheme.toDisplayString(),
            onClick = ::showThemePicker
        )

        clickablePreference(
            preference = SETTINGS_TEXTSIZE,
            onClick = ::showTextSizePicker
        )

        clickableDynamicPreference(
            preference = SETTINGS_HOMEPAGE_WALLPAPER,
            summary = userPreferences.homepageWallpaperMode.toWallpaperModeDisplayString(),
            onClick = ::showHomepageWallpaperPicker
        )

        clickableDynamicPreference(
            preference = SETTINGS_RAIL_SIZE,
            summary = userPreferences.solipsismRailSize.toRailSizeDisplayString(),
            onClick = ::showRailSizePicker
        )

        clickableDynamicPreference(
            preference = SETTINGS_RAIL_POSITION,
            summary = userPreferences.solipsismRailOnLeft.toRailPositionDisplayString(),
            onClick = ::showRailPositionPicker
        )

        togglePreference(
            preference = SETTINGS_HIDESTATUSBAR,
            isChecked = userPreferences.hideStatusBarEnabled,
            onCheckChange = { userPreferences.hideStatusBarEnabled = it }
        )

        togglePreference(
            preference = SETTINGS_FULLSCREEN,
            isChecked = userPreferences.fullScreenEnabled,
            onCheckChange = { userPreferences.fullScreenEnabled = it }
        )

        togglePreference(
            preference = SETTINGS_VIEWPORT,
            isChecked = userPreferences.useWideViewPortEnabled,
            onCheckChange = { userPreferences.useWideViewPortEnabled = it }
        )

        togglePreference(
            preference = SETTINGS_OVERVIEWMODE,
            isChecked = userPreferences.overviewModeEnabled,
            onCheckChange = { userPreferences.overviewModeEnabled = it }
        )

        togglePreference(
            preference = SETTINGS_REFLOW,
            isChecked = userPreferences.textReflowEnabled,
            onCheckChange = { userPreferences.textReflowEnabled = it }
        )

        togglePreference(
            preference = SETTINGS_BLACK_STATUS,
            isChecked = userPreferences.useBlackStatusBar,
            onCheckChange = { userPreferences.useBlackStatusBar = it }
        )

    }

    private fun showTextSizePicker() {
        val maxValue = 5
        MaterialAlertDialogBuilder(requireActivity()).apply {
            val layoutInflater = requireActivity().layoutInflater
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

    private fun showThemePicker(summaryUpdater: SummaryUpdater) {
        val currentTheme = userPreferences.useTheme
        MaterialAlertDialogBuilder(requireActivity()).apply {
            setTitle(resources.getString(R.string.theme))
            val values = AppTheme.entries.map { Pair(it, it.toDisplayString()) }
            withSingleChoiceItems(values, userPreferences.useTheme) {
                userPreferences.useTheme = it
                summaryUpdater.updateSummary(it.toDisplayString())
            }
            setPositiveButton(resources.getString(R.string.action_ok)) { _, _ ->
                if (currentTheme != userPreferences.useTheme) {
                    requireActivity().onBackPressed()
                }
            }
            setOnCancelListener {
                if (currentTheme != userPreferences.useTheme) {
                    requireActivity().onBackPressed()
                }
            }
        }.resizeAndShow()
    }

    private fun showRailSizePicker(summaryUpdater: SummaryUpdater) {
        MaterialAlertDialogBuilder(requireActivity()).apply {
            setTitle(R.string.settings_rail_size)
            val values = listOf(
                Pair(RAIL_SIZE_SUPER_COMPACT, getString(R.string.settings_rail_size_super_compact)),
                Pair(RAIL_SIZE_SMALL, getString(R.string.settings_rail_size_small)),
                Pair(RAIL_SIZE_MEDIUM, getString(R.string.settings_rail_size_medium)),
                Pair(RAIL_SIZE_LARGE, getString(R.string.settings_rail_size_large))
            )
            withSingleChoiceItems(values, userPreferences.solipsismRailSize.coerceToKnownRailSize()) {
                userPreferences.solipsismRailSize = it
                summaryUpdater.updateSummary(it.toRailSizeDisplayString())
            }
            setPositiveButton(resources.getString(R.string.action_ok), null)
        }.resizeAndShow()
    }

    private fun showRailPositionPicker(summaryUpdater: SummaryUpdater) {
        MaterialAlertDialogBuilder(requireActivity()).apply {
            setTitle(R.string.settings_rail_position)
            val values = listOf(
                Pair(false, getString(R.string.settings_rail_position_right)),
                Pair(true, getString(R.string.settings_rail_position_left))
            )
            withSingleChoiceItems(values, userPreferences.solipsismRailOnLeft) {
                userPreferences.solipsismRailOnLeft = it
                summaryUpdater.updateSummary(it.toRailPositionDisplayString())
            }
            setPositiveButton(resources.getString(R.string.action_ok), null)
        }.resizeAndShow()
    }

    private fun showHomepageWallpaperPicker(summaryUpdater: SummaryUpdater) {
        wallpaperSummaryUpdater = summaryUpdater
        MaterialAlertDialogBuilder(requireActivity()).apply {
            setTitle(R.string.settings_homepage_wallpaper)
            val values = listOf(
                Pair(HOMEPAGE_WALLPAPER_DEFAULT, getString(R.string.settings_homepage_wallpaper_default)),
                Pair(HOMEPAGE_WALLPAPER_CUSTOM, getString(R.string.settings_homepage_wallpaper_custom)),
                Pair(HOMEPAGE_WALLPAPER_BLACK, getString(R.string.settings_homepage_wallpaper_black))
            )
            withSingleChoiceItems(values, userPreferences.homepageWallpaperMode.coerceToKnownWallpaperMode()) {
                when (it) {
                    HOMEPAGE_WALLPAPER_CUSTOM -> wallpaperPicker.launch(arrayOf("image/*"))
                    else -> {
                        userPreferences.homepageWallpaperMode = it
                        summaryUpdater.updateSummary(it.toWallpaperModeDisplayString())
                    }
                }
            }
            setPositiveButton(resources.getString(R.string.action_ok), null)
        }.resizeAndShow()
    }

    private fun copyHomepageWallpaper(uri: Uri) {
        val targetDirectory = File(requireContext().filesDir, HOMEPAGE_WALLPAPER_DIRECTORY).apply {
            mkdirs()
        }
        val targetFile = File(targetDirectory, HOMEPAGE_WALLPAPER_FILE)
        requireContext().contentResolver.openInputStream(uri)?.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: return
        userPreferences.homepageWallpaperPath = targetFile.absolutePath
        userPreferences.homepageWallpaperMode = HOMEPAGE_WALLPAPER_CUSTOM
        wallpaperSummaryUpdater?.updateSummary(HOMEPAGE_WALLPAPER_CUSTOM.toWallpaperModeDisplayString())
    }

    private fun AppTheme.toDisplayString(): String = getString(
        when (this) {
            AppTheme.LIGHT -> R.string.light_theme
            AppTheme.DARK -> R.string.dark_theme
            AppTheme.BLACK -> R.string.black_theme
        }
    )

    private fun Int.toRailSizeDisplayString(): String = getString(
        when (coerceToKnownRailSize()) {
            RAIL_SIZE_SUPER_COMPACT -> R.string.settings_rail_size_super_compact
            RAIL_SIZE_SMALL -> R.string.settings_rail_size_small
            RAIL_SIZE_LARGE -> R.string.settings_rail_size_large
            else -> R.string.settings_rail_size_medium
        }
    )

    private fun Int.coerceToKnownRailSize(): Int = when (this) {
        RAIL_SIZE_SUPER_COMPACT, RAIL_SIZE_SMALL, RAIL_SIZE_MEDIUM, RAIL_SIZE_LARGE -> this
        else -> RAIL_SIZE_MEDIUM
    }

    private fun Boolean.toRailPositionDisplayString(): String = getString(
        if (this) R.string.settings_rail_position_left else R.string.settings_rail_position_right
    )

    private fun Int.toWallpaperModeDisplayString(): String = getString(
        when (coerceToKnownWallpaperMode()) {
            HOMEPAGE_WALLPAPER_CUSTOM -> R.string.settings_homepage_wallpaper_custom
            HOMEPAGE_WALLPAPER_BLACK -> R.string.settings_homepage_wallpaper_black
            else -> R.string.settings_homepage_wallpaper_default
        }
    )

    private fun Int.coerceToKnownWallpaperMode(): Int = when (this) {
        HOMEPAGE_WALLPAPER_DEFAULT, HOMEPAGE_WALLPAPER_CUSTOM, HOMEPAGE_WALLPAPER_BLACK -> this
        else -> HOMEPAGE_WALLPAPER_DEFAULT
    }

    private class TextSeekBarListener(
        private val sampleText: TextView
    ) : SeekBar.OnSeekBarChangeListener {

        override fun onProgressChanged(view: SeekBar, size: Int, user: Boolean) {
            this.sampleText.textSize = getTextSize(size)
        }

        override fun onStartTrackingTouch(arg0: SeekBar) {}

        override fun onStopTrackingTouch(arg0: SeekBar) {}

    }

    companion object {

        private const val SETTINGS_HIDESTATUSBAR = "fullScreenOption"
        private const val SETTINGS_FULLSCREEN = "fullscreen"
        private const val SETTINGS_VIEWPORT = "wideViewPort"
        private const val SETTINGS_OVERVIEWMODE = "overViewMode"
        private const val SETTINGS_REFLOW = "text_reflow"
        private const val SETTINGS_THEME = "app_theme"
        private const val SETTINGS_TEXTSIZE = "text_size"
        private const val SETTINGS_HOMEPAGE_WALLPAPER = "homepage_wallpaper"
        private const val SETTINGS_RAIL_SIZE = "rail_size"
        private const val SETTINGS_RAIL_POSITION = "rail_position"
        private const val SETTINGS_BLACK_STATUS = "black_status_bar"

        private const val HOMEPAGE_WALLPAPER_DEFAULT = 0
        private const val HOMEPAGE_WALLPAPER_CUSTOM = 1
        private const val HOMEPAGE_WALLPAPER_BLACK = 2
        private const val HOMEPAGE_WALLPAPER_DIRECTORY = "homepage-wallpaper"
        private const val HOMEPAGE_WALLPAPER_FILE = "custom-homepage-wallpaper"

        private const val RAIL_SIZE_SUPER_COMPACT = 30
        private const val RAIL_SIZE_SMALL = 60
        private const val RAIL_SIZE_MEDIUM = 72
        private const val RAIL_SIZE_LARGE = 88

        private const val XX_LARGE = 30.0f
        private const val X_LARGE = 26.0f
        private const val LARGE = 22.0f
        private const val MEDIUM = 18.0f
        private const val SMALL = 14.0f
        private const val X_SMALL = 10.0f

        private fun getTextSize(size: Int): Float = when (size) {
            0 -> X_SMALL
            1 -> SMALL
            2 -> MEDIUM
            3 -> LARGE
            4 -> X_LARGE
            5 -> XX_LARGE
            else -> MEDIUM
        }
    }
}
