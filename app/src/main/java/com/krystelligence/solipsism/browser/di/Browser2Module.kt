package com.krystelligence.solipsism.browser.di

import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.adblock.AdBlocker
import com.krystelligence.solipsism.adblock.BloomFilterAdBlocker
import com.krystelligence.solipsism.adblock.CompositeAdBlocker
import com.krystelligence.solipsism.adblock.NoOpAdBlocker
import com.krystelligence.solipsism.browser.BrowserContract
import com.krystelligence.solipsism.browser.data.CookieAdministrator
import com.krystelligence.solipsism.browser.data.DefaultCookieAdministrator
import com.krystelligence.solipsism.browser.history.DefaultHistoryRecord
import com.krystelligence.solipsism.browser.history.HistoryRecord
import com.krystelligence.solipsism.browser.history.NoOpHistoryRecord
import com.krystelligence.solipsism.browser.image.IconFreeze
import com.krystelligence.solipsism.browser.notification.DefaultTabCountNotifier
import com.krystelligence.solipsism.browser.notification.IncognitoTabCountNotifier
import com.krystelligence.solipsism.browser.notification.TabCountNotifier
import com.krystelligence.solipsism.browser.search.IntentExtractor
import com.krystelligence.solipsism.browser.tab.DefaultUserAgent
import com.krystelligence.solipsism.browser.tab.bundle.BundleStore
import com.krystelligence.solipsism.browser.tab.bundle.DefaultBundleStore
import com.krystelligence.solipsism.browser.tab.bundle.IncognitoBundleStore
import com.krystelligence.solipsism.browser.ui.BookmarkConfiguration
import com.krystelligence.solipsism.browser.ui.TabConfiguration
import com.krystelligence.solipsism.browser.ui.UiConfiguration
import com.krystelligence.solipsism.extensions.drawable
import com.krystelligence.solipsism.preference.UserPreferences
import com.krystelligence.solipsism.utils.IntentUtils
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.webkit.WebSettings
import androidx.core.graphics.drawable.toBitmap
import dagger.Module
import dagger.Provides
import javax.inject.Provider

/**
 * Constructs dependencies for the browser scope.
 */
@Module
class Browser2Module {

    @Provides
    fun providesAdBlocker(
        userPreferences: UserPreferences,
        bloomFilterAdBlocker: Provider<BloomFilterAdBlocker>,
        compositeAdBlocker: Provider<CompositeAdBlocker>,
        noOpAdBlocker: NoOpAdBlocker
    ): AdBlocker = if (userPreferences.adBlockEnabled) {
        if (userPreferences.uBlockOriginEnabled) {
            compositeAdBlocker.get()
        } else {
            bloomFilterAdBlocker.get()
        }
    } else {
        noOpAdBlocker
    }

    // TODO: dont force cast
    @Provides
    @InitialUrl
    fun providesInitialUrl(
        @InitialIntent initialIntent: Intent?,
        intentExtractor: IntentExtractor
    ): String? =
        (intentExtractor.extractUrlFromIntent(initialIntent) as? BrowserContract.Action.LoadUrl)?.url

    // TODO: auto inject intent utils
    @Provides
    fun providesIntentUtils(activity: Activity): IntentUtils = IntentUtils(activity)

    @Provides
    fun providesUiConfiguration(): UiConfiguration = UiConfiguration(
        tabConfiguration = TabConfiguration.SOLIPSISM,
        bookmarkConfiguration = BookmarkConfiguration.RIGHT
    )

    @DefaultUserAgent
    @Provides
    fun providesDefaultUserAgent(application: Application): String =
        WebSettings.getDefaultUserAgent(application)


    @Provides
    fun providesHistoryRecord(
        @IncognitoMode incognitoMode: Boolean,
        defaultHistoryRecord: DefaultHistoryRecord
    ): HistoryRecord = if (incognitoMode) {
        NoOpHistoryRecord
    } else {
        defaultHistoryRecord
    }

    @Provides
    fun providesCookieAdministrator(
        @IncognitoMode incognitoMode: Boolean,
        defaultCookieAdministrator: DefaultCookieAdministrator,
        incognitoCookieAdministrator: DefaultCookieAdministrator
    ): CookieAdministrator = if (incognitoMode) {
        incognitoCookieAdministrator
    } else {
        defaultCookieAdministrator
    }

    @Provides
    fun providesTabCountNotifier(
        @IncognitoMode incognitoMode: Boolean,
        incognitoTabCountNotifier: IncognitoTabCountNotifier
    ): TabCountNotifier = if (incognitoMode) {
        incognitoTabCountNotifier
    } else {
        DefaultTabCountNotifier
    }

    @Provides
    fun providesBundleStore(
        @IncognitoMode incognitoMode: Boolean,
        defaultBundleStore: DefaultBundleStore
    ): BundleStore = if (incognitoMode) {
        IncognitoBundleStore
    } else {
        defaultBundleStore
    }

    @IconFreeze
    @Provides
    fun providesFrozenIcon(activity: Activity): Bitmap =
        activity.drawable(R.drawable.ic_frozen).toBitmap()

}
