package com.krystelligence.solipsism.adblock

import dagger.Reusable
import javax.inject.Inject

/**
 * Runs multiple blockers as one browser-level blocker.
 */
@Reusable
class CompositeAdBlocker @Inject constructor(
    private val bloomFilterAdBlocker: BloomFilterAdBlocker,
    private val uBlockOriginAdBlocker: UBlockOriginAdBlocker
) : AdBlocker {

    override fun isAd(url: String): Boolean =
        bloomFilterAdBlocker.isAd(url) || uBlockOriginAdBlocker.isAd(url)

    override fun isAd(url: String, pageUrl: String): Boolean =
        bloomFilterAdBlocker.isAd(url, pageUrl) || uBlockOriginAdBlocker.isAd(url, pageUrl)
}
