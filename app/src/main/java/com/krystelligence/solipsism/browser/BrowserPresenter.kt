package com.krystelligence.solipsism.browser

import com.krystelligence.solipsism.adblock.allowlist.AllowListModel
import com.krystelligence.solipsism.browser.data.CookieAdministrator
import com.krystelligence.solipsism.browser.di.Browser2Scope
import com.krystelligence.solipsism.browser.di.DatabaseScheduler
import com.krystelligence.solipsism.browser.di.DiskScheduler
import com.krystelligence.solipsism.browser.di.IncognitoMode
import com.krystelligence.solipsism.browser.di.MainScheduler
import com.krystelligence.solipsism.browser.di.SuggestionsClient
import com.krystelligence.solipsism.browser.download.PendingDownload
import com.krystelligence.solipsism.browser.engine.BrowserCore
import com.krystelligence.solipsism.browser.engine.BrowserCorePreferences
import com.krystelligence.solipsism.browser.history.HistoryRecord
import com.krystelligence.solipsism.browser.history.DecoyTimeframe
import com.krystelligence.solipsism.browser.keys.KeyCombo
import com.krystelligence.solipsism.browser.menu.MenuSelection
import com.krystelligence.solipsism.browser.notification.TabCountNotifier
import com.krystelligence.solipsism.browser.search.SearchBoxModel
import com.krystelligence.solipsism.browser.tab.DownloadPageInitializer
import com.krystelligence.solipsism.browser.tab.HistoryPageInitializer
import com.krystelligence.solipsism.browser.tab.HomePageInitializer
import com.krystelligence.solipsism.browser.tab.NoOpInitializer
import com.krystelligence.solipsism.browser.tab.TabInitializer
import com.krystelligence.solipsism.browser.tab.TabModel
import com.krystelligence.solipsism.browser.tab.TabViewState
import com.krystelligence.solipsism.browser.tab.UrlInitializer
import com.krystelligence.solipsism.browser.ui.TabConfiguration
import com.krystelligence.solipsism.browser.ui.UiConfiguration
import com.krystelligence.solipsism.browser.view.targetUrl.LongPress
import com.krystelligence.solipsism.database.Bookmark
import com.krystelligence.solipsism.database.HistoryEntry
import com.krystelligence.solipsism.database.SearchSuggestion
import com.krystelligence.solipsism.database.WebPage
import com.krystelligence.solipsism.database.asFolder
import com.krystelligence.solipsism.database.bookmark.BookmarkRepository
import com.krystelligence.solipsism.database.bookmark.BookmarkSortOrder
import com.krystelligence.solipsism.database.downloads.DownloadEntry
import com.krystelligence.solipsism.database.downloads.DownloadsRepository
import com.krystelligence.solipsism.database.history.HistoryRepository
import com.krystelligence.solipsism.database.vault.VaultRepository
import com.krystelligence.solipsism.download.DecoyDownloadFactory
import com.krystelligence.solipsism.html.bookmark.BookmarkPageFactory
import com.krystelligence.solipsism.html.history.HistoryPageFactory
import com.krystelligence.solipsism.haptics.HapticFeedbackController
import com.krystelligence.solipsism.log.Logger
import com.krystelligence.solipsism.preference.UserPreferences
import com.krystelligence.solipsism.search.SearchEngineProvider
import com.krystelligence.solipsism.ssl.SslState
import com.krystelligence.solipsism.utils.Option
import com.krystelligence.solipsism.utils.QUERY_PLACE_HOLDER
import com.krystelligence.solipsism.utils.isBookmarkUrl
import com.krystelligence.solipsism.utils.isDownloadsUrl
import com.krystelligence.solipsism.utils.isHistoryUrl
import com.krystelligence.solipsism.utils.isStartPageUrl
import com.krystelligence.solipsism.utils.isSpecialUrl
import com.krystelligence.solipsism.utils.smartUrlFilter
import com.krystelligence.solipsism.utils.value
import androidx.activity.result.ActivityResult
import androidx.core.net.toUri
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.Observables
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.kotlin.toObservable
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.random.Random
import kotlin.system.exitProcess

/**
 * The monolithic (oops) presenter that governs the behavior of the browser UI and interactions by
 * the user for both default and incognito browsers. This presenter should live for the entire
 * duration of the browser activity, which itself should not be recreated during configuration
 * changes.
 */
