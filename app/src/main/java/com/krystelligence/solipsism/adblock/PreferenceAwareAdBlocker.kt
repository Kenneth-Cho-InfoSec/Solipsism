package com.krystelligence.solipsism.adblock

import com.krystelligence.solipsism.preference.UserPreferences
import dagger.Reusable
import javax.inject.Inject

/**
 * Selects the active blocker for every request so preference changes take effect without
 * recreating the browser activity or its tabs.
 */
@Reusable
class PreferenceAwareAdBlocker @Inject constructor(
    private val userPreferences: UserPreferences,
    private val bloomFilterAdBlocker: BloomFilterAdBlocker,
    private val uBlockOriginAdBlocker: UBlockOriginAdBlocker,
) : AdBlocker {

    override fun isAd(url: String): Boolean =
        userPreferences.adBlockEnabled &&
            (bloomFilterAdBlocker.isAd(url) ||
                (userPreferences.uBlockOriginEnabled && uBlockOriginAdBlocker.isAd(url)))

    override fun isAd(url: String, pageUrl: String): Boolean =
        userPreferences.adBlockEnabled &&
            (bloomFilterAdBlocker.isAd(url, pageUrl) ||
                (userPreferences.uBlockOriginEnabled &&
                    uBlockOriginAdBlocker.isAd(url, pageUrl)))
}
