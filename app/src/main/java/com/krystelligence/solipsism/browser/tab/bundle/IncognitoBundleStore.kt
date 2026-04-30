package com.krystelligence.solipsism.browser.tab.bundle

import com.krystelligence.solipsism.browser.tab.TabModel
import com.krystelligence.solipsism.browser.tab.TabInitializer

/**
 * A bundle store implementation that no-ops for for incognito mode.
 */
object IncognitoBundleStore : BundleStore {
    override fun save(tabs: List<TabModel>) = Unit

    override fun retrieve(): List<TabInitializer> = emptyList()

    override fun deleteAll() = Unit
}
