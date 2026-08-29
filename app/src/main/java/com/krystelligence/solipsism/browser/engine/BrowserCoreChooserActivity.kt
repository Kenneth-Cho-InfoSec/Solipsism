package com.krystelligence.solipsism.browser.engine

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.widget.FrameLayout
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.GridLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.net.toUri
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.krystelligence.solipsism.AccentPalette
import com.krystelligence.solipsism.AppTheme
import com.krystelligence.solipsism.DefaultBrowserActivity
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.ThemeApplication
import com.krystelligence.solipsism.browser.ui.SolipsismRailPosition
import com.krystelligence.solipsism.browser.DonationPromptPreferences
import com.krystelligence.solipsism.device.ScreenSize
import com.krystelligence.solipsism.preference.UserPreferences
import com.krystelligence.solipsism.search.SearchEngineProvider
import com.krystelligence.solipsism.search.Suggestions
import com.krystelligence.solipsism.search.engine.DuckSearch

/** First-run setup flow and settings entry point for the global browser-core choice. */
class BrowserCoreChooserActivity : AppCompatActivity() {
    private lateinit var preferences: BrowserCorePreferences
    private lateinit var userPreferences: UserPreferences
    private lateinit var enginePackage: AntaresEnginePackage
    private lateinit var statusView: TextView
    private lateinit var continueButton: MaterialButton
    private lateinit var webViewCard: MaterialCardView
    private lateinit var antaresCard: MaterialCardView

    private var selectedCore = BrowserCore.WEBVIEW
    private var selectedAccent = AccentPalette.TEAL
    private var selectedTheme = AppTheme.LIGHT
    private var selectedSearchIndex = SearchEngineProvider.DEFAULT_SEARCH_ENGINE_INDEX
    private var currentStep = SetupStep.CORE
    private var launchBrowserAfterChoice = true

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeApplication.applySavedTheme(this)
        super.onCreate(savedInstanceState)
        preferences = BrowserCorePreferences(applicationContext)
        userPreferences = UserPreferences(
            getSharedPreferences(USER_PREFERENCES, MODE_PRIVATE),
            ScreenSize(applicationContext)
        )
        enginePackage = AntaresEnginePackage(applicationContext)
        launchBrowserAfterChoice = !intent.getBooleanExtra(EXTRA_MANAGE_ONLY, false)

        if (launchBrowserAfterChoice && preferences.onboardingComplete && selectionIsUsable()) {
            maybeWarnLegacyAntares { launchBrowser() }
            return
        }

