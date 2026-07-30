package com.krystelligence.solipsism.settings.fragment

import android.os.Bundle
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.browser.di.injector
import com.krystelligence.solipsism.extensions.setViewWithDialogMargins
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.EditText
import com.krystelligence.solipsism.preference.SitePermissionDecision
import com.krystelligence.solipsism.preference.SitePermissionKey
import com.krystelligence.solipsism.preference.SitePermissionStore
import com.krystelligence.solipsism.preference.UserPreferences
import javax.inject.Inject

class SitePermissionsSettingsFragment : AbstractSettingsFragment() {

    @Inject internal lateinit var userPreferences: UserPreferences
    @Inject internal lateinit var sitePermissionStore: SitePermissionStore

    override fun providePreferencesXmlResource() = R.xml.preference_site_permissions

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        injector.inject(this)

        updateAllSitesSummary()
        clickablePreference(SETTINGS_ADD_SITE, onClick = ::addSite)
        togglePreference(
            preference = SETTINGS_LOCATION_MASTER,
            isChecked = userPreferences.locationEnabled,
            onCheckChange = { userPreferences.locationEnabled = it }
        )
        clickablePreference(SETTINGS_ALL_SITES, onClick = ::chooseSite)
        clickablePreference(SETTINGS_CLEAR_ALL, onClick = ::clearAll)

