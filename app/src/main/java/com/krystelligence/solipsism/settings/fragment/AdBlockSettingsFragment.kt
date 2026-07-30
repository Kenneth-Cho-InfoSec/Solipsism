package com.krystelligence.solipsism.settings.fragment

import com.krystelligence.solipsism.BuildConfig
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.adblock.BloomFilterAdBlocker
import com.krystelligence.solipsism.adblock.custom.CustomFilterRepository
import com.krystelligence.solipsism.adblock.source.HostsSourceType
import com.krystelligence.solipsism.adblock.source.selectedHostsSource
import com.krystelligence.solipsism.adblock.source.toPreferenceIndex
import com.krystelligence.solipsism.browser.di.DiskScheduler
import com.krystelligence.solipsism.browser.di.MainScheduler
import com.krystelligence.solipsism.browser.di.injector
import com.krystelligence.solipsism.dialog.BrowserDialog
import com.krystelligence.solipsism.dialog.DialogItem
import com.krystelligence.solipsism.extensions.toast
import com.krystelligence.solipsism.preference.UserPreferences
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.krystelligence.solipsism.extensions.setViewWithDialogMargins
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Settings for the ad block mechanic.
 */
class AdBlockSettingsFragment : AbstractSettingsFragment() {

    @Inject internal lateinit var userPreferences: UserPreferences
    @Inject @MainScheduler internal lateinit var mainScheduler: Scheduler
    @Inject @DiskScheduler internal lateinit var diskScheduler: Scheduler
    @Inject internal lateinit var bloomFilterAdBlocker: BloomFilterAdBlocker
    @Inject internal lateinit var customFilterRepository: CustomFilterRepository

    private var recentSummaryUpdater: SummaryUpdater? = null
    private val compositeDisposable = CompositeDisposable()
    private var forceRefreshHostsPreference: Preference? = null

    override fun providePreferencesXmlResource(): Int = R.xml.preference_ad_block

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        injector.inject(this)

        togglePreference(
            preference = "cb_block_ads",
            isChecked = userPreferences.adBlockEnabled,
            onCheckChange = { userPreferences.adBlockEnabled = it }
        )

        togglePreference(
            preference = "cb_ublock_origin",
            isChecked = userPreferences.uBlockOriginEnabled,
            onCheckChange = { userPreferences.uBlockOriginEnabled = it }
        )

        togglePreference(
            preference = "cb_cosmetic_filters",
            isChecked = userPreferences.cosmeticFiltersEnabled,
            onCheckChange = { userPreferences.cosmeticFiltersEnabled = it }
        )

        togglePreference(
            preference = "cb_block_gif",
            isChecked = userPreferences.blockGifImagesEnabled,
            onCheckChange = { userPreferences.blockGifImagesEnabled = it }
        )

        clickablePreference("custom_filters", summary = customFilterSummary()) {
            showCustomFilterEditor()
        }

        clickableDynamicPreference(
            preference = "preference_hosts_source",
            isEnabled = BuildConfig.FULL_VERSION,
            summary = if (BuildConfig.FULL_VERSION) {
                userPreferences.selectedHostsSource().toSummary()
            } else {
                getString(R.string.block_ads_upsell_source)
            },
            onClick = ::showHostsSourceChooser
        )

