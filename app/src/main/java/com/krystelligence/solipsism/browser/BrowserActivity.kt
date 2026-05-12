package com.krystelligence.solipsism.browser

import com.krystelligence.solipsism.AppTheme
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.ThemableBrowserActivity
import com.krystelligence.solipsism.animation.AnimationUtils
import com.krystelligence.solipsism.browser.bookmark.BookmarkRecyclerViewAdapter
import com.krystelligence.solipsism.browser.color.ColorAnimator
import com.krystelligence.solipsism.browser.di.MainHandler
import com.krystelligence.solipsism.browser.di.injector
import com.krystelligence.solipsism.browser.image.ImageLoader
import com.krystelligence.solipsism.browser.keys.KeyEventAdapter
import com.krystelligence.solipsism.browser.menu.MenuItemAdapter
import com.krystelligence.solipsism.browser.menu.MenuSelection
import com.krystelligence.solipsism.browser.search.IntentExtractor
import com.krystelligence.solipsism.browser.search.SearchListener
import com.krystelligence.solipsism.browser.search.StyleRemovingTextWatcher
import com.krystelligence.solipsism.browser.tab.BottomDrawerTabRecyclerViewAdapter
import com.krystelligence.solipsism.browser.tab.DesktopTabRecyclerViewAdapter
import com.krystelligence.solipsism.browser.tab.DrawerTabRecyclerViewAdapter
import com.krystelligence.solipsism.browser.tab.TabPager
import com.krystelligence.solipsism.browser.tab.TabViewHolder
import com.krystelligence.solipsism.browser.tab.TabViewState
import com.krystelligence.solipsism.browser.theme.ThemeProvider
import com.krystelligence.solipsism.browser.ui.BookmarkConfiguration
import com.krystelligence.solipsism.browser.ui.TabConfiguration
import com.krystelligence.solipsism.browser.ui.UiConfiguration
import com.krystelligence.solipsism.browser.view.ViewDelegate
import com.krystelligence.solipsism.browser.view.delegates.BottomTabViewDelegate
import com.krystelligence.solipsism.browser.view.delegates.DesktopTabViewDelegate
import com.krystelligence.solipsism.browser.view.delegates.DrawerTabViewDelegate
import com.krystelligence.solipsism.browser.download.DownloadPermissionsHelper
import com.krystelligence.solipsism.browser.view.delegates.SolipsismRailViewDelegate
import com.krystelligence.solipsism.browser.view.targetUrl.LongPress
import com.krystelligence.solipsism.constant.HTTP
import com.krystelligence.solipsism.database.Bookmark
import com.krystelligence.solipsism.database.HistoryEntry
import com.krystelligence.solipsism.database.SearchSuggestion
import com.krystelligence.solipsism.database.WebPage
import com.krystelligence.solipsism.database.downloads.DownloadEntry
import com.krystelligence.solipsism.databinding.BrowserActivityBottomBinding
import com.krystelligence.solipsism.databinding.BrowserActivityDesktopBinding
import com.krystelligence.solipsism.databinding.BrowserActivityDrawerBinding
import com.krystelligence.solipsism.databinding.BrowserActivitySolipsismBinding
import com.krystelligence.solipsism.databinding.BrowserBottomTabsBinding
import com.krystelligence.solipsism.dialog.BrowserDialog
import com.krystelligence.solipsism.dialog.DialogItem
import com.krystelligence.solipsism.dialog.SolipsismDialogBuilder
import com.krystelligence.solipsism.extensions.color
import com.krystelligence.solipsism.extensions.drawable
import com.krystelligence.solipsism.extensions.resizeAndShow
import com.krystelligence.solipsism.extensions.takeIfInstance
import com.krystelligence.solipsism.extensions.tint
import com.krystelligence.solipsism.qr.QrScannerActivity
import com.krystelligence.solipsism.search.SuggestionsAdapter
import com.krystelligence.solipsism.ssl.SslCertificateInfo
import com.krystelligence.solipsism.ssl.createSslDrawableForState
import com.krystelligence.solipsism.ssl.showSslDialog as showSslCertificateDialog
import com.krystelligence.solipsism.utils.ProxyUtils
import com.krystelligence.solipsism.utils.value
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.annotation.SuppressLint
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.view.animation.PathInterpolator
import android.widget.AdapterView
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.MenuRes
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * The base browser activity that governs the browsing experience for both default and incognito
 * browsers.
 */
abstract class BrowserActivity : ThemableBrowserActivity(), BrowserContract.View {

    private lateinit var binding: ViewDelegate
    private lateinit var tabsAdapter: ListAdapter<TabViewState, TabViewHolder>
    private lateinit var bookmarksAdapter: BookmarkRecyclerViewAdapter
    private var activeRecyclerView: RecyclerView? = null
    private var customView: View? = null
    private var customViewOriginalOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var customViewHidSystemUi = false
    private var previousSystemUiVisibility = 0
    private var browserMenuPopup: PopupWindow? = null

    private var menuItemShare: MenuItem? = null
    private var menuItemCopyLink: MenuItem? = null

    @Inject internal lateinit var presenter: BrowserPresenter
    @Inject internal lateinit var imageLoader: ImageLoader
    @Inject internal lateinit var themeProvider: ThemeProvider
    @Inject internal lateinit var uiConfiguration: UiConfiguration
    @Inject internal lateinit var intentExtractor: IntentExtractor
    @Inject internal lateinit var downloadPermissionsHelper: DownloadPermissionsHelper
    @Inject internal lateinit var solipsismDialogBuilder: SolipsismDialogBuilder
    @Inject internal lateinit var tabPager: TabPager
    @Inject @MainHandler internal lateinit var mainHandler: Handler

    private val inputMethodManager: InputMethodManager by lazy {
        getSystemService(InputMethodManager::class.java)
    }

    private val expressiveSpatialInterpolator by lazy {
        PathInterpolator(0.2f, 0f, 0f, 1f)
    }

    private val expressiveEffectsInterpolator by lazy {
        PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
    }

