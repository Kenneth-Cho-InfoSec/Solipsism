package com.krystelligence.solipsism.browser.ui

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class RailMenuLayoutTest {

    @Test
    fun `normalise keeps tabs on rail and overflow out of editable actions`() {
        val layout = RailMenuLayoutCodec.normalise(
            RailMenuLayout(
                railActions = listOf(RailActionId.HISTORY, RailActionId.HISTORY),
                overflowActions = listOf(RailActionId.TABS, RailActionId.OVERFLOW)
            )
        )

        assertThat(layout.railActions).contains(RailActionId.TABS)
        assertThat(layout.railActions + layout.quickActions + layout.overflowActions)
            .doesNotContain(RailActionId.OVERFLOW)
        assertThat(layout.railActions + layout.quickActions + layout.overflowActions)
            .doesNotHaveDuplicates()
    }

    @Test
    fun `normalise caps movable rail actions at eight`() {
        val layout = RailMenuLayoutCodec.normalise(
            RailMenuLayout(
                railActions = RailActionId.entries.filter { it != RailActionId.OVERFLOW },
                overflowActions = emptyList()
            )
        )

        assertThat(layout.railActions).contains(RailActionId.TABS)
        assertThat(layout.railActions.count { it != RailActionId.TABS })
            .isEqualTo(RailMenuLayout.MAX_MOVABLE_RAIL_ACTIONS)
        assertThat(layout.overflowActions).isNotEmpty()
    }

    @Test
    fun `tabs remains movable within the rail`() {
        val layout = RailMenuLayoutCodec.normalise(
            RailMenuLayout(
                topActions = listOf(RailActionId.BACK, RailActionId.TABS, RailActionId.HOME),
                addressActions = emptyList(),
                bottomActions = emptyList(),
                overflowActions = emptyList()
            )
        )

        assertThat(layout.railActions.take(3))
            .containsExactly(RailActionId.BACK, RailActionId.TABS, RailActionId.HOME)
    }

    @Test
    fun `encoded layout round trips without duplicate actions`() {
        val encoded = RailMenuLayoutCodec.encode(RailMenuLayout.default())
        val decoded = RailMenuLayoutCodec.decode(encoded)

        assertThat(decoded.railActions + decoded.quickActions + decoded.overflowActions)
            .containsExactlyInAnyOrderElementsOf(RailActionId.entries.filter { it != RailActionId.OVERFLOW })
    }

    @Test
    fun `encoded custom rail order survives a preference round trip`() {
        val input = RailMenuLayout.default().let { default ->
            default.copy(
                topActions = listOf(RailActionId.TABS, RailActionId.NEW_TAB),
                addressActions = emptyList(),
                bottomActions = listOf(RailActionId.HOME),
                overflowActions = default.overflowActions - RailActionId.NEW_TAB + RailActionId.REFRESH
            )
        }

        assertThat(RailMenuLayoutCodec.decode(RailMenuLayoutCodec.encode(input)).railActions)
            .containsExactly(RailActionId.TABS, RailActionId.NEW_TAB, RailActionId.HOME)
    }

    @Test
    fun `default layout puts the five intended actions in the icon-only row`() {
        val layout = RailMenuLayout.default()

        assertThat(layout.quickActions).containsExactly(
            RailActionId.NEW_TAB,
            RailActionId.INCOGNITO,
            RailActionId.DOWNLOADS,
            RailActionId.BOOKMARKS,
            RailActionId.COPY_LINK
        )
        assertThat(layout.overflowActions).doesNotContainAnyElementsOf(layout.quickActions)
    }

    @Test
    fun `normalise keeps quick actions unique and caps them at five`() {
        val layout = RailMenuLayoutCodec.normalise(
            RailMenuLayout.default().copy(
                topActions = listOf(RailActionId.TABS, RailActionId.NEW_TAB),
                quickActions = RailActionId.entries.filter { it != RailActionId.OVERFLOW },
                overflowActions = RailActionId.entries.toList()
            )
        )

        assertThat(layout.quickActions).hasSize(RailMenuLayout.MAX_QUICK_ACTIONS)
        assertThat(layout.quickActions).doesNotContain(RailActionId.TABS, RailActionId.NEW_TAB)
        assertThat(layout.railActions + layout.quickActions + layout.overflowActions).doesNotHaveDuplicates()
    }

    @Test
    fun `disabled quick actions return to the labelled overflow list`() {
        val layout = RailMenuLayout.default().copy(quickActionsEnabled = false)

        assertThat(layout.visibleOverflowActions).containsAll(layout.quickActions)
        assertThat(layout.visibleOverflowActions).doesNotHaveDuplicates()
    }
}
