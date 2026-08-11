package com.krystelligence.solipsism.settings.fragment

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import android.os.Bundle
import android.text.InputType
import android.text.format.DateUtils
import android.util.Log
import android.widget.EditText
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.krystelligence.solipsism.extensions.setViewWithDialogMargins
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.browser.di.MainScheduler
import com.krystelligence.solipsism.browser.di.NetworkScheduler
import com.krystelligence.solipsism.browser.di.injector
import com.krystelligence.solipsism.malware.LocalMalwareDatabase
import com.krystelligence.solipsism.malware.MalwareDefinitionsInfo
import com.krystelligence.solipsism.preference.UserPreferences
import com.krystelligence.solipsism.virustotal.VirusTotalCancellationSignal
import com.krystelligence.solipsism.virustotal.VirusTotalApiKeyStore
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.subscribeBy
import javax.inject.Inject

class VirusTotalSettingsFragment : AbstractSettingsFragment() {

    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var apiKeyStore: VirusTotalApiKeyStore
    @Inject lateinit var localDatabase: LocalMalwareDatabase
    @Inject @NetworkScheduler lateinit var networkScheduler: Scheduler
    @Inject @MainScheduler lateinit var mainScheduler: Scheduler
    private val disposables = CompositeDisposable()

    override fun providePreferencesXmlResource() = R.xml.preference_virus_total

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        injector.inject(this)

