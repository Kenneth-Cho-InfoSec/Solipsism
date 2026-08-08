package com.krystelligence.solipsism.browser.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomDownloadManagerTest {

    @Test
    fun `accepts Android package names`() {
        assertTrue(CustomDownloadManager.isValidPackageName("com.gianlu.aria2app"))
        assertTrue(CustomDownloadManager.isValidPackageName("idm.internet.download.manager"))
    }

    @Test
    fun `rejects malformed package names`() {
        assertFalse(CustomDownloadManager.isValidPackageName("com"))
        assertFalse(CustomDownloadManager.isValidPackageName("com..manager"))
        assertFalse(CustomDownloadManager.isValidPackageName("example manager"))
    }
}
