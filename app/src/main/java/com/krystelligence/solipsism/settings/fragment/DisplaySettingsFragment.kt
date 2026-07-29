/*
 * Copyright 2014 A.C.R. Development
 */
package com.krystelligence.solipsism.settings.fragment

import com.krystelligence.solipsism.AppTheme
import com.krystelligence.solipsism.AccentPalette
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.browser.di.injector
import com.krystelligence.solipsism.extensions.resizeAndShow
import com.krystelligence.solipsism.extensions.withSingleChoiceItems
import com.krystelligence.solipsism.preference.UserPreferences
import com.krystelligence.solipsism.html.homepage.HomepageSource
import com.krystelligence.solipsism.html.homepage.StaticHomepageSanitizer
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.text.InputType
import android.widget.LinearLayout
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.color.MaterialColors
import java.io.File
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

class DisplaySettingsFragment : AbstractSettingsFragment() {

    @Inject internal lateinit var userPreferences: UserPreferences
    private var wallpaperSummaryUpdater: SummaryUpdater? = null
    private val wallpaperPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::copyHomepageWallpaper)
    }
    private var homepageSourceSummaryUpdater: SummaryUpdater? = null
    private val htmlHomepagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::importStaticHomepage)
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
            preference = SETTINGS_HOMEPAGE_SOURCE,
            summary = homepageSourceDisplayName(),
            onClick = ::showHomepageSourcePicker
        )

        clickableDynamicPreference(
            preference = SETTINGS_ACCENT_PALETTE,
            summary = if (userPreferences.matchSystemAccent) {
                getString(R.string.settings_match_system_accent)
            } else {
                userPreferences.accentPalette.toAccentPalette().displayName()
            },
            // Keep the picker available while system matching is enabled; selecting a
            // swatch explicitly switches back to a user-selected palette.
            isEnabled = true,
            onClick = ::showAccentPalettePicker
        )
        togglePreference(
            preference = SETTINGS_MATCH_SYSTEM_ACCENT,
            isChecked = userPreferences.matchSystemAccent,
            isEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            summary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getString(R.string.settings_match_system_accent_summary)
            } else {
                getString(R.string.settings_match_system_accent_unavailable)
            },
            onCheckChange = {
                userPreferences.matchSystemAccent = it
                requireActivity().recreate()
            }
        )

        val timeFormatPreference = clickableDynamicPreference(
            preference = SETTINGS_HOMEPAGE_TIME_FORMAT,
            summary = userPreferences.homepageTimeFormat,
            onClick = ::showTimeFormatPicker
        )
        val dateFormatPreference = clickableDynamicPreference(
            preference = SETTINGS_HOMEPAGE_DATE_FORMAT,
            summary = userPreferences.homepageDateFormat,
            onClick = ::showDateFormatPicker
        )
        val opacityPreference = clickableDynamicPreference(
            preference = SETTINGS_HOMEPAGE_DATETIME_OPACITY,
            summary = getString(R.string.settings_homepage_opacity_summary, userPreferences.homepageDateTimeOpacity),
            onClick = ::showDateTimeOpacityPicker
        )
        val dateTimeControls = listOf(timeFormatPreference, dateFormatPreference, opacityPreference)
        togglePreference(
            preference = SETTINGS_HOMEPAGE_DATETIME_ENABLED,
            isChecked = userPreferences.homepageDateTimeEnabled,
            onCheckChange = { enabled -> dateTimeControls.forEach { it.isEnabled = enabled } }
        )
        dateTimeControls.forEach { it.isEnabled = userPreferences.homepageDateTimeEnabled }

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
            preference = SETTINGS_SWAP_QR_AND_TABS,
            isChecked = userPreferences.swapQrAndTabsButtons,
            onCheckChange = { userPreferences.swapQrAndTabsButtons = it }
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
        val values = AppTheme.entries.map { Pair(it, it.toDisplayString()) }
        lateinit var themeDialog: androidx.appcompat.app.AlertDialog
        themeDialog = MaterialAlertDialogBuilder(requireActivity())
            .setTitle(resources.getString(R.string.theme))
            .setSingleChoiceItems(
                values.map { it.second }.toTypedArray(),
                values.indexOfFirst { it.first == userPreferences.useTheme }
            ) { _, which ->
                val selectedTheme = values[which].first
                if (selectedTheme != userPreferences.useTheme) {
                    userPreferences.useTheme = selectedTheme
                    summaryUpdater.updateSummary(selectedTheme.toDisplayString())
                    themeDialog.dismiss()
                    requireActivity().recreate()
                }
            }
            .setPositiveButton(resources.getString(R.string.action_ok), null)
            .create()
        themeDialog.show()
        com.krystelligence.solipsism.dialog.BrowserDialog.setDialogSize(
            requireActivity(),
            themeDialog
        )
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

    private fun showHomepageSourcePicker(summaryUpdater: SummaryUpdater) {
        homepageSourceSummaryUpdater = summaryUpdater
        val values = arrayOf(
            getString(R.string.settings_homepage_source_builtin),
            getString(R.string.settings_homepage_source_html),
            getString(R.string.settings_homepage_source_domain)
        )
        val selected = HomepageSource.fromValue(userPreferences.homepageSource).value
        MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.settings_homepage_source)
            .setSingleChoiceItems(values, selected) { dialog, which ->
                when (HomepageSource.fromValue(which)) {
                    HomepageSource.BUILT_IN -> {
                        userPreferences.homepageSource = HomepageSource.BUILT_IN.value
                        userPreferences.homepage = com.krystelligence.solipsism.constant.SCHEME_HOMEPAGE
                        summaryUpdater.updateSummary(homepageSourceDisplayName())
                        dialog.dismiss()
                    }
                    HomepageSource.STATIC_HTML -> {
                        dialog.dismiss()
                        htmlHomepagePicker.launch(arrayOf("text/html", "text/plain"))
                    }
                    HomepageSource.DOMAIN -> {
                        dialog.dismiss()
                        showHomepageDomainEditor(summaryUpdater)
                    }
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showHomepageDomainEditor(summaryUpdater: SummaryUpdater) {
        val input = EditText(requireContext()).apply {
            setText(userPreferences.homepage.takeIf { it.startsWith("http://") || it.startsWith("https://") }.orEmpty())
            hint = getString(R.string.settings_homepage_domain_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.settings_homepage_domain)
            .setMessage(R.string.settings_homepage_domain_safe_mode)
            .setView(input)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                val uri = Uri.parse(input.text.toString().trim())
                if ((uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()) {
                    userPreferences.homepage = uri.toString()
                    userPreferences.homepageSource = HomepageSource.DOMAIN.value
                    summaryUpdater.updateSummary(homepageSourceDisplayName())
                } else {
                    input.error = getString(R.string.settings_homepage_domain_invalid)
                }
            }
            .show()
    }

    private fun importStaticHomepage(uri: Uri) {
        runCatching {
            val source = requireContext().contentResolver.openInputStream(uri)?.use { input ->
                ByteArrayOutputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    var count: Int
                    while (input.read(buffer).also { count = it } != -1) {
                        total += count
                        require(total <= StaticHomepageSanitizer.MAX_HTML_BYTES) {
                            getString(R.string.settings_homepage_html_too_large)
                        }
                        output.write(buffer, 0, count)
                    }
                    output.toString(Charsets.UTF_8.name())
                }
            } ?: error("Unable to read HTML")
            val sanitized = StaticHomepageSanitizer.sanitize(source)
            val directory = File(requireContext().filesDir, "homepage").apply { mkdirs() }
            val target = File(directory, "static-homepage.html")
            target.writeText(sanitized, Charsets.UTF_8)
            userPreferences.homepageHtmlPath = target.absolutePath
            userPreferences.homepageSource = HomepageSource.STATIC_HTML.value
            homepageSourceSummaryUpdater?.updateSummary(homepageSourceDisplayName())
        }.onFailure {
            android.widget.Toast.makeText(
                requireContext(),
                it.message ?: getString(R.string.settings_homepage_html_invalid),
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun homepageSourceDisplayName(): String = when (HomepageSource.fromValue(userPreferences.homepageSource)) {
        HomepageSource.BUILT_IN -> getString(R.string.settings_homepage_source_builtin)
        HomepageSource.STATIC_HTML -> getString(R.string.settings_homepage_source_html)
        HomepageSource.DOMAIN -> getString(R.string.settings_homepage_source_domain)
    }

    private fun showAccentPalettePicker(summaryUpdater: SummaryUpdater) {
        val grid = GridLayout(requireContext()).apply {
            columnCount = 4
            rowCount = 2
            setPadding(12.dp, 8.dp, 12.dp, 8.dp)
        }
        val selectedPalette = userPreferences.accentPalette.toAccentPalette()
        AccentPalette.entries.forEach { palette ->
            val cell = FrameLayout(requireContext()).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = 72.dp
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }
                contentDescription = getString(
                    R.string.settings_accent_palette_name,
                    palette.displayName()
                )
                isClickable = true
                isFocusable = true
            }
            val swatch = android.view.View(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(48.dp, 48.dp, android.view.Gravity.CENTER)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(palette.previewColor)
                    setStroke(
                        if (palette == selectedPalette) 4.dp else 1.dp,
                        if (palette == selectedPalette) {
                            MaterialColors.getColor(cell, com.google.android.material.R.attr.colorOnSurface)
                        } else {
                            Color.TRANSPARENT
                        }
                    )
                }
            }
            cell.addView(swatch)
            cell.setOnClickListener {
                userPreferences.accentPalette = palette.ordinal
                userPreferences.matchSystemAccent = false
                summaryUpdater.updateSummary(palette.displayName())
                requireActivity().recreate()
            }
            grid.addView(cell)
        }
        MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.settings_accent_palette)
            .setView(grid)
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showTimeFormatPicker(summaryUpdater: SummaryUpdater) {
        showFormatEditor(
            title = getString(R.string.settings_homepage_format_title, getString(R.string.settings_homepage_time_format)),
            initialValue = userPreferences.homepageTimeFormat,
            summaryUpdater = summaryUpdater,
            fallback = "HH:mm",
            examples = listOf("HH:mm", "hh:mm a", "HH:mm:ss")
        ) { userPreferences.homepageTimeFormat = it }
    }

    private fun showDateFormatPicker(summaryUpdater: SummaryUpdater) {
        showFormatEditor(
            title = getString(R.string.settings_homepage_format_title, getString(R.string.settings_homepage_date_format)),
            initialValue = userPreferences.homepageDateFormat,
            summaryUpdater = summaryUpdater,
            fallback = "EEEE, d MMMM yyyy",
            examples = listOf("d MMM yyyy", "EEEE, d MMMM yyyy", "yyyy-MM-dd")
        ) { userPreferences.homepageDateFormat = it }
    }

    private fun showFormatEditor(
        title: String,
        initialValue: String,
        summaryUpdater: SummaryUpdater,
        fallback: String,
        examples: List<String>,
        onSave: (String) -> Unit
    ) {
        val input = EditText(requireContext()).apply {
            setText(initialValue)
            selectAll()
            hint = getString(R.string.settings_homepage_format_hint)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp, 0, 24.dp, 0)
            addView(input)
            addView(TextView(requireContext()).apply {
                text = examples.joinToString("  •  ")
                alpha = 0.7f
                setPadding(0, 8.dp, 0, 0)
            })
        }
        MaterialAlertDialogBuilder(requireActivity())
            .setTitle(title)
            .setView(container)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                val value = input.text.toString().trim().ifBlank { fallback }
                if (isValidDateFormat(value)) {
                    onSave(value)
                    summaryUpdater.updateSummary(value)
                }
            }
            .show()
    }

    private fun showDateTimeOpacityPicker(summaryUpdater: SummaryUpdater) {
        val valueText = TextView(requireContext()).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            textSize = 18f
        }
        val seekBar = SeekBar(requireContext()).apply {
            max = 100
            progress = userPreferences.homepageDateTimeOpacity.coerceIn(0, 100)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    valueText.text = getString(R.string.settings_homepage_opacity_summary, progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp, 0, 24.dp, 0)
            addView(valueText)
            addView(seekBar)
        }
        valueText.text = getString(R.string.settings_homepage_opacity_summary, seekBar.progress)
        MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.settings_homepage_opacity_title)
            .setView(container)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                userPreferences.homepageDateTimeOpacity = seekBar.progress
                summaryUpdater.updateSummary(
                    getString(R.string.settings_homepage_opacity_summary, seekBar.progress)
                )
            }
            .show()
    }

    private fun isValidDateFormat(pattern: String): Boolean = runCatching {
        SimpleDateFormat(pattern, Locale.getDefault())
    }.isSuccess

    private fun Int.toAccentPalette(): AccentPalette = AccentPalette.fromValue(this)

    private fun AccentPalette.displayName(): String = getString(
        when (this) {
            AccentPalette.TEAL -> R.string.settings_accent_teal
            AccentPalette.BLUE -> R.string.settings_accent_blue
            AccentPalette.INDIGO -> R.string.settings_accent_indigo
            AccentPalette.PURPLE -> R.string.settings_accent_purple
            AccentPalette.PINK -> R.string.settings_accent_pink
            AccentPalette.RED -> R.string.settings_accent_red
            AccentPalette.ORANGE -> R.string.settings_accent_orange
            AccentPalette.GREEN -> R.string.settings_accent_green
        }
    )

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private fun copyHomepageWallpaper(uri: Uri) {
        val targetDirectory = File(requireContext().filesDir, HOMEPAGE_WALLPAPER_DIRECTORY).apply {
            mkdirs()
        }
        val targetFile = File(targetDirectory, HOMEPAGE_WALLPAPER_FILE)
        requireContext().contentResolver.openInputStream(uri)?.use { input ->
            targetFile.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0
                var count: Int
                while (input.read(buffer).also { count = it } != -1) {
                    total += count
                    require(total <= StaticHomepageSanitizer.MAX_IMAGE_BYTES) {
                        "Homepage image exceeds ${StaticHomepageSanitizer.MAX_IMAGE_BYTES / (1024 * 1024)} MB"
                    }
                    output.write(buffer, 0, count)
                }
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
        private const val SETTINGS_HOMEPAGE_SOURCE = "homepage_source"
        private const val SETTINGS_ACCENT_PALETTE = "accent_palette"
        private const val SETTINGS_MATCH_SYSTEM_ACCENT = "match_system_accent"
        private const val SETTINGS_HOMEPAGE_DATETIME_ENABLED = "homepage_datetime_enabled"
        private const val SETTINGS_HOMEPAGE_TIME_FORMAT = "homepage_time_format"
        private const val SETTINGS_HOMEPAGE_DATE_FORMAT = "homepage_date_format"
        private const val SETTINGS_HOMEPAGE_DATETIME_OPACITY = "homepage_datetime_opacity"
        private const val SETTINGS_RAIL_SIZE = "rail_size"
        private const val SETTINGS_RAIL_POSITION = "rail_position"
        private const val SETTINGS_SWAP_QR_AND_TABS = "swap_qr_and_tabs_buttons"
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
