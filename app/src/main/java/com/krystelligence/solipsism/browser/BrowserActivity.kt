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
import android.os.Bundle
import android.os.Handler
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.FrameLayout
import android.widget.ImageView
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

    private val qrScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val scannedValue = result.data?.getStringExtra(QrScannerActivity.EXTRA_SCAN_RESULT)
        if (result.resultCode == RESULT_OK && scannedValue != null) {
            presenter.onSearch(scannedValue)
        }
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
            if (superCompact) 2.dp else 10.dp,
            if (superCompact) 30.dp else 42.dp,
            if (superCompact) 2.dp else 10.dp,
            if (superCompact) 16.dp else 28.dp
        )

        if (superCompact) {
            val railButtonSize = 26.dp
            val urlButtonSize = 24.dp
            val navButtonSize = 27.dp
            val addressRailWidth = 26.dp

            binding.homeButton.setSquareSize(railButtonSize)
            binding.tabCountView.setSquareSize(22.dp)
            binding.verticalUrlText?.updateLayoutParams<ViewGroup.LayoutParams> {
                width = 108.dp
            }
            binding.verticalUrlText?.textSize = 12f
            binding.settingsButton?.setSquareSize(urlButtonSize)
            binding.searchRefresh.setSquareSize(urlButtonSize)
            binding.actionBack.setSquareSize(navButtonSize)
            binding.actionForward.setSquareSize(navButtonSize)
            binding.actionHome.setSquareSize(navButtonSize)
            binding.actionAddBookmark.setSquareSize(navButtonSize)
            binding.toolbar.setSquareSize(navButtonSize)
            binding.toolbar.minimumHeight = navButtonSize

            (binding.verticalUrlText?.parent as? View)?.apply {
                updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    width = addressRailWidth
                    topMargin = 12.dp
                    bottomMargin = 12.dp
                }
                setPaddingRelative(1.dp, 5.dp, 1.dp, 5.dp)
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
        binding.findQuit.setOnClickListener { presenter.onFindDismiss() }

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
        binding.settingsButton?.setOnClickListener {
            presenter.onRefreshOrStopClick()
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        menuItemShare = menu.findItem(R.id.action_share)
        menuItemCopyLink = menu.findItem(R.id.action_copy)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
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
        }
        return super.onOptionsItemSelected(item)
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
        startActivityForResult(Intent.createChooser(intent, getString(R.string.title_file_chooser)), 1)
    }

    /**
     * @see BrowserContract.View.showCustomView
     */
    override fun showCustomView(view: View) {
        binding.contentFrame.addView(view)
    }

    /**
     * @see BrowserContract.View.hideCustomView
     */
    override fun hideCustomView() {
        val view = binding.contentFrame.getChildAt(binding.contentFrame.childCount - 1)
        if (view != null && view != binding.contentFrame && view != binding.progressView && view != binding.addressOverlay && view != binding.toolbarLayout && view != binding.findBar) {
            binding.contentFrame.removeView(view)
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
            DialogItem(title = R.string.dialog_delete_download) {
                presenter.onDownloadOptionClick(download, BrowserContract.DownloadOptionEvent.DELETE)
            },
            DialogItem(title = R.string.dialog_delete_all_downloads) {
                presenter.onDownloadOptionClick(download, BrowserContract.DownloadOptionEvent.DELETE_ALL)
            }
        )
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

    private fun showAddressOverlay() {
        binding.addressOverlay?.isVisible = true
        binding.search.requestFocus()
        inputMethodManager.showSoftInput(binding.search, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideAddressOverlay() {
        binding.addressOverlay?.isVisible = false
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

private const val SUPER_COMPACT_RAIL_WIDTH_DP = 30
private const val MIN_SOLIPSISM_RAIL_WIDTH_DP = SUPER_COMPACT_RAIL_WIDTH_DP
private const val MAX_SOLIPSISM_RAIL_WIDTH_DP = 96
private const val ADDRESS_OVERLAY_RAIL_GAP_DP = 14
private const val FIND_BAR_RAIL_GAP_DP = 14
