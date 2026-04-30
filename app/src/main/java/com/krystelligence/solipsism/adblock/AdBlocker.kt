package com.krystelligence.solipsism.adblock

/**
 * The ad blocking interface.
 */
interface AdBlocker {

    /**
     * a method that determines if the given URL is an ad or not. It performs a search of the URL's
     * domain on the blocked domain hash set.
     *
     * @param url the URL to check for being an ad.
     * @return true if it is an ad, false if it is not an ad.
     */
    fun isAd(url: String): Boolean

    /**
     * Determines if [url] should be blocked in the context of [pageUrl].
     */
    fun isAd(url: String, pageUrl: String): Boolean = isAd(url)

}