@Browser2Scope
class BrowserPresenter @Inject constructor(
    private val model: BrowserContract.Model,
    private val navigator: BrowserContract.Navigator,
    private val bookmarkRepository: BookmarkRepository,
    private val downloadsRepository: DownloadsRepository,
    private val historyRepository: HistoryRepository,
    private val vaultRepository: VaultRepository,
    @DiskScheduler private val diskScheduler: Scheduler,
    @MainScheduler private val mainScheduler: Scheduler,
    @DatabaseScheduler private val databaseScheduler: Scheduler,
    private val historyRecord: HistoryRecord,
    private val bookmarkPageFactory: BookmarkPageFactory,
    private val homePageInitializer: HomePageInitializer,
    private val historyPageInitializer: HistoryPageInitializer,
    private val downloadPageInitializer: DownloadPageInitializer,
    private val searchBoxModel: SearchBoxModel,
    private val searchEngineProvider: SearchEngineProvider,
    private val uiConfiguration: UiConfiguration,
    private val historyPageFactory: HistoryPageFactory,
    private val allowListModel: AllowListModel,
    private val cookieAdministrator: CookieAdministrator,
    private val tabCountNotifier: TabCountNotifier,
    private val hapticFeedback: HapticFeedbackController,
    private val logger: Logger,
    private val userPreferences: UserPreferences,
    private val browserCorePreferences: BrowserCorePreferences,
    @SuggestionsClient private val okHttpClient: Single<OkHttpClient>,
    @IncognitoMode private val incognitoMode: Boolean
) {

    private var view: BrowserContract.View? = null
    private var appliedUserAgentSettings = currentUserAgentSettings()
    private var appliedContentBlockingSettings = currentContentBlockingSettings()
    var viewState: BrowserViewState = BrowserViewState(
        displayUrl = "",
        isRefresh = true,
        sslState = SslState.None,
        progress = 0,
        enableFullMenu = true,
        themeColor = Option.None,
        isForwardEnabled = false,
        isBackEnabled = false,
        bookmarks = emptyList(),
        isBookmarked = false,
        isBookmarkEnabled = true,
        isRootFolder = true,
        findInPage = ""
    )
    private var tabListState: List<TabViewState> = emptyList()
    private var currentTab: TabModel? = null
    private var currentFolder: Bookmark.Folder = Bookmark.Folder.Root
    private var isTabDrawerOpen = false
    private var isBookmarkDrawerOpen = false
    private var isAddressOverlayOpen = false
    private var isBrowserMenuOpen = false
    private var isBrowserChromeGestureActive = false
    private var isSearchViewFocused = false
    private var bookmarksNeedRefresh = false
    private var pendingAction: BrowserContract.Action.LoadUrl? = null
    private var isCustomViewShowing = false
    private var viewIsResumed = false
    private var pendingBrowserCoreSwitch: BrowserCore? = null

    private val compositeDisposable = CompositeDisposable()
    private val allTabsDisposable = CompositeDisposable()
    private var tabDisposable: CompositeDisposable = CompositeDisposable()

    /**
     * Call when the view is attached to the presenter.
     */
    fun onViewAttached(view: BrowserContract.View) {
        this.view = view
        view.updateState(viewState)

        cookieAdministrator.adjustCookieSettings()

        currentFolder = Bookmark.Folder.Root
        compositeDisposable += bookmarkRepository.bookmarksAndFolders(folder = Bookmark.Folder.Root)
            .subscribeOn(databaseScheduler)
            .observeOn(mainScheduler)
            .subscribe { list ->
                this.view?.updateState(viewState.copy(bookmarks = list, isRootFolder = true))
            }

        compositeDisposable += model.tabsListChanges()
            .observeOn(mainScheduler)
            .subscribe { list ->
                this.view?.updateTabs(list.map { it.asViewState() })

                allTabsDisposable.clear()
                list.subscribeToUpdates(allTabsDisposable)

                tabCountNotifier.notifyTabCountChange(list.size)
            }

        compositeDisposable += model.initializeTabs()
            .observeOn(mainScheduler)
            .switchIfEmpty(model.createTab(homePageInitializer).map(::listOf))
            .subscribe { list ->
                selectTab(model.selectTab(list.last().id))
            }
    }

    /**
     * Call when the view is detached from the presenter.
     */
    fun onViewDetached() {
        viewIsResumed = false
        view = null

        compositeDisposable.dispose()
        tabDisposable.dispose()
    }

    /**
     * Call when the view is hidden (i.e. the browser is sent to the background).
     */
    fun onViewHidden() {
        viewIsResumed = false
        bookmarksNeedRefresh = true
        model.markAllNonEphemeral()
        model.freeze()
    }

    /**
     * Refreshes bookmark-backed UI after returning from another activity, such as Settings.
     * Imported bookmarks are persisted by the settings activity while this activity is paused;
     * the drawer and generated HTML must be queried/generated again before they are displayed.
     */
    fun onViewResumed() {
        viewIsResumed = true
        pendingBrowserCoreSwitch?.let { core ->
            pendingBrowserCoreSwitch = null
            switchBrowserCore(core)
        }
        currentTab?.onHostResumed()
        val currentAgentSettings = currentUserAgentSettings()
        if (currentAgentSettings != appliedUserAgentSettings) {
            appliedUserAgentSettings = currentAgentSettings
            model.tabsList.forEach(TabModel::applyUserAgentPreference)
            currentTab?.reload()
        }
        val currentContentBlockingSettings = currentContentBlockingSettings()
        if (currentContentBlockingSettings != appliedContentBlockingSettings) {
            appliedContentBlockingSettings = currentContentBlockingSettings
            model.tabsList.forEach(TabModel::applyContentBlockingPreferences)
            currentTab?.reload()
        }
        if (!bookmarksNeedRefresh) return
        bookmarksNeedRefresh = false

        compositeDisposable += bookmarkRepository
            .bookmarksAndFolders(folder = currentFolder)
            .subscribeOn(databaseScheduler)
            .observeOn(mainScheduler)
            .subscribeBy { list ->
                view?.updateState(
                    viewState.copy(
                        bookmarks = list,
                        isRootFolder = currentFolder == Bookmark.Folder.Root
                    )
                )
            }

        when {
            currentTab?.url.isBookmarkUrl() -> rebuildBookmarkPageAndReload()
            currentTab?.url.isStartPageUrl() -> currentTab?.loadFromInitializer(homePageInitializer)
        }
    }

    private fun TabModel.asViewState(): TabViewState = TabViewState(
        id = id,
        icon = favicon,
        title = title,
        isSelected = isForeground,
        preview = preview
    )

    private fun List<TabViewState>.updateId(
        id: Int,
        map: (TabViewState) -> TabViewState
    ): List<TabViewState> = map {
        if (it.id == id) {
            map(it)
        } else {
            it
        }
    }

    private fun selectTab(tabModel: TabModel?, focusTab: Boolean = true) {
        if (currentTab == tabModel) {
            view?.closeTabDrawer()
            return
        }
        if (isCustomViewShowing) {
            view?.hideCustomView()
            currentTab?.hideCustomView()
            isCustomViewShowing = false
            updateBrowserChromeOverlayVisibility()
        }
        currentTab?.isForeground = false
        currentTab = tabModel
        currentTab?.isForeground = true
        updateBrowserChromeOverlayVisibility()

        view?.clearSearchFocus()

        val tab = tabModel ?: return run {
            view.updateState(
                viewState.copy(
                    displayUrl = searchBoxModel.getDisplayContent(
                        url = "",
                        title = null,
                        isLoading = false
                    ),
                    enableFullMenu = false,
                    isForwardEnabled = false,
                    isBackEnabled = false,
                    sslState = SslState.None,
                    progress = 100,
                    findInPage = ""
                )
            )
            view.updateTabs(tabListState.map { it.copy(isSelected = false) })
        }

        view?.showToolbar()
        if (focusTab) {
            view?.closeTabDrawer()
        }

        view.updateTabs(tabListState.map { it.copy(isSelected = it.id == tab.id) })

        tabDisposable.dispose()
        tabDisposable = CompositeDisposable()
        tabDisposable += Observable.combineLatest(
            tab.sslChanges().startWithItem(tab.sslState),
            tab.titleChanges().startWithItem(tab.title),
            tab.urlChanges().startWithItem(tab.url),
            tab.loadingProgress().startWithItem(tab.loadingProgress),
            tab.canGoBackChanges().startWithItem(tab.canGoBack()),
            tab.canGoForwardChanges().startWithItem(tab.canGoForward()),
            tab.urlChanges().startWithItem(tab.url).observeOn(diskScheduler)
                .flatMapSingle(bookmarkRepository::isBookmark).observeOn(mainScheduler),
            tab.urlChanges().startWithItem(tab.url).map(String::isSpecialUrl),
            tab.themeColorChanges().startWithItem(tab.themeColor)
        ) { sslState, title, url, progress, canGoBack, canGoForward, isBookmark, isSpecialUrl, themeColor ->
            viewState.copy(
                displayUrl = searchBoxModel.getDisplayContent(
                    url = url,
                    title = title,
                    isLoading = progress < 100
                ).takeIf { !isSearchViewFocused } ?: viewState.displayUrl,
                enableFullMenu = !url.isSpecialUrl(),
                themeColor = Option.Some(themeColor),
                isRefresh = (progress == 100).takeIf { !isSearchViewFocused }
                    ?: viewState.isRefresh,
                isForwardEnabled = canGoForward,
                isBackEnabled = canGoBack,
                sslState = sslState.takeIf { !isSearchViewFocused } ?: viewState.sslState,
                progress = progress,
                isBookmarked = isBookmark,
                isBookmarkEnabled = !isSpecialUrl,
                findInPage = tab.findQuery.orEmpty()
            )
        }.observeOn(mainScheduler)
            .subscribe { view.updateState(it) }

        tabDisposable += tab.downloadRequests()
            .subscribeOn(mainScheduler)
            .subscribeBy(onNext = navigator::download)

        tabDisposable += tab.urlChanges()
            .distinctUntilChanged()
            .subscribeOn(mainScheduler)
            .subscribeBy { url ->
                url.takeIf { !it.isSpecialUrl() && it.isNotBlank() }?.let {
                    historyRecord.visit(tab.title, it)
                }
                view?.showToolbar()
            }

        tabDisposable += tab.createWindowRequests()
            .subscribeOn(mainScheduler)
            .subscribeBy {
                createNewTabAndSelect(
                    tabInitializer = it,
                    shouldSelect = true,
                    tabType = TabModel.Type.POP_UP
                )
            }

        tabDisposable += tab.closeWindowRequests()
            .subscribeOn(mainScheduler)
            .subscribeBy { onTabClose(tabListState.indexOfCurrentTab()) }

        tabDisposable += tab.fileChooserRequests()
            .subscribeOn(mainScheduler)
            .subscribeBy { view?.showFileChooser(it) }

        tabDisposable += tab.showCustomViewRequests()
            .subscribeOn(mainScheduler)
            .subscribeBy {
                isCustomViewShowing = true
                updateBrowserChromeOverlayVisibility()
                view?.showCustomView(it)
            }

        tabDisposable += tab.hideCustomViewRequests()
            .subscribeOn(mainScheduler)
            .subscribeBy {
                view?.hideCustomView()
                isCustomViewShowing = false
                updateBrowserChromeOverlayVisibility()
            }

        tabDisposable += tab.hasFocusChanges()
            .subscribeOn(mainScheduler)
            .subscribeBy {
                if (it) {
                    view?.closeTabDrawer()
                }
            }
    }

    private fun List<TabModel>.subscribeToUpdates(compositeDisposable: CompositeDisposable) {
        forEach { tabModel ->
            compositeDisposable += Observables.combineLatest(
                tabModel.titleChanges().startWithItem(tabModel.title),
                tabModel.faviconChanges()
                    .startWithItem(Option.fromNullable(tabModel.favicon)),
                tabModel.previewChanges()
            ).distinctUntilChanged()
                .subscribeOn(mainScheduler)
                .observeOn(mainScheduler)
                .subscribeBy { (title, bitmap, _) ->
                    view.updateTabs(tabListState.updateId(tabModel.id) {
                        it.copy(title = title, icon = bitmap.value(), preview = tabModel.preview)
                    })
                }
        }
    }

    /**
     * Call when a new action is triggered, such as the user opening a new URL in the browser.
     */
    fun onNewAction(action: BrowserContract.Action) {
        when (action) {
            is BrowserContract.Action.LoadUrl -> if (action.url.isSpecialUrl()) {
                view?.showLocalFileBlockedDialog()
                pendingAction = action
            } else {
                createNewTabAndSelect(
                    tabInitializer = UrlInitializer(action.url),
                    shouldSelect = true,
                    tabType = TabModel.Type.EPHEMERAL
                )
            }

            BrowserContract.Action.Panic -> panicClean()
        }
    }

    /**
     * Call when the user confirms that they do or do not want to allow a local file to be opened
     * in the browser. This is a security gate to prevent malicious local files from being opened
     * in the browser without the user's knowledge.
     */
    fun onConfirmOpenLocalFile(allow: Boolean) {
        if (allow) {
            pendingAction?.let {
                createNewTabAndSelect(
                    tabInitializer = UrlInitializer(it.url),
                    shouldSelect = true,
                    tabType = TabModel.Type.EPHEMERAL
                )
            }
        }
        pendingAction = null
    }

    private fun panicClean() {
        createNewTabAndSelect(tabInitializer = NoOpInitializer(), shouldSelect = true)
        model.clean()

        historyPageFactory.deleteHistoryPage().subscribe()
        model.deleteAllTabs().subscribe()
        navigator.closeBrowser()

        // System exit needed in the case of receiving
        // the panic intent since finish() isn't completely
        // closing the browser
        exitProcess(1)
    }

    /**
     * Call when the user selects an option from the menu.
     */
    fun onMenuClick(menuSelection: MenuSelection) {
        when (menuSelection) {
            MenuSelection.NEW_TAB -> onNewTabClick()
            MenuSelection.NEW_INCOGNITO_TAB -> navigator.launchIncognito(url = null)
            MenuSelection.FEELING_LUCKY -> onFeelingLuckyClick()
            MenuSelection.SHARE -> currentTab?.url?.takeIf { !it.isSpecialUrl() }?.let {
                navigator.sharePage(url = it, title = currentTab?.title)
            }

            MenuSelection.HISTORY -> {
                createNewTabAndSelect(historyPageInitializer, shouldSelect = true)
            }

            MenuSelection.DOWNLOADS -> {
                createNewTabAndSelect(downloadPageInitializer, shouldSelect = true)
            }

            MenuSelection.FIND -> {
                view?.showFindInPageDialog()
            }
            MenuSelection.COPY_LINK -> currentTab?.url?.takeIf { !it.isSpecialUrl() }
                ?.let(navigator::copyPageLink)

            MenuSelection.ADD_TO_HOME -> currentTab?.url?.takeIf { !it.isSpecialUrl() }
                ?.let { addToHomeScreen() }

            MenuSelection.BOOKMARKS -> view?.openBookmarkDrawer()
            MenuSelection.ADD_BOOKMARK -> currentTab?.url?.takeIf { !it.isSpecialUrl() }
                ?.let { showAddBookmarkDialog() }

            MenuSelection.SETTINGS -> navigator.openSettings()
            MenuSelection.BACK -> onBackClick()
            MenuSelection.FORWARD -> onForwardClick()
        }
    }

    private fun addToHomeScreen() {
        currentTab?.let {
            navigator.addToHomeScreen(it.url, it.title, it.favicon)
        }
    }

    private fun onFeelingLuckyClick() {
        val query = currentTab?.title
            ?.takeIf { it.isNotBlank() && it != currentTab?.url }
            ?: currentTab?.url?.takeIf { !it.isSpecialUrl() && it.isNotBlank() }
            ?: DEFAULT_LUCKY_QUERY
        compositeDisposable += okHttpClient
            .flatMap { client ->
                Single.fromCallable {
                    val searchUrl = "$DUCKDUCKGO_HTML_SEARCH${URLEncoder.encode(query, "UTF-8")}"
                    val request = Request.Builder()
                        .url(searchUrl)
                        .header("User-Agent", LUCKY_USER_AGENT)
                        .build()
                    client.newCall(request).execute().use { response ->
                        response.body?.string()?.let(::parseDuckDuckGoResultUrls).orEmpty()
                    }
                }
            }
            .subscribeOn(diskScheduler)
            .observeOn(mainScheduler)
            .subscribeBy(
                onSuccess = { results ->
                    currentTab?.loadUrl(
                        results.take(MAX_LUCKY_RESULTS).randomOrNull()
                            ?: duckDuckGoLuckySearchUrl(query)
                    )
                },
                onError = {
                    currentTab?.loadUrl(duckDuckGoLuckySearchUrl(query))
                }
            )
    }

    private fun createNewTabAndSelect(
        tabInitializer: TabInitializer,
        shouldSelect: Boolean,
        tabType: TabModel.Type = TabModel.Type.NORMAL
    ) {
        compositeDisposable += model.createTab(tabInitializer, tabType = tabType)
            .observeOn(mainScheduler)
            .subscribe { tab ->
                if (shouldSelect) {
                    selectTab(model.selectTab(tab.id))
                }
            }
    }

    private fun List<TabViewState>.tabIndexForId(id: Int?): Int =
        indexOfFirst { it.id == id }

    private fun List<TabViewState>.indexOfCurrentTab(): Int = tabIndexForId(currentTab?.id)

    /**
     * Call when the user selects a combination of keys to perform a shortcut.
     */
    fun onKeyComboClick(keyCombo: KeyCombo) {
        when (keyCombo) {
            KeyCombo.CTRL_F -> view?.showFindInPageDialog()
            KeyCombo.CTRL_T -> onNewTabClick()
            KeyCombo.CTRL_W -> onTabClose(tabListState.indexOfCurrentTab())
            KeyCombo.CTRL_Q -> view?.showCloseBrowserDialog(tabListState.indexOfCurrentTab())
            KeyCombo.CTRL_R -> onRefreshOrStopClick()
            KeyCombo.CTRL_TAB -> TODO()
            KeyCombo.CTRL_SHIFT_TAB -> TODO()
            KeyCombo.SEARCH -> TODO()
            KeyCombo.ALT_0 -> onTabClick(0.coerceAtMost(tabListState.size - 1))
            KeyCombo.ALT_1 -> onTabClick(1.coerceAtMost(tabListState.size - 1))
            KeyCombo.ALT_2 -> onTabClick(2.coerceAtMost(tabListState.size - 1))
            KeyCombo.ALT_3 -> onTabClick(3.coerceAtMost(tabListState.size - 1))
            KeyCombo.ALT_4 -> onTabClick(4.coerceAtMost(tabListState.size - 1))
            KeyCombo.ALT_5 -> onTabClick(5.coerceAtMost(tabListState.size - 1))
            KeyCombo.ALT_6 -> onTabClick(6.coerceAtMost(tabListState.size - 1))
            KeyCombo.ALT_7 -> onTabClick(7.coerceAtMost(tabListState.size - 1))
            KeyCombo.ALT_8 -> onTabClick(8.coerceAtMost(tabListState.size - 1))
            KeyCombo.ALT_9 -> onTabClick(9.coerceAtMost(tabListState.size - 1))
        }
    }

    /**
     * Call when the user selects a tab to switch to at the provided [index].
     */
    fun onTabClick(index: Int) {
        hapticFeedback.tap(HapticFeedbackController.Category.TABS)
        selectTab(model.selectTab(tabListState[index].id))
    }

    fun currentUrlForEditing(): String =
        currentTab?.url?.takeIf { !it.isSpecialUrl() }.orEmpty()

    fun onUrlBarSwipeTab(direction: Int): Boolean {
        return previewUrlBarSwipeTab(direction)?.let {
            commitUrlBarSwipeTab(it.targetId)
            true
        } ?: false
    }

    fun previewUrlBarSwipeTab(direction: Int): UrlBarTabTransition? {
        if (tabListState.size < 2) return null
        val currentIndex = tabListState.indexOfCurrentTab()
        if (currentIndex !in tabListState.indices) return null
        val nextIndex = (currentIndex + direction).floorMod(tabListState.size)
        return UrlBarTabTransition(
            currentId = tabListState[currentIndex].id,
            targetId = tabListState[nextIndex].id,
            direction = direction
        )
    }

    fun commitUrlBarSwipeTab(targetId: Int) {
        selectTab(model.selectTab(targetId))
    }

    /**
     * Call when the user long presses on a tab at the provided [index].
     */
    fun onTabLongClick(index: Int) {
        view?.showCloseBrowserDialog(tabListState[index].id)
    }

    private fun <T> List<T>.nextSelected(removedIndex: Int): T? {
        val nextIndex = when {
            size > removedIndex + 1 -> removedIndex + 1
            removedIndex > 0 -> removedIndex - 1
            else -> -1
        }
        return if (nextIndex >= 0) {
            this[nextIndex]
        } else {
            null
        }
    }

    /**
     * Call when the user clicks on the close button for the tab at the provided [index]
     */
    fun onTabClose(index: Int) {
        if (index == -1) {
            // If the user clicks on close multiple times, the index may be -1 if the view is in the
            // process of being removed.
            return
        }
        hapticFeedback.tap(HapticFeedbackController.Category.TABS)
        val nextTab = tabListState.nextSelected(index)

        val currentTabId = currentTab?.id
        val needToSelectNextTab = tabListState[index].id == currentTabId

        compositeDisposable += model.deleteTab(tabListState[index].id)
            .observeOn(mainScheduler)
            .subscribe {
                if (needToSelectNextTab) {
                    nextTab?.id?.let {
                        selectTab(model.selectTab(it), focusTab = false)
                    } ?: run {
                        selectTab(tabModel = null)
                        navigator.closeBrowser()
                    }

                }
            }
    }

    /**
     * Call when the tab drawer is opened or closed.
     *
     * @param isOpen True if the drawer is now open, false if it is now closed.
     */
    fun onTabDrawerMoved(isOpen: Boolean) {
        isTabDrawerOpen = isOpen
        updateBrowserChromeOverlayVisibility()
    }

    /**
     * Call when the bookmark drawer is opened or closed.
     *
     * @param isOpen True if the drawer is now open, false if it is now closed.
     */
    fun onBookmarkDrawerMoved(isOpen: Boolean) {
        isBookmarkDrawerOpen = isOpen
        updateBrowserChromeOverlayVisibility()
    }

    /**
     * Call when the expanded horizontal address bar is shown or hidden.
     *
     * Antares renders into a remote SurfaceView. That surface normally needs to be above the host
     * window to receive input, but must temporarily move below Solipsism chrome that overlaps the
     * page. Keep the address overlay in the same state calculation as both drawers so one overlay
     * closing cannot accidentally cover another overlay that remains open.
     */
    fun onAddressOverlayMoved(isOpen: Boolean, onApplied: () -> Unit = {}) {
        isAddressOverlayOpen = isOpen
        updateBrowserChromeOverlayVisibility(onApplied)
    }

    fun onBrowserMenuMoved(isOpen: Boolean) {
        isBrowserMenuOpen = isOpen
        updateBrowserChromeOverlayVisibility()
    }

    fun onBrowserChromeGestureMoved(isActive: Boolean) {
        isBrowserChromeGestureActive = isActive
        updateBrowserChromeOverlayVisibility()
    }

    private fun updateBrowserChromeOverlayVisibility(onApplied: () -> Unit = {}) {
        val tab = currentTab
        if (tab == null) {
            onApplied()
            return
        }
        tab.setBrowserChromeOverlayVisible(
            visible = isTabDrawerOpen || isBookmarkDrawerOpen || isAddressOverlayOpen ||
                isBrowserMenuOpen || isBrowserChromeGestureActive || isCustomViewShowing,
            onApplied = onApplied,
        )
    }

    /**
     * Called when the user clicks on the device back button or swipes to go back. Differentiated
     * from [onBackClick] which is called when the user presses the browser's back button.
     */
    fun onNavigateBack() {
        when {
            isCustomViewShowing -> {
                view?.hideCustomView()
                currentTab?.hideCustomView()
                isCustomViewShowing = false
                updateBrowserChromeOverlayVisibility()
            }

            isTabDrawerOpen -> view?.closeTabDrawer()
            isBookmarkDrawerOpen -> if (currentFolder != Bookmark.Folder.Root) {
                onBookmarkMenuClick()
            } else {
                view?.closeBookmarkDrawer()
            }

            currentTab?.canGoBack() == true -> currentTab?.goBack()
            currentTab?.canGoBack() == false -> if (incognitoMode) {
                currentTab?.id?.let {
                    view?.showCloseBrowserDialog(it)
                }
            } else if (currentTab?.tabType in listOf(
                    TabModel.Type.EPHEMERAL,
                    TabModel.Type.POP_UP
                )
            ) {
                onTabClose(tabListState.indexOfCurrentTab())
            } else {
                onTabClose(tabListState.indexOfCurrentTab())
            }
        }
    }

    /**
     * Called when the user presses the browser's back button.
     */
    fun onBackClick() {
        if (currentTab?.canGoBack() == true) {
            currentTab?.goBack()
        }
    }

    /**
     * Called when the user presses the browser's forward button.
     */
    fun onForwardClick() {
        if (currentTab?.canGoForward() == true) {
            currentTab?.goForward()
        }
    }

    /**
     * Call when the user clicks on the home button.
     */
    fun onHomeClick() {
        currentTab?.loadFromInitializer(homePageInitializer)
    }

    /**
     * Call when the user clicks on the open new tab button.
     */
    fun onNewTabClick() {
        hapticFeedback.tap(HapticFeedbackController.Category.TABS)
        createNewTabAndSelect(homePageInitializer, shouldSelect = true)
    }

    /**
     * Call when the user long clicks on the new tab button, indicating that they want to re-open
     * the last closed tab.
     */
    fun onNewTabLongClick() {
        compositeDisposable += model.reopenTab()
            .observeOn(mainScheduler)
            .subscribeBy { tab ->
                selectTab(model.selectTab(tab.id))
            }
    }

    /**
     * Call when the user clicks on the refresh (or stop/delete) button that is located in the
     * search bar.
     */
    fun onRefreshOrStopClick() {
        if (isSearchViewFocused) {
            view?.renderState(viewState.copy(displayUrl = ""))
            return
        }
        if (currentTab?.loadingProgress != 100) {
            currentTab?.stopLoading()
        } else {
            reload()
        }
    }

    private fun Int.floorMod(other: Int): Int = ((this % other) + other) % other

    data class UrlBarTabTransition(
        val currentId: Int,
        val targetId: Int,
        val direction: Int
    )

    fun onReloadClick() {
        reload()
    }

    /**
     * Reload the current page once without JavaScript, without changing the saved preference.
     */
    fun onJavaScriptDisabledReload() {
        currentTab?.reloadWithJavaScriptDisabled()
    }

    private fun reload() {
        val currentUrl = currentTab?.url
        if (currentUrl?.isSpecialUrl() == true) {
            when {
                currentUrl.isBookmarkUrl() -> rebuildBookmarkPageAndReload()

                currentUrl.isDownloadsUrl() ->
                    currentTab?.loadFromInitializer(downloadPageInitializer)

                currentUrl.isHistoryUrl() ->
                    currentTab?.loadFromInitializer(historyPageInitializer)

                else -> currentTab?.reload()
            }
        } else {
            currentTab?.reload()
        }
    }

    private fun rebuildBookmarkPageAndReload() {
        compositeDisposable += bookmarkPageFactory.buildPage()
            .subscribeOn(diskScheduler)
            .observeOn(mainScheduler)
            .subscribeBy(
                onSuccess = { currentTab?.reload() },
                onError = { throwable ->
                    logger.log(TAG, "Unable to rebuild bookmark page", throwable)
                }
            )
    }

    /**
     * Call when the focus state changes for the search bar.
     *
     * @param isFocused True if the view is now focused, false otherwise.
     */
    fun onSearchFocusChanged(isFocused: Boolean) {
        isSearchViewFocused = isFocused
        if (isFocused) {
            view?.updateState(
                viewState.copy(
                    sslState = SslState.None,
                    isRefresh = false,
                    displayUrl = currentTab?.url?.takeIf { !it.isSpecialUrl() }.orEmpty()
                )
            )
        } else {
            view?.updateState(
                viewState.copy(
                    sslState = currentTab?.sslState ?: SslState.None,
                    isRefresh = (currentTab?.loadingProgress ?: 0) == 100,
                    displayUrl = searchBoxModel.getDisplayContent(
                        url = currentTab?.url.orEmpty(),
                        title = currentTab?.title.orEmpty(),
                        isLoading = (currentTab?.loadingProgress ?: 0) < 100
                    )
                )
            )
        }
    }

    /**
     * Call when the user submits a search [query] to the search bar. At this point the user has
     * provided intent to search and is no longer trying to manipulate the query.
     */
    fun onSearch(query: String) {
        if (query.isEmpty()) {
            return
        }
        currentTab?.stopLoading()
        val searchUrl = searchEngineProvider.provideSearchEngine().queryUrl + QUERY_PLACE_HOLDER
        val url = smartUrlFilter(query.trim(), true, searchUrl)
        view?.updateState(
            viewState.copy(
                displayUrl = searchBoxModel.getDisplayContent(
                    url = url,
                    title = currentTab?.title,
                    isLoading = (currentTab?.loadingProgress ?: 0) < 100
                )
            )
        )
        currentTab?.loadUrl(url)
    }

    /**
     * Call when the user enters a [query] to look for in the current web page.
     */
    fun onFindInPage(query: String) {
        if (query.isBlank()) {
            currentTab?.clearFindMatches()
        } else {
            currentTab?.find(query)
        }
        view?.updateState(viewState.copy(findInPage = query))
    }

    /**
     * Call when the user selects to move to the next highlighted word in the web page.
     */
    fun onFindNext() {
        currentTab?.findNext()
    }

    /**
     * Call when the user selects to move to the previous highlighted word in the web page.
     */
    fun onFindPrevious() {
        currentTab?.findPrevious()
    }

    /**
     * Call when the user chooses to dismiss the find in page UI component.
     */
    fun onFindDismiss() {
        currentTab?.clearFindMatches()
        view?.updateState(viewState.copy(findInPage = ""))
    }

    /** Extract page text for the system Text to Speech action without modifying the page. */
    fun onReadPageAloud() {
        currentTab?.readPageText { text -> view?.speakPageText(text) }
    }

    /**
     * Call when the user selects a search suggestion that was suggested by the search box.
     */
    fun onSearchSuggestionClicked(webPage: WebPage) {
        val url = when (webPage) {
            is HistoryEntry,
            is Bookmark.Entry -> webPage.url

            is SearchSuggestion -> webPage.title
            else -> null
        } ?: error("Other types cannot be search suggestions: $webPage")

        onSearch(url)
    }

    /**
     * Call when the user clicks on the SSL icon in the search box.
     */
    fun onSslIconClick() {
        currentTab?.sslCertificateInfo?.let {
            view?.showSslDialog(it)
        }
    }

    /**
     * Call when the user clicks on a bookmark from the bookmark list at the provided [index].
     */
    fun onBookmarkClick(index: Int) {
        when (val bookmark = viewState.bookmarks[index]) {
            is Bookmark.Entry -> {
                currentTab?.loadUrl(bookmark.url)
                view?.closeBookmarkDrawer()
            }

            Bookmark.Folder.Root -> error("Cannot click on root folder")
            is Bookmark.Folder.Entry -> {
                currentFolder = bookmark
                compositeDisposable += bookmarkRepository
                    .bookmarksAndFolders(folder = bookmark)
                    .subscribeOn(databaseScheduler)
                    .observeOn(mainScheduler)
                    .subscribe { list ->
                        view?.updateState(viewState.copy(bookmarks = list, isRootFolder = false))
                    }
            }
        }
    }

    private fun BookmarkRepository.bookmarksAndFolders(folder: Bookmark.Folder): Single<List<Bookmark>> =
        getBookmarksFromFolderSorted(
            folder = folder.title,
            sortOrder = userPreferences.bookmarkSortOrder
        )
            .concatWith(Single.defer {
                if (folder == Bookmark.Folder.Root) {
                    getFoldersSorted()
                } else {
                    Single.just(emptyList())
                }
            })
            .toList()
            .map(MutableList<List<Bookmark>>::flatten)

    /**
     * Call when the user long presses on a bookmark in the bookmark list at the provided [index].
     */
    fun onBookmarkLongClick(index: Int) {
        when (val item = viewState.bookmarks[index]) {
            is Bookmark.Entry -> view?.showBookmarkOptionsDialog(item)
            is Bookmark.Folder.Entry -> view?.showFolderOptionsDialog(item)
            Bookmark.Folder.Root -> Unit // Root is not clickable
        }
    }

    /**
     * Call when the user clicks on the page tools button.
     */
    fun onToolsClick() {
        val currentUrl = currentTab?.url ?: return
        view?.showToolsDialog(
            areAdsAllowed = allowListModel.isUrlAllowedAds(currentUrl),
            shouldShowAdBlockOption = !currentUrl.isSpecialUrl(),
            shouldShowElementPicker = currentUrl.startsWith("http://") || currentUrl.startsWith("https://")
        )
    }

    fun onUserAgentMenuClick() {
        view?.showUserAgentDialog(userPreferences.userAgentChoice)
    }

    fun onUserAgentChoiceSelected(choice: Int) {
        userPreferences.userAgentChoice = choice.coerceIn(1, 5)
        if (userPreferences.userAgentChoice == 4) {
            view?.showCustomUserAgentDialog(userPreferences.userAgentString)
            return
        }
        appliedUserAgentSettings = currentUserAgentSettings()
        currentTab?.applyUserAgentPreference()
        currentTab?.reload()
    }

    fun onCustomUserAgentEntered(value: String) {
        val custom = value.trim()
        if (custom.isEmpty()) return
        userPreferences.userAgentString = custom
        userPreferences.userAgentChoice = 4
        appliedUserAgentSettings = currentUserAgentSettings()
        currentTab?.applyUserAgentPreference()
        currentTab?.reload()
    }

    private fun currentUserAgentSettings(): String = buildString {
        append(userPreferences.userAgentChoice)
        append('|')
        append(userPreferences.chrompatibilityModeEnabled)
        append('|')
        append(userPreferences.userAgentString)
    }

    private fun currentContentBlockingSettings(): ContentBlockingSettings =
        ContentBlockingSettings(
            blockAds = userPreferences.adBlockEnabled,
            blockGifs = userPreferences.blockGifImagesEnabled,
            uBlockOrigin = userPreferences.uBlockOriginEnabled,
            cosmeticFilters = userPreferences.cosmeticFiltersEnabled,
        )

    private data class ContentBlockingSettings(
        val blockAds: Boolean,
        val blockGifs: Boolean,
        val uBlockOrigin: Boolean,
        val cosmeticFilters: Boolean,
    )

    fun onPickElement() {
        currentTab?.pickElement()
    }

    /**
     * Call when the user chooses to toggle the desktop user agent on/off.
     */
    fun onToggleDesktopAgent() {
        currentTab?.toggleDesktopAgent()
        currentTab?.reload()
    }

    /**
     * Call when the user chooses to toggle ad blocking on/off for the current web page.
     */
    fun onToggleAdBlocking() {
        val currentUrl = currentTab?.url ?: return
        if (allowListModel.isUrlAllowedAds(currentUrl)) {
            allowListModel.removeUrlFromAllowList(currentUrl)
        } else {
            allowListModel.addUrlToAllowList(currentUrl)
        }
        currentTab?.reload()
    }

    /**
     * Call when the user clicks on the star icon to add a bookmark for the current page or remove
     * the existing one.
     */
    fun onStarClick() {
        val url = currentTab?.url ?: return
        val title = currentTab?.title.orEmpty()
        if (url.isSpecialUrl()) {
            return
        }
        compositeDisposable += bookmarkRepository.isBookmark(url)
            .flatMapMaybe {
                if (it) {
                    bookmarkRepository.deleteBookmark(
                        Bookmark.Entry(
                            url = url,
                            title = title,
                            position = 0,
                            folder = Bookmark.Folder.Root
                        )
                    ).toMaybe()
                } else {
                    Maybe.empty()
                }
            }
            .doOnComplete(::showAddBookmarkDialog)
            .flatMapSingle { bookmarkRepository.bookmarksAndFolders(folder = currentFolder) }
            .subscribeOn(databaseScheduler)
            .observeOn(mainScheduler)
            .subscribeBy { list ->
                this.view?.updateState(viewState.copy(bookmarks = list))
            }
    }

    private fun showAddBookmarkDialog() {
        compositeDisposable += bookmarkRepository.getFolderNames()
            .subscribeOn(databaseScheduler)
            .observeOn(mainScheduler)
            .subscribeBy {
                view?.showAddBookmarkDialog(
                    title = currentTab?.title.orEmpty(),
                    url = currentTab?.url.orEmpty(),
                    folders = it
                )
            }
    }

    /**
     * Call when the user confirms the details for adding a bookmark.
     *
     * @param title The title of the bookmark.
     * @param url The URL of the bookmark.
     * @param folder The name of the folder the bookmark is in.
     */
    fun onBookmarkConfirmed(title: String, url: String, folder: String) {
        compositeDisposable += bookmarkRepository.addBookmarkIfNotExists(
            Bookmark.Entry(
                url = url,
                title = title.ifBlank { url },
                position = 0,
                folder = folder.asFolder()
            )
        ).flatMap {
            bookmarkPageFactory.buildPage()
                .onErrorReturnItem("")
                .flatMap { bookmarkRepository.bookmarksAndFolders(folder = currentFolder) }
        }
            .subscribeOn(databaseScheduler)
            .observeOn(mainScheduler)
            .subscribeBy { list ->
                hapticFeedback.success(HapticFeedbackController.Category.BOOKMARKS)
                this.view?.updateState(viewState.copy(bookmarks = list, isBookmarked = true))
                if (currentTab?.url?.isBookmarkUrl() == true) {
                    reload()
                }
            }
    }

    /**
     * Call when the user confirms the details when editing a bookmark.
     *
     * @param title The title of the bookmark.
     * @param url The URL of the bookmark.
     * @param folder The name of the folder the bookmark is in.
     */
    fun onBookmarkEditConfirmed(title: String, url: String, folder: String) {
        compositeDisposable += bookmarkRepository.editBookmark(
            oldBookmark = Bookmark.Entry(
                url = url,
                title = "",
                position = 0,
                folder = Bookmark.Folder.Root
            ),
            newBookmark = Bookmark.Entry(
                url = url,
                title = title,
                position = 0,
                folder = folder.asFolder()
            )
        ).andThen(bookmarkRepository.bookmarksAndFolders(folder = currentFolder))
            .subscribeOn(databaseScheduler)
            .observeOn(mainScheduler)
            .subscribeBy { list ->
                this.view?.updateState(viewState.copy(bookmarks = list))
                if (currentTab?.url?.isBookmarkUrl() == true) {
                    reload()
                }
            }
    }

    /**
     * Call when the user confirms a name change to an existing folder.
     *
     * @param oldTitle The previous title of the folder.
     * @param newTitle The new title of the folder.
     */
    fun onBookmarkFolderRenameConfirmed(oldTitle: String, newTitle: String) {
        compositeDisposable += bookmarkRepository.renameFolder(oldTitle, newTitle)
            .andThen(bookmarkRepository.bookmarksAndFolders(folder = currentFolder))
            .subscribeOn(databaseScheduler)
            .observeOn(mainScheduler)
            .subscribe { list ->
                this.view?.updateState(viewState.copy(bookmarks = list))
                if (currentTab?.url?.isBookmarkUrl() == true) {
                    reload()
                }
            }
    }

    /**
     * Call when the user clicks on a menu [option] for the provided [bookmark].
     */
    fun onBookmarkOptionClick(
        bookmark: Bookmark.Entry,
        option: BrowserContract.BookmarkOptionEvent
    ) {
        when (option) {
            BrowserContract.BookmarkOptionEvent.NEW_TAB ->
                createNewTabAndSelect(UrlInitializer(bookmark.url), shouldSelect = true)

            BrowserContract.BookmarkOptionEvent.BACKGROUND_TAB ->
                createNewTabAndSelect(UrlInitializer(bookmark.url), shouldSelect = false)

            BrowserContract.BookmarkOptionEvent.INCOGNITO_TAB -> navigator.launchIncognito(bookmark.url)
            BrowserContract.BookmarkOptionEvent.SHARE ->
                navigator.sharePage(url = bookmark.url, title = bookmark.title)

            BrowserContract.BookmarkOptionEvent.COPY_LINK ->
                navigator.copyPageLink(bookmark.url)

            BrowserContract.BookmarkOptionEvent.REMOVE ->
                compositeDisposable += bookmarkRepository.deleteBookmark(bookmark)
                    .flatMap { bookmarkRepository.bookmarksAndFolders(folder = currentFolder) }
                    .subscribeOn(databaseScheduler)
                    .observeOn(mainScheduler)
                    .subscribe { list ->
                        hapticFeedback.success(HapticFeedbackController.Category.BOOKMARKS)
                        view?.updateState(viewState.copy(bookmarks = list))
                        if (currentTab?.url?.isBookmarkUrl() == true) {
                            reload()
                        }
                    }

            BrowserContract.BookmarkOptionEvent.EDIT ->
                compositeDisposable += bookmarkRepository.getFolderNames()
                    .subscribeOn(databaseScheduler)
                    .observeOn(mainScheduler)
                    .subscribeBy { folders ->
                        view?.showEditBookmarkDialog(
                            bookmark.title,
                            bookmark.url,
                            bookmark.folder.title,
                            folders
                        )
                    }
        }
    }

    fun onCookieManager() {
        currentTab?.url
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?.let { view?.showCookieManager(it) }
    }

    fun onScreenshotClick() {
        captureScreenshot(attempt = 0)
    }

    fun onBrowserCoreSelected(core: BrowserCore) {
        if (!viewIsResumed) {
            // Debug Settings and the chooser are separate activities. Constructing and selecting
            // remote SurfaceViews while the browser window is covered races its surface/input
            // lifecycle and can leave the migrated tab black until another relaunch.
            pendingBrowserCoreSwitch = core
            return
        }
        switchBrowserCore(core)
    }

    /** True when browser chrome must be presented above Antares' remote SurfaceView window. */
    fun isUsingAntaresCore(): Boolean = browserCorePreferences.selectedCore == BrowserCore.ANTARES

    private fun switchBrowserCore(core: BrowserCore) {
        val selectedIndex = currentTab?.let(model.tabsList::indexOf)?.takeIf { it >= 0 }
        compositeDisposable += model.switchCore(core)
            .observeOn(mainScheduler)
            .subscribeBy(
                onComplete = {
                    val tab = selectedIndex?.let(model.tabsList::getOrNull) ?: model.tabsList.firstOrNull()
                    tab?.let {
                        selectTab(model.selectTab(it.id))
                        it.onHostResumed()
                    }
                },
                onError = { error ->
                    android.util.Log.e(
                        "BrowserCoreSwitch",
                        "Unable to switch browser core to $core",
                        error,
                    )
                    view?.showBrowserCoreSwitchFailed()
                },
            )
    }

    private fun captureScreenshot(attempt: Int) {
        currentTab?.captureVisiblePage()?.let { bitmap ->
            view?.showScreenshot(bitmap)
            return
        }
        if (attempt < MAX_SCREENSHOT_CAPTURE_RETRIES) {
            mainScheduler.scheduleDirect(
                { captureScreenshot(attempt + 1) },
                SCREENSHOT_CAPTURE_RETRY_DELAY_MS,
                TimeUnit.MILLISECONDS
            )
        } else {
            view?.showScreenshotCaptureFailed()
        }
    }

    /**
     * Call when the user clicks on a menu [option] for the provided [folder].
     */
    fun onFolderOptionClick(folder: Bookmark.Folder, option: BrowserContract.FolderOptionEvent) {
        when (option) {
            BrowserContract.FolderOptionEvent.RENAME -> view?.showEditFolderDialog(folder.title)
            BrowserContract.FolderOptionEvent.REMOVE ->
                compositeDisposable += bookmarkRepository.deleteFolder(folder.title)
                    .andThen(bookmarkRepository.bookmarksAndFolders(folder = currentFolder))
                    .subscribeOn(databaseScheduler)
                    .observeOn(mainScheduler)
                    .subscribe { list ->
                        view?.updateState(viewState.copy(bookmarks = list))
                        if (currentTab?.url?.isBookmarkUrl() == true) {
                            reload()
                            currentTab?.goBack()
                        }
                    }
        }
    }

    /**
     * Call when the user clicks on a menu [option] for the provided [download] entry.
     */
    fun onDownloadOptionClick(
        download: DownloadEntry,
        option: BrowserContract.DownloadOptionEvent
    ) {
        when (option) {
            BrowserContract.DownloadOptionEvent.DELETE ->
                compositeDisposable += downloadsRepository.deleteAllDownloads()
                    .subscribeOn(databaseScheduler)
                    .observeOn(mainScheduler)
                    .subscribeBy {
                        if (currentTab?.url?.isDownloadsUrl() == true) {
                            reload()
                        }
                    }

            BrowserContract.DownloadOptionEvent.DELETE_ALL ->
                compositeDisposable += downloadsRepository.deleteDownload(download.url)
                    .subscribeOn(databaseScheduler)
                    .observeOn(mainScheduler)
                    .subscribeBy {
                        if (currentTab?.url?.isDownloadsUrl() == true) {
                            reload()
                        }
                    }
        }
    }

    /**
     * Call when the user clicks on a menu [option] for the provided [historyEntry].
     */
    fun onHistoryOptionClick(
        historyEntry: HistoryEntry,
        option: BrowserContract.HistoryOptionEvent
    ) {
        when (option) {
            BrowserContract.HistoryOptionEvent.NEW_TAB ->
                createNewTabAndSelect(UrlInitializer(historyEntry.url), shouldSelect = true)

            BrowserContract.HistoryOptionEvent.BACKGROUND_TAB ->
                createNewTabAndSelect(UrlInitializer(historyEntry.url), shouldSelect = false)

            BrowserContract.HistoryOptionEvent.INCOGNITO_TAB ->
                navigator.launchIncognito(historyEntry.url)

            BrowserContract.HistoryOptionEvent.SHARE ->
                navigator.sharePage(url = historyEntry.url, title = historyEntry.title)

            BrowserContract.HistoryOptionEvent.COPY_LINK -> navigator.copyPageLink(historyEntry.url)
            BrowserContract.HistoryOptionEvent.REMOVE ->
                compositeDisposable += historyRepository.deleteHistoryEntry(historyEntry.url)
                    .subscribeOn(databaseScheduler)
                    .observeOn(mainScheduler)
                    .subscribeBy {
                        if (currentTab?.url?.isHistoryUrl() == true) {
                            reload()
                        }
                    }
        }
    }

    fun onClearAllHistoryClick() {
        compositeDisposable += historyRepository.deleteHistory()
            .andThen(historyPageFactory.deleteHistoryPage())
            .subscribeOn(databaseScheduler)
            .observeOn(mainScheduler)
            .subscribeBy {
                if (currentTab?.url?.isHistoryUrl() == true) {
                    reload()
                }
            }
    }

    fun onClearAllDownloadsClick() {
        compositeDisposable += downloadsRepository.deleteAllDownloads()
            .subscribeOn(databaseScheduler)
            .observeOn(mainScheduler)
            .subscribeBy {
                if (currentTab?.url?.isDownloadsUrl() == true) reload()
            }
    }

    fun onDownloadDecoyModeConfirmed() {
        compositeDisposable += downloadsRepository
            .replaceWithDecoyDownloads(DecoyDownloadFactory.create(count = 8))
            .subscribeOn(databaseScheduler)
            .observeOn(mainScheduler)
            .subscribeBy {
                if (currentTab?.url?.isDownloadsUrl() == true) reload()
            }
    }

    fun onHistoryDecoyModeConfirmed(timeframe: DecoyTimeframe) {
        val now = System.currentTimeMillis()
        val startTime = when (timeframe) {
            DecoyTimeframe.FOUR_HOURS -> now - 4 * 60 * 60 * 1000L
            DecoyTimeframe.FORTY_EIGHT_HOURS -> now - 48 * 60 * 60 * 1000L
            DecoyTimeframe.ALL_TIME -> 0L
        }
        val entries = createDecoyHistoryEntries(startTime, now)
        compositeDisposable += historyRepository.replaceRecentHistory(startTime, entries)
            .andThen(historyPageFactory.deleteHistoryPage())
            .subscribeOn(databaseScheduler)
            .observeOn(mainScheduler)
            .subscribeBy {
                if (currentTab?.url?.isHistoryUrl() == true) {
                    reload()
                }
            }
    }

    /**
     * Call when the user clicks on the tab count button (or home button in desktop mode, or
     * incognito icon in incognito mode).
     */
    fun onTabCountViewClick(drawerIsOpen: Boolean? = null) {
        if (uiConfiguration.tabConfiguration == TabConfiguration.DRAWER_SIDE) {
            view?.openTabDrawer()
        } else if (uiConfiguration.tabConfiguration == TabConfiguration.DRAWER_BOTTOM ||
            uiConfiguration.tabConfiguration == TabConfiguration.SOLIPSISM) {
            if (drawerIsOpen ?: isTabDrawerOpen) {
                view?.closeTabDrawer()
            } else {
                view?.openTabDrawer()
            }
        } else {
            currentTab?.loadFromInitializer(homePageInitializer)
        }
    }

    /**
     * Call when the user clicks on the tab menu located in the tab drawer.
     */
    fun onTabMenuClick() {
        currentTab?.let {
            view?.showCloseBrowserDialog(it.id)
        }
    }

    /**
     * Call when the user clicks on the bookmark menu (star or back arrow) located in the bookmark
     * drawer.
     */
    fun onBookmarkMenuClick() {
        if (currentFolder != Bookmark.Folder.Root) {
            currentFolder = Bookmark.Folder.Root
            compositeDisposable += bookmarkRepository
                .bookmarksAndFolders(folder = Bookmark.Folder.Root)
                .subscribeOn(databaseScheduler)
                .observeOn(mainScheduler)
                .subscribeBy { list ->
                    view?.updateState(viewState.copy(bookmarks = list, isRootFolder = true))
                }
        }
    }

    /**
     * Call when the user long presses anywhere on the web page with the provided tab [id].
     */
    fun onPageLongPress(id: Int, longPress: LongPress) {
        val pageUrl = model.tabsList.find { it.id == id }?.url
        if (pageUrl?.isSpecialUrl() == true) {
            val url = longPress.targetUrl ?: return
            if (pageUrl.isBookmarkUrl()) {
                if (url.isBookmarkUrl()) {
                    val filename = requireNotNull(longPress.targetUrl.toUri().lastPathSegment) {
                        "Last segment should always exist for bookmark file"
                    }
                    val folderTitle = filename.substring(
                        0,
                        filename.length - BookmarkPageFactory.FILENAME.length - 1
                    )
                    view?.showFolderOptionsDialog(folderTitle.asFolder())
                } else {
                    compositeDisposable += bookmarkRepository.findBookmarkForUrl(url)
                        .subscribeOn(databaseScheduler)
                        .observeOn(mainScheduler)
                        .subscribeBy {
                            view?.showBookmarkOptionsDialog(it)
                        }
                }
            } else if (pageUrl.isDownloadsUrl()) {
                compositeDisposable += downloadsRepository.findDownloadForUrl(url)
                    .subscribeOn(databaseScheduler)
                    .observeOn(mainScheduler)
                    .subscribeBy {
                        view?.showDownloadOptionsDialog(it)
                    }
            } else if (pageUrl.isHistoryUrl()) {
                compositeDisposable += historyRepository.findHistoryEntriesContaining(url)
                    .subscribeOn(databaseScheduler)
                    .observeOn(mainScheduler)
                    .subscribeBy { entries ->
                        entries.firstOrNull()?.let {
                            view?.showHistoryOptionsDialog(it)
                        } ?: view?.showHistoryOptionsDialog(HistoryEntry(url = url, title = ""))
                    }

            }
        } else {
            when (longPress.hitCategory) {
                LongPress.Category.IMAGE -> view?.showImageLongPressDialog(longPress)
                LongPress.Category.LINK -> view?.showLinkLongPressDialog(longPress)
                LongPress.Category.UNKNOWN -> Unit // Do nothing
            }
        }
    }

    /**
     * Call when the user selects an option from the close browser menu that can be invoked by long
     * pressing on individual tabs.
     */
    fun onCloseBrowserEvent(id: Int, closeTabEvent: BrowserContract.CloseTabEvent) {
        when (closeTabEvent) {
            BrowserContract.CloseTabEvent.CLOSE_CURRENT ->
                onTabClose(tabListState.tabIndexForId(id))

            BrowserContract.CloseTabEvent.CLOSE_OTHERS -> model.tabsList
                .filter { it.id != id }
                .toObservable()
                .flatMapCompletable { model.deleteTab(it.id) }
                .subscribeOn(mainScheduler)
                .subscribe()

            BrowserContract.CloseTabEvent.CLOSE_ALL ->
                compositeDisposable += model.deleteAllTabs().subscribeOn(mainScheduler)
                    .subscribeBy(onComplete = navigator::closeBrowser)
        }
    }

    /**
     * Call when the user long presses on a link within the web page and selects what they want to
     * do with that link.
     */
    fun onLinkLongPressEvent(
        longPress: LongPress,
        linkLongPressEvent: BrowserContract.LinkLongPressEvent
    ) {
        when (linkLongPressEvent) {
            BrowserContract.LinkLongPressEvent.NEW_TAB ->
                longPress.targetUrl?.let {
                    createNewTabAndSelect(
                        UrlInitializer(it),
                        shouldSelect = true
                    )
                }

            BrowserContract.LinkLongPressEvent.BACKGROUND_TAB ->
                longPress.targetUrl?.let {
                    createNewTabAndSelect(
                        UrlInitializer(it),
                        shouldSelect = false
                    )
                }

            BrowserContract.LinkLongPressEvent.INCOGNITO_TAB -> longPress.targetUrl?.let(navigator::launchIncognito)
            BrowserContract.LinkLongPressEvent.SHARE ->
                longPress.targetUrl?.let { navigator.sharePage(url = it, title = null) }

            BrowserContract.LinkLongPressEvent.COPY_LINK ->
                longPress.targetUrl?.let(navigator::copyPageLink)
        }
    }

    /**
     * Call when the user long presses on an image within the web page and selects what they want to
     * do with that image.
     */
    fun onImageLongPressEvent(
        longPress: LongPress,
        imageLongPressEvent: BrowserContract.ImageLongPressEvent
    ) {
        when (imageLongPressEvent) {
            BrowserContract.ImageLongPressEvent.NEW_TAB ->
                longPress.targetUrl?.let {
                    createNewTabAndSelect(
                        UrlInitializer(it),
                        shouldSelect = true
                    )
                }

            BrowserContract.ImageLongPressEvent.BACKGROUND_TAB ->
                longPress.targetUrl?.let {
                    createNewTabAndSelect(
                        UrlInitializer(it),
                        shouldSelect = false
                    )
                }

            BrowserContract.ImageLongPressEvent.INCOGNITO_TAB -> longPress.targetUrl?.let(navigator::launchIncognito)
            BrowserContract.ImageLongPressEvent.SHARE ->
                longPress.targetUrl?.let { navigator.sharePage(url = it, title = null) }

            BrowserContract.ImageLongPressEvent.COPY_LINK ->
                longPress.targetUrl?.let(navigator::copyPageLink)

            BrowserContract.ImageLongPressEvent.DOWNLOAD -> navigator.download(
                PendingDownload(
                    url = longPress.hitUrl.orEmpty(),
                    userAgent = null,
                    contentDisposition = "attachment",
                    mimeType = null,
                    contentLength = 0,
                    origin = currentTab?.url
                )
            )
        }
    }

    /**
     * Call when the user has selected a file from the file chooser to upload.
     */
    fun onFileChooserResult(activityResult: ActivityResult) {
        currentTab?.handleFileChooserResult(activityResult)
    }

    /**
     * Call when the user clicks on the QR button.
     */
    fun onQrButtonClick() {
        view?.launchQrScanner()
    }

    /**
     * Call when the user long presses on the QR button.
     */
    fun onQrButtonLongClick() {
        currentTab?.url?.takeIf { !it.isSpecialUrl() }?.let(navigator::showQrCode)
    }

    fun onVaultButtonClick() {
        val tab = currentTab
        if (tab == null) {
            view?.showVaultSaveFailed()
            return
        }
        val url = tab.url
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            view?.showVaultSaveFailed()
            return
        }
        compositeDisposable += vaultRepository.savePage(url, tab.title)
            .subscribeOn(databaseScheduler)
            .observeOn(mainScheduler)
            .subscribe(
                { view?.showVaultSaved() },
                { view?.showVaultSaveFailed() }
            )
    }

    fun onVaultButtonLongClick() {
        view?.openVault()
    }

    private fun createDecoyHistoryEntries(startTime: Long, endTime: Long): List<HistoryEntry> {
        val random = Random(System.currentTimeMillis())
        var visitedAt = startTime + random.nextLong(4 * 60 * 1000L, 14 * 60 * 1000L)
        val entries = mutableListOf<HistoryEntry>()
        val windowHours = (endTime - startTime) / (60 * 60 * 1000L)
        val maxJourneys = when {
            windowHours <= 4 -> DECOY_JOURNEYS.shuffled(random).take(random.nextInt(2, 4))
            windowHours <= 48 -> DECOY_JOURNEYS.shuffled(random) + DECOY_JOURNEYS.shuffled(random)
            else -> {
                // All time: cycle through journeys enough to fill the window
                val repeats = 6
                (1..repeats).flatMap { DECOY_JOURNEYS.shuffled(random) }
            }
        }

        for (journey in maxJourneys) {
            for (step in journey.steps) {
                if (visitedAt >= endTime) {
                    return entries
                }
                entries += HistoryEntry(
                    url = step.url,
                    title = step.title,
                    lastTimeVisited = visitedAt
                )
                visitedAt += random.nextLong(step.minDelayMinutes, step.maxDelayMinutes + 1) * 60 * 1000L
            }
            visitedAt += random.nextLong(12, 35) * 60 * 1000L
        }
        return entries
    }

    private data class DecoyStep(
        val title: String,
        val url: String,
        val minDelayMinutes: Long = 4,
        val maxDelayMinutes: Long = 18
    )

    private data class DecoyJourney(
        val steps: List<DecoyStep>
    )

    private fun parseDuckDuckGoResultUrls(html: String): List<String> =
        Jsoup.parse(html)
            .select("a.result__a[href], a.result-link[href], a[data-testid=result-title-a][href]")
            .mapNotNull { element -> normalizeDuckDuckGoResultUrl(element.attr("href")) }
            .distinct()
            .take(MAX_LUCKY_RESULTS)

    private fun duckDuckGoLuckySearchUrl(query: String): String =
        "$DUCKDUCKGO_SEARCH${URLEncoder.encode("\\$query", "UTF-8")}"

    private fun normalizeDuckDuckGoResultUrl(rawHref: String): String? {
        val href = when {
            rawHref.startsWith("//") -> "https:$rawHref"
            rawHref.startsWith("/") -> "https://duckduckgo.com$rawHref"
            else -> rawHref
        }
        val uri = href.toUri()
        val redirectedUrl = uri.getQueryParameter("uddg")
        val decodedUrl = redirectedUrl?.let { URLDecoder.decode(it, "UTF-8") } ?: href
        return decodedUrl.takeIf {
            (it.startsWith("http://") || it.startsWith("https://")) &&
                !it.contains("duckduckgo.com/y.js") &&
                !it.contains("duckduckgo.com/html")
        }
    }

    private fun BrowserContract.View?.updateState(state: BrowserViewState) {
        viewState = state
        this?.renderState(viewState)
    }

    private fun BrowserContract.View?.updateTabs(tabs: List<TabViewState>) {
        tabListState = tabs
        this?.renderTabs(tabListState)
    }

    companion object {
        private const val MAX_SCREENSHOT_CAPTURE_RETRIES = 3
        private const val SCREENSHOT_CAPTURE_RETRY_DELAY_MS = 120L
        private const val TAG = "BrowserPresenter"
        private const val DUCKDUCKGO_HTML_SEARCH = "https://html.duckduckgo.com/html/?q="
        private const val DUCKDUCKGO_SEARCH = "https://duckduckgo.com/?q="
        private const val DEFAULT_LUCKY_QUERY = "interesting websites"
        private const val LUCKY_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"
        private const val MAX_LUCKY_RESULTS = 20
        private const val DECOY_WINDOW_MILLIS = 4 * 60 * 60 * 1000L
        private fun searchUrl(engine: String, query: String): String =
            when (engine) {
                "amazon" -> "https://www.amazon.com/s?k=${URLEncoder.encode(query, "UTF-8")}"
                "reddit" -> "https://www.reddit.com/search/?q=${URLEncoder.encode(query, "UTF-8")}"
                "youtube" -> "https://www.youtube.com/results?search_query=${URLEncoder.encode(query, "UTF-8")}"
                else -> "https://www.google.com/search?q=${URLEncoder.encode(query, "UTF-8")}"
            }

        private val DECOY_JOURNEYS = listOf(
            DecoyJourney(
                listOf(
                    DecoyStep("best compact desk lamp 2026 - Google Search", searchUrl("google", "best compact desk lamp 2026"), 5, 11),
                    DecoyStep("compact desk lamp dimmable - Amazon.com", searchUrl("amazon", "compact desk lamp dimmable"), 7, 16),
                    DecoyStep("Reddit - compact desk lamp recommendations", searchUrl("reddit", "compact desk lamp recommendations"), 6, 14),
                    DecoyStep("compact desk lamp review - YouTube", searchUrl("youtube", "compact desk lamp review"), 9, 22)
                )
            ),
            DecoyJourney(
                listOf(
                    DecoyStep("easy weeknight rice bowl ideas - Google Search", searchUrl("google", "easy weeknight rice bowl ideas"), 4, 12),
                    DecoyStep("rice cooker recipes - YouTube", searchUrl("youtube", "rice cooker recipes"), 8, 18),
                    DecoyStep("Reddit - meal prep rice bowl ideas", searchUrl("reddit", "meal prep rice bowl ideas"), 5, 13),
                    DecoyStep("bento lunch containers - Amazon.com", searchUrl("amazon", "bento lunch containers"), 10, 24)
                )
            ),
            DecoyJourney(
                listOf(
                    DecoyStep("android privacy settings checklist - Google Search", searchUrl("google", "android privacy settings checklist"), 6, 14),
                    DecoyStep("Reddit - android privacy settings", searchUrl("reddit", "android privacy settings"), 8, 16),
                    DecoyStep("android privacy settings explained - YouTube", searchUrl("youtube", "android privacy settings explained"), 7, 18),
                    DecoyStep("privacy screen protector phone - Amazon.com", searchUrl("amazon", "privacy screen protector phone"), 9, 20)
                )
            ),
            DecoyJourney(
                listOf(
                    DecoyStep("best walking shoes for city travel - Google Search", searchUrl("google", "best walking shoes for city travel"), 5, 12),
                    DecoyStep("Reddit - comfortable walking shoes travel", searchUrl("reddit", "comfortable walking shoes travel"), 7, 17),
                    DecoyStep("walking shoes comparison - YouTube", searchUrl("youtube", "walking shoes comparison"), 8, 19),
                    DecoyStep("walking shoes men women - Amazon.com", searchUrl("amazon", "walking shoes"), 10, 23)
                )
            ),
            DecoyJourney(
                listOf(
                    DecoyStep("usb c hub for laptop reviews - Google Search", searchUrl("google", "usb c hub for laptop reviews"), 4, 11),
                    DecoyStep("usb c hub 4k hdmi - Amazon.com", searchUrl("amazon", "usb c hub 4k hdmi"), 6, 15),
                    DecoyStep("Reddit - usb c hub recommendations", searchUrl("reddit", "usb c hub recommendations"), 7, 18),
                    DecoyStep("usb c hub review 2026 - YouTube", searchUrl("youtube", "usb c hub review 2026"), 8, 21)
                )
            )
        )
    }
}
