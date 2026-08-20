package com.krystelligence.solipsism.download

import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class WebAddressTest {

    @Test
    fun `adds the default HTTP scheme and port`() {
        val address = WebAddress("example.com/path")
        assertThat(address.scheme).isEqualTo("http")
        assertThat(address.port).isEqualTo(80)
        assertThat(address.toString()).isEqualTo("http://example.com/path")
    }

    @Test
    fun `infers HTTPS from port 443`() {
        val address = WebAddress("example.com:443")
        assertThat(address.scheme).isEqualTo("https")
        assertThat(address.toString()).isEqualTo("https://example.com/")
    }

    @Test
    fun `preserves credentials and non-default ports`() {
        val address = WebAddress("https://user:pass@example.com:8443/path")
        assertThat(address.authInfo).isEqualTo("user:pass")
        assertThat(address.host).isEqualTo("example.com")
        assertThat(address.toString()).isEqualTo("https://user:pass@example.com:8443/path")
    }

    @Test
    fun `rejects null and malformed input`() {
        assertThrows(IllegalArgumentException::class.java) { WebAddress(null) }
    }
}
