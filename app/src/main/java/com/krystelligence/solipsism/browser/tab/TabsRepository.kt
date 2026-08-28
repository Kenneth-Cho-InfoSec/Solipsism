package com.krystelligence.solipsism.browser.tab

import com.krystelligence.solipsism.constant.SCHEME_ANTARES_HOMEPAGE

import com.krystelligence.solipsism.browser.BrowserContract
import com.krystelligence.solipsism.browser.di.DiskScheduler
import com.krystelligence.solipsism.browser.di.InitialUrls
import com.krystelligence.solipsism.browser.di.MainScheduler
import com.krystelligence.solipsism.browser.tab.bundle.BundleStore
import com.krystelligence.solipsism.preference.UserPreferences
import com.krystelligence.solipsism.browser.engine.BrowserCore
import com.krystelligence.solipsism.browser.engine.BrowserCorePreferences
import com.krystelligence.solipsism.browser.engine.AntaresEngineConnection
import com.krystelligence.solipsism.utils.isFileUrl
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import android.view.View
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import javax.inject.Inject

/**
 * The repository for tabs that implements the [BrowserContract.Model] interface. Manages the state
 * of the tabs list and adding new tabs to it or removing tabs from it.
 */
class TabsRepository @Inject constructor(
    private val webViewFactory: WebViewFactory,
    private val tabPager: TabPager,
    @DiskScheduler private val diskScheduler: Scheduler,
    @MainScheduler private val mainScheduler: Scheduler,
    private val bundleStore: BundleStore,
    private val recentTabModel: RecentTabModel,
    private val tabFactory: TabFactory,
    private val antaresTabAdapterFactory: AntaresTabAdapter.Factory,
    private val antaresEngineConnection: AntaresEngineConnection,
    private val browserCorePreferences: BrowserCorePreferences,
    private val userPreferences: UserPreferences,
    private val homePageInitializer: HomePageInitializer,
    @InitialUrls private val initialUrls: List<String>,
    private val permissionInitializerFactory: PermissionInitializer.Factory,
    private val tabContentHostFactory: TabContentHost.Factory,
) : BrowserContract.Model {

    private var isInitialized = BehaviorSubject.createDefault(false)
    private var activeCore = browserCorePreferences.selectedCore
    private var selectedTab: TabModel? = null
    private val tabsListObservable = PublishSubject.create<List<TabModel>>()

    private fun afterInitialization(): Single<Boolean> =
        isInitialized.filter { it }.firstOrError()

    override fun deleteTab(id: Int): Completable = Completable.fromAction {
        if (selectedTab?.id == id) {
            tabPager.clearTab()
        }
        val tab = tabsList.forId(id)
        recentTabModel.addClosedTab(tab.freeze())
        tab.destroy()
        tabPager.removeTab(id)
        tabsList = tabsList - tab
    }.doOnComplete {
        tabsListObservable.onNext(tabsList)
    }.subscribeOn(mainScheduler)

    override fun deleteAllTabs(): Completable =
        afterInitialization().flatMapCompletable {
            Completable.fromAction {
                tabPager.clearTab()

                tabsList.forEach(TabModel::destroy)
                tabsList = emptyList()
                tabPager.replaceTabs(emptyMap())
            }
        }.doOnComplete {
            tabsListObservable.onNext(tabsList)
        }.subscribeOn(mainScheduler)

    override fun createTab(
        tabInitializer: TabInitializer,
        tabType: TabModel.Type
    ): Single<TabModel> = afterInitialization()
        .flatMap { createTabUnsafe(tabInitializer, tabType) }
        .subscribeOn(mainScheduler)

    /**
     * Creates a tab without waiting for the browser to be initialized.
     */
    private data class ConstructedTab(
        val view: Lazy<View>,
        val model: TabModel,
    )

    private fun constructTabForCore(
        tabInitializer: TabInitializer,
        tabType: TabModel.Type,
        core: BrowserCore,
    ): Single<ConstructedTab> = if (core == BrowserCore.ANTARES) {
        Single.fromCallable {
            val tab = antaresTabAdapterFactory.create(tabInitializer, tabType)
            ConstructedTab(
                lazyOf(tabContentHostFactory.create(tab, lazyOf(tab.engineView))),
                tab,
            )
        }
    } else {
        Single.fromCallable(webViewFactory::createWebView)
            .flatMap { webViewLazy ->
                tabFactory.constructTab(tabInitializer, webViewLazy, tabType)
                    .map { tab ->
                        ConstructedTab(
                            lazyOf(tabContentHostFactory.create(tab, webViewLazy)),
                            tab,
                        )
                    }
            }
    }

    private fun createTabUnsafe(
        tabInitializer: TabInitializer,
        tabType: TabModel.Type
    ): Single<TabModel> = constructTabForCore(
        tabInitializer,
        tabType,
        activeCore,
    )
            .doOnSuccess { constructed ->
                tabPager.addTab(constructed.model.id, constructed.view)
            }
            .map(ConstructedTab::model)
            .doOnSuccess {
                val selectedIndex = selectedTab?.let(tabsList::indexOf)
                tabsList = if (selectedIndex == null || selectedIndex < 0) {
                    tabsList + it
                } else {
                    tabsList.toMutableList().apply { add(selectedIndex + 1, it) }
                }
                tabsListObservable.onNext(tabsList)
            }
            .subscribeOn(mainScheduler)

    override fun reopenTab(): Maybe<TabModel> = Maybe.fromCallable(recentTabModel::lastClosed)
        .flatMapSingle { createTab(BundleInitializer(it)) }
        .subscribeOn(mainScheduler)

    override fun selectTab(id: Int): TabModel {
        val selected = tabsList.forId(id)
        selectedTab = selected
        tabPager.selectTab(id)

        return selected
    }

    override var tabsList = emptyList<TabModel>()
        private set

    override fun tabsListChanges(): Observable<List<TabModel>> = tabsListObservable.hide()

    override fun initializeTabs(): Maybe<List<TabModel>> =
        Single.fromCallable(bundleStore::retrieve)
            .subscribeOn(diskScheduler)
            .observeOn(mainScheduler)
            .flatMapObservable { Observable.fromIterable(it) }
            .flatMapSingle { createTabUnsafe(it, tabType = TabModel.Type.NORMAL) }
            .concatWith(
                Observable.fromIterable(initialUrls)
                    .map { url ->
                        if (url.isFileUrl()) permissionInitializerFactory.create(url) else UrlInitializer(url)
                    }
                    .flatMapSingle { createTabUnsafe(it, tabType = TabModel.Type.EPHEMERAL) },
            )
            .toList()
            .filter(List<TabModel>::isNotEmpty)
            .doAfterTerminate {
                isInitialized.onNext(true)
            }

    override fun markAllNonEphemeral() {
        tabsList.forEach { it.tabType = TabModel.Type.NORMAL }
    }

    override fun freeze() {
        if (userPreferences.restoreLostTabsEnabled) {
            bundleStore.save(tabsList)
        }
    }

    override fun clean() {
        bundleStore.deleteAll()
    }

    override fun switchCore(core: BrowserCore): Completable = afterInitialization()
        .flatMapCompletable {
            if (activeCore == core) return@flatMapCompletable Completable.complete()

            val previousTabs = tabsList
            val selectedIndex = selectedTab?.let(previousTabs::indexOf)?.takeIf { it >= 0 }
            val prepared = mutableListOf<ConstructedTab>()
            val engineReady = if (core == BrowserCore.ANTARES) {
                antaresEngineConnection.verify()
            } else {
                Completable.complete()
            }
            engineReady.andThen(Observable.fromIterable(previousTabs)
                .concatMapSingle { oldTab ->
                    constructTabForCore(
                        migrationInitializer(oldTab, core),
                        oldTab.tabType,
                        core,
                    )
                }
                .doOnNext(prepared::add)
                .toList()
                .flatMapCompletable { replacements ->
                    Completable.fromAction {
                        val replacementMap = replacements.associate { it.model.id to it.view }
                        tabPager.replaceTabs(replacementMap)
                        browserCorePreferences.selectedCore = core
                        activeCore = core
                        tabsList = replacements.map(ConstructedTab::model)
                        selectedTab = selectedIndex?.let(tabsList::getOrNull) ?: tabsList.firstOrNull()
                        selectedTab?.let {
                            tabPager.selectTab(it.id)
                            it.isForeground = true
                        }
                        previousTabs.forEach(TabModel::destroy)
                        if (core == BrowserCore.WEBVIEW) {
                            antaresEngineConnection.disconnect()
                        }
                        tabsListObservable.onNext(tabsList)
                    }
                }
                .doOnError {
                    prepared.forEach { it.model.destroy() }
                }
            )
        }
        .subscribeOn(mainScheduler)

    private fun List<TabModel>.forId(id: Int): TabModel = requireNotNull(find { it.id == id })

    private fun migrationInitializer(tab: TabModel, targetCore: BrowserCore): TabInitializer =
        if (tab.contentKind == TabContentKind.NATIVE_HOMEPAGE ||
            (targetCore == BrowserCore.WEBVIEW && tab.url == SCHEME_ANTARES_HOMEPAGE)
        ) {
            homePageInitializer
        } else {
            EngineMigrationInitializer(tab.url, tab.title, tab.contentKind)
        }
}
