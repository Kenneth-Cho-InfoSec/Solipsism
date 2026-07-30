package com.krystelligence.solipsism.settings.fragment

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.audio.AudioPreset
import com.krystelligence.solipsism.audio.ChannelTestPlayer
import com.krystelligence.solipsism.browser.di.injector
import com.krystelligence.solipsism.extensions.resizeAndShow
import com.krystelligence.solipsism.preference.UserPreferences
import javax.inject.Inject

/** Top-level audio controls, kept independent from the Graphics settings page. */
class AudioSettingsFragment : AbstractSettingsFragment() {

    @Inject internal lateinit var userPreferences: UserPreferences

    override fun providePreferencesXmlResource() = R.xml.preference_audio

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        injector.inject(this)
        clickableDynamicPreference(
            preference = AUDIO_CONTROLS,
            summary = audioSettingsSummary(),
            onClick = ::showAudioSettings
        )
    }

    private fun showAudioSettings(summaryUpdater: SummaryUpdater) {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        content.addView(TextView(requireContext()).apply {
            setText(R.string.settings_audio_compatibility)
            setPadding(0, 0, 0, dp(12))
        })
        val effects = SwitchMaterial(requireContext()).apply {
            setText(R.string.settings_audio_effects)
            isChecked = userPreferences.audioEffectsEnabled
        }
        content.addView(effects)
        content.addView(TextView(requireContext()).apply {
            setText(R.string.settings_audio_channel_test)
            setPadding(0, dp(16), 0, dp(12))
            isClickable = true
            setOnClickListener { ChannelTestPlayer.play() }
        })
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

        val eqSliders = mutableListOf<SeekBar>()
        fun addDbSlider(
            label: String,
            value: Int,
            onChanged: (Int) -> Unit,
            includeInEq: Boolean = true
        ): SeekBar {
            content.addView(TextView(requireContext()).apply {
                text = label
                setPadding(0, dp(8), 0, 0)
            })
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
                if (includeInEq) eqSliders += this
            }
        }
        addDbSlider("60 Hz", userPreferences.audioEq60, onChanged = { userPreferences.audioEq60 = it })
        addDbSlider("250 Hz", userPreferences.audioEq250, onChanged = { userPreferences.audioEq250 = it })
        addDbSlider("1 kHz", userPreferences.audioEq1000, onChanged = { userPreferences.audioEq1000 = it })
        addDbSlider("4 kHz", userPreferences.audioEq4000, onChanged = { userPreferences.audioEq4000 = it })
        addDbSlider("12 kHz", userPreferences.audioEq12000, onChanged = { userPreferences.audioEq12000 = it })
        addDbSlider(
            getString(R.string.settings_audio_preamp),
            userPreferences.audioPreampDb,
            onChanged = { userPreferences.audioPreampDb = it },
            includeInEq = false
        )
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
        val balance = addDbSlider(
            getString(R.string.settings_audio_balance),
            userPreferences.audioBalance,
            onChanged = { userPreferences.audioBalance = it },
            includeInEq = false
        ).apply {
            max = 200
            progress = (userPreferences.audioBalance + 100).coerceIn(0, 200)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    userPreferences.audioBalance = progress - 100
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
        }

        fun updateEnabled() {
            val enabled = effects.isChecked
            preset.isEnabled = enabled
            customEq.isEnabled = enabled
            eqSliders.forEach { it.isEnabled = enabled && customEq.isChecked }
            balance.isEnabled = enabled
            limiter.isEnabled = enabled
            mono.isEnabled = enabled
        }
        fun updatePresetLabel() {
            preset.text = getString(R.string.settings_audio_preset) + ": " + presetName(userPreferences.audioPreset)
            preset.setOnClickListener {
                val presets = AudioPreset.entries
                MaterialAlertDialogBuilder(requireActivity())
                    .setTitle(R.string.settings_audio_preset)
                    .setSingleChoiceItems(presets.map(::presetName).toTypedArray(), presets.indexOf(userPreferences.audioPreset)) { dialog, which ->
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
        getString(R.string.settings_audio_effects) + ": " + presetName(userPreferences.audioPreset)
    } else {
        getString(R.string.settings_audio_effects) + ": Off"
    }

    private fun presetName(preset: AudioPreset): String = when (preset) {
        AudioPreset.FLAT -> getString(R.string.audio_preset_flat)
        AudioPreset.BASS_BOOST -> getString(R.string.audio_preset_bass_boost)
        AudioPreset.VOCAL_BOOST -> getString(R.string.audio_preset_vocal_boost)
        AudioPreset.TREBLE_BOOST -> getString(R.string.audio_preset_treble_boost)
        AudioPreset.ROCK -> getString(R.string.audio_preset_rock)
        AudioPreset.CLASSICAL -> getString(R.string.audio_preset_classical)
        AudioPreset.PODCAST -> getString(R.string.audio_preset_podcast)
        AudioPreset.NIGHT -> getString(R.string.audio_preset_night)
    }

    companion object {
        private const val AUDIO_CONTROLS = "audio_controls"
    }
}