        forceRefreshHostsPreference = clickableDynamicPreference(
            preference = "preference_hosts_refresh_force",
            isEnabled = isRefreshHostsEnabled(),
            onClick = {
                bloomFilterAdBlocker.populateAdBlockerFromDataSource(forceRefresh = true)
            }
        )
    }

    private fun updateRefreshHostsEnabledStatus() {
        forceRefreshHostsPreference?.isEnabled = isRefreshHostsEnabled()
    }

    private fun isRefreshHostsEnabled() =
        userPreferences.selectedHostsSource() is HostsSourceType.Remote

    @Deprecated("Deprecated in Java")
    override fun onDestroy() {
        super.onDestroy()
        compositeDisposable.clear()
    }

    private fun HostsSourceType.toSummary(): String = when (this) {
        HostsSourceType.Default -> getString(R.string.block_source_default)
        is HostsSourceType.Local -> getString(R.string.block_source_local_description, file.path)
        is HostsSourceType.Remote -> getString(R.string.block_source_remote_description, httpUrl)
    }

    private fun showHostsSourceChooser(summaryUpdater: SummaryUpdater) {
        BrowserDialog.showListChoices(
            requireActivity(),
            R.string.block_ad_source,
            DialogItem(
                title = R.string.block_source_default,
                isConditionMet = userPreferences.selectedHostsSource() == HostsSourceType.Default,
                onClick = {
                    userPreferences.hostsSource = HostsSourceType.Default.toPreferenceIndex()
                    summaryUpdater.updateSummary(userPreferences.selectedHostsSource().toSummary())
                    updateForNewHostsSource()
                }
            ),
            DialogItem(
                title = R.string.block_source_local,
                isConditionMet = userPreferences.selectedHostsSource() is HostsSourceType.Local,
                onClick = {
                    showFileChooser(summaryUpdater)
                }
            ),
            DialogItem(
                title = R.string.block_source_remote,
                isConditionMet = userPreferences.selectedHostsSource() is HostsSourceType.Remote,
                onClick = {
                    showUrlChooser(summaryUpdater)
                }
            )
        )
    }

    private fun showFileChooser(summaryUpdater: SummaryUpdater) {
        this.recentSummaryUpdater = summaryUpdater
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = TEXT_MIME_TYPE
        }

        startActivityForResult(intent, FILE_REQUEST_CODE)
    }

    private fun showUrlChooser(summaryUpdater: SummaryUpdater) {
        BrowserDialog.showEditText(
            requireActivity(),
            title = R.string.block_source_remote,
            hint = R.string.hint_url,
            currentText = userPreferences.hostsRemoteFile,
            action = R.string.action_ok,
            textInputListener = {
                val url = it.toHttpUrlOrNull()
                    ?: return@showEditText run { activity?.toast(R.string.problem_download) }
                userPreferences.hostsSource = HostsSourceType.Remote(url).toPreferenceIndex()
                userPreferences.hostsRemoteFile = it
                summaryUpdater.updateSummary(it)
                updateForNewHostsSource()
            }
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                data?.data?.also { uri ->
                    compositeDisposable += readTextFromUri(uri)
                        .subscribeOn(diskScheduler)
                        .observeOn(mainScheduler)
                        .subscribeBy(
                            onComplete = { activity?.toast(R.string.action_message_canceled) },
                            onSuccess = { file ->
                                userPreferences.hostsSource =
                                    HostsSourceType.Local(file).toPreferenceIndex()
                                userPreferences.hostsLocalFile = file.path
                                recentSummaryUpdater?.updateSummary(
                                    userPreferences.selectedHostsSource().toSummary()
                                )
                                updateForNewHostsSource()
                            }
                        )
                }
            } else {
                activity?.toast(R.string.action_message_canceled)
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun updateForNewHostsSource() {
        bloomFilterAdBlocker.populateAdBlockerFromDataSource(forceRefresh = true)
        updateRefreshHostsEnabledStatus()
    }

    private fun customFilterSummary(): String = getString(
        R.string.custom_filters_count,
        customFilterRepository.all().count { it.enabled }
    )

    private fun showCustomFilterEditor() {
        val context = requireContext()
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minLines = 8
            setText(customFilterRepository.all().joinToString("\n") { it.line })
            hint = getString(R.string.custom_filters_hint)
        }
        val help = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_info_details)
            contentDescription = getString(R.string.custom_filters_help)
            setOnClickListener { showFilterLanguageHelp() }
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(help, LinearLayout.LayoutParams(48.dp, 48.dp))
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.custom_filters)
            .setViewWithDialogMargins(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                customFilterRepository.clear()
                val errors = customFilterRepository.addAll(input.text.toString().lineSequence().toList())
                if (errors.isEmpty()) {
                    activity?.toast(R.string.custom_filters_saved)
                } else {
                    android.widget.Toast.makeText(
                        context,
                        getString(R.string.custom_filters_invalid, errors.size),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .show()
    }

    private fun showFilterLanguageHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.custom_filters_help)
            .setMessage(R.string.custom_filters_help_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private fun readTextFromUri(uri: Uri): Maybe<File> = Maybe.create {
        val externalFilesDir = activity?.getExternalFilesDir("")
            ?: return@create it.onComplete()
        val inputStream = activity?.contentResolver?.openInputStream(uri)
            ?: return@create it.onComplete()

        try {
            val outputFile = File(externalFilesDir, AD_HOSTS_FILE)

            val input = inputStream.source()
            val output = outputFile.sink().buffer()
            output.writeAll(input)
            return@create it.onSuccess(outputFile)
        } catch (exception: IOException) {
            return@create it.onComplete()
        }
    }

    companion object {
        private const val FILE_REQUEST_CODE = 100
        private const val AD_HOSTS_FILE = "local_hosts.txt"
        private const val TEXT_MIME_TYPE = "text/*"
    }
}
