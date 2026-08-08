package com.krystelligence.solipsism.bookmark

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class DecoyBookmarkFactoryTest {

    @Test
    fun `creates safe public bookmark entries`() {
        val entries = DecoyBookmarkFactory.create()

        assertThat(entries).isNotEmpty
        assertThat(entries).allMatch { it.url.startsWith("https://") }
        assertThat(entries).allMatch { it.title.isNotBlank() }
        assertThat(entries.map { it.position }).doesNotHaveDuplicates()
    }
}
