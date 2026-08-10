package com.krystelligence.solipsism.browser.ui

/**
 * The user-owned arrangement of the Solipsism side rail and overflow menu.
 *
 * The rail deliberately has three independent regions. This preserves the original Solipsism
 * preset while allowing an action to be placed above, inside, or below the URL surface.
 */
data class RailMenuLayout(
    val topActions: List<RailActionId>,
    val addressActions: List<RailActionId>,
    val bottomActions: List<RailActionId>,
    val overflowActions: List<RailActionId>,
    /** Icon-only actions displayed before the labelled overflow items. */
    val quickActions: List<RailActionId> = emptyList(),
    val quickActionsEnabled: Boolean = true,
    val schemaVersion: Int = SCHEMA_VERSION
) {
    /** Flattened view retained for callers that only need to count rail actions. */
    val railActions: List<RailActionId>
        get() = topActions + addressActions + bottomActions

    /** The actions currently rendered as labelled entries in the overflow menu. */
    val visibleOverflowActions: List<RailActionId>
        get() = if (quickActionsEnabled) {
            overflowActions
        } else {
            (overflowActions + quickActions)
                .distinct()
                .sortedBy(RailActionId.entries::indexOf)
        }

    /** Compatibility constructor for layouts saved by the first Studio build. */
    constructor(
        railActions: List<RailActionId>,
        overflowActions: List<RailActionId>,
        quickActions: List<RailActionId> = emptyList(),
        quickActionsEnabled: Boolean = true,
        schemaVersion: Int = SCHEMA_VERSION
    ) : this(
        topActions = railActions.filter { it == RailActionId.TABS },
        addressActions = railActions.filter { it == RailActionId.REFRESH || it == RailActionId.UTILITY },
        bottomActions = railActions.filter {
            it != RailActionId.TABS && it != RailActionId.REFRESH && it != RailActionId.UTILITY
        },
        overflowActions = overflowActions,
        quickActions = quickActions,
        quickActionsEnabled = quickActionsEnabled,
        schemaVersion = schemaVersion
    )

    companion object {
        const val MAX_MOVABLE_RAIL_ACTIONS = 8
        const val MAX_QUICK_ACTIONS = 5
        const val SCHEMA_VERSION = 3

        val DEFAULT_QUICK_ACTIONS = listOf(
            RailActionId.NEW_TAB,
            RailActionId.INCOGNITO,
            RailActionId.DOWNLOADS,
            RailActionId.BOOKMARKS,
            RailActionId.COPY_LINK
        )

        fun default(): RailMenuLayout = RailMenuLayout(
            topActions = listOf(RailActionId.TABS),
            addressActions = listOf(RailActionId.REFRESH, RailActionId.UTILITY),
            bottomActions = listOf(
                RailActionId.BACK,
                RailActionId.FORWARD,
                RailActionId.HOME,
                RailActionId.ADD_BOOKMARK
            ),
            quickActions = DEFAULT_QUICK_ACTIONS,
            overflowActions = RailActionId.entries.filterNot {
                it == RailActionId.TABS || it == RailActionId.OVERFLOW || it in setOf(
                    RailActionId.REFRESH,
                    RailActionId.UTILITY,
                    RailActionId.BACK,
                    RailActionId.FORWARD,
                    RailActionId.HOME,
                    RailActionId.ADD_BOOKMARK,
                    *DEFAULT_QUICK_ACTIONS.toTypedArray()
                )
            }
        )
    }
}

/** Actions which can be arranged in Rail & Menu Studio. */
enum class RailActionId {
    TABS,
    REFRESH,
    UTILITY,
    BACK,
    FORWARD,
    HOME,
    ADD_BOOKMARK,
    NEW_TAB,
    INCOGNITO,
    FEELING_LUCKY,
    ADD_TO_HOME,
    HISTORY,
    DOWNLOADS,
    BOOKMARKS,
    FIND,
    READ_ALOUD,
    COPY_LINK,
    SCREENSHOT,
    USER_AGENT,
    BLOCK_ELEMENT,
    COOKIE_MANAGER,
    SETTINGS,
    OVERFLOW
}

enum class RailMenuZone { TOP, ADDRESS, BOTTOM }