        currentStep = savedInstanceState
            ?.getString(STATE_STEP)
            ?.let(SetupStep::valueOf)
            ?: if (launchBrowserAfterChoice) SetupStep.APPEARANCE else SetupStep.CORE
        selectedCore = savedInstanceState
            ?.getString(STATE_CORE)
            ?.let(BrowserCore::valueOf)
            ?: preferences.selectedCore
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = navigateBack()
            }
        )
        showCurrentStep()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_STEP, currentStep.name)
        outState.putString(STATE_CORE, selectedCore.name)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (currentStep == SetupStep.CORE && ::statusView.isInitialized) updateStatus()
    }

    private fun showCurrentStep() {
        when (currentStep) {
            SetupStep.APPEARANCE -> showAppearanceStep()
            SetupStep.RAIL -> showRailStep()
            SetupStep.CORE -> showCoreStep()
            SetupStep.SEARCH -> showSearchStep()
        }
    }

    private fun showAppearanceStep() {
        currentStep = SetupStep.APPEARANCE
        setContentView(R.layout.activity_onboarding_appearance)
        bindBackNavigation()
        val themeChoices = themeChoices()
        val themeField = findViewById<MaterialAutoCompleteTextView>(R.id.onboarding_theme)
        val systemAccent = findViewById<MaterialSwitch>(R.id.onboarding_match_system_accent)
        val accentGrid = findViewById<GridLayout>(R.id.onboarding_accent_grid)
        selectedAccent = AccentPalette.fromValue(userPreferences.accentPalette)
        selectedTheme = userPreferences.useTheme
        findViewById<View>(R.id.onboarding_brand_icon).setOnLongClickListener { icon ->
            icon.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            startAntaresQuickStart()
            true
        }

        themeField.setAdapter(
            UnfilteredDropdownAdapter(this, themeChoices.map(ThemeChoice::title))
        )
        themeField.setText(themeChoices.first { it.theme == selectedTheme }.title, false)
        themeField.setOnItemClickListener { _, _, position, _ ->
            val newTheme = themeChoices[position].theme
            if (newTheme == userPreferences.useTheme) return@setOnItemClickListener
            selectedTheme = newTheme
            if (selectedTheme == AppTheme.SYSTEM && systemAccent.isEnabled) {
                systemAccent.isChecked = true
            }
            userPreferences.useTheme = selectedTheme
            userPreferences.accentPalette = selectedAccent.ordinal
            userPreferences.matchSystemAccent =
                selectedTheme == AppTheme.SYSTEM || systemAccent.isChecked
            themeField.dismissDropDown()
            themeField.clearFocus()
            recreate()
        }
        systemAccent.isChecked = userPreferences.matchSystemAccent
        systemAccent.isEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        populateAccentGrid(accentGrid, systemAccent)

        findViewById<MaterialButton>(R.id.onboarding_appearance_continue).setOnClickListener {
            userPreferences.useTheme = selectedTheme
            userPreferences.accentPalette = selectedAccent.ordinal
            userPreferences.matchSystemAccent =
                selectedTheme == AppTheme.SYSTEM || systemAccent.isChecked
            currentStep = SetupStep.RAIL
            recreate()
        }
    }

    private fun populateAccentGrid(grid: GridLayout, systemAccent: MaterialSwitch) {
        grid.removeAllViews()
        AccentPalette.entries.forEach { palette ->
            val cell = FrameLayout(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = 64.dp
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }
                contentDescription = getString(
                    R.string.settings_accent_palette_name,
                    accentName(palette)
                )
                isClickable = true
                isFocusable = true
            }
            cell.addView(android.view.View(this).apply {
                layoutParams = FrameLayout.LayoutParams(44.dp, 44.dp, Gravity.CENTER)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(palette.previewColor)
                    setStroke(
                        if (palette == selectedAccent && !systemAccent.isChecked) 4.dp else 1.dp,
                        if (palette == selectedAccent && !systemAccent.isChecked) {
                            MaterialColors.getColor(cell, com.google.android.material.R.attr.colorOnSurface)
                        } else {
                            Color.TRANSPARENT
                        }
                    )
                }
            })
            cell.setOnClickListener {
                selectedAccent = palette
                systemAccent.isChecked = false
                populateAccentGrid(grid, systemAccent)
            }
            grid.addView(cell)
        }
        systemAccent.setOnCheckedChangeListener { _, _ -> populateAccentGrid(grid, systemAccent) }
    }

    private fun showRailStep() {
        currentStep = SetupStep.RAIL
        setContentView(R.layout.activity_onboarding_rail_position)
        bindBackNavigation()
        val railChoices = findViewById<RadioGroup>(R.id.onboarding_rail_choices)
        railChoices.check(
            when (userPreferences.solipsismRailPosition) {
                SolipsismRailPosition.LEFT -> R.id.onboarding_rail_left
                SolipsismRailPosition.RIGHT -> R.id.onboarding_rail_right
                SolipsismRailPosition.TOP -> R.id.onboarding_rail_top
                SolipsismRailPosition.BOTTOM -> R.id.onboarding_rail_bottom
            }
        )
        findViewById<MaterialButton>(R.id.onboarding_rail_continue).setOnClickListener {
            val position = when (railChoices.checkedRadioButtonId) {
                R.id.onboarding_rail_right -> SolipsismRailPosition.RIGHT
                R.id.onboarding_rail_top -> SolipsismRailPosition.TOP
                R.id.onboarding_rail_bottom -> SolipsismRailPosition.BOTTOM
                else -> SolipsismRailPosition.LEFT
            }
            userPreferences.solipsismRailPosition = position
            userPreferences.solipsismRailOnLeft = position != SolipsismRailPosition.RIGHT
            getSharedPreferences(DEVELOPER_PREFERENCES, MODE_PRIVATE)
                .edit()
                .putBoolean(EXPERIMENTAL_RAIL_LAYOUTS, position.isExperimental)
                .apply()
            currentStep = SetupStep.CORE
            showCoreStep()
        }
    }

    private fun showCoreStep() {
        currentStep = SetupStep.CORE
        setContentView(R.layout.activity_browser_core_chooser)
        bindBackNavigation()
        statusView = findViewById(R.id.browser_core_antares_status)
        continueButton = findViewById(R.id.browser_core_continue)
        webViewCard = findViewById(R.id.browser_core_webview_card)
        antaresCard = findViewById(R.id.browser_core_antares_card)

        webViewCard.setOnClickListener { selectCore(BrowserCore.WEBVIEW) }
        antaresCard.setOnClickListener { selectCore(BrowserCore.ANTARES) }
        selectCore(selectedCore)
        continueButton.setOnClickListener { confirmSelection() }
        updateStatus()
    }

    private fun confirmSelection() {
        if (selectedCore == BrowserCore.WEBVIEW) {
            if (!launchBrowserAfterChoice) {
                commitSelection(BrowserCore.WEBVIEW)
                return
            }
            currentStep = SetupStep.SEARCH
            showSearchStep()
            return
        }
        val status = enginePackage.status()
        if (!status.usable) {
            openAntaresListing()
            return
        }
        enforceDuckDuckGoForAntares()
        commitSelection(BrowserCore.ANTARES)
    }

    private fun selectCore(core: BrowserCore) {
        selectedCore = core
        val webViewSelected = core == BrowserCore.WEBVIEW
        webViewCard.isChecked = webViewSelected
        antaresCard.isChecked = !webViewSelected
        updateStatus()
    }

    private fun showSearchStep() {
        currentStep = SetupStep.SEARCH
        setContentView(R.layout.activity_onboarding_search_engine)
        bindBackNavigation()
        val choices = searchChoices()
        selectedSearchIndex = userPreferences.searchChoice
            .takeIf { saved -> choices.any { it.index == saved } }
            ?: SearchEngineProvider.DEFAULT_SEARCH_ENGINE_INDEX
        val searchField =
            findViewById<MaterialAutoCompleteTextView>(R.id.onboarding_search_engine)
        searchField.setAdapter(
            UnfilteredDropdownAdapter(this, choices.map(SearchChoice::title))
        )
        searchField.setText(choices.first { it.index == selectedSearchIndex }.title, false)
        searchField.setOnItemClickListener { _, _, position, _ ->
            selectedSearchIndex = choices[position].index
        }
        findViewById<MaterialButton>(R.id.onboarding_search_continue).setOnClickListener {
            userPreferences.searchChoice = selectedSearchIndex
            userPreferences.searchSuggestionChoice = suggestionForSearch(selectedSearchIndex).index
            commitSelection(BrowserCore.WEBVIEW)
        }
    }

    private fun enforceDuckDuckGoForAntares() {
        userPreferences.searchChoice = SearchEngineProvider.DEFAULT_SEARCH_ENGINE_INDEX
        userPreferences.searchUrl = DuckSearch().queryUrl
        userPreferences.searchSuggestionChoice = Suggestions.DUCK.index
        Toast.makeText(
            this,
            R.string.browser_core_antares_duckduckgo_message,
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * Hidden one-gesture path for experienced users who already have Antares installed. It resets
     * only choices offered by onboarding, never personal browsing data or privacy preferences.
     */
    private fun startAntaresQuickStart() {
        if (!enginePackage.status().usable) {
            openAntaresListing()
            return
        }
        userPreferences.useTheme = AppTheme.LIGHT
        userPreferences.accentPalette = AccentPalette.TEAL.ordinal
        userPreferences.matchSystemAccent = false
        userPreferences.solipsismRailPosition = SolipsismRailPosition.RIGHT
        userPreferences.solipsismRailOnLeft = false
        getSharedPreferences(DEVELOPER_PREFERENCES, MODE_PRIVATE)
            .edit()
            .putBoolean(EXPERIMENTAL_RAIL_LAYOUTS, false)
            .apply()
        enforceDuckDuckGoForAntares()
        getSharedPreferences(DonationPromptPreferences.FILE_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(DonationPromptPreferences.KEY_SHOWN, true)
            .apply()
        preferences.select(BrowserCore.ANTARES)
        launchBrowser(OnboardingStarterTabs.urls)
    }

    private fun commitSelection(core: BrowserCore) {
        if (launchBrowserAfterChoice) {
            preferences.select(core)
            launchBrowser()
        } else {
            sendBroadcast(
                Intent(BrowserCoreSwitchRequest.ACTION)
                    .setPackage(packageName)
                    .putExtra(BrowserCoreSwitchRequest.EXTRA_CORE, core.preferenceValue)
            )
            finish()
        }
    }

    private fun updateStatus() {
        val status = enginePackage.status()
        statusView.text = when {
            status.usable -> getString(
                R.string.browser_core_antares_ready,
                status.versionName.orEmpty()
            )
            !status.installed -> getString(R.string.browser_core_antares_not_installed)
            !status.platformSupported -> getString(R.string.browser_core_antares_unsupported)
            !status.trusted -> getString(R.string.browser_core_antares_not_trusted)
            else -> status.reason ?: getString(R.string.browser_core_antares_unavailable)
        }
        continueButton.setText(
            when {
                selectedCore == BrowserCore.ANTARES && !status.usable ->
                    R.string.browser_core_install_antares
                selectedCore == BrowserCore.WEBVIEW -> R.string.onboarding_next
                else -> R.string.browser_core_continue
            }
        )
    }

    private fun selectionIsUsable(): Boolean = when (preferences.selectedCore) {
        BrowserCore.WEBVIEW -> true
        BrowserCore.ANTARES -> enginePackage.status().usable
    }

    private fun openAntaresListing() {
        startActivity(
            Intent(
                Intent.ACTION_VIEW,
                "https://github.com/Kenneth-Cho-InfoSec/Antares/releases".toUri(),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT),
        )
    }

    private fun maybeWarnLegacyAntares(continueAction: () -> Unit) {
        if (preferences.selectedCore != BrowserCore.ANTARES) {
            continueAction()
            return
        }
        val status = enginePackage.status()
        if (!status.usable || !enginePackage.updateRecommended(status.versionName)) {
            continueAction()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.browser_core_update_antares_title)
            .setMessage(getString(R.string.browser_core_update_antares_message, status.versionName.orEmpty()))
            .setNegativeButton(R.string.browser_core_update_antares_later) { _, _ -> continueAction() }
            .setPositiveButton(R.string.browser_core_update_antares_download) { _, _ -> openAntaresListing() }
            .setOnCancelListener { continueAction() }
            .show()
    }

    private fun launchBrowser(starterUrls: ArrayList<String>? = null) {
        val forwarded = Intent(intent).apply {
            setClass(this@BrowserCoreChooserActivity, DefaultBrowserActivity::class.java)
            removeExtra(EXTRA_MANAGE_ONLY)
            starterUrls?.let { putStringArrayListExtra(OnboardingStarterTabs.EXTRA_URLS, it) }
            flags = flags and Intent.FLAG_ACTIVITY_NEW_TASK.inv()
        }
        startActivity(forwarded)
        finish()
    }

    private fun bindBackNavigation() {
        val root = findViewById<View>(R.id.onboarding_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            view.updatePadding(
                top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
        findViewById<MaterialToolbar>(R.id.onboarding_top_app_bar)
            .setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun navigateBack() {
        when (currentStep) {
            SetupStep.APPEARANCE -> finish()
            SetupStep.RAIL -> showAppearanceStep()
            SetupStep.CORE -> {
                if (launchBrowserAfterChoice) showRailStep() else finish()
            }
            SetupStep.SEARCH -> showCoreStep()
        }
    }

    private fun searchChoices(): List<SearchChoice> = listOf(
        SearchChoice(7, getString(R.string.onboarding_search_duckduckgo)),
        SearchChoice(1, getString(R.string.search_engine_google)),
        SearchChoice(2, getString(R.string.search_engine_ask)),
        SearchChoice(3, getString(R.string.search_engine_bing)),
        SearchChoice(4, getString(R.string.search_engine_yahoo)),
        SearchChoice(5, getString(R.string.search_engine_startpage)),
        SearchChoice(6, getString(R.string.search_engine_startpage_mobile)),
        SearchChoice(8, getString(R.string.search_engine_duckduckgo_lite)),
        SearchChoice(9, getString(R.string.search_engine_baidu)),
        SearchChoice(10, getString(R.string.search_engine_yandex)),
        SearchChoice(11, getString(R.string.search_engine_naver))
    )

    private fun themeChoices(): List<ThemeChoice> = listOf(
        ThemeChoice(AppTheme.LIGHT, getString(R.string.light_theme)),
        ThemeChoice(AppTheme.DARK, getString(R.string.dark_theme)),
        ThemeChoice(AppTheme.BLACK, getString(R.string.black_theme)),
        ThemeChoice(AppTheme.SYSTEM, getString(R.string.system_theme))
    )

    private fun suggestionForSearch(searchIndex: Int): Suggestions = when (searchIndex) {
        1 -> Suggestions.GOOGLE
        7, 8 -> Suggestions.DUCK
        9 -> Suggestions.BAIDU
        11 -> Suggestions.NAVER
        else -> Suggestions.NONE
    }

    private fun accentName(palette: AccentPalette): String = getString(
        when (palette) {
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

    private data class SearchChoice(val index: Int, val title: String)

    private data class ThemeChoice(val theme: AppTheme, val title: String)

    private class UnfilteredDropdownAdapter(
        context: Context,
        private val items: List<String>
    ) : ArrayAdapter<String>(
        context,
        com.google.android.material.R.layout.m3_auto_complete_simple_item,
        items
    ) {
        private val unfiltered = object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults =
                FilterResults().apply {
                    values = items
                    count = items.size
                }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                notifyDataSetChanged()
            }

            override fun convertResultToString(resultValue: Any?): CharSequence =
                resultValue?.toString().orEmpty()
        }

        override fun getFilter(): Filter = unfiltered
    }

    private enum class SetupStep {
        APPEARANCE,
        RAIL,
        CORE,
        SEARCH
    }

    companion object {
        const val EXTRA_MANAGE_ONLY = "manage_only"
        private const val USER_PREFERENCES = "settings"
        private const val DEVELOPER_PREFERENCES = "developer_settings"
        private const val EXPERIMENTAL_RAIL_LAYOUTS = "experimentalRailLayouts"
        private const val STATE_STEP = "setup_step"
        private const val STATE_CORE = "setup_core"
    }
}
