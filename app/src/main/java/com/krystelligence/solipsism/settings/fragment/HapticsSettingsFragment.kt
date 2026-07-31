package com.krystelligence.solipsism.settings.fragment

import android.content.res.ColorStateList
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.browser.di.injector
import com.krystelligence.solipsism.extensions.resizeAndShow
import com.krystelligence.solipsism.preference.UserPreferences
import org.json.JSONObject
import javax.inject.Inject

/** Independent controls for tactile feedback produced by the browser. */
class HapticsSettingsFragment : AbstractSettingsFragment() {

    @Inject internal lateinit var userPreferences: UserPreferences

    private val vibrator: Vibrator? by lazy {
        requireContext().getSystemService(Vibrator::class.java)
    }

    private val importPresetLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(::readPreset) }

    private val exportPresetLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(::writePreset) }

    override fun providePreferencesXmlResource() = R.xml.preference_haptics

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        injector.inject(this)

        togglePreference(
            preference = GLOBAL_KEY,
            isChecked = userPreferences.hapticsEnabled,
            summary = getString(R.string.settings_haptics_summary)
        ) { enabled ->
            userPreferences.hapticsEnabled = enabled
            updateFeatureRows(enabled)
        }
        clickableDynamicPreference(
            preference = SCREENSHOT_KEY,
            summary = screenshotSummary(),
            onClick = ::showScreenshotSettings
        )
        clickableDynamicPreference(
            preference = RAIL_KEY,
            summary = railSummary(),
            onClick = ::showRailSettings
        )
        clickablePreference(PRESETS_KEY, onClick = ::showPresets)
        togglePreference(
            preference = INTERACTIONS_KEY,
            isChecked = userPreferences.interactionHapticsEnabled,
            onCheckChange = {
                userPreferences.interactionHapticsEnabled = it
                updateFeatureRows(userPreferences.hapticsEnabled)
            }
        )
        interactionSettings().forEach { setting ->
            clickableDynamicPreference(
                preference = setting.key,
                summary = interactionSummary(setting),
                onClick = { showInteractionSettings(setting, it) }
            )
        }
        updateFeatureRows(userPreferences.hapticsEnabled)
    }

    private fun updateFeatureRows(enabled: Boolean) {
        findPreference<androidx.preference.Preference>(SCREENSHOT_KEY)?.isEnabled = enabled
        findPreference<androidx.preference.Preference>(RAIL_KEY)?.isEnabled = enabled
        findPreference<androidx.preference.Preference>(INTERACTIONS_KEY)?.isEnabled = enabled
        interactionSettings().forEach {
            findPreference<androidx.preference.Preference>(it.key)?.isEnabled =
                enabled && userPreferences.interactionHapticsEnabled
        }
    }

    private fun interactionSettings() = listOf(
        InteractionSetting(TABS_KEY, R.string.settings_haptics_tabs,
            { userPreferences.tabsHapticsEnabled }, { userPreferences.tabsHapticsEnabled = it },
            { userPreferences.tabsHapticsDurationMs }, { userPreferences.tabsHapticsDurationMs = it },
            { userPreferences.tabsHapticsIntensity }, { userPreferences.tabsHapticsIntensity = it }),
        InteractionSetting(BOOKMARKS_KEY, R.string.settings_haptics_bookmarks,
            { userPreferences.bookmarksHapticsEnabled }, { userPreferences.bookmarksHapticsEnabled = it },
            { userPreferences.bookmarksHapticsDurationMs }, { userPreferences.bookmarksHapticsDurationMs = it },
            { userPreferences.bookmarksHapticsIntensity }, { userPreferences.bookmarksHapticsIntensity = it }),
        InteractionSetting(QR_KEY, R.string.settings_haptics_qr,
            { userPreferences.qrHapticsEnabled }, { userPreferences.qrHapticsEnabled = it },
            { userPreferences.qrHapticsDurationMs }, { userPreferences.qrHapticsDurationMs = it },
            { userPreferences.qrHapticsIntensity }, { userPreferences.qrHapticsIntensity = it }),
        InteractionSetting(DOWNLOADS_KEY, R.string.settings_haptics_downloads,
            { userPreferences.downloadHapticsEnabled }, { userPreferences.downloadHapticsEnabled = it },
            { userPreferences.downloadHapticsDurationMs }, { userPreferences.downloadHapticsDurationMs = it },
            { userPreferences.downloadHapticsIntensity }, { userPreferences.downloadHapticsIntensity = it }),
        InteractionSetting(ADBLOCK_KEY, R.string.settings_haptics_adblock,
            { userPreferences.adblockHapticsEnabled }, { userPreferences.adblockHapticsEnabled = it },
            { userPreferences.adblockHapticsDurationMs }, { userPreferences.adblockHapticsDurationMs = it },
            { userPreferences.adblockHapticsIntensity }, { userPreferences.adblockHapticsIntensity = it }),
        InteractionSetting(PERMISSIONS_KEY, R.string.settings_haptics_permissions,
            { userPreferences.permissionsHapticsEnabled }, { userPreferences.permissionsHapticsEnabled = it },
            { userPreferences.permissionsHapticsDurationMs }, { userPreferences.permissionsHapticsDurationMs = it },
            { userPreferences.permissionsHapticsIntensity }, { userPreferences.permissionsHapticsIntensity = it }),
        InteractionSetting(REFRESH_KEY, R.string.settings_haptics_refresh,
            { userPreferences.refreshHapticsEnabled }, { userPreferences.refreshHapticsEnabled = it },
            { userPreferences.refreshHapticsDurationMs }, { userPreferences.refreshHapticsDurationMs = it },
            { userPreferences.refreshHapticsIntensity }, { userPreferences.refreshHapticsIntensity = it })
    )

    private fun interactionSummary(setting: InteractionSetting): String = if (setting.isEnabled()) {
        getString(R.string.settings_haptics_duration_intensity, setting.duration(), setting.intensity())
    } else getString(R.string.settings_haptics_off)

    private fun showInteractionSettings(setting: InteractionSetting, summaryUpdater: SummaryUpdater) {
        val content = createDialogContent()
        val description = addDescription(content)
        val enabled = createSwitch(setting.title).apply { isChecked = setting.isEnabled() }
        content.addView(enabled)
        val durationLabel = addLabel(content)
        val durationSlider = createSlider(MIN_ACTION_DURATION_MS, MAX_ACTION_DURATION_MS, ACTION_DURATION_STEP_MS, setting.duration())
        content.addView(durationSlider)
        val intensityLabel = addLabel(content)
        val intensitySlider = createSlider(MIN_PERCENT, MAX_PERCENT, PERCENT_STEP, setting.intensity())
        content.addView(intensitySlider)
        content.addView(createButton(R.string.settings_haptics_test_interaction) {
            if (userPreferences.hapticsEnabled && userPreferences.interactionHapticsEnabled && setting.isEnabled()) {
                vibrator?.vibrate(VibrationEffect.createOneShot(setting.duration().toLong(), intensityAmplitude(setting.intensity())))
            }
        })
        fun update() {
            description.isEnabled = userPreferences.hapticsEnabled && userPreferences.interactionHapticsEnabled
            durationLabel.text = getString(R.string.settings_haptics_duration, setting.duration())
            intensityLabel.text = getString(R.string.settings_haptics_rail_intensity, setting.intensity())
            val active = enabled.isChecked && userPreferences.interactionHapticsEnabled
            durationSlider.isEnabled = active
            intensitySlider.isEnabled = active
            durationLabel.isEnabled = active
            intensityLabel.isEnabled = active
        }
        enabled.setOnCheckedChangeListener { _, checked ->
            setting.setEnabled(checked)
            summaryUpdater.updateSummary(interactionSummary(setting))
            update()
        }
        durationSlider.addOnChangeListener { _, value, fromUser -> if (fromUser) { setting.setDuration(value.toInt()); update() } }
        intensitySlider.addOnChangeListener { _, value, fromUser -> if (fromUser) { setting.setIntensity(value.toInt()); update() } }
        update()
        showDialog(setting.title, content)
    }

    private fun showScreenshotSettings(summaryUpdater: SummaryUpdater) {
        val content = createDialogContent()
        val description = addDescription(content)
        val enabled = createSwitch(R.string.settings_haptics_screenshot).apply {
            isChecked = userPreferences.screenshotHapticsEnabled
        }
        content.addView(enabled)

        val durationLabel = addLabel(content)
        val durationSlider = createSlider(
            MIN_DURATION_MS,
            MAX_DURATION_MS,
            DURATION_STEP_MS,
            userPreferences.screenshotHapticDurationMs
        )
        content.addView(durationSlider)
        val intensityLabel = addLabel(content)
        val intensitySlider = createSlider(
            MIN_PERCENT,
            MAX_PERCENT,
            PERCENT_STEP,
            userPreferences.screenshotHapticIntensity
        )
        content.addView(intensitySlider)
        content.addView(createButton(R.string.settings_haptics_test_screenshot) {
            if (userPreferences.hapticsEnabled && userPreferences.screenshotHapticsEnabled) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(
                        userPreferences.screenshotHapticDurationMs
                            .coerceIn(MIN_DURATION_MS, MAX_DURATION_MS).toLong(),
                        intensityAmplitude(userPreferences.screenshotHapticIntensity)
                    )
                )
            }
        })

        fun update() {
            description.isEnabled = userPreferences.hapticsEnabled
            durationLabel.text = getString(
                R.string.settings_haptics_duration,
                userPreferences.screenshotHapticDurationMs
            )
            intensityLabel.text = getString(
                R.string.settings_haptics_intensity,
                userPreferences.screenshotHapticIntensity
            )
            durationLabel.isEnabled = enabled.isChecked
            durationSlider.isEnabled = enabled.isChecked
            intensityLabel.isEnabled = enabled.isChecked
            intensitySlider.isEnabled = enabled.isChecked
        }
        enabled.setOnCheckedChangeListener { _, checked ->
            userPreferences.screenshotHapticsEnabled = checked
            summaryUpdater.updateSummary(screenshotSummary())
            update()
        }
        durationSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) userPreferences.screenshotHapticDurationMs = value.toInt()
            update()
        }
        intensitySlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) userPreferences.screenshotHapticIntensity = value.toInt()
            update()
        }
        update()
        showDialog(R.string.settings_haptics_screenshot, content)
    }

    private fun showRailSettings(summaryUpdater: SummaryUpdater) {
        val content = createDialogContent()
        val description = addDescription(content)
        val enabled = createSwitch(R.string.settings_haptics_rail).apply {
            isChecked = userPreferences.railHapticsEnabled
        }
        content.addView(enabled)

        val intensityLabel = addLabel(content)
        val intensitySlider = createSlider(
            MIN_PERCENT,
            MAX_PERCENT,
            PERCENT_STEP,
            userPreferences.railHapticsIntensity
        )
        content.addView(intensitySlider)

        val completion = createSwitch(R.string.settings_haptics_completion).apply {
            isChecked = userPreferences.railCompletionHapticsEnabled
        }
        content.addView(completion)

        val completionIntensityLabel = addLabel(content)
        val completionIntensitySlider = createSlider(
            MIN_PERCENT,
            MAX_PERCENT,
            PERCENT_STEP,
            userPreferences.railCompletionHapticsIntensity
        )
        content.addView(completionIntensitySlider)
        val curve = createButton(R.string.settings_haptics_curve) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_haptics_curve)
                .setSingleChoiceItems(
                    arrayOf(
                        getString(R.string.settings_haptics_curve_linear),
                        getString(R.string.settings_haptics_curve_nonlinear)
                    ), userPreferences.railHapticCurve
                ) { dialog, which ->
                    userPreferences.railHapticCurve = which
                    dialog.dismiss()
                }.show()
        }
        content.addView(curve)
        content.addView(createButton(R.string.settings_haptics_test_rail) {
            if (userPreferences.hapticsEnabled && userPreferences.railHapticsEnabled) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(
                        350L,
                        intensityAmplitude(userPreferences.railHapticsIntensity)
                    )
                )
            }
        })

        fun update() {
            description.isEnabled = userPreferences.hapticsEnabled
            intensityLabel.text = getString(
                R.string.settings_haptics_rail_intensity,
                userPreferences.railHapticsIntensity
            )
            completionIntensityLabel.text = getString(
                R.string.settings_haptics_completion_intensity,
                userPreferences.railCompletionHapticsIntensity
            )
            intensityLabel.isEnabled = enabled.isChecked
            intensitySlider.isEnabled = enabled.isChecked
            completion.isEnabled = enabled.isChecked
            completionIntensityLabel.isEnabled = enabled.isChecked && completion.isChecked
            completionIntensitySlider.isEnabled = enabled.isChecked && completion.isChecked
        }
        enabled.setOnCheckedChangeListener { _, checked ->
            userPreferences.railHapticsEnabled = checked
            summaryUpdater.updateSummary(railSummary())
            update()
        }
        completion.setOnCheckedChangeListener { _, checked ->
            userPreferences.railCompletionHapticsEnabled = checked
            update()
        }
        intensitySlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) userPreferences.railHapticsIntensity = value.toInt()
            update()
        }
        completionIntensitySlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) userPreferences.railCompletionHapticsIntensity = value.toInt()
            update()
        }
        update()
        showDialog(R.string.settings_haptics_rail, content)
    }

    private fun createDialogContent(): LinearLayout = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        val padding = (24 * resources.displayMetrics.density).toInt()
        setPadding(padding, (8 * resources.displayMetrics.density).toInt(), padding, 0)
    }

    private fun addDescription(content: LinearLayout): TextView = TextView(requireContext()).apply {
        setText(R.string.settings_haptics_summary)
        val padding = (12 * resources.displayMetrics.density).toInt()
        setPadding(0, 0, 0, padding)
        content.addView(this)
    }

    private fun addLabel(content: LinearLayout): TextView = TextView(requireContext()).apply {
        setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0)
        content.addView(this)
    }

    private fun showDialog(title: Int, content: LinearLayout) {
        val scroll = ScrollView(requireContext()).apply { addView(content) }
        MaterialAlertDialogBuilder(requireActivity())
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
            .resizeAndShow()
    }

    private fun createSwitch(label: Int): MaterialSwitch = MaterialSwitch(requireContext()).apply {
        setText(label)
        thumbTintList = AppCompatResources.getColorStateList(
            requireContext(), R.color.settings_switch_thumb_tint
        )
        trackTintList = AppCompatResources.getColorStateList(
            requireContext(), R.color.settings_switch_track_tint
        )
    }

    private fun createSlider(min: Int, max: Int, step: Int, initial: Int): Slider =
        Slider(requireContext()).apply {
            valueFrom = min.toFloat()
            valueTo = max.toFloat()
            stepSize = step.toFloat()
            value = initial.coerceIn(min, max).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

    private fun createButton(label: Int, onClick: () -> Unit): MaterialButton =
        MaterialButton(requireContext()).apply {
            setText(label)
            backgroundTintList = ColorStateList.valueOf(
                MaterialColors.getColor(this, R.attr.colorAccent)
            )
            setTextColor(MaterialColors.getColor(this, R.attr.colorOnPrimary))
            setOnClickListener { onClick() }
        }

    private fun intensityAmplitude(percent: Int): Int =
        (180 * percent.coerceIn(1, 100) / 100).coerceIn(1, 255)

    private fun screenshotSummary(): String = if (userPreferences.screenshotHapticsEnabled) {
        getString(R.string.settings_haptics_duration, userPreferences.screenshotHapticDurationMs)
    } else getString(R.string.settings_haptics_off)

    private fun railSummary(): String = if (userPreferences.railHapticsEnabled) {
        getString(R.string.settings_haptics_rail_intensity, userPreferences.railHapticsIntensity)
    } else getString(R.string.settings_haptics_off)

    private fun showPresets() {
        val presets = listOf(
            Preset(R.string.settings_haptics_preset_default, 18, 70, 28, 100, 46, 100, 46, 100),
            Preset(R.string.settings_haptics_preset_pixel, 12, 62, 22, 88, 34, 90, 34, 90),
            Preset(R.string.settings_haptics_preset_samsung, 24, 78, 36, 100, 58, 100, 58, 100),
            Preset(R.string.settings_haptics_preset_accessibility, 42, 100, 55, 100, 80, 100, 80, 100)
        )
        val labels = presets.map { getString(it.name) }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_haptics_presets)
            .setItems(labels) { dialog, which ->
                applyPreset(presets[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.settings_haptics_import_preset) { _, _ ->
                importPresetLauncher.launch(arrayOf("application/json", "text/json", "text/plain"))
            }
            .setPositiveButton(R.string.settings_haptics_export_preset) { _, _ ->
                exportPresetLauncher.launch("solipsism-haptics-preset.json")
            }
            .show()
    }

    private fun applyPreset(preset: Preset) {
        val settings = interactionSettings()
        settings[0].setDuration(preset.tabsDuration); settings[0].setIntensity(preset.tabsIntensity)
        settings[1].setDuration(preset.bookmarksDuration); settings[1].setIntensity(preset.bookmarksIntensity)
        settings[2].setDuration(preset.qrDuration); settings[2].setIntensity(preset.qrIntensity)
        settings[3].setDuration(preset.downloadDuration); settings[3].setIntensity(preset.downloadIntensity)
        settings.drop(4).forEach { it.setDuration(preset.downloadDuration); it.setIntensity(preset.downloadIntensity) }
        updateInteractionSummaries()
    }

    private fun updateInteractionSummaries() {
        interactionSettings().forEach { setting ->
            findPreference<androidx.preference.Preference>(setting.key)?.summary = interactionSummary(setting)
        }
    }

    private fun writePreset(uri: Uri) {
        runCatching {
            requireContext().contentResolver.openOutputStream(uri)?.use { output ->
                output.write(presetJson().toString(2).toByteArray(Charsets.UTF_8))
            } ?: error("Unable to open file")
        }
    }

    private fun readPreset(uri: Uri) {
        val result = runCatching {
            val text = requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Unable to open file")
            val root = JSONObject(text)
            val interactions = root.getJSONObject("interactions")
            val values = interactionSettings().map { setting ->
                val item = interactions.getJSONObject(setting.key.removePrefix("haptics_"))
                val duration = item.getInt("durationMs")
                val intensity = item.getInt("intensity")
                require(duration in MIN_ACTION_DURATION_MS..MAX_ACTION_DURATION_MS)
                require(intensity in MIN_PERCENT..MAX_PERCENT)
                duration to intensity
            }
            interactionSettings().zip(values).forEach { (setting, value) ->
                setting.setDuration(value.first); setting.setIntensity(value.second)
            }
            updateInteractionSummaries()
        }
        if (result.isFailure) {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.settings_haptics_preset_file_error)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun presetJson(): JSONObject {
        val interactions = JSONObject()
        interactionSettings().forEach { setting ->
            interactions.put(setting.key.removePrefix("haptics_"), JSONObject()
                .put("durationMs", setting.duration())
                .put("intensity", setting.intensity()))
        }
        return JSONObject().put("format", "solipsism-haptics-preset-v1").put("interactions", interactions)
    }

    companion object {
        private const val GLOBAL_KEY = "haptics_global"
        private const val SCREENSHOT_KEY = "haptics_screenshot"
        private const val RAIL_KEY = "haptics_rail"
        private const val PRESETS_KEY = "haptics_presets"
        private const val INTERACTIONS_KEY = "haptics_interactions"
        private const val TABS_KEY = "haptics_tabs"
        private const val BOOKMARKS_KEY = "haptics_bookmarks"
        private const val QR_KEY = "haptics_qr"
        private const val DOWNLOADS_KEY = "haptics_downloads"
        private const val ADBLOCK_KEY = "haptics_adblock"
        private const val PERMISSIONS_KEY = "haptics_permissions"
        private const val REFRESH_KEY = "haptics_refresh"
        private const val MIN_DURATION_MS = 50
        private const val MAX_DURATION_MS = 1000
        private const val DURATION_STEP_MS = 10
        private const val MIN_ACTION_DURATION_MS = 10
        private const val MAX_ACTION_DURATION_MS = 500
        private const val ACTION_DURATION_STEP_MS = 1
        private const val MIN_PERCENT = 0
        private const val MAX_PERCENT = 100
        private const val PERCENT_STEP = 5
    }

    private data class InteractionSetting(
        val key: String,
        val title: Int,
        val isEnabled: () -> Boolean,
        val setEnabled: (Boolean) -> Unit,
        val duration: () -> Int,
        val setDuration: (Int) -> Unit,
        val intensity: () -> Int,
        val setIntensity: (Int) -> Unit
    )

    private data class Preset(
        val name: Int,
        val tabsDuration: Int, val tabsIntensity: Int,
        val bookmarksDuration: Int, val bookmarksIntensity: Int,
        val qrDuration: Int, val qrIntensity: Int,
        val downloadDuration: Int, val downloadIntensity: Int = 100
    )
}
