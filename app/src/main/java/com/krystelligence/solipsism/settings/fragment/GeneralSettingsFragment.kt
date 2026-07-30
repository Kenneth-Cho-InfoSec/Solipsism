package com.krystelligence.solipsism.settings.fragment

import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.browser.di.injector
import com.krystelligence.solipsism.browser.proxy.ProxyChoice
import com.krystelligence.solipsism.constant.SCHEME_BLANK
import com.krystelligence.solipsism.constant.SCHEME_BOOKMARKS
import com.krystelligence.solipsism.constant.SCHEME_HOMEPAGE
import com.krystelligence.solipsism.dialog.BrowserDialog
import com.krystelligence.solipsism.extensions.withSingleChoiceItems
import com.krystelligence.solipsism.preference.UserPreferences
import com.krystelligence.solipsism.html.homepage.HomepageSource
import com.krystelligence.solipsism.search.SearchEngineProvider
import com.krystelligence.solipsism.search.Suggestions
import com.krystelligence.solipsism.search.engine.BaseSearchEngine
import com.krystelligence.solipsism.search.engine.CustomSearch
import com.krystelligence.solipsism.utils.FileUtils
import com.krystelligence.solipsism.utils.ProxyUtils
import com.krystelligence.solipsism.utils.ThemeUtils
import com.krystelligence.solipsism.i18n.TranslationOverrides
import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.LayoutInflater
import android.webkit.URLUtil
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import javax.inject.Inject

/**
 * The general settings of the app.
 */
class GeneralSettingsFragment : AbstractSettingsFragment() {

    @Inject lateinit var searchEngineProvider: SearchEngineProvider
    @Inject lateinit var userPreferences: UserPreferences

    private lateinit var proxyChoices: Array<String>