        togglePreference(
            preference = ENABLED,
            isChecked = userPreferences.virusTotalScanningEnabled,
            onCheckChange = { userPreferences.virusTotalScanningEnabled = it }
        )
        clickablePreference(
            preference = DISCLAIMER,
            onClick = ::showDisclaimer
        )
        togglePreference(
            preference = SCAN_IMAGES,
            isChecked = userPreferences.virusTotalScanImages,
            onCheckChange = { userPreferences.virusTotalScanImages = it }
        )
        togglePreference(
            preference = SCAN_VIDEOS,
            isChecked = userPreferences.virusTotalScanVideos,
            onCheckChange = { userPreferences.virusTotalScanVideos = it }
        )
        togglePreference(
            preference = AUTO_UPDATE,
            isChecked = userPreferences.malwareDefinitionsAutoUpdate,
            onCheckChange = { userPreferences.malwareDefinitionsAutoUpdate = it }
        )
        togglePreference(
            preference = CLOUD_ENABLED,
            isChecked = userPreferences.virusTotalCloudEnabled,
            onCheckChange = {
                userPreferences.virusTotalCloudEnabled = it
                updateCloudControls(it)
            }
        )
        clickablePreference(
            preference = DEFINITIONS,
            onClick = ::updateDefinitions
        )
        clickablePreference(DEFINITIONS_SOURCE) {
            startActivity(Intent(Intent.ACTION_VIEW, DEFINITIONS_SOURCE_URL.toUri()))
        }
        clickablePreference(
            preference = API_KEY,
            summary = keySummary(),
            onClick = ::showApiKeyDialog
        )
        clickablePreference(PRIVACY) {
            startActivity(Intent(Intent.ACTION_VIEW, VIRUS_TOTAL_PRIVACY_URL.toUri()))
        }
        updateCloudControls(userPreferences.virusTotalCloudEnabled)
        refreshDefinitionsSummary()
    }

    override fun onDestroy() {
        disposables.clear()
        super.onDestroy()
    }

    private fun updateDefinitions() {
        val preference = findPreference<Preference>(DEFINITIONS) ?: return
        preference.isEnabled = false
        preference.setSummary(R.string.malware_definitions_updating)
        val cancellation = VirusTotalCancellationSignal()
        disposables.add(
            Single.fromCallable { localDatabase.update(cancellation, force = true) }
                .subscribeOn(networkScheduler)
                .observeOn(mainScheduler)
                .subscribeBy(
                    onSuccess = {
                        preference.isEnabled = true
                        preference.summary = definitionsSummary(it)
                        MaterialAlertDialogBuilder(requireContext())
                            .setMessage(R.string.malware_definitions_updated)
                            .setPositiveButton(R.string.action_ok, null)
                            .show()
                    },
                    onError = { error ->
                        Log.e(TAG, "Unable to update malware definitions", error)
                        preference.isEnabled = true
                        refreshDefinitionsSummary()
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.malware_definitions_update_failed_title)
                            .setMessage(R.string.malware_definitions_update_failed)
                            .setPositiveButton(R.string.action_ok, null)
                            .show()
                    }
                )
        )
    }

    private fun refreshDefinitionsSummary() {
        disposables.add(
            Single.fromCallable(localDatabase::info)
                .subscribeOn(networkScheduler)
                .observeOn(mainScheduler)
                .subscribeBy(
                    onSuccess = {
                        findPreference<Preference>(DEFINITIONS)?.summary =
                            definitionsSummary(it)
                    },
                    onError = {
                        findPreference<Preference>(DEFINITIONS)?.setSummary(
                            R.string.malware_definitions_missing
                        )
                    }
                )
        )
    }

    private fun definitionsSummary(info: MalwareDefinitionsInfo): String {
        if (!info.installed) return getString(R.string.malware_definitions_missing)
        val size = android.text.format.Formatter.formatShortFileSize(requireContext(), info.sizeBytes)
        val updated = if (info.updatedAt > 0) {
            DateUtils.getRelativeTimeSpanString(
                info.updatedAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            ).toString()
        } else {
            getString(R.string.malware_definitions_update_time_unknown)
        }
        return getString(R.string.malware_definitions_installed, size, updated)
    }

    private fun updateCloudControls(enabled: Boolean) {
        findPreference<Preference>(API_KEY)?.isEnabled = enabled
        findPreference<Preference>(PRIVACY)?.isEnabled = enabled
    }

    private fun showApiKeyDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.virus_total_api_key_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(apiKeyStore.get())
            setSelection(text.length)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.virus_total_api_key)
            .setMessage(R.string.virus_total_api_key_explanation)
            .setViewWithDialogMargins(input)
            .setPositiveButton(R.string.action_save) { _, _ ->
                apiKeyStore.set(input.text.toString())
                findPreference<androidx.preference.Preference>(API_KEY)?.summary = keySummary()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .setNeutralButton(R.string.action_clear) { _, _ ->
                apiKeyStore.clear()
                findPreference<androidx.preference.Preference>(API_KEY)?.summary = keySummary()
            }
            .show()
    }

    private fun showDisclaimer() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.malware_scanner_disclaimer)
            .setMessage(R.string.malware_scanner_disclaimer_message)
            .setPositiveButton(R.string.action_ok, null)
            .show()
    }

    private fun keySummary(): String = if (apiKeyStore.hasKey()) {
        getString(R.string.virus_total_api_key_configured)
    } else {
        getString(R.string.virus_total_api_key_missing)
    }

    private companion object {
        const val ENABLED = "virus_total_enabled"
        const val DISCLAIMER = "malware_scanner_disclaimer"
        const val API_KEY = "virus_total_api_key"
        const val CLOUD_ENABLED = "virus_total_cloud_enabled"
        const val DEFINITIONS = "malware_definitions"
        const val AUTO_UPDATE = "malware_definitions_auto_update"
        const val DEFINITIONS_SOURCE = "malware_definitions_source"
        const val SCAN_IMAGES = "virus_total_scan_images"
        const val SCAN_VIDEOS = "virus_total_scan_videos"
        const val PRIVACY = "virus_total_privacy"
        const val VIRUS_TOTAL_PRIVACY_URL = "https://docs.virustotal.com/docs/privacy-policy"
        const val DEFINITIONS_SOURCE_URL = "https://github.com/MaintainTeam/HypatiaDatabases"
        const val TAG = "MalwareScannerSettings"
    }
}
