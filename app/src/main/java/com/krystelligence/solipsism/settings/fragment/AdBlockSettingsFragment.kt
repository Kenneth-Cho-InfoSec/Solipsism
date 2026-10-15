package com.krystelligence.solipsism.settings.fragment

import com.krystelligence.solipsism.BuildConfig
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.adblock.BloomFilterAdBlocker
import com.krystelligence.solipsism.adblock.custom.CustomFilterRepository
import com.krystelligence.solipsism.adblock.lists.AddListResult
import com.krystelligence.solipsism.adblock.lists.FilterListEntry
import com.krystelligence.solipsism.adblock.lists.FilterListKind
import com.krystelligence.solipsism.adblock.lists.RemoteFilterListManager
import com.krystelligence.solipsism.adblock.source.HostsSourceType
import com.krystelligence.solipsism.adblock.snapshot.AdblockSnapshotManager
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
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
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
    @Inject internal lateinit var filterListManager: RemoteFilterListManager
    @Inject internal lateinit var snapshotManager: AdblockSnapshotManager

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
            preference = "cb_filter_auto_update",
            isChecked = userPreferences.filterListsAutoUpdate,
            onCheckChange = { userPreferences.filterListsAutoUpdate = it }
        )

        togglePreference(
            preference = "cb_filter_wifi_only",
            isChecked = userPreferences.filterListsWifiOnly,
            onCheckChange = { userPreferences.filterListsWifiOnly = it }
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

        clickablePreference("preference_filter_lists", summary = filterListsSummary()) {
            showFilterListsManager()
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
            isEnabled = true,
            summary = snapshotSummary(),
            onClick = {
                forceRefreshHostsPreference?.isEnabled = false
                snapshotManager.refreshAsync(force = true) { changed ->
                    forceRefreshHostsPreference?.isEnabled = true
                    forceRefreshHostsPreference?.summary = snapshotSummary()
                    activity?.toast(
                        when {
                            changed -> R.string.filter_snapshot_refresh_done
                            snapshotManager.status().let { it.hostsPresent || it.uboPresent } ->
                                R.string.filter_snapshot_refresh_uptodate
                            else -> R.string.filter_snapshot_refresh_failed
                        }
                    )
                    bloomFilterAdBlocker.populateAdBlockerFromDataSource(forceRefresh = true)
                }
            }
        )
    }

    private fun updateRefreshHostsEnabledStatus() {
        forceRefreshHostsPreference?.isEnabled = true
        forceRefreshHostsPreference?.summary = snapshotSummary()
    }

    private fun snapshotSummary(): String {
        val status = snapshotManager.status()
        return if (!status.hostsPresent && !status.uboPresent) {
            getString(R.string.filter_snapshot_bundled)
        } else {
            val updatedAt = maxOf(status.hostsUpdatedAt, status.uboUpdatedAt)
            val date = android.text.format.DateUtils.getRelativeTimeSpanString(
                updatedAt,
                System.currentTimeMillis(),
                android.text.format.DateUtils.DAY_IN_MILLIS
            )
            getString(
                R.string.filter_snapshot_status,
                getString(
                    R.string.filter_snapshot_updated,
                    date,
                    status.hostsCount,
                    status.uboRuleCount
                )
            )
        }
    }

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

    private fun customFilterSummary(): String {
        val manual = customFilterRepository.manualRules().count { it.enabled }
        val base = getString(R.string.custom_filters_count, manual)
        val imported = customFilterRepository.importedRuleCount()
        return if (imported > 0) {
            "$base; ${getString(R.string.custom_filters_imported, imported)}"
        } else {
            base
        }
    }

    private fun filterListsSummary(): String {
        val lists = filterListManager.lists()
        return getString(
            R.string.filter_lists_summary,
            lists.size,
            lists.count { it.enabled }
        )
    }

    private fun showFilterListsManager() {
        val context = requireContext()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroller = ScrollView(context).apply { addView(container) }
        lateinit var dialog: AlertDialog

        fun rebuild() {
            container.removeAllViews()
            val lists = filterListManager.lists()
            if (lists.isEmpty()) {
                container.addView(
                    TextView(context).apply {
                        text = getString(R.string.filter_list_empty)
                        setPadding(8.dp, 16.dp, 8.dp, 16.dp)
                    }
                )
            }
            lists.forEach { entry ->
                container.addView(filterListRow(entry, ::rebuild))
            }
        }

        dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.filter_lists)
            .setViewWithDialogMargins(scroller)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.filter_list_suggested) { _, _ ->
                showSuggestedFilterLists { showFilterListsManager() }
            }
            .setNegativeButton(R.string.filter_list_add_list) { _, _ ->
                showAddFilterListUrl { showFilterListsManager() }
            }
            .show()
        rebuild()
    }

    /**
     * One-tap suggested subscriptions so users get curated blocking without
     * pasting URLs. Downloads go through the same validation as manual adds.
     */
    private fun showSuggestedFilterLists(onAdded: () -> Unit) {
        val presets = listOf(
            R.string.filter_preset_easylist to SUGGESTED_EASYLIST_URL,
            R.string.filter_preset_easyprivacy to SUGGESTED_EASYPRIVACY_URL,
            R.string.filter_preset_stevenblack to SUGGESTED_STEVENBLACK_URL,
            R.string.filter_preset_adaway to SUGGESTED_ADAWAY_URL
        )
        BrowserDialog.showListChoices(
            requireActivity(),
            R.string.filter_list_suggested_title,
            *presets.map { (nameRes, url) ->
                DialogItem(
                    title = nameRes,
                    isConditionMet = filterListManager.lists().none { it.url == url },
                    onClick = {
                        filterListManager.addList(url) { result ->
                            activity?.toast(
                                when (result) {
                                    is AddListResult.Added ->
                                        if (result.kind == FilterListKind.ABP) {
                                            R.string.filter_list_added_abp
                                        } else {
                                            R.string.filter_list_added_hosts
                                        }
                                    AddListResult.Duplicate -> R.string.filter_list_duplicate
                                    AddListResult.InvalidUrl,
                                    AddListResult.DownloadFailed -> R.string.problem_download
                                    AddListResult.UnrecognizedFormat ->
                                        R.string.filter_list_unrecognized
                                }
                            )
                            if (result is AddListResult.Added) onAdded()
                        }
                    }
                )
            }.toTypedArray()
        )
    }

    private fun filterListRow(entry: FilterListEntry, onChanged: () -> Unit): LinearLayout {
        val context = requireContext()
        val host = entry.url.toHttpUrlOrNull()?.host ?: entry.url
        val kindLabel = getString(
            if (entry.kind == FilterListKind.ABP) {
                R.string.filter_list_kind_abp
            } else {
                R.string.filter_list_kind_hosts
            }
        )
        val updated = if (entry.lastUpdated > 0) {
            getString(
                R.string.filter_list_updated,
                java.text.DateFormat
                    .getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                    .format(java.util.Date(entry.lastUpdated))
            )
        } else {
            getString(R.string.filter_list_never)
        }

        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply { text = host })
            addView(
                TextView(context).apply {
                    text = "$kindLabel · $updated"
                    textSize = 12f
                }
            )
        }
        val toggle = MaterialSwitch(context).apply {
            isChecked = entry.enabled
            contentDescription = host
            setOnCheckedChangeListener { _, checked ->
                filterListManager.setListEnabled(entry.url, checked) { onChanged() }
            }
        }
        val refreshButton = ImageButton(context).apply {
            setImageResource(R.drawable.ic_action_refresh)
            contentDescription = getString(R.string.filter_list_refresh)
            setBackgroundResource(android.R.color.transparent)
            setOnClickListener {
                filterListManager.refreshList(entry.url) { ok ->
                    activity?.toast(
                        if (ok) R.string.filter_list_refreshed else R.string.filter_list_refresh_failed
                    )
                    onChanged()
                }
            }
        }
        val deleteButton = ImageButton(context).apply {
            setImageResource(R.drawable.ic_action_delete)
            contentDescription = getString(R.string.filter_list_delete)
            setBackgroundResource(android.R.color.transparent)
            setOnClickListener {
                filterListManager.removeList(entry.url) {
                    activity?.toast(R.string.filter_list_removed)
                    onChanged()
                }
            }
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8.dp, 0, 8.dp)
            addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(toggle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(refreshButton, LinearLayout.LayoutParams(48.dp, 48.dp))
            addView(deleteButton, LinearLayout.LayoutParams(48.dp, 48.dp))
        }
    }

    private fun showAddFilterListUrl(onAdded: () -> Unit) {
        BrowserDialog.showEditText(
            requireActivity(),
            title = R.string.filter_list_add_list,
            hint = R.string.hint_url,
            currentText = "https://",
            action = R.string.filter_list_add_button,
            textInputListener = { input ->
                filterListManager.addList(input) { result ->
                    val message = when (result) {
                        is AddListResult.Added ->
                            if (result.kind == FilterListKind.ABP) {
                                R.string.filter_list_added_abp
                            } else {
                                R.string.filter_list_added_hosts
                            }
                        AddListResult.Duplicate -> R.string.filter_list_duplicate
                        AddListResult.InvalidUrl,
                        AddListResult.DownloadFailed -> R.string.problem_download
                        AddListResult.UnrecognizedFormat -> R.string.filter_list_unrecognized
                    }
                    activity?.toast(message)
                    if (result is AddListResult.Added) onAdded()
                }
            }
        )
    }

    private fun showCustomFilterEditor() {
        val context = requireContext()
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minLines = 8
            setText(customFilterRepository.manualRules().joinToString("\n") { it.line })
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
                val errors = customFilterRepository.replaceManual(input.text.toString().lineSequence().toList())
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

        private const val SUGGESTED_EASYLIST_URL = "https://easylist.to/easylist/easylist.txt"
        private const val SUGGESTED_EASYPRIVACY_URL = "https://easylist.to/easyprivacy/easyprivacy.txt"
        private const val SUGGESTED_STEVENBLACK_URL =
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
        private const val SUGGESTED_ADAWAY_URL = "https://adaway.org/hosts.txt"
    }
}