    private val qrScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val scannedValue = result.data?.getStringExtra(QrScannerActivity.EXTRA_SCAN_RESULT)
        if (result.resultCode == RESULT_OK && scannedValue != null) {
            presenter.onSearch(scannedValue)
        }
    }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        presenter.onFileChooserResult(result)
    }

    private fun applySolipsismRailPreferences() {
        val railWidth = userPreferences.solipsismRailSize.coerceIn(
            MIN_SOLIPSISM_RAIL_WIDTH_DP,
            MAX_SOLIPSISM_RAIL_WIDTH_DP
        ).dp
        val railOnLeft = userPreferences.solipsismRailOnLeft
        val superCompact = userPreferences.solipsismRailSize <= SUPER_COMPACT_RAIL_WIDTH_DP

        binding.toolbarLayout.updateLayoutParams<FrameLayout.LayoutParams> {
            width = railWidth
            gravity = if (railOnLeft) Gravity.START else Gravity.END
        }
        binding.toolbarLayout.setPaddingRelative(
            if (superCompact) 1.dp else 10.dp,
            if (superCompact) 30.dp else 42.dp,
            if (superCompact) 1.dp else 10.dp,
            if (superCompact) 16.dp else 28.dp
        )
        binding.tabCountView.setShowCount(!superCompact)

        if (superCompact) {
            val railButtonSize = 28.dp
            val urlButtonSize = 27.dp
            val navButtonSize = 28.dp
            val addressRailWidth = 28.dp
            val iconPadding = 5.dp

            binding.homeButton.setSquareSize(railButtonSize)
            binding.tabCountView.setSquareSize(24.dp)
            binding.verticalUrlText?.updateLayoutParams<ViewGroup.LayoutParams> {
                width = 148.dp
            }
            binding.verticalUrlText?.textSize = 11.5f
            binding.settingsButton?.setSquareSize(urlButtonSize)
            binding.searchRefresh.setSquareSize(urlButtonSize)
            binding.actionBack.setSquareSize(navButtonSize)
            binding.actionForward.setSquareSize(navButtonSize)
            binding.actionHome.setSquareSize(navButtonSize)
            binding.actionAddBookmark.setSquareSize(navButtonSize)
            binding.toolbar.setSquareSize(navButtonSize)
            binding.toolbar.minimumHeight = navButtonSize
            binding.toolbar.overflowIcon = drawable(R.drawable.ic_action_more_vertical)?.also {
                it.tint(color(R.color.solipsism_rail_text))
            }
            binding.toolbar.contentInsetStartWithNavigation = 0
            binding.toolbar.setContentInsetsRelative(0, 0)
            listOfNotNull(
                binding.settingsButton,
                binding.searchRefresh,
                binding.actionBack,
                binding.actionForward,
                binding.actionHome,
                binding.actionAddBookmark
            ).forEach { it.setPadding(iconPadding, iconPadding, iconPadding, iconPadding) }

            (binding.verticalUrlText?.parent as? View)?.apply {
                updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    width = addressRailWidth
                    topMargin = 8.dp
                    bottomMargin = 8.dp
                }
                setPaddingRelative(1.dp, 4.dp, 1.dp, 4.dp)
            }
            (binding.actionBack.parent as? View)?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                width = addressRailWidth
            }
            listOf(
                binding.actionForward,
                binding.actionHome,
                binding.actionAddBookmark,
                binding.toolbar
            ).forEach { it.setTopMargin(3.dp) }
        }

        binding.contentFrame.applyRailMargin(railWidth, railOnLeft)
        binding.progressView.applyRailMargin(railWidth, railOnLeft)
        binding.addressOverlay?.applyRailMargin(
            railWidth = railWidth,
            railOnLeft = railOnLeft,
            oppositeMargin = resources.getDimensionPixelSize(R.dimen.chrome_outer_margin),
            extraRailMargin = ADDRESS_OVERLAY_RAIL_GAP_DP.dp
        )
        binding.findBar.applyRailMargin(
            railWidth = railWidth,
            railOnLeft = railOnLeft,
            oppositeMargin = resources.getDimensionPixelSize(R.dimen.chrome_outer_margin),
            extraRailMargin = FIND_BAR_RAIL_GAP_DP.dp
        )
    }

    private fun View.applyRailMargin(
        railWidth: Int,
        railOnLeft: Boolean,
        oppositeMargin: Int = 0,
        extraRailMargin: Int = 0
    ) {
        updateLayoutParams<ViewGroup.MarginLayoutParams> {
            val railMargin = railWidth + extraRailMargin
            marginStart = if (railOnLeft) railMargin else oppositeMargin
            marginEnd = if (railOnLeft) oppositeMargin else railMargin
        }
    }

    private fun View.setSquareSize(size: Int) {
        updateLayoutParams<ViewGroup.LayoutParams> {
            width = size
            height = size
        }
    }

    private fun View.setTopMargin(topMargin: Int) {
        updateLayoutParams<ViewGroup.MarginLayoutParams> {
            this.topMargin = topMargin
        }
    }

    private fun View.setStartMargin(startMargin: Int) {
        updateLayoutParams<ViewGroup.MarginLayoutParams> {
            marginStart = startMargin
        }
    }

    private inline fun <reified T : ViewGroup.LayoutParams> View.updateLayoutParams(
        block: T.() -> Unit
    ) {
        val params = layoutParams as? T ?: return
        params.block()
        layoutParams = params
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).roundToInt()

    protected abstract fun isIncognito(): Boolean

    @MenuRes
    protected abstract fun menu(): Int

    @DrawableRes
    protected abstract fun homeIcon(): Int

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tabConfiguration = TabConfiguration.SOLIPSISM
        val bottomTabsBinding = if (tabConfiguration == TabConfiguration.DRAWER_BOTTOM) {
            BrowserBottomTabsBinding.inflate(layoutInflater)
        } else {
            null
        }

        binding = when (tabConfiguration) {
            TabConfiguration.DESKTOP -> DesktopTabViewDelegate(
                BrowserActivityDesktopBinding.inflate(layoutInflater)
            )
            TabConfiguration.DRAWER_SIDE -> DrawerTabViewDelegate(
                BrowserActivityDrawerBinding.inflate(layoutInflater)
            )
            TabConfiguration.DRAWER_BOTTOM -> BottomTabViewDelegate(
                BrowserActivityBottomBinding.inflate(layoutInflater)
            )
            TabConfiguration.SOLIPSISM -> SolipsismRailViewDelegate(
                BrowserActivitySolipsismBinding.inflate(layoutInflater)
            )
        }

        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        applySolipsismRailPreferences()

        injector.browser2ComponentBuilder()
            .activity(this)
            .browserFrame(binding.contentFrame)
            .toolbarRoot(binding.uiLayout)
            .browserRoot(binding.browserLayoutContainer)
            .bottomTabsLayout(bottomTabsBinding)
            .toolbar(binding.toolbarLayout)
            .initialIntent(intent)
            .incognitoMode(isIncognito())
            .build()
            .inject(this)
        configureSolipsismOverflowMenu()

        if (uiConfiguration.tabConfiguration == TabConfiguration.DESKTOP) {
            tabsAdapter = DesktopTabRecyclerViewAdapter(
                context = this,
                onClick = presenter::onTabClick,
                onLongClick = presenter::onTabLongClick,
                onCloseClick = presenter::onTabClose
            )
            binding.desktopTabsList.adapter = tabsAdapter
            binding.desktopTabsList.layoutManager =
                LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
            binding.desktopTabsList.itemAnimator?.takeIfInstance<SimpleItemAnimator>()
                ?.supportsChangeAnimations = false
            binding.drawerTabsList.isVisible = false
            activeRecyclerView = binding.desktopTabsList
        } else {
            tabsAdapter = if (uiConfiguration.tabConfiguration == TabConfiguration.DRAWER_BOTTOM) {
                BottomDrawerTabRecyclerViewAdapter(
                    themeProvider = themeProvider,
                    onClick = presenter::onTabClick,
                    onLongClick = presenter::onTabLongClick,
                    onCloseClick = presenter::onTabClose,
                    onBackClick = { presenter.onBackClick() },
                    onForwardClick = { presenter.onForwardClick() },
                    onHomeClick = { presenter.onHomeClick() }
                )
            } else {
                DrawerTabRecyclerViewAdapter(
                    onClick = presenter::onTabClick,
                    onLongClick = presenter::onTabLongClick,
                    onCloseClick = presenter::onTabClose
                )
            }
            binding.drawerTabsList.adapter = tabsAdapter
            binding.drawerTabsList.layoutManager = LinearLayoutManager(this)
            binding.drawerTabsList.itemAnimator?.takeIfInstance<SimpleItemAnimator>()
                ?.supportsChangeAnimations = false
            binding.desktopTabsList.isVisible = false
            activeRecyclerView = binding.drawerTabsList

            if (uiConfiguration.tabConfiguration == TabConfiguration.DRAWER_SIDE || uiConfiguration.tabConfiguration == TabConfiguration.SOLIPSISM) {
                binding.drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
                    override fun onDrawerOpened(drawerView: View) {
                        if (drawerView == binding.tabDrawer) {
                            presenter.onTabDrawerMoved(true)
                        } else if (drawerView == binding.bookmarkDrawer) {
                            presenter.onBookmarkDrawerMoved(true)
                        }
                    }

                    override fun onDrawerClosed(drawerView: View) {
                        if (drawerView == binding.tabDrawer) {
                            presenter.onTabDrawerMoved(false)
                        } else if (drawerView == binding.bookmarkDrawer) {
                            presenter.onBookmarkDrawerMoved(false)
                        }
                    }
                })
            }
        }

        bookmarksAdapter = BookmarkRecyclerViewAdapter(
            onClick = presenter::onBookmarkClick,
            onLongClick = presenter::onBookmarkLongClick,
            imageLoader = imageLoader
        )
        binding.bookmarkListView.adapter = bookmarksAdapter
        binding.bookmarkListView.layoutManager = LinearLayoutManager(this)

        presenter.onViewAttached(BrowserStateAdapter(this))
        maybeShowFirstRunDonationDialog()

        val suggestionsAdapter = SuggestionsAdapter(this, isIncognito = isIncognito()).apply {
            onSuggestionInsertClick = {
                if (it is SearchSuggestion) {
                    binding.search.setText(it.title)
                    binding.search.setSelection(it.title.length)
                } else {
                    binding.search.setText(it.url)
                    binding.search.setSelection(it.url.length)
                }
            }
        }
        binding.search.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            binding.search.clearFocus()
            hideAddressOverlay()
            presenter.onSearchSuggestionClicked(suggestionsAdapter.getItem(position) as WebPage)
            inputMethodManager.hideSoftInputFromWindow(binding.root.windowToken, 0)
        }
        binding.search.setAdapter(suggestionsAdapter)
        val searchListener = SearchListener(
            onConfirm = {
                presenter.onSearch(binding.search.text.toString())
                hideAddressOverlay()
            },
            inputMethodManager = inputMethodManager
        )
        binding.search.setOnEditorActionListener(searchListener)
        binding.search.setOnKeyListener(searchListener)
        binding.search.addTextChangedListener(StyleRemovingTextWatcher())
        binding.search.setOnFocusChangeListener { _, hasFocus ->
            presenter.onSearchFocusChanged(hasFocus)
            binding.search.selectAll()
            if (hasFocus) {
                showAddressOverlay()
            } else {
                hideAddressOverlay()
            }
        }

        binding.findPrevious.setOnClickListener { presenter.onFindPrevious() }
        binding.findNext.setOnClickListener { presenter.onFindNext() }
        binding.findQuit.setOnClickListener {
            binding.findBar.isVisible = false
            binding.findQuery.clearFocus()
            inputMethodManager.hideSoftInputFromWindow(binding.findQuery.windowToken, 0)
            presenter.onFindDismiss()
        }
        binding.findQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                presenter.onFindInPage(s?.toString().orEmpty())
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
        listOfNotNull(
            binding.homeButton,
            binding.actionBack,
            binding.actionForward,
            binding.actionHome,
            binding.newTabButton,
            binding.searchRefresh,
            binding.searchQr,
            binding.actionAddBookmark,
            binding.tabHeaderButton,
            binding.bookmarkBackButton,
            binding.settingsButton,
            binding.verticalUrlText?.parent as? View
        ).forEach(::applyPhysicalPressFeedback)

        binding.homeButton.setOnClickListener { presenter.onTabCountViewClick() }
        binding.actionBack.setOnClickListener { presenter.onBackClick() }
        binding.actionForward.setOnClickListener { presenter.onForwardClick() }
        binding.actionHome.setOnClickListener { presenter.onHomeClick() }
        binding.newTabButton.setOnClickListener { presenter.onNewTabClick() }
        binding.newTabButton.setOnLongClickListener {
            presenter.onNewTabLongClick()
            true
        }
        binding.searchRefresh.setOnClickListener {
            if (uiConfiguration.tabConfiguration == TabConfiguration.SOLIPSISM) {
                presenter.onQrButtonClick()
            } else {
                presenter.onRefreshOrStopClick()
            }
        }
        binding.searchRefresh.setOnLongClickListener {
            if (uiConfiguration.tabConfiguration == TabConfiguration.SOLIPSISM) {
                presenter.onQrButtonLongClick()
                true
            } else {
                false
            }
        }
        binding.actionAddBookmark.setOnClickListener { presenter.onStarClick() }
        binding.actionPageTools.setOnClickListener { presenter.onToolsClick() }
        binding.tabHeaderButton.setOnClickListener { presenter.onTabMenuClick() }
        binding.bookmarkBackButton.setOnClickListener { presenter.onBookmarkMenuClick() }
        binding.searchSslStatus.setOnClickListener { presenter.onSslIconClick() }
        binding.verticalUrlText?.setOnClickListener { showAddressOverlay() }
        (binding.verticalUrlText?.parent as? View)?.setOnClickListener { showAddressOverlay() }
        installUrlRailGestures()
        binding.settingsButton?.setOnClickListener {
            presenter.onReloadClick()
        }

        binding.searchQr?.setOnClickListener { presenter.onQrButtonClick() }
        binding.searchQr?.setOnLongClickListener {
            presenter.onQrButtonLongClick()
            true
        }

        tabPager.longPressListener = presenter::onPageLongPress

        onBackPressedDispatcher.addCallback {
            presenter.onNavigateBack()
        }
    }

    override fun onNewIntent(intent: Intent) {
        intentExtractor.extractUrlFromIntent(intent)?.let(presenter::onNewAction)
        super.onNewIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        presenter.onViewDetached()
    }

    override fun onPause() {
        super.onPause()
        presenter.onViewHidden()
    }

    override fun onResume() {
        super.onResume()
        intentExtractor.extractUrlFromIntent(intent)?.let(presenter::onNewAction)
    }

    @SuppressLint("DiscouragedPrivateApi")
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (::uiConfiguration.isInitialized &&
            uiConfiguration.tabConfiguration == TabConfiguration.SOLIPSISM
        ) {
            return false
        }
        menuInflater.inflate(R.menu.main, menu)
        runCatching {
            menu.javaClass
                .getDeclaredMethod("setOptionalIconsVisible", Boolean::class.javaPrimitiveType)
                .invoke(menu, true)
        }
        menuItemShare = menu.findItem(R.id.action_share)
        menuItemCopyLink = menu.findItem(R.id.action_copy)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        if (::uiConfiguration.isInitialized &&
            uiConfiguration.tabConfiguration == TabConfiguration.SOLIPSISM
        ) {
            return false
        }
        menuItemShare?.isVisible = presenter.viewState.enableFullMenu
        menuItemCopyLink?.isVisible = presenter.viewState.enableFullMenu
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_new_tab -> presenter.onMenuClick(MenuSelection.NEW_TAB)
            R.id.action_incognito -> presenter.onMenuClick(MenuSelection.NEW_INCOGNITO_TAB)
            R.id.action_share -> presenter.onMenuClick(MenuSelection.SHARE)
            R.id.action_history -> presenter.onMenuClick(MenuSelection.HISTORY)
            R.id.action_downloads -> presenter.onMenuClick(MenuSelection.DOWNLOADS)
            R.id.action_find -> presenter.onMenuClick(MenuSelection.FIND)
            R.id.action_copy -> presenter.onMenuClick(MenuSelection.COPY_LINK)
            R.id.action_bookmarks -> presenter.onMenuClick(MenuSelection.BOOKMARKS)
            R.id.action_settings -> presenter.onMenuClick(MenuSelection.SETTINGS)
            R.id.action_add_to_homescreen -> presenter.onMenuClick(MenuSelection.ADD_TO_HOME)
            R.id.action_add_bookmark -> presenter.onMenuClick(MenuSelection.ADD_BOOKMARK)
        }
        return super.onOptionsItemSelected(item)
    }

    private fun configureSolipsismOverflowMenu() {
        if (uiConfiguration.tabConfiguration != TabConfiguration.SOLIPSISM) {
            return
        }
        binding.toolbar.menu.clear()
        binding.toolbar.navigationIcon = drawable(R.drawable.ic_action_more_vertical)?.also {
            it.tint(color(R.color.solipsism_rail_text))
        }
        binding.toolbar.setNavigationOnClickListener { showBrowserOverflowMenu() }
        binding.toolbar.setOnClickListener { showBrowserOverflowMenu() }
    }

    private fun showBrowserOverflowMenu() {
        browserMenuPopup?.dismiss()
        val menuView = buildBrowserOverflowMenuView()
        val popupWidth = resources.displayMetrics.widthPixels
            .coerceAtMost(BROWSER_MENU_MAX_WIDTH_DP.dp + (BROWSER_MENU_SCREEN_MARGIN_DP * 2).dp) -
            (BROWSER_MENU_SCREEN_MARGIN_DP * 2).dp
        browserMenuPopup = PopupWindow(
            menuView,
            popupWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            elevation = 18.dp.toFloat()
            isOutsideTouchable = true
            setAnimationStyle(android.R.style.Animation_Dialog)
        }

        val location = IntArray(2)
        binding.toolbar.getLocationOnScreen(location)
        val x = if (userPreferences.solipsismRailOnLeft) {
            BROWSER_MENU_SCREEN_MARGIN_DP.dp
        } else {
            resources.displayMetrics.widthPixels - popupWidth - BROWSER_MENU_SCREEN_MARGIN_DP.dp
        }
        val y = (location[1] - 12.dp).coerceAtLeast(BROWSER_MENU_SCREEN_MARGIN_DP.dp)
        browserMenuPopup?.showAtLocation(binding.root, Gravity.TOP or Gravity.START, x, y)
        menuView.alpha = 0f
        menuView.scaleX = 0.96f
        menuView.scaleY = 0.96f
        menuView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220L)
            .setInterpolator(expressiveSpatialInterpolator)
            .start()
    }

    private fun buildBrowserOverflowMenuView(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = drawable(R.drawable.browser_overflow_menu_background)
            clipToOutline = true
            elevation = 18.dp.toFloat()
            setPadding(9.dp, 9.dp, 9.dp, 9.dp)
        }

        container.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val quickActions = listOf(
                OverflowAction(R.drawable.ic_action_back, R.string.action_back, MenuSelection.BACK),
                OverflowAction(R.drawable.ic_action_star, R.string.action_add_bookmark, MenuSelection.ADD_BOOKMARK),
                OverflowAction(R.drawable.ic_settings_download, R.string.action_downloads, MenuSelection.DOWNLOADS),
                OverflowAction(R.drawable.ic_settings_info, R.string.action_site_info, null) {
                    presenter.onSslIconClick()
                },
                OverflowAction(R.drawable.ic_action_refresh, R.string.action_refresh, null) {
                    presenter.onReloadClick()
                }
            )
            quickActions.forEachIndexed { index, action ->
                addView(createQuickActionButton(action).apply {
                    if (index > 0) {
                        setStartMargin(6.dp)
                    }
                })
            }
        })

        container.addView(createMenuRow(R.drawable.ic_action_plus, R.string.action_new_tab, MenuSelection.NEW_TAB))
        container.addView(createMenuRow(R.drawable.incognito_mode, R.string.action_incognito, MenuSelection.NEW_INCOGNITO_TAB))
        container.addView(createMenuRow(R.drawable.ic_action_invert, R.string.action_feeling_lucky, MenuSelection.FEELING_LUCKY))
        container.addView(createMenuRow(R.drawable.ic_webpage, R.string.action_add_to_homescreen, MenuSelection.ADD_TO_HOME))
        container.addView(createMenuDivider())
        container.addView(createMenuRow(R.drawable.ic_history, R.string.action_history, MenuSelection.HISTORY))
        container.addView(createMenuRow(R.drawable.ic_settings_download, R.string.action_downloads, MenuSelection.DOWNLOADS))
        container.addView(createMenuDivider())
        container.addView(createMenuRow(R.drawable.ic_bookmark, R.string.action_bookmarks, MenuSelection.BOOKMARKS))
        container.addView(createMenuRow(R.drawable.ic_search, R.string.action_find, MenuSelection.FIND))
        container.addView(createMenuRow(R.drawable.ic_insert, R.string.action_copy, MenuSelection.COPY_LINK))
        container.addView(createMenuDivider())
        container.addView(createMenuRow(R.drawable.ic_action_settings, R.string.settings, MenuSelection.SETTINGS))

        return container
    }

    private fun createQuickActionButton(action: OverflowAction): ImageButton =
        ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(38.dp, 38.dp)
            background = drawable(R.drawable.browser_overflow_quick_button_background)
            contentDescription = getString(action.title)
            setPadding(8.dp, 8.dp, 8.dp, 8.dp)
            setImageResource(action.icon)
            setColorFilter(themeProvider.color(R.attr.colorOnSurface))
            scaleType = ImageView.ScaleType.CENTER
            setOnClickListener { runOverflowAction(action) }
        }

    private fun createMenuRow(
        icon: Int,
        title: Int,
        selection: MenuSelection
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = drawable(R.drawable.browser_overflow_menu_item_background)
        isClickable = true
        isFocusable = true
        setPadding(4.dp, 3.dp, 6.dp, 3.dp)
        minimumHeight = 38.dp
        addView(ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(30.dp, 30.dp)
            setPadding(6.dp, 6.dp, 6.dp, 6.dp)
            setImageResource(icon)
            setColorFilter(themeProvider.color(R.attr.colorOnSurfaceVariant))
        })
        addView(TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = 7.dp }
            text = getString(title)
            setTextColor(themeProvider.color(R.attr.colorOnSurface))
            textSize = 15f
            maxLines = 1
            includeFontPadding = true
        })
        setOnClickListener {
            browserMenuPopup?.dismiss()
            presenter.onMenuClick(selection)
        }
    }

    private fun createMenuDivider(): View =
        View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
                1.dp
            ).apply {
                setMargins(4.dp, 5.dp, 4.dp, 5.dp)
            }
            setBackgroundColor(themeProvider.color(R.attr.colorOutlineVariant))
            alpha = 0.7f
        }

    private fun runOverflowAction(action: OverflowAction) {
        browserMenuPopup?.dismiss()
        action.customAction?.invoke() ?: action.selection?.let(presenter::onMenuClick)
    }

    /**
     * @see BrowserContract.View.openBookmarkDrawer
     */
    override fun openBookmarkDrawer() {
        binding.drawerLayout.openDrawer(binding.bookmarkDrawer)
    }

    /**
     * @see BrowserContract.View.closeBookmarkDrawer
     */
    override fun closeBookmarkDrawer() {
        binding.drawerLayout.closeDrawer(binding.bookmarkDrawer)
    }

    /**
     * @see BrowserContract.View.openTabDrawer
     */
    override fun openTabDrawer() {
        binding.drawerLayout.openDrawer(binding.tabDrawer)
    }

    /**
     * @see BrowserContract.View.closeTabDrawer
     */
    override fun closeTabDrawer() {
        binding.drawerLayout.closeDrawer(binding.tabDrawer)
    }

    /**
     * @see BrowserContract.View.showToolbar
     */
    override fun showToolbar() {
        if (uiConfiguration.tabConfiguration != TabConfiguration.SOLIPSISM) {
            binding.uiLayout.animate().translationY(0f).setDuration(200).start()
        }
    }

    /**
     * @see BrowserContract.View.showToolsDialog
     */
    override fun showToolsDialog(areAdsAllowed: Boolean, shouldShowAdBlockOption: Boolean) {
        BrowserDialog.showWithIcons(
            this, getString(R.string.dialog_tools_title),
            DialogItem(
                title = R.string.dialog_toggle_desktop,
                icon = R.drawable.ic_action_desktop,
                onClick = presenter::onToggleDesktopAgent
            ),
            DialogItem(
                title = if (areAdsAllowed) R.string.dialog_adblock_disable_for_site else R.string.dialog_adblock_enable_for_site,
                icon = R.drawable.ic_block,
                isConditionMet = shouldShowAdBlockOption,
                onClick = presenter::onToggleAdBlocking
            )
        )
    }

    /**
     * @see BrowserContract.View.showLocalFileBlockedDialog
     */
    override fun showLocalFileBlockedDialog() {
        BrowserDialog.showPositiveNegativeDialog(
            this,
            R.string.title_warning,
            R.string.message_blocked_local,
            positiveButton = DialogItem(title = R.string.action_allow) { presenter.onConfirmOpenLocalFile(true) },
            negativeButton = DialogItem(title = R.string.action_dont_allow) { presenter.onConfirmOpenLocalFile(false) },
            onCancel = { presenter.onConfirmOpenLocalFile(false) }
        )
    }

    /**
     * @see BrowserContract.View.showFileChooser
     */
    override fun showFileChooser(intent: Intent) {
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        fileChooserLauncher.launch(Intent.createChooser(intent, getString(R.string.title_file_chooser)))
    }

    /**
     * @see BrowserContract.View.showCustomView
     */
    override fun showCustomView(view: View) {
        customView?.let(binding.root::removeView)
        customView = view
        customViewOriginalOrientation = requestedOrientation
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        view.setBackgroundColor(color(android.R.color.black))
        binding.root.addView(
            view,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        binding.root.bringChildToFront(view)
        binding.uiLayout.isVisible = false
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        view.requestLayout()
        customViewHidSystemUi = true
        previousSystemUiVisibility = window.decorView.systemUiVisibility
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = previousSystemUiVisibility or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    /**
     * @see BrowserContract.View.hideCustomView
     */
    override fun hideCustomView() {
        customView?.let(binding.root::removeView)
        customView = null
        binding.uiLayout.isVisible = true
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        requestedOrientation = customViewOriginalOrientation
        if (customViewHidSystemUi) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.decorView.systemUiVisibility = previousSystemUiVisibility
            customViewHidSystemUi = false
        }
    }

    /**
     * @see BrowserContract.View.clearSearchFocus
     */
    override fun clearSearchFocus() {
        binding.search.clearFocus()
    }

    override fun launchQrScanner() {
        qrScannerLauncher.launch(Intent(this, QrScannerActivity::class.java))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun installUrlRailGestures() {
        val rail = binding.verticalUrlText?.parent as? View ?: return
        var downY = 0f
        var downX = 0f
        val listener = View.OnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.rawY
                    downX = event.rawX
                    view.animate().cancel()
                    view.animate()
                        .scaleX(0.96f)
                        .scaleY(0.96f)
                        .setDuration(120L)
                        .setInterpolator(expressiveEffectsInterpolator)
                        .start()
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val dy = event.rawY - downY
                    val dx = event.rawX - downX
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(180L)
                        .setInterpolator(expressiveSpatialInterpolator)
                        .start()
                    if (kotlin.math.abs(dy) > URL_RAIL_SWIPE_THRESHOLD_DP.dp &&
                        kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.2f
                    ) {
                        val direction = if (dy < 0f) 1 else -1
                        if (presenter.onUrlBarSwipeTab(direction)) {
                            animateUrlRailTabSwitch(view, direction)
                            vibratePixelMini(view)
                        }
                    } else {
                        view.performClick()
                        showAddressOverlay()
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150L)
                        .setInterpolator(expressiveSpatialInterpolator)
                        .start()
                    true
                }

                else -> true
            }
        }
        rail.setOnTouchListener(listener)
        binding.verticalUrlText?.setOnTouchListener(listener)
    }

    private fun animateUrlRailTabSwitch(view: View, direction: Int) {
        val distance = 10.dp.toFloat() * direction
        view.animate().cancel()
        view.translationY = -distance
        view.alpha = 0.72f
        view.animate()
            .translationY(0f)
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(240L)
            .setInterpolator(expressiveSpatialInterpolator)
            .start()
    }

    private fun vibratePixelMini(view: View) {
        if (Build.MANUFACTURER.equals("Google", ignoreCase = true) &&
            Build.MODEL.contains("Pixel", ignoreCase = true)
        ) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    override fun renderState(viewState: BrowserViewState) {
        renderState(viewState.asPartial())
    }

    fun renderState(viewState: PartialBrowserViewState) {
        viewState.displayUrl?.let { displayUrl ->
            if (!binding.search.hasFocus()) {
                binding.search.setText(displayUrl)
            }
            binding.verticalUrlText?.text = displayUrl
        }
        viewState.progress?.let {
            binding.progressView.progress = it
            binding.progressView.isVisible = it in 1..99
        }
        viewState.isRefresh?.let {
            binding.settingsButton?.setImageResource(
                if (it) R.drawable.ic_action_refresh else R.drawable.ic_action_delete
            )
            if (uiConfiguration.tabConfiguration == TabConfiguration.SOLIPSISM) {
                binding.searchRefresh.setImageResource(R.drawable.ic_action_qr_code)
            } else {
                binding.searchRefresh.setImageResource(
                    if (it) R.drawable.ic_action_refresh else R.drawable.ic_action_delete
                )
            }
        }
        viewState.isBackEnabled?.let { binding.actionBack.isEnabled = it }
        viewState.isForwardEnabled?.let { binding.actionForward.isEnabled = it }
        viewState.isBookmarked?.let {
            binding.actionAddBookmark.isSelected = it
        }
        viewState.sslState?.let {
            binding.searchSslStatus.setImageDrawable(createSslDrawableForState(it))
            binding.searchSslStatus.updateVisibilityForDrawable()
        }
        viewState.bookmarks?.let(bookmarksAdapter::submitList)
        viewState.findInPage?.let { query ->
            val shouldShowFind = query.isNotEmpty() || binding.findBar.isVisible && binding.findQuery.hasFocus()
            binding.findBar.isVisible = shouldShowFind
            if (binding.findQuery.text.toString() != query) {
                binding.findQuery.setText(query)
            }
            if (!shouldShowFind) {
                binding.findQuery.clearFocus()
                inputMethodManager.hideSoftInputFromWindow(binding.findQuery.windowToken, 0)
            }
        }
        val suggestionsAdapter = binding.search.adapter as? SuggestionsAdapter
        suggestionsAdapter?.refreshBookmarks()
    }

    override fun renderTabs(tabs: List<TabViewState>) {
        tabsAdapter.submitList(tabs)
        if (uiConfiguration.tabConfiguration == TabConfiguration.DRAWER_BOTTOM ||
            uiConfiguration.tabConfiguration == TabConfiguration.SOLIPSISM) {
            binding.tabCountView.updateTabCount(tabs.size)
        }
    }

    override fun showAddBookmarkDialog(title: String, url: String, folders: List<String>) {
        solipsismDialogBuilder.showAddBookmarkDialog(this, title, url, folders, presenter::onBookmarkConfirmed)
    }

    override fun showBookmarkOptionsDialog(bookmark: Bookmark.Entry) {
        BrowserDialog.show(
            this, R.string.dialog_bookmark,
            DialogItem(title = R.string.dialog_open_new_tab) {
                presenter.onBookmarkOptionClick(bookmark, BrowserContract.BookmarkOptionEvent.NEW_TAB)
            },
            DialogItem(title = R.string.dialog_open_background_tab) {
                presenter.onBookmarkOptionClick(bookmark, BrowserContract.BookmarkOptionEvent.BACKGROUND_TAB)
            },
            DialogItem(title = R.string.dialog_open_incognito_tab, isConditionMet = !isIncognito()) {
                presenter.onBookmarkOptionClick(bookmark, BrowserContract.BookmarkOptionEvent.INCOGNITO_TAB)
            },
            DialogItem(title = R.string.action_share) {
                presenter.onBookmarkOptionClick(bookmark, BrowserContract.BookmarkOptionEvent.SHARE)
            },
            DialogItem(title = R.string.action_copy) {
                presenter.onBookmarkOptionClick(bookmark, BrowserContract.BookmarkOptionEvent.COPY_LINK)
            },
            DialogItem(title = R.string.dialog_remove_bookmark) {
                presenter.onBookmarkOptionClick(bookmark, BrowserContract.BookmarkOptionEvent.REMOVE)
            },
            DialogItem(title = R.string.action_edit) {
                presenter.onBookmarkOptionClick(bookmark, BrowserContract.BookmarkOptionEvent.EDIT)
            }
        )
    }

    override fun showEditBookmarkDialog(title: String, url: String, folder: String, folders: List<String>) {
        solipsismDialogBuilder.showEditBookmarkDialog(this, title, url, folder, folders, presenter::onBookmarkEditConfirmed)
    }

    override fun showFolderOptionsDialog(folder: Bookmark.Folder) {
        BrowserDialog.show(
            this, R.string.dialog_folder,
            DialogItem(title = R.string.action_rename) {
                presenter.onFolderOptionClick(folder, BrowserContract.FolderOptionEvent.RENAME)
            },
            DialogItem(title = R.string.dialog_remove_folder) {
                presenter.onFolderOptionClick(folder, BrowserContract.FolderOptionEvent.REMOVE)
            }
        )
    }

    override fun showEditFolderDialog(title: String) {
        BrowserDialog.showEditText(
            this, R.string.title_rename_folder, R.string.hint_title, title, R.string.action_ok
        ) { presenter.onBookmarkFolderRenameConfirmed(title, it) }
    }

    override fun showDownloadOptionsDialog(download: DownloadEntry) {
        BrowserDialog.show(
            this, download.title,
            DialogItem(title = R.string.action_donate) {
                openDonationPage()
            },
            DialogItem(title = R.string.dialog_delete_download) {
                presenter.onDownloadOptionClick(download, BrowserContract.DownloadOptionEvent.DELETE)
            },
            DialogItem(title = R.string.dialog_delete_all_downloads) {
                presenter.onDownloadOptionClick(download, BrowserContract.DownloadOptionEvent.DELETE_ALL)
            }
        )
    }

    private fun maybeShowFirstRunDonationDialog() {
        val preferences = getSharedPreferences(DONATION_PREFERENCES, MODE_PRIVATE)
        if (preferences.getBoolean(DONATION_PROMPT_SHOWN, false) || isIncognito()) {
            return
        }

        preferences.edit().putBoolean(DONATION_PROMPT_SHOWN, true).apply()
        mainHandler.post {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.donation_prompt_title)
                .setMessage(R.string.donation_prompt_message)
                .setPositiveButton(R.string.action_donate) { _, _ -> openDonationPage() }
                .setNegativeButton(R.string.action_not_now, null)
                .resizeAndShow()
        }
    }

    private fun openDonationPage() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(KO_FI_URL)))
    }

    override fun showHistoryOptionsDialog(historyEntry: HistoryEntry) {
        BrowserDialog.show(
            this, R.string.dialog_history_long_press,
            DialogItem(title = R.string.dialog_open_new_tab) {
                presenter.onHistoryOptionClick(historyEntry, BrowserContract.HistoryOptionEvent.NEW_TAB)
            },
            DialogItem(title = R.string.dialog_open_background_tab) {
                presenter.onHistoryOptionClick(historyEntry, BrowserContract.HistoryOptionEvent.BACKGROUND_TAB)
            },
            DialogItem(title = R.string.dialog_open_incognito_tab, isConditionMet = !isIncognito()) {
                presenter.onHistoryOptionClick(historyEntry, BrowserContract.HistoryOptionEvent.INCOGNITO_TAB)
            },
            DialogItem(title = R.string.action_share) {
                presenter.onHistoryOptionClick(historyEntry, BrowserContract.HistoryOptionEvent.SHARE)
            },
            DialogItem(title = R.string.action_copy) {
                presenter.onHistoryOptionClick(historyEntry, BrowserContract.HistoryOptionEvent.COPY_LINK)
            },
            DialogItem(title = R.string.dialog_remove_from_history) {
                presenter.onHistoryOptionClick(historyEntry, BrowserContract.HistoryOptionEvent.REMOVE)
            }
        )
    }

    override fun showFindInPageDialog() {
        binding.findBar.isVisible = true
        binding.findQuery.requestFocus()
        binding.findQuery.setSelection(binding.findQuery.text.length)
        inputMethodManager.showSoftInput(binding.findQuery, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun showLinkLongPressDialog(longPress: LongPress) {
        BrowserDialog.show(
            this, longPress.targetUrl,
            DialogItem(title = R.string.dialog_open_new_tab) {
                presenter.onLinkLongPressEvent(longPress, BrowserContract.LinkLongPressEvent.NEW_TAB)
            },
            DialogItem(title = R.string.dialog_open_background_tab) {
                presenter.onLinkLongPressEvent(longPress, BrowserContract.LinkLongPressEvent.BACKGROUND_TAB)
            },
            DialogItem(title = R.string.dialog_open_incognito_tab, isConditionMet = !isIncognito()) {
                presenter.onLinkLongPressEvent(longPress, BrowserContract.LinkLongPressEvent.INCOGNITO_TAB)
            },
            DialogItem(title = R.string.action_share) {
                presenter.onLinkLongPressEvent(longPress, BrowserContract.LinkLongPressEvent.SHARE)
            },
            DialogItem(title = R.string.dialog_copy_link) {
                presenter.onLinkLongPressEvent(longPress, BrowserContract.LinkLongPressEvent.COPY_LINK)
            }
        )
    }

    override fun showImageLongPressDialog(longPress: LongPress) {
        BrowserDialog.show(
            this, longPress.targetUrl,
            DialogItem(title = R.string.dialog_open_new_tab) {
                presenter.onImageLongPressEvent(
                    longPress,
                    BrowserContract.ImageLongPressEvent.NEW_TAB
                )
            },
            DialogItem(title = R.string.dialog_open_background_tab) {
                presenter.onImageLongPressEvent(
                    longPress,
                    BrowserContract.ImageLongPressEvent.BACKGROUND_TAB
                )
            },
            DialogItem(
                title = R.string.dialog_open_incognito_tab,
                isConditionMet = !isIncognito()
            ) {
                presenter.onImageLongPressEvent(
                    longPress,
                    BrowserContract.ImageLongPressEvent.INCOGNITO_TAB
                )
            },
            DialogItem(title = R.string.action_share) {
                presenter.onImageLongPressEvent(
                    longPress,
                    BrowserContract.ImageLongPressEvent.SHARE
                )
            },
            DialogItem(title = R.string.dialog_copy_link) {
                presenter.onImageLongPressEvent(
                    longPress,
                    BrowserContract.ImageLongPressEvent.COPY_LINK
                )
            },
            DialogItem(title = R.string.dialog_download_image) {
                presenter.onImageLongPressEvent(
                    longPress,
                    BrowserContract.ImageLongPressEvent.DOWNLOAD
                )
            })
    }

    /**
     * @see BrowserContract.View.showCloseBrowserDialog
     */
    override fun showCloseBrowserDialog(id: Int) {
        BrowserDialog.show(
            this, R.string.dialog_title_close_browser,
            DialogItem(title = R.string.close_tab) {
                presenter.onCloseBrowserEvent(id, BrowserContract.CloseTabEvent.CLOSE_CURRENT)
            },
            DialogItem(title = R.string.close_other_tabs) {
                presenter.onCloseBrowserEvent(id, BrowserContract.CloseTabEvent.CLOSE_OTHERS)
            },
            DialogItem(title = R.string.close_all_tabs, onClick = {
                presenter.onCloseBrowserEvent(id, BrowserContract.CloseTabEvent.CLOSE_ALL)
            })
        )
    }

    override fun showSslDialog(sslCertificateInfo: SslCertificateInfo) {
        showSslCertificateDialog(sslCertificateInfo)
    }

    fun clearAllHistoryFromHistoryPage() {
        presenter.onClearAllHistoryClick()
    }

    fun showHistoryDecoyModePrompt() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.history_decoy_mode_title)
            .setMessage(R.string.history_decoy_mode_message)
            .setPositiveButton(R.string.history_decoy_mode_start) { _, _ ->
                presenter.onHistoryDecoyModeConfirmed()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showAddressOverlay() {
        val overlay = binding.addressOverlay ?: return
        val searchContainer = binding.searchContainer
        val editUrl = presenter.currentUrlForEditing()
        overlay.animate().cancel()
        searchContainer.animate().cancel()
        if (!overlay.isVisible) {
            overlay.alpha = 0f
            overlay.translationY = -overlay.height.coerceAtLeast(72.dp).toFloat()
            overlay.scaleX = 0.98f
            overlay.scaleY = 0.98f
            searchContainer.elevation = 0f
            searchContainer.translationZ = 0f
            overlay.isVisible = true
        }
        if (editUrl.isNotBlank() && binding.search.text.toString() != editUrl) {
            binding.search.setText(editUrl)
            binding.search.setSelection(editUrl.length)
        }
        overlay.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(ADDRESS_OVERLAY_ENTER_DURATION_MS)
            .setInterpolator(expressiveSpatialInterpolator)
            .start()
        searchContainer.animate()
            .translationZ(18.dp.toFloat())
            .setStartDelay(ADDRESS_OVERLAY_SHADOW_DELAY_MS)
            .setDuration(ADDRESS_OVERLAY_SHADOW_DURATION_MS)
            .setInterpolator(expressiveSpatialInterpolator)
            .withEndAction {
                searchContainer.elevation = 18.dp.toFloat()
            }
            .start()
        binding.search.requestFocus()
        inputMethodManager.showSoftInput(binding.search, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideAddressOverlay() {
        val overlay = binding.addressOverlay ?: return
        val searchContainer = binding.searchContainer
        overlay.animate().cancel()
        searchContainer.animate().cancel()
        searchContainer.animate()
            .translationZ(0f)
            .setDuration(ADDRESS_OVERLAY_EXIT_DURATION_MS)
            .setInterpolator(expressiveEffectsInterpolator)
            .withEndAction {
                searchContainer.elevation = 0f
            }
            .start()
        overlay.animate()
            .alpha(0f)
            .translationY(-overlay.height.coerceAtLeast(72.dp).toFloat() * 0.35f)
            .scaleX(0.98f)
            .scaleY(0.98f)
            .setDuration(ADDRESS_OVERLAY_EXIT_DURATION_MS)
            .setInterpolator(expressiveEffectsInterpolator)
            .withEndAction {
                overlay.isVisible = false
                overlay.translationY = 0f
                overlay.scaleX = 1f
                overlay.scaleY = 1f
                searchContainer.translationZ = 0f
            }
            .start()
    }

    private fun applyPhysicalPressFeedback(view: View) {
        view.setOnTouchListener { touchedView, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> touchedView.animate()
                    .scaleX(0.94f)
                    .scaleY(0.94f)
                    .setDuration(PRESS_FEEDBACK_DURATION_MS)
                    .setInterpolator(expressiveEffectsInterpolator)
                    .start()

                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> touchedView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(RELEASE_FEEDBACK_DURATION_MS)
                    .setInterpolator(expressiveSpatialInterpolator)
                    .start()
            }
            false
        }
    }

    private fun ImageView.updateVisibilityForDrawable() {
        visibility = if (drawable == null) {
            View.GONE
        } else {
            View.VISIBLE
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            downloadPermissionsHelper.onRequestPermissionsResult(this, grantResults)
        }
    }
}

private data class OverflowAction(
    @DrawableRes val icon: Int,
    val title: Int,
    val selection: MenuSelection?,
    val customAction: (() -> Unit)? = null
)

private const val SUPER_COMPACT_RAIL_WIDTH_DP = 30
private const val MIN_SOLIPSISM_RAIL_WIDTH_DP = SUPER_COMPACT_RAIL_WIDTH_DP
private const val MAX_SOLIPSISM_RAIL_WIDTH_DP = 96
private const val ADDRESS_OVERLAY_RAIL_GAP_DP = 14
private const val FIND_BAR_RAIL_GAP_DP = 14
private const val URL_RAIL_SWIPE_THRESHOLD_DP = 34
private const val BROWSER_MENU_MAX_WIDTH_DP = 258
private const val BROWSER_MENU_SCREEN_MARGIN_DP = 14
private const val DONATION_PREFERENCES = "solipsism_donation"
private const val DONATION_PROMPT_SHOWN = "donationPromptShown"
private const val KO_FI_URL = "https://ko-fi.com/kennethchoinfosec"
private const val ADDRESS_OVERLAY_ENTER_DURATION_MS = 360L
private const val ADDRESS_OVERLAY_EXIT_DURATION_MS = 180L
private const val ADDRESS_OVERLAY_SHADOW_DELAY_MS = 90L
private const val ADDRESS_OVERLAY_SHADOW_DURATION_MS = 320L
private const val PRESS_FEEDBACK_DURATION_MS = 95L
private const val RELEASE_FEEDBACK_DURATION_MS = 260L
