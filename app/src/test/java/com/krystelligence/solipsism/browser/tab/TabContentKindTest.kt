package com.krystelligence.solipsism.browser.tab

import android.os.Bundle
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TabContentKindTest {
    @Test
    fun `visual homepage always selects native content`() {
        assertThat(VisualHomePageInitializer().contentKind)
            .isEqualTo(TabContentKind.NATIVE_HOMEPAGE)
    }

    @Test
    fun `saved native homepage content restores as native`() {
        val bundle = Bundle().apply {
            putString(TabStateKeys.CONTENT_KIND, TabContentKind.NATIVE_HOMEPAGE.name)
        }

        assertThat(BundleInitializer(bundle).contentKind)
            .isEqualTo(TabContentKind.NATIVE_HOMEPAGE)
    }

    @Test
    fun `missing or invalid saved content kind falls back to engine`() {
        assertThat(BundleInitializer(Bundle()).contentKind)
            .isEqualTo(TabContentKind.ENGINE)
        assertThat(
            BundleInitializer(Bundle().apply {
                putString(TabStateKeys.CONTENT_KIND, "NOT_A_CONTENT_KIND")
            }).contentKind,
        ).isEqualTo(TabContentKind.ENGINE)
    }

    @Test
    fun `core migration preserves content kind`() {
        val initializer = EngineMigrationInitializer(
            url = "about:home",
            title = "Solipsism Browser",
            contentKind = TabContentKind.NATIVE_HOMEPAGE,
        )

        assertThat(initializer.contentKind).isEqualTo(TabContentKind.NATIVE_HOMEPAGE)
    }
}
