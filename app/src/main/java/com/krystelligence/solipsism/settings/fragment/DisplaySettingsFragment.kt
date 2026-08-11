/*
 * Copyright 2014 A.C.R. Development
 */
package com.krystelligence.solipsism.settings.fragment

import com.krystelligence.solipsism.AppTheme
import com.krystelligence.solipsism.AccentPalette
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.browser.di.injector
import com.krystelligence.solipsism.extensions.resizeAndShow
import com.krystelligence.solipsism.extensions.setViewWithDialogMargins
import com.krystelligence.solipsism.extensions.withSingleChoiceItems
import com.krystelligence.solipsism.utils.CustomFontManager
import com.krystelligence.solipsism.preference.UserPreferences
import com.krystelligence.solipsism.audio.AudioPreset
import com.krystelligence.solipsism.browser.ui.RailUtilityAction
import com.krystelligence.solipsism.browser.ui.RailMenuStudioActivity
import com.krystelligence.solipsism.html.homepage.HomepageSource
import com.krystelligence.solipsism.html.homepage.StaticHomepageSanitizer
import android.net.Uri
import android.os.Bundle
import android.content.Intent
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
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.color.MaterialColors
import com.google.android.material.switchmaterial.SwitchMaterial
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
    private val customFontPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::importCustomFont)
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
            preference = SETTINGS_CUSTOM_FONT,
            summary = customFontSummary(),
            onClick = { customFontPicker.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream")) }
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

        clickablePreference(
            preference = SETTINGS_HOMEPAGE_LAYOUT,
            onClick = ::showHomepageLayoutEditor
        )
        clickablePreference(
            preference = SETTINGS_HOMEPAGE_EDITOR,
            onClick = ::showHomepageCodeEditor
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
            preference = SETTINGS_RAIL_UTILITY_ACTION,
            summary = getString(userPreferences.railUtilityAction.labelRes),
            onClick = ::showRailUtilityActionPicker
        )

        clickablePreference(SETTINGS_RAIL_MENU_STUDIO) {
            startActivity(Intent(requireContext(), RailMenuStudioActivity::class.java))
        }

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
            preference = SETTINGS_HIDE_RAIL_IN_FULLSCREEN,
            isChecked = userPreferences.hideRailInFullscreen,
            onCheckChange = { userPreferences.hideRailInFullscreen = it }
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

    private fun customFontSummary(): String = userPreferences.customFontPath
        ?.let(::File)
        ?.takeIf(File::isFile)
        ?.name
        ?: getString(R.string.settings_custom_font_default)

    private fun importCustomFont(uri: Uri) {
        val context = requireContext()
        val target = File(context.filesDir, "custom-font.ttf")
        var temporary: File? = null
        try {
            temporary = File.createTempFile("custom-font-", ".tmp", context.cacheDir)
            var copied = 0L
            context.contentResolver.openInputStream(uri)?.use { input ->
                temporary!!.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_FONT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        if (copied > MAX_CUSTOM_FONT_BYTES) error("Font file is too large")
                        output.write(buffer, 0, count)
                    }
                }
            } ?: error("Unable to read font file")

            check(CustomFontManager.load(temporary!!.absolutePath) != null) { "Unsupported font file" }
            temporary!!.copyTo(target, overwrite = true)
            temporary!!.delete()
            userPreferences.customFontPath = target.absolutePath
            findPreference<androidx.preference.Preference>(SETTINGS_CUSTOM_FONT)?.summary = customFontSummary()
            CustomFontManager.applyToViewTree(requireActivity().window.decorView, userPreferences.customFontPath)
        } catch (_: Exception) {
            temporary?.delete()
            Toast.makeText(context, R.string.settings_custom_font_invalid, Toast.LENGTH_LONG).show()
        }
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

    private fun showAudioSettings(summaryUpdater: SummaryUpdater) {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val compatibility = TextView(requireContext()).apply {
            setText(R.string.settings_audio_compatibility)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(0, 0, 0, dp(12))
        }
        content.addView(compatibility)

        val effects = SwitchMaterial(requireContext()).apply {
            setText(R.string.settings_audio_effects)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            isChecked = userPreferences.audioEffectsEnabled
        }
        content.addView(effects)

        val preset = TextView(requireContext()).apply {
            setPadding(0, dp(16), 0, dp(12))
            textSize = 16f
        }
        content.addView(preset)

        val customEq = SwitchMaterial(requireContext()).apply {
            setText(R.string.settings_audio_custom_eq)
            isChecked = userPreferences.audioCustomEqEnabled
        }
        content.addView(customEq)

        val sliders = mutableListOf<SeekBar>()
        fun addSlider(label: String, value: Int, onChanged: (Int) -> Unit): SeekBar {
            val labelView = TextView(requireContext()).apply {
                text = label
                setPadding(0, dp(8), 0, 0)
            }
            content.addView(labelView)
            return SeekBar(requireContext()).apply {
                max = 24
                progress = (value + 12).coerceIn(0, 24)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        onChanged(progress - 12)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
                })
                content.addView(this)
                sliders += this
            }
        }
        addSlider("60 Hz", userPreferences.audioEq60) { userPreferences.audioEq60 = it }
        addSlider("250 Hz", userPreferences.audioEq250) { userPreferences.audioEq250 = it }
        addSlider("1 kHz", userPreferences.audioEq1000) { userPreferences.audioEq1000 = it }
        addSlider("4 kHz", userPreferences.audioEq4000) { userPreferences.audioEq4000 = it }
        addSlider("12 kHz", userPreferences.audioEq12000) { userPreferences.audioEq12000 = it }
        addSlider(getString(R.string.settings_audio_preamp), userPreferences.audioPreampDb) {
            userPreferences.audioPreampDb = it
        }

        val limiter = SwitchMaterial(requireContext()).apply {
            setText(R.string.settings_audio_limiter)
            isChecked = userPreferences.audioLimiterEnabled
        }
        content.addView(limiter)
        val mono = SwitchMaterial(requireContext()).apply {
            setText(R.string.settings_audio_mono)
            isChecked = userPreferences.audioMonoEnabled
        }
        content.addView(mono)
        val balanceSlider = addSlider(getString(R.string.settings_audio_balance), userPreferences.audioBalance) {
            userPreferences.audioBalance = it
        }.apply { max = 200; progress = (userPreferences.audioBalance + 100).coerceIn(0, 200) }
        balanceSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                userPreferences.audioBalance = progress - 100
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        fun updateEnabled() {
            val enabled = effects.isChecked
            preset.isEnabled = enabled
            customEq.isEnabled = enabled
            sliders.forEach { it.isEnabled = enabled && customEq.isChecked }
            balanceSlider.isEnabled = enabled
            limiter.isEnabled = enabled
            mono.isEnabled = enabled
        }
        fun updatePresetLabel() {
            preset.text = getString(R.string.settings_audio_preset) + ": " + audioPresetName(userPreferences.audioPreset)
            preset.setOnClickListener {
                val presets = AudioPreset.entries
                MaterialAlertDialogBuilder(requireActivity())
                    .setTitle(R.string.settings_audio_preset)
                    .setSingleChoiceItems(presets.map(::audioPresetName).toTypedArray(), presets.indexOf(userPreferences.audioPreset)) { dialog, which ->
                        userPreferences.audioPreset = presets[which]
                        updatePresetLabel()
                        dialog.dismiss()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
        effects.setOnCheckedChangeListener { _, checked ->
            userPreferences.audioEffectsEnabled = checked
            updateEnabled()
            summaryUpdater.updateSummary(audioSettingsSummary())
        }
        customEq.setOnCheckedChangeListener { _, checked ->
            userPreferences.audioCustomEqEnabled = checked
            updateEnabled()
        }
        limiter.setOnCheckedChangeListener { _, checked -> userPreferences.audioLimiterEnabled = checked }
        mono.setOnCheckedChangeListener { _, checked -> userPreferences.audioMonoEnabled = checked }
        updatePresetLabel()
        updateEnabled()
        val scroll = ScrollView(requireContext()).apply { addView(content) }
        MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.settings_audio)
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
            .resizeAndShow()
    }

    private fun audioSettingsSummary(): String = if (userPreferences.audioEffectsEnabled) {
        getString(R.string.settings_audio_effects) + ": " + audioPresetName(userPreferences.audioPreset)
    } else {
        getString(R.string.settings_audio_effects) + ": Off"
    }

    private fun audioPresetName(preset: AudioPreset): String = when (preset) {
        AudioPreset.FLAT -> getString(R.string.audio_preset_flat)
        AudioPreset.BASS_BOOST -> getString(R.string.audio_preset_bass_boost)
        AudioPreset.VOCAL_BOOST -> getString(R.string.audio_preset_vocal_boost)
        AudioPreset.TREBLE_BOOST -> getString(R.string.audio_preset_treble_boost)
        AudioPreset.ROCK -> getString(R.string.audio_preset_rock)
        AudioPreset.CLASSICAL -> getString(R.string.audio_preset_classical)
        AudioPreset.PODCAST -> getString(R.string.audio_preset_podcast)
        AudioPreset.NIGHT -> getString(R.string.audio_preset_night)
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

    private fun showRailUtilityActionPicker(summaryUpdater: SummaryUpdater) {
        val actions = RailUtilityAction.values()
        MaterialAlertDialogBuilder(requireActivity()).apply {
            setTitle(R.string.settings_rail_utility_action)
            setSingleChoiceItems(
                actions.map { getString(it.labelRes) }.toTypedArray(),
                actions.indexOf(userPreferences.railUtilityAction)
            ) { dialog, which ->
                val selected = actions[which]
                userPreferences.railUtilityAction = selected
                summaryUpdater.updateSummary(getString(selected.labelRes))
                dialog.dismiss()
            }
            setPositiveButton(R.string.action_ok, null)
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
            .setViewWithDialogMargins(input)
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

    private fun showHomepageLayoutEditor() {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp, 0, 24.dp, 0)
        }
        val mottoEnabled = SwitchMaterial(requireContext()).apply {
            text = getString(R.string.settings_homepage_motto)
            isChecked = userPreferences.homepageMottoEnabled
        }
        val motto = EditText(requireContext()).apply {
            hint = getString(R.string.settings_homepage_motto_hint)
            setText(userPreferences.homepageMotto)
            setSingleLine(true)
        }
        val bookmarksEnabled = SwitchMaterial(requireContext()).apply {
            text = getString(R.string.settings_homepage_bookmarks)
            isChecked = userPreferences.homepageBookmarksEnabled
        }
        container.addView(mottoEnabled)
        container.addView(motto)
        container.addView(bookmarksEnabled)
        val mottoSize = addHomepageSlider(
            container,
            R.string.settings_homepage_motto_size,
            10,
            32,
            userPreferences.homepageMottoSize.coerceIn(10, 32)
        ) { getString(R.string.settings_homepage_size_summary, it) }
        val mottoOpacity = addHomepageSlider(
            container,
            R.string.settings_homepage_motto_opacity,
            0,
            100,
            userPreferences.homepageMottoOpacity.coerceIn(0, 100)
        ) { getString(R.string.settings_homepage_opacity_summary, it) }
        val columns = addHomepageSlider(
            container,
            R.string.settings_homepage_bookmark_columns,
            1,
            4,
            userPreferences.homepageBookmarkColumns.coerceIn(1, 4)
        ) { getString(R.string.settings_homepage_columns_summary, it) }
        val wallpaperOpacity = addHomepageSlider(
            container,
            R.string.settings_homepage_wallpaper_opacity,
            0,
            100,
            userPreferences.homepageWallpaperOpacity.coerceIn(0, 100)
        ) { getString(R.string.settings_homepage_opacity_summary, it) }
        val wallpaperX = addHomepageSlider(
            container,
            R.string.settings_homepage_wallpaper_position_x,
            0,
            100,
            userPreferences.homepageWallpaperPositionX.coerceIn(0, 100)
        ) { getString(R.string.settings_homepage_percent_summary, it) }
        val wallpaperY = addHomepageSlider(
            container,
            R.string.settings_homepage_wallpaper_position_y,
            0,
            100,
            userPreferences.homepageWallpaperPositionY.coerceIn(0, 100)
        ) { getString(R.string.settings_homepage_percent_summary, it) }
        motto.isEnabled = mottoEnabled.isChecked
        mottoEnabled.setOnCheckedChangeListener { _, checked -> motto.isEnabled = checked }
        val scroll = ScrollView(requireContext()).apply { addView(container) }
        MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.settings_homepage_layout)
            .setView(scroll)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                userPreferences.homepageMottoEnabled = mottoEnabled.isChecked
                userPreferences.homepageMotto = motto.text.toString().trim()
                userPreferences.homepageBookmarksEnabled = bookmarksEnabled.isChecked
                // SeekBar progress is offset by the slider's minimum value (ten sp).
                userPreferences.homepageMottoSize = mottoSize.progress + 10
                userPreferences.homepageMottoOpacity = mottoOpacity.progress
                // SeekBar progress is offset by the slider's minimum value (one column).
                userPreferences.homepageBookmarkColumns = columns.progress + 1
                userPreferences.homepageWallpaperOpacity = wallpaperOpacity.progress
                userPreferences.homepageWallpaperPositionX = wallpaperX.progress
                userPreferences.homepageWallpaperPositionY = wallpaperY.progress
            }
            .show()
    }

    private fun addHomepageSlider(
        container: LinearLayout,
        label: Int,
        min: Int,
        max: Int,
        value: Int,
        summary: (Int) -> String
    ): SeekBar {
        val labelView = TextView(requireContext()).apply {
            setPadding(0, 12.dp, 0, 0)
            text = "${getString(label)}: ${summary(value)}"
        }
        val seekBar = SeekBar(requireContext()).apply {
            this.max = max - min
            progress = value - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    labelView.text = "${getString(label)}: ${summary(progress + min)}"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        container.addView(labelView)
        container.addView(seekBar)
        return seekBar
    }

    private fun showHomepageCodeEditor() {
        val existing = userPreferences.homepageHtmlPath?.let(::File)?.takeIf(File::isFile)?.readText()
        val htmlInput = EditText(requireContext()).apply {
            hint = getString(R.string.settings_homepage_html_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minHeight = 220.dp
            setText(existing?.let(::extractHomepageBody).orEmpty())
            gravity = Gravity.TOP or Gravity.START
        }
        val cssInput = EditText(requireContext()).apply {
            hint = getString(R.string.settings_homepage_css_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minHeight = 220.dp
            setText(existing?.let(::extractHomepageCss).orEmpty())
            gravity = Gravity.TOP or Gravity.START
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp, 0, 24.dp, 0)
            addView(TextView(requireContext()).apply { text = getString(R.string.settings_homepage_html_label) })
            addView(htmlInput)
            addView(TextView(requireContext()).apply {
                text = getString(R.string.settings_homepage_css_label)
                setPadding(0, 12.dp, 0, 0)
            })
            addView(cssInput)
            addView(TextView(requireContext()).apply {
                text = getString(R.string.settings_homepage_editor_help)
                alpha = 0.75f
                setPadding(0, 12.dp, 0, 0)
            })
        }
        MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.settings_homepage_editor)
            .setView(ScrollView(requireContext()).apply { addView(container) })
            .setNeutralButton(R.string.settings_homepage_source_builtin) { _, _ ->
                userPreferences.homepageSource = HomepageSource.BUILT_IN.value
                userPreferences.homepage = com.krystelligence.solipsism.constant.SCHEME_HOMEPAGE
            }
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                saveInlineHomepage(htmlInput.text.toString(), cssInput.text.toString())
            }
            .show()
    }

    private fun saveInlineHomepage(html: String, css: String) {
        runCatching {
            val source = "<html><head><style>$css</style></head><body>$html</body></html>"
            val sanitized = StaticHomepageSanitizer.sanitize(source)
            val directory = File(requireContext().filesDir, "homepage").apply { mkdirs() }
            val target = File(directory, "inline-homepage.html")
            target.writeText(sanitized, Charsets.UTF_8)
            userPreferences.homepageHtmlPath = target.absolutePath
            userPreferences.homepageSource = HomepageSource.STATIC_HTML.value
        }.onFailure {
            android.widget.Toast.makeText(
                requireContext(),
                it.message ?: getString(R.string.settings_homepage_html_invalid),
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun extractHomepageBody(source: String): String =
        runCatching { org.jsoup.Jsoup.parse(source).body().html() }.getOrDefault(source)

    private fun extractHomepageCss(source: String): String =
        runCatching { org.jsoup.Jsoup.parse(source).head().getElementsByTag("style").first()?.data().orEmpty() }
            .getOrDefault("")

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
        private const val SETTINGS_HIDE_RAIL_IN_FULLSCREEN = "hide_rail_in_fullscreen"
        private const val SETTINGS_VIEWPORT = "wideViewPort"
        private const val SETTINGS_OVERVIEWMODE = "overViewMode"
        private const val SETTINGS_REFLOW = "text_reflow"
        private const val SETTINGS_THEME = "app_theme"
        private const val SETTINGS_AUDIO = "audio_settings"
        private const val SETTINGS_TEXTSIZE = "text_size"
        private const val SETTINGS_CUSTOM_FONT = "custom_font"
        private const val MAX_CUSTOM_FONT_BYTES = 20L * 1024L * 1024L
        private const val DEFAULT_FONT_BUFFER_SIZE = 8 * 1024
        private const val SETTINGS_HOMEPAGE_WALLPAPER = "homepage_wallpaper"
        private const val SETTINGS_HOMEPAGE_SOURCE = "homepage_source"
        private const val SETTINGS_HOMEPAGE_LAYOUT = "homepage_layout"
        private const val SETTINGS_HOMEPAGE_EDITOR = "homepage_editor"
        private const val SETTINGS_ACCENT_PALETTE = "accent_palette"
        private const val SETTINGS_MATCH_SYSTEM_ACCENT = "match_system_accent"
        private const val SETTINGS_HOMEPAGE_DATETIME_ENABLED = "homepage_datetime_enabled"
        private const val SETTINGS_HOMEPAGE_TIME_FORMAT = "homepage_time_format"
        private const val SETTINGS_HOMEPAGE_DATE_FORMAT = "homepage_date_format"
        private const val SETTINGS_HOMEPAGE_DATETIME_OPACITY = "homepage_datetime_opacity"
        private const val SETTINGS_RAIL_SIZE = "rail_size"
        private const val SETTINGS_RAIL_UTILITY_ACTION = "rail_utility_action"
        private const val SETTINGS_RAIL_MENU_STUDIO = "rail_menu_studio"
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
