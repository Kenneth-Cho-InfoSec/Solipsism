package com.krystelligence.solipsism.download

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class DecoyDownloadFactoryTest {

    @Test
    fun `creates bounded synthetic download entries`() {
        val entries = DecoyDownloadFactory.create(8, now = 1_700_000_000_000L)

        assertThat(entries).hasSize(8)
        assertThat(entries).allMatch { it.isDecoy }
        assertThat(entries).allMatch { it.url.startsWith("solipsism://decoy-download/") }
        assertThat(entries).allMatch { it.title.matches(Regex("[A-Za-z0-9_.-]+")) }
    }
}