        PERMISSION_PREFERENCES.forEach { (preference, permission) ->
            clickablePreference(preference) {
                if (permission in EXPERIMENTAL_PERMISSIONS) {
                    showExperimentalWarning { chooseSiteFor(permission) }
                } else {
                    chooseSiteFor(permission)
                }
            }
        }
    }

    private fun chooseSite() {
        val origins = sitePermissionStore.savedOrigins()
        if (origins.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.site_permission_choose_site)
                .setMessage(R.string.site_permission_no_saved_sites)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.site_permission_choose_site)
            .setItems(origins.toTypedArray()) { _, which -> showSiteOverview(origins[which]) }
            .show()
    }

    private fun addSite() {
        val input = EditText(requireContext()).apply {
            hint = "https://example.com"
            setSingleLine(true)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.site_permission_add_site)
            .setMessage(R.string.site_permission_add_site_message)
            .setViewWithDialogMargins(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (sitePermissionStore.ensureOrigin(input.text.toString())) {
                    updateAllSitesSummary()
                } else {
                    MaterialAlertDialogBuilder(requireContext())
                        .setMessage(R.string.site_permission_invalid_site)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
            .show()
    }

    private fun chooseSiteFor(permission: SitePermissionKey) {
        val origins = sitePermissionStore.savedOrigins()
        if (origins.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.site_permission_choose_site)
                .setMessage(R.string.site_permission_no_saved_sites)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.site_permission_choose_decision, permissionLabel(permission)))
            .setItems(origins.toTypedArray()) { _, which ->
                showDecisionDialog(origins[which], permission)
            }
            .show()
    }

    private fun showSiteOverview(origin: String) {
        val permissions = SitePermissionKey.entries.filter {
            sitePermissionStore.decision(origin, it) != SitePermissionDecision.DEFAULT
        }
        val message = if (permissions.isEmpty()) {
            getString(R.string.site_permission_default)
        } else {
            permissions.joinToString("\n") {
                "${permissionLabel(it)}: ${decisionLabel(sitePermissionStore.decision(origin, it))}"
            }
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(origin)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.site_permission_reset) { _, _ ->
                sitePermissionStore.clearOrigin(origin)
                updateAllSitesSummary()
            }
            .show()
    }

    private fun showDecisionDialog(origin: String, permission: SitePermissionKey) {
        val decisions = SitePermissionDecision.entries
        val labels = decisions.map(::decisionLabel).toTypedArray()
        val checked = decisions.indexOf(sitePermissionStore.decision(origin, permission))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.site_permission_choose_decision, permissionLabel(permission)))
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                sitePermissionStore.setDecision(origin, permission, decisions[which])
                updateAllSitesSummary()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showExperimentalWarning(onContinue: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.site_permission_warning_title)
            .setMessage(R.string.site_permission_warning_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> onContinue() }
            .show()
    }

    private fun clearAll() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.site_permission_clear_all)
            .setMessage(R.string.site_permission_clear_all_summary)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_yes) { _, _ ->
                sitePermissionStore.clearAll()
                updateAllSitesSummary()
            }
            .show()
    }

    private fun updateAllSitesSummary() {
        findPreference<androidx.preference.Preference>(SETTINGS_ALL_SITES)?.summary =
            if (sitePermissionStore.savedOrigins().isEmpty()) {
                getString(R.string.site_permission_no_saved_sites)
            } else {
                sitePermissionStore.savedOrigins().joinToString("\n")
            }
    }

    private fun permissionLabel(permission: SitePermissionKey): String = when (permission) {
        SitePermissionKey.LOCATION -> getString(R.string.location)
        SitePermissionKey.CAMERA -> getString(R.string.site_permission_camera)
        SitePermissionKey.MICROPHONE -> getString(R.string.site_permission_microphone)
        SitePermissionKey.NOTIFICATIONS -> getString(R.string.site_permission_notifications)
        SitePermissionKey.CLIPBOARD -> getString(R.string.site_permission_clipboard)
        SitePermissionKey.MOTION_SENSORS -> getString(R.string.site_permission_motion_sensors)
        SitePermissionKey.PROTECTED_CONTENT -> getString(R.string.site_permission_protected_content)
        SitePermissionKey.EMBEDDED_CONTENT -> getString(R.string.site_permission_embedded_content)
        SitePermissionKey.LOCAL_NETWORK -> getString(R.string.site_permission_local_network)
        SitePermissionKey.AUTOMATIC_DOWNLOADS -> getString(R.string.site_permission_automatic_downloads)
        SitePermissionKey.NFC -> getString(R.string.site_permission_nfc)
        SitePermissionKey.USB -> getString(R.string.site_permission_usb)
        SitePermissionKey.SERIAL -> getString(R.string.site_permission_serial)
        SitePermissionKey.FILE_EDITING -> getString(R.string.site_permission_file_editing)
        SitePermissionKey.VIRTUAL_REALITY -> getString(R.string.site_permission_virtual_reality)
        SitePermissionKey.AUGMENTED_REALITY -> getString(R.string.site_permission_augmented_reality)
        SitePermissionKey.DEVICE_USE -> getString(R.string.site_permission_device_use)
        SitePermissionKey.APPS_ON_DEVICE -> getString(R.string.site_permission_apps_on_device)
        SitePermissionKey.JAVASCRIPT_JIT -> getString(R.string.site_permission_javascript_jit)
    }

    private fun decisionLabel(decision: SitePermissionDecision): String = when (decision) {
        SitePermissionDecision.DEFAULT -> getString(R.string.site_permission_default)
        SitePermissionDecision.ALLOW -> getString(R.string.action_allow)
        SitePermissionDecision.ASK -> getString(R.string.site_permission_ask)
        SitePermissionDecision.DENY -> getString(R.string.action_dont_allow)
    }

    private companion object {
        const val SETTINGS_ALL_SITES = "all_sites"
        const val SETTINGS_ADD_SITE = "add_site"
        const val SETTINGS_CLEAR_ALL = "clear_all"
        const val SETTINGS_LOCATION_MASTER = "location_master"
        val PERMISSION_PREFERENCES = mapOf(
            "location" to SitePermissionKey.LOCATION,
            "camera" to SitePermissionKey.CAMERA,
            "microphone" to SitePermissionKey.MICROPHONE,
            "notifications" to SitePermissionKey.NOTIFICATIONS,
            "clipboard" to SitePermissionKey.CLIPBOARD,
            "motion_sensors" to SitePermissionKey.MOTION_SENSORS,
            "protected_content" to SitePermissionKey.PROTECTED_CONTENT,
            "embedded_content" to SitePermissionKey.EMBEDDED_CONTENT,
            "local_network" to SitePermissionKey.LOCAL_NETWORK,
            "automatic_downloads" to SitePermissionKey.AUTOMATIC_DOWNLOADS,
            "nfc" to SitePermissionKey.NFC,
            "usb" to SitePermissionKey.USB,
            "serial" to SitePermissionKey.SERIAL,
            "file_editing" to SitePermissionKey.FILE_EDITING,
            "virtual_reality" to SitePermissionKey.VIRTUAL_REALITY,
            "augmented_reality" to SitePermissionKey.AUGMENTED_REALITY,
            "device_use" to SitePermissionKey.DEVICE_USE,
            "apps_on_device" to SitePermissionKey.APPS_ON_DEVICE,
            "javascript_jit" to SitePermissionKey.JAVASCRIPT_JIT
        )
        val EXPERIMENTAL_PERMISSIONS = setOf(
            SitePermissionKey.NFC, SitePermissionKey.USB, SitePermissionKey.SERIAL,
            SitePermissionKey.FILE_EDITING, SitePermissionKey.VIRTUAL_REALITY,
            SitePermissionKey.AUGMENTED_REALITY, SitePermissionKey.DEVICE_USE,
            SitePermissionKey.APPS_ON_DEVICE, SitePermissionKey.JAVASCRIPT_JIT
        )
    }
}