/** Validates and normalises persisted layouts, including layouts from earlier app versions. */
object RailMenuLayoutCodec {
    fun decode(serialized: String?): RailMenuLayout {
        if (serialized.isNullOrBlank()) return RailMenuLayout.default()
        return runCatching {
            val hasZones = serialized.contains("\"top\"")
            val hasQuickActions = serialized.contains("\"quick\"")
            if (hasZones) {
                RailMenuLayout(
                    topActions = serialized.arrayValue("top").toActions(),
                    addressActions = serialized.arrayValue("address").toActions(),
                    bottomActions = serialized.arrayValue("bottom").toActions(),
                    overflowActions = serialized.arrayValue("overflow").toActions(),
                    // Layouts saved before quick actions receive the product default once.
                    quickActions = if (hasQuickActions) {
                        serialized.arrayValue("quick").toActions()
                    } else {
                        RailMenuLayout.DEFAULT_QUICK_ACTIONS
                    },
                    quickActionsEnabled = serialized.booleanValue("quickEnabled", true)
                )
            } else {
                // Version 1 stored one rail list. Recreate the established preset regions while
                // retaining custom actions and their relative order within each region.
                RailMenuLayout(
                    railActions = serialized.arrayValue("rail").toActions(),
                    overflowActions = serialized.arrayValue("overflow").toActions(),
                    quickActions = RailMenuLayout.DEFAULT_QUICK_ACTIONS,
                    schemaVersion = 1
                )
            }.let(::normalise)
        }.getOrElse { RailMenuLayout.default() }
    }

    fun encode(layout: RailMenuLayout): String {
        val normalised = normalise(layout)
        return "{\"version\":${RailMenuLayout.SCHEMA_VERSION}," +
            "\"top\":${normalised.topActions.toJsonArray()}," +
            "\"address\":${normalised.addressActions.toJsonArray()}," +
            "\"bottom\":${normalised.bottomActions.toJsonArray()}," +
            "\"quick\":${normalised.quickActions.toJsonArray()}," +
            "\"quickEnabled\":${normalised.quickActionsEnabled}," +
            "\"overflow\":${normalised.overflowActions.toJsonArray()}}"
    }

    fun normalise(layout: RailMenuLayout): RailMenuLayout {
        val recognised = RailActionId.entries.filter { it != RailActionId.OVERFLOW }.toSet()
        val zones = listOf(
            layout.topActions.filter { it in recognised },
            layout.addressActions.filter { it in recognised },
            layout.bottomActions.filter { it in recognised }
        )
        val seen = linkedSetOf<RailActionId>()
        val uniqueZones = zones.map { zone -> zone.filter(seen::add) }
        val requestedRail = uniqueZones.flatten()
        val railWithTabs = uniqueZones.toMutableList().apply {
            if (RailActionId.TABS !in seen) this[0] = listOf(RailActionId.TABS) + this[0]
        }
        var movableCount = 0
        val keptZones = railWithTabs.map { zone ->
            zone.filter { action ->
                action == RailActionId.TABS || movableCount++ < RailMenuLayout.MAX_MOVABLE_RAIL_ACTIONS
            }
        }
        val keptRail = keptZones.flatten()
        val quickActions = layout.quickActions
            .filter { it in recognised && it !in keptRail && it != RailActionId.TABS }
            .distinct()
            .take(RailMenuLayout.MAX_QUICK_ACTIONS)
        val ordered = (requestedRail + quickActions + layout.overflowActions + RailActionId.entries)
            .filter { it in recognised }
            .distinct()
        val overflow = ordered
            .filterNot(keptRail::contains)
            .filterNot(quickActions::contains)
        return RailMenuLayout(
            topActions = keptZones[0],
            addressActions = keptZones[1],
            bottomActions = keptZones[2],
            overflowActions = overflow,
            quickActions = quickActions,
            quickActionsEnabled = layout.quickActionsEnabled
        )
    }

    private fun String.arrayValue(key: String): String {
        val match = Regex("\\\"$key\\\"\\s*:\\s*\\[([^]]*)]").find(this) ?: return ""
        return match.groupValues[1]
    }

    private fun String.booleanValue(key: String, default: Boolean): Boolean {
        val match = Regex("\\\"$key\\\"\\s*:\\s*(true|false)").find(this) ?: return default
        return match.groupValues[1].toBoolean()
    }

    private fun String.toActions(): List<RailActionId> = split(',')
        .map { it.trim().trim('"') }
        .mapNotNull { name -> RailActionId.entries.firstOrNull { it.name == name } }

    private fun List<RailActionId>.toJsonArray(): String = joinToString(",", prefix = "[", postfix = "]") {
        "\"${it.name}\""
    }
}