    private val customLanguagePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        loadCustomLanguage(uri)
    }

    override fun providePreferencesXmlResource() = R.xml.preference_general

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        injector.inject(this)

        proxyChoices = resources.getStringArray(R.array.proxy_choices_array)

        clickableDynamicPreference(
            preference = SETTINGS_LANGUAGE,
            summary = currentLanguageName(),
            onClick = ::showLanguagePicker
        )

        clickablePreference(
            preference = SETTINGS_CUSTOM_LANGUAGE,
            summary = if (TranslationOverrides.count(requireContext()) == 0) {
                getString(R.string.settings_custom_language_summary)
            } else {
                getString(
                    R.string.settings_custom_language_loaded,
                    TranslationOverrides.count(requireContext())
                )
            },
            onClick = ::showCustomLanguageDialog
        )

        clickableDynamicPreference(
            preference = SETTINGS_PROXY,
            summary = userPreferences.proxyChoice.toSummary(),
            onClick = ::showProxyPicker
        )

        clickableDynamicPreference(
            preference = SETTINGS_USER_AGENT,
            summary = choiceToUserAgent(userPreferences.userAgentChoice),
            onClick = ::showUserAgentChooserDialog
        )

        togglePreference(
            preference = SETTINGS_CHROMPATIBILITY,
            isChecked = userPreferences.chrompatibilityModeEnabled,
            summary = getString(R.string.chrompatibility_mode_summary),
            onCheckChange = { userPreferences.chrompatibilityModeEnabled = it }
        )

        clickableDynamicPreference(
            preference = SETTINGS_DOWNLOAD,
            summary = userPreferences.downloadDirectory,
            onClick = ::showDownloadLocationDialog
        )

        clickableDynamicPreference(
            preference = SETTINGS_HOME,
            summary = homePageUrlToDisplayTitle(userPreferences.homepage),
            onClick = ::showHomePageDialog
        )

        clickableDynamicPreference(
            preference = SETTINGS_SEARCH_ENGINE,
            summary = getSearchEngineSummary(searchEngineProvider.provideSearchEngine()),
            onClick = ::showSearchProviderDialog
        )

        clickableDynamicPreference(
            preference = SETTINGS_SUGGESTIONS,
            summary = searchSuggestionChoiceToTitle(Suggestions.from(userPreferences.searchSuggestionChoice)),
            onClick = ::showSearchSuggestionsDialog
        )

        togglePreference(
            preference = SETTINGS_IMAGES,
            isChecked = userPreferences.blockImagesEnabled,
            onCheckChange = { userPreferences.blockImagesEnabled = it }
        )

        togglePreference(
            preference = SETTINGS_SAVEDATA,
            isChecked = userPreferences.saveDataEnabled,
            onCheckChange = { userPreferences.saveDataEnabled = it }
        )

        togglePreference(
            preference = SETTINGS_JAVASCRIPT,
            isChecked = userPreferences.javaScriptEnabled,
            onCheckChange = { userPreferences.javaScriptEnabled = it }
        )

        togglePreference(
            preference = SETTINGS_COLOR_MODE,
            isChecked = userPreferences.colorModeEnabled,
            onCheckChange = { userPreferences.colorModeEnabled = it }
        )
    }

    private fun currentLanguageName(): String {
        val selectedTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            .takeIf { it.isNotBlank() } ?: "system"
        val values = resources.getStringArray(R.array.language_values)
        val entries = resources.getStringArray(R.array.language_entries)
        return entries[values.indexOf(selectedTag).takeIf { it >= 0 } ?: 0]
    }

    private fun showLanguagePicker(summaryUpdater: SummaryUpdater) {
        val entries = resources.getStringArray(R.array.language_entries)
        val values = resources.getStringArray(R.array.language_values)
        val selectedTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            .takeIf { it.isNotBlank() } ?: "system"
        val selectedIndex = values.indexOf(selectedTag).takeIf { it >= 0 } ?: 0

        BrowserDialog.showCustomDialog(requireActivity()) {
            setTitle(R.string.settings_language)
            setSingleChoiceItems(entries, selectedIndex) { _, which ->
                val languageTag = values[which]
                summaryUpdater.updateSummary(entries[which])
                AppCompatDelegate.setApplicationLocales(
                    if (languageTag == "system") LocaleListCompat.getEmptyLocaleList()
                    else LocaleListCompat.forLanguageTags(languageTag)
                )
            }
            setPositiveButton(R.string.action_ok, null)
        }
    }

    private fun showCustomLanguageDialog() {
        BrowserDialog.showCustomDialog(requireActivity()) {
            setTitle(R.string.settings_custom_language_format_title)
            setMessage(R.string.settings_custom_language_format_message)
            setPositiveButton(R.string.settings_custom_language_choose) { _, _ ->
                customLanguagePicker.launch(arrayOf("text/xml", "application/xml", "text/plain"))
            }
            if (TranslationOverrides.count(requireContext()) > 0) {
                setNeutralButton(R.string.settings_custom_language_clear) { _, _ ->
                    TranslationOverrides.clear(requireContext())
                    Toast.makeText(
                        requireContext(),
                        R.string.settings_custom_language_cleared,
                        Toast.LENGTH_SHORT
                    ).show()
                    requireActivity().recreate()
                }
            }
            setNegativeButton(R.string.action_cancel, null)
        }
    }

    private fun loadCustomLanguage(uri: Uri) {
        runCatching {
            requireContext().contentResolver.openInputStream(uri)
                ?.let { TranslationOverrides.import(requireContext(), it) }
                ?: error("could not open file")
        }.onSuccess { count ->
            Toast.makeText(
                requireContext(),
                getString(R.string.settings_custom_language_loaded, count),
                Toast.LENGTH_SHORT
            ).show()
            requireActivity().recreate()
        }.onFailure { error ->
            BrowserDialog.showCustomDialog(requireActivity()) {
                setTitle(R.string.settings_custom_language)
                setMessage(
                    getString(
                        R.string.settings_custom_language_invalid,
                        error.message ?: "Unknown error"
                    )
                )
                setPositiveButton(R.string.action_ok, null)
            }
        }
    }

    private fun ProxyChoice.toSummary(): String {
        val stringArray = resources.getStringArray(R.array.proxy_choices_array)
        return when (this) {
            ProxyChoice.NONE -> stringArray[0]
            ProxyChoice.ORBOT -> stringArray[1]
            // ProxyChoice.I2P -> stringArray[2]
            ProxyChoice.MANUAL -> "${userPreferences.proxyHost}:${userPreferences.proxyPort}"
        }
    }

    private fun showProxyPicker(summaryUpdater: SummaryUpdater) {
        BrowserDialog.showCustomDialog(activity) {
            setTitle(R.string.http_proxy)
            val stringArray = resources.getStringArray(R.array.proxy_choices_array)
            val values = ProxyChoice.entries.map {
                Pair(
                    it, when (it) {
                        ProxyChoice.NONE -> stringArray[0]
                        ProxyChoice.ORBOT -> stringArray[1]
                        // ProxyChoice.I2P -> stringArray[2]
                        ProxyChoice.MANUAL -> stringArray[2]
                    }
                )
            }
            withSingleChoiceItems(values, userPreferences.proxyChoice) {
                updateProxyChoice(it, requireActivity(), summaryUpdater)
            }
            setPositiveButton(R.string.action_ok, null)
        }
    }

    private fun updateProxyChoice(
        choice: ProxyChoice,
        activity: Activity,
        summaryUpdater: SummaryUpdater
    ) {
        val sanitizedChoice = ProxyUtils.sanitizeProxyChoice(choice, activity)
        if (sanitizedChoice == ProxyChoice.MANUAL) {
            showManualProxyPicker(activity, summaryUpdater)
        }

        userPreferences.proxyChoice = sanitizedChoice
        summaryUpdater.updateSummary(sanitizedChoice.toSummary())
    }

    private fun showManualProxyPicker(activity: Activity, summaryUpdater: SummaryUpdater) {
        val v = activity.layoutInflater.inflate(R.layout.dialog_manual_proxy, null)
        val eProxyHost = v.findViewById<TextView>(R.id.proxyHost)
        val eProxyPort = v.findViewById<TextView>(R.id.proxyPort)

        // Limit the number of characters since the port needs to be of type int
        // Use input filters to limit the EditText length and determine the max
        // length by using length of integer MAX_VALUE
        val maxCharacters = Integer.MAX_VALUE.toString().length
        eProxyPort.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(maxCharacters - 1))

        eProxyHost.text = userPreferences.proxyHost
        eProxyPort.text = userPreferences.proxyPort.toString()

        BrowserDialog.showCustomDialog(activity) {
            setTitle(R.string.manual_proxy)
            setView(v)
            setPositiveButton(R.string.action_ok) { _, _ ->
                val proxyHost = eProxyHost.text.toString()
                val proxyPort = try {
                    // Try/Catch in case the user types an empty string or a number
                    // larger than max integer
                    Integer.parseInt(eProxyPort.text.toString())
                } catch (ignored: NumberFormatException) {
                    userPreferences.proxyPort
                }

                userPreferences.proxyHost = proxyHost
                userPreferences.proxyPort = proxyPort
                summaryUpdater.updateSummary("$proxyHost:$proxyPort")
            }
        }
    }

    private fun choiceToUserAgent(index: Int) = when (index) {
        1 -> resources.getString(R.string.agent_default)
        2 -> resources.getString(R.string.agent_desktop)
        3 -> resources.getString(R.string.agent_mobile)
        4 -> resources.getString(R.string.agent_custom)
        else -> resources.getString(R.string.agent_default)
    }

    private fun showUserAgentChooserDialog(summaryUpdater: SummaryUpdater) {
        BrowserDialog.showCustomDialog(activity) {
            setTitle(resources.getString(R.string.title_user_agent))
            setSingleChoiceItems(
                R.array.user_agent,
                userPreferences.userAgentChoice - 1
            ) { _, which ->
                userPreferences.userAgentChoice = which + 1
                summaryUpdater.updateSummary(choiceToUserAgent(userPreferences.userAgentChoice))
                when (which) {
                    in 0..2 -> Unit
                    3 -> {
                        summaryUpdater.updateSummary(resources.getString(R.string.agent_custom))
                        showCustomUserAgentPicker(summaryUpdater)
                    }
                }
            }
            setPositiveButton(resources.getString(R.string.action_ok), null)
        }
    }

    private fun showCustomUserAgentPicker(summaryUpdater: SummaryUpdater) {
        activity?.let {
            BrowserDialog.showEditText(
                it,
                R.string.title_user_agent,
                R.string.title_user_agent,
                userPreferences.userAgentString,
                R.string.action_ok
            ) { s ->
                userPreferences.userAgentString = s
                summaryUpdater.updateSummary(it.getString(R.string.agent_custom))
            }
        }
    }

    private fun showDownloadLocationDialog(summaryUpdater: SummaryUpdater) {
        BrowserDialog.showCustomDialog(activity) {
            setTitle(resources.getString(R.string.title_download_location))
            val n: Int =
                if (userPreferences.downloadDirectory.contains(Environment.DIRECTORY_DOWNLOADS)) {
                    0
                } else {
                    1
                }

            setSingleChoiceItems(R.array.download_folder, n) { _, which ->
                when (which) {
                    0 -> {
                        userPreferences.downloadDirectory = FileUtils.DEFAULT_DOWNLOAD_PATH
                        summaryUpdater.updateSummary(FileUtils.DEFAULT_DOWNLOAD_PATH)
                    }

                    1 -> {
                        showCustomDownloadLocationPicker(summaryUpdater)
                    }
                }
            }
            setPositiveButton(resources.getString(R.string.action_ok), null)
        }
    }


    private fun showCustomDownloadLocationPicker(summaryUpdater: SummaryUpdater) {
        activity?.let { activity ->
            val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_edit_text, null)
            val getDownload = dialogView.findViewById<EditText>(R.id.dialog_edit_text)

            val errorColor = ContextCompat.getColor(activity, R.color.error_red)
            val regularColor = ThemeUtils.getTextColor(activity)
            getDownload.setTextColor(regularColor)
            getDownload.addTextChangedListener(
                DownloadLocationTextWatcher(
                    getDownload,
                    errorColor,
                    regularColor
                )
            )
            getDownload.setText(userPreferences.downloadDirectory)

            BrowserDialog.showCustomDialog(activity) {
                setTitle(R.string.title_download_location)
                setView(dialogView)
                setPositiveButton(R.string.action_ok) { _, _ ->
                    var text = getDownload.text.toString()
                    text = FileUtils.addNecessarySlashes(text)
                    userPreferences.downloadDirectory = text
                    summaryUpdater.updateSummary(text)
                }
            }
        }
    }

    private class DownloadLocationTextWatcher(
        private val getDownload: EditText,
        private val errorColor: Int,
        private val regularColor: Int
    ) : TextWatcher {

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable) {
            if (!FileUtils.isWriteAccessAvailable(s.toString())) {
                this.getDownload.setTextColor(this.errorColor)
            } else {
                this.getDownload.setTextColor(this.regularColor)
            }
        }
    }

    private fun homePageUrlToDisplayTitle(url: String): String = when (url) {
        SCHEME_HOMEPAGE -> resources.getString(R.string.action_homepage)
        SCHEME_BLANK -> resources.getString(R.string.action_blank)
        SCHEME_BOOKMARKS -> resources.getString(R.string.action_bookmarks)
        else -> url
    }

    private fun showHomePageDialog(summaryUpdater: SummaryUpdater) {
        BrowserDialog.showCustomDialog(activity) {
            setTitle(R.string.home)
            val n = when (userPreferences.homepage) {
                SCHEME_HOMEPAGE -> 0
                SCHEME_BLANK -> 1
                SCHEME_BOOKMARKS -> 2
                else -> 3
            }

            setSingleChoiceItems(R.array.homepage, n) { _, which ->
                when (which) {
                    0 -> {
                        userPreferences.homepage = SCHEME_HOMEPAGE
                        userPreferences.homepageSource = HomepageSource.BUILT_IN.value
                        summaryUpdater.updateSummary(resources.getString(R.string.action_homepage))
                    }

                    1 -> {
                        userPreferences.homepage = SCHEME_BLANK
                        userPreferences.homepageSource = HomepageSource.BUILT_IN.value
                        summaryUpdater.updateSummary(resources.getString(R.string.action_blank))
                    }

                    2 -> {
                        userPreferences.homepage = SCHEME_BOOKMARKS
                        userPreferences.homepageSource = HomepageSource.BUILT_IN.value
                        summaryUpdater.updateSummary(resources.getString(R.string.action_bookmarks))
                    }

                    3 -> {
                        showCustomHomePagePicker(summaryUpdater)
                    }
                }
            }
            setPositiveButton(resources.getString(R.string.action_ok), null)
        }
    }

    private fun showCustomHomePagePicker(summaryUpdater: SummaryUpdater) {
        val currentHomepage: String = if (!URLUtil.isAboutUrl(userPreferences.homepage)) {
            userPreferences.homepage
        } else {
            "https://www.google.com"
        }

        activity?.let {
            BrowserDialog.showEditText(
                it,
                R.string.title_custom_homepage,
                R.string.title_custom_homepage,
                currentHomepage,
                R.string.action_ok
            ) { url ->
                val uri = Uri.parse(url.trim())
                if ((uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()) {
                    userPreferences.homepage = uri.toString()
                    userPreferences.homepageSource = HomepageSource.DOMAIN.value
                    summaryUpdater.updateSummary(uri.toString())
                }
            }
        }
    }

    private fun getSearchEngineSummary(baseSearchEngine: BaseSearchEngine): String {
        return if (baseSearchEngine is CustomSearch) {
            baseSearchEngine.queryUrl
        } else {
            getString(baseSearchEngine.titleRes)
        }
    }

    private fun convertSearchEngineToString(searchEngines: List<BaseSearchEngine>): Array<CharSequence> =
        searchEngines.map { getString(it.titleRes) }.toTypedArray()

    private fun showSearchProviderDialog(summaryUpdater: SummaryUpdater) {
        BrowserDialog.showCustomDialog(activity) {
            setTitle(resources.getString(R.string.title_search_engine))

            val searchEngineList = searchEngineProvider.provideAllSearchEngines()

            val chars = convertSearchEngineToString(searchEngineList)

            val n = userPreferences.searchChoice

            setSingleChoiceItems(chars, n) { _, which ->
                val searchEngine = searchEngineList[which]

                // Store the search engine preference
                val preferencesIndex =
                    searchEngineProvider.mapSearchEngineToPreferenceIndex(searchEngine)
                userPreferences.searchChoice = preferencesIndex

                if (searchEngine is CustomSearch) {
                    // Show the URL picker
                    showCustomSearchDialog(searchEngine, summaryUpdater)
                } else {
                    // Set the new search engine summary
                    summaryUpdater.updateSummary(getSearchEngineSummary(searchEngine))
                }
            }
            setPositiveButton(R.string.action_ok, null)
        }
    }

    private fun showCustomSearchDialog(customSearch: CustomSearch, summaryUpdater: SummaryUpdater) {
        activity?.let {
            BrowserDialog.showEditText(
                it,
                R.string.search_engine_custom,
                R.string.search_engine_custom,
                userPreferences.searchUrl,
                R.string.action_ok
            ) { searchUrl ->
                userPreferences.searchUrl = searchUrl
                summaryUpdater.updateSummary(getSearchEngineSummary(customSearch))
            }

        }
    }

    private fun searchSuggestionChoiceToTitle(choice: Suggestions): String =
        when (choice) {
            Suggestions.NONE -> getString(R.string.search_suggestions_off)
            Suggestions.GOOGLE -> getString(R.string.powered_by_google)
            Suggestions.DUCK -> getString(R.string.powered_by_duck)
            Suggestions.BAIDU -> getString(R.string.powered_by_baidu)
            Suggestions.NAVER -> getString(R.string.powered_by_naver)
        }

    private fun showSearchSuggestionsDialog(summaryUpdater: SummaryUpdater) {
        BrowserDialog.showCustomDialog(activity) {
            setTitle(resources.getString(R.string.search_suggestions))

            val currentChoice = when (Suggestions.from(userPreferences.searchSuggestionChoice)) {
                Suggestions.GOOGLE -> 0
                Suggestions.DUCK -> 1
                Suggestions.BAIDU -> 2
                Suggestions.NAVER -> 3
                Suggestions.NONE -> 3
            }

            setSingleChoiceItems(R.array.suggestions, currentChoice) { _, which ->
                val suggestionsProvider = when (which) {
                    0 -> Suggestions.GOOGLE
                    1 -> Suggestions.DUCK
                    2 -> Suggestions.BAIDU
                    3 -> Suggestions.NAVER
                    4 -> Suggestions.NONE
                    else -> Suggestions.GOOGLE
                }
                userPreferences.searchSuggestionChoice = suggestionsProvider.index
                summaryUpdater.updateSummary(searchSuggestionChoiceToTitle(suggestionsProvider))
            }
            setPositiveButton(resources.getString(R.string.action_ok), null)
        }
    }

    companion object {
        private const val SETTINGS_LANGUAGE = "app_language"
        private const val SETTINGS_CUSTOM_LANGUAGE = "custom_language_xml"
        private const val SETTINGS_PROXY = "proxy"
        private const val SETTINGS_IMAGES = "cb_images"
        private const val SETTINGS_SAVEDATA = "savedata"
        private const val SETTINGS_JAVASCRIPT = "cb_javascript"
        private const val SETTINGS_COLOR_MODE = "cb_colormode"
        private const val SETTINGS_USER_AGENT = "agent"
        private const val SETTINGS_CHROMPATIBILITY = "chrompatibility_mode"
        private const val SETTINGS_DOWNLOAD = "download"
        private const val SETTINGS_HOME = "home"
        private const val SETTINGS_SEARCH_ENGINE = "search"
        private const val SETTINGS_SUGGESTIONS = "suggestions_choice"
    }
}
