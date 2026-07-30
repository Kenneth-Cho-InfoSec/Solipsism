package com.krystelligence.solipsism.preference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SitePermissionStoreTest {

    @Test
    fun normalizesHttpOriginAndDropsPath() {
        assertEquals("https://example.com:8443", SitePermissionStore.normalizeOrigin(" HTTPS://Example.com:8443/path "))
    }

    @Test
    fun rejectsAppendedSchemeTypo() {
        assertNull(SitePermissionStore.normalizeOrigin("https://example.comhttps"))
        assertNull(SitePermissionStore.normalizeOrigin("http://example.comhttp"))
    }

    @Test
    fun rejectsUnsafeOrUnsupportedOriginForms() {
        assertNull(SitePermissionStore.normalizeOrigin("file:///tmp/test"))
        assertNull(SitePermissionStore.normalizeOrigin("https://user:pass@example.com"))
        assertNull(SitePermissionStore.normalizeOrigin("https://example.com#fragment"))
        assertNull(SitePermissionStore.normalizeOrigin("https://example.com with-space"))
    }
}
