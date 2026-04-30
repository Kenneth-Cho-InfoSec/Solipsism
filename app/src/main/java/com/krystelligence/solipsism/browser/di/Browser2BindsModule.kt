package com.krystelligence.solipsism.browser.di

import com.krystelligence.solipsism.browser.BrowserContract
import com.krystelligence.solipsism.browser.BrowserNavigator
import com.krystelligence.solipsism.browser.cleanup.DelegatingExitCleanup
import com.krystelligence.solipsism.browser.cleanup.ExitCleanup
import com.krystelligence.solipsism.browser.image.FaviconImageLoader
import com.krystelligence.solipsism.browser.image.ImageLoader
import com.krystelligence.solipsism.browser.tab.TabsRepository
import com.krystelligence.solipsism.browser.theme.DefaultThemeProvider
import com.krystelligence.solipsism.browser.theme.ThemeProvider
import android.app.Activity
import androidx.fragment.app.FragmentActivity
import dagger.Binds
import dagger.Module

/**
 * Binds implementations to interfaces for the browser scope.
 */
@Module
interface Browser2BindsModule {

    @Binds
    fun bindsActivity(fragmentActivity: FragmentActivity): Activity

    @Binds
    fun bindsBrowserModel(tabsRepository: TabsRepository): BrowserContract.Model

    @Binds
    fun bindsFaviconImageLoader(faviconImageLoader: FaviconImageLoader): ImageLoader

    @Binds
    fun bindsBrowserNavigator(browserNavigator: BrowserNavigator): BrowserContract.Navigator

    @Binds
    fun bindsExitCleanup(delegatingExitCleanup: DelegatingExitCleanup): ExitCleanup

    @Binds
    fun bindsThemeProvider(legacyThemeProvider: DefaultThemeProvider): ThemeProvider
}
