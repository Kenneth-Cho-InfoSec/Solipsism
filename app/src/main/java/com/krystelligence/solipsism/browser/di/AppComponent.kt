package com.krystelligence.solipsism.browser.di

import com.krystelligence.solipsism.BrowserApp
import com.krystelligence.solipsism.ThemableBrowserActivity
import com.krystelligence.solipsism.adblock.BloomFilterAdBlocker
import com.krystelligence.solipsism.adblock.NoOpAdBlocker
import com.krystelligence.solipsism.browser.search.SearchBoxModel
import com.krystelligence.solipsism.device.BuildInfo
import com.krystelligence.solipsism.dialog.SolipsismDialogBuilder
import com.krystelligence.solipsism.search.SuggestionsAdapter
import com.krystelligence.solipsism.settings.activity.ThemableSettingsActivity
import com.krystelligence.solipsism.settings.fragment.AccessibilitySettingsFragment
import com.krystelligence.solipsism.settings.fragment.AdBlockSettingsFragment
import com.krystelligence.solipsism.settings.fragment.AdvancedSettingsFragment
import com.krystelligence.solipsism.settings.fragment.BookmarkSettingsFragment
import com.krystelligence.solipsism.settings.fragment.DebugSettingsFragment
import com.krystelligence.solipsism.settings.fragment.DisplaySettingsFragment
import com.krystelligence.solipsism.settings.fragment.GeneralSettingsFragment
import com.krystelligence.solipsism.settings.fragment.PrivacySettingsFragment
import com.krystelligence.solipsism.settings.fragment.RootSettingsFragment
import com.krystelligence.solipsism.settings.fragment.UserScriptsSettingsFragment
import android.app.Application
import dagger.BindsInstance
import dagger.Component
import dagger.Module
import javax.inject.Singleton

@Singleton
@Component(modules = [AppModule::class, AppBindsModule::class, Submodules::class])
interface AppComponent {

    @Component.Builder
    interface Builder {

        @BindsInstance
        fun application(application: Application): Builder

        @BindsInstance
        fun buildInfo(buildInfo: BuildInfo): Builder

        fun build(): AppComponent
    }

    fun inject(fragment: BookmarkSettingsFragment)

    fun inject(builder: SolipsismDialogBuilder)

    fun inject(activity: ThemableBrowserActivity)

    fun inject(advancedSettingsFragment: AdvancedSettingsFragment)

    fun inject(app: BrowserApp)

    fun inject(activity: ThemableSettingsActivity)

    fun inject(fragment: PrivacySettingsFragment)

    fun inject(fragment: DebugSettingsFragment)

    fun inject(suggestionsAdapter: SuggestionsAdapter)

    fun inject(searchBoxModel: SearchBoxModel)

    fun inject(activity: RootSettingsFragment)

    fun inject(generalSettingsFragment: GeneralSettingsFragment)

    fun inject(displaySettingsFragment: DisplaySettingsFragment)

    fun inject(adBlockSettingsFragment: AdBlockSettingsFragment)

    fun inject(accessibilitySettingsFragment: AccessibilitySettingsFragment)

    fun inject(userScriptsSettingsFragment: UserScriptsSettingsFragment)

    fun provideBloomFilterAdBlocker(): BloomFilterAdBlocker

    fun provideNoOpAdBlocker(): NoOpAdBlocker

    fun browser2ComponentBuilder(): Browser2Component.Builder

}

@Module(subcomponents = [Browser2Component::class])
internal class Submodules
