package com.krystelligence.solipsism.settings.fragment

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.krystelligence.solipsism.extensions.setViewWithDialogMargins
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.browser.di.HostsClient
import com.krystelligence.solipsism.browser.di.injector
import com.krystelligence.solipsism.preference.UserPreferences
import com.krystelligence.solipsism.userscript.UserScript
import com.krystelligence.solipsism.userscript.UserScriptManager
import io.reactivex.rxjava3.core.Single
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject

/** Settings and lifecycle management for installed Tampermonkey-compatible scripts. */
class UserScriptsSettingsFragment : AbstractSettingsFragment() {

    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var userScriptManager: UserScriptManager
    @Inject @HostsClient lateinit var httpClient: Single<OkHttpClient>

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        lifecycleScope.launch {
            runCatching {
                val source = withContext(Dispatchers.IO) { readSource(uri) }
                withContext(Dispatchers.IO) { userScriptManager.install(source) }
            }.onSuccess { script ->
                refreshScripts()
                toast(getString(R.string.userscripts_install_success, script.metadata.name))
            }.onFailure { error -> showError(error) }
        }
    }

    override fun providePreferencesXmlResource(): Int = R.xml.preference_userscripts

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        injector.inject(this)

        togglePreference(
            preference = ENABLED_KEY,
            isChecked = userPreferences.userscriptsEnabled,
            onCheckChange = { userPreferences.userscriptsEnabled = it }
        )
        clickablePreference(FILE_KEY, onClick = { filePicker.launch(arrayOf("text/*", "application/javascript")) })
        clickablePreference(URL_KEY, onClick = ::showUrlInstallDialog)
        refreshScripts()
    }

    private fun refreshScripts() {
        val category = findPreference<PreferenceCategory>(LIST_KEY) ?: return
        category.removeAll()
        val scripts = userScriptManager.all()
        if (scripts.isEmpty()) {
            category.addPreference(Preference(requireContext()).apply {
                title = getString(R.string.userscripts_no_scripts)
                isEnabled = false
            })
            return
        }
        scripts.forEach { script ->
            category.addPreference(androidx.preference.SwitchPreferenceCompat(requireContext()).apply {
                key = "userscript_enabled_${script.id}"
                title = script.metadata.name
                summary = getString(
                    R.string.userscripts_script_summary,
                    script.metadata.version,
                    script.metadata.matches.firstOrNull() ?: script.metadata.includes.firstOrNull().orEmpty()
                )
                isChecked = script.enabled
                setOnPreferenceChangeListener { _, value ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        userScriptManager.setEnabled(script.id, value as Boolean)
                    }
                    true
                }
            })
            category.addPreference(Preference(requireContext()).apply {
                title = getString(R.string.userscripts_edit)
                summary = script.metadata.description.takeIf(String::isNotBlank)
                setOnPreferenceClickListener { showEditor(script); true }
            })
            category.addPreference(Preference(requireContext()).apply {
                title = getString(R.string.userscripts_delete)
                setOnPreferenceClickListener { confirmDelete(script); true }
            })
        }
    }

    private fun showUrlInstallDialog() {
        val input = EditText(requireContext()).apply {
            hint = "https://example.com/script.user.js"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.userscripts_import_url)
            .setViewWithDialogMargins(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val url = input.text.toString().trim()
                lifecycleScope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            userScriptManager.installFromUrl(url, httpClient.blockingGet())
                        }
                    }.onSuccess { script ->
                        refreshScripts()
                        toast(getString(R.string.userscripts_install_success, script.metadata.name))
                    }.onFailure { error -> showError(error) }
                }
            }
            .show()
    }

    private fun showEditor(script: UserScript) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setText(script.source)
            setSelection(length())
            minLines = 12
            maxLines = 24
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.userscripts_edit_title, script.metadata.name))
            .setViewWithDialogMargins(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                lifecycleScope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            userScriptManager.updateSource(script.id, input.text.toString())
                        }
                    }.onSuccess {
                        refreshScripts()
                        toast(getString(R.string.userscripts_saved, script.metadata.name))
                    }.onFailure { error -> showError(error) }
                }
            }
            .show()
    }

    private fun confirmDelete(script: UserScript) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.userscripts_delete_title, script.metadata.name))
            .setMessage(R.string.userscripts_delete_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    userScriptManager.delete(script.id)
                    withContext(Dispatchers.Main) {
                        refreshScripts()
                        toast(getString(R.string.userscripts_deleted, script.metadata.name))
                    }
                }
            }
            .show()
    }

    private fun readSource(uri: android.net.Uri): String {
        val input = requireContext().contentResolver.openInputStream(uri) ?: throw IOException("Unable to open file")
        input.use {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                if (output.size() > MAX_SOURCE_BYTES) throw IOException("Userscript is larger than 1 MiB")
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }

    private fun showError(error: Throwable) {
        if (!isAdded) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.userscripts)
            .setMessage(error.message ?: error.javaClass.simpleName)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun toast(message: String) = Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()

    private companion object {
        const val ENABLED_KEY = "userscripts_enabled"
        const val FILE_KEY = "userscripts_import_file"
        const val URL_KEY = "userscripts_import_url"
        const val LIST_KEY = "userscripts_list"
        const val MAX_SOURCE_BYTES = 1024 * 1024
    }
}
