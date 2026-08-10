package com.krystelligence.solipsism.browser.ui

import android.content.ClipData
import android.os.Bundle
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.ThemableBrowserActivity
import com.krystelligence.solipsism.databinding.ActivityRailMenuStudioBinding
import com.krystelligence.solipsism.utils.CustomFontManager

/** Native editor for the user-configurable Solipsism rail and overflow menu. */
class RailMenuStudioActivity : ThemableBrowserActivity() {
    private lateinit var binding: ActivityRailMenuStudioBinding
    private var stagedLayout = RailMenuLayout.default()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRailMenuStudioBinding.inflate(layoutInflater)
        setContentView(binding.root)
        stagedLayout = savedInstanceState?.getString(STATE_LAYOUT)
            ?.let(RailMenuLayoutCodec::decode)
            ?: userPreferences.railMenuLayout

        binding.confirmButton.setOnClickListener {
            userPreferences.railMenuLayout = stagedLayout
            setResult(RESULT_OK)
            finish()
        }
        binding.studioOverflowButton.setOnClickListener { binding.studioOverflowMenu.visibility = View.VISIBLE }
        binding.studioRail.setOnDragListener { view, event -> onZoneDrop(RailMenuZone.BOTTOM, view, event) }
        binding.studioRailActions.setOnDragListener { view, event -> onZoneDrop(RailMenuZone.TOP, view, event) }
        binding.studioUrlSurface.setOnDragListener { view, event -> onZoneDrop(RailMenuZone.ADDRESS, view, event) }
        binding.studioAddressTopActions.setOnDragListener { view, event -> onZoneDrop(RailMenuZone.ADDRESS, view, event) }
        binding.studioAddressBottomActions.setOnDragListener { view, event -> onZoneDrop(RailMenuZone.ADDRESS, view, event) }
        binding.studioRailBottomActions.setOnDragListener { view, event -> onZoneDrop(RailMenuZone.BOTTOM, view, event) }
        binding.studioQuickActions.setOnDragListener { view, event -> onQuickDrop(view, event) }
        binding.studioQuickActionsEnabled.setOnCheckedChangeListener { _, enabled ->
            if (stagedLayout.quickActionsEnabled != enabled) {
                stagedLayout = stagedLayout.copy(quickActionsEnabled = enabled)
                render()
            }
        }
        // The complete menu surface is a target, including blank space below its last item.
        // This mirrors the Studio prototype: dragging a rail action back anywhere in the menu
        // removes it from the rail rather than requiring a tiny, populated target.
        binding.studioOverflowMenu.setOnDragListener { view, event -> onOverflowDrop(view, event) }
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_LAYOUT, RailMenuLayoutCodec.encode(stagedLayout))
        super.onSaveInstanceState(outState)
    }

    private fun render() {
        stagedLayout = RailMenuLayoutCodec.normalise(stagedLayout)
        binding.studioRailActions.removeAllViews()
        binding.studioAddressTopActions.removeAllViews()
        binding.studioAddressBottomActions.removeAllViews()
        binding.studioRailBottomActions.removeAllViews()
        binding.studioQuickActions.removeAllViews()
        binding.studioOverflowActions.removeAllViews()
        stagedLayout.topActions.forEach { action -> binding.studioRailActions.addView(createRailAction(action, RailMenuZone.TOP)) }
        stagedLayout.addressActions.forEachIndexed { index, action ->
            val container = if (index == 0) binding.studioAddressTopActions else binding.studioAddressBottomActions
            container.addView(createRailAction(action, RailMenuZone.ADDRESS))
        }
        stagedLayout.bottomActions.forEach { action -> binding.studioRailBottomActions.addView(createRailAction(action, RailMenuZone.BOTTOM)) }
        stagedLayout.quickActions.forEach { action ->
            binding.studioQuickActions.addView(createQuickAction(action))
        }
        binding.studioQuickActions.visibility = if (stagedLayout.quickActionsEnabled) View.VISIBLE else View.GONE
        binding.studioQuickActionsEnabled.setOnCheckedChangeListener(null)
        binding.studioQuickActionsEnabled.isChecked = stagedLayout.quickActionsEnabled
        binding.studioQuickActionsEnabled.setOnCheckedChangeListener { _, enabled ->
            if (stagedLayout.quickActionsEnabled != enabled) {
                stagedLayout = stagedLayout.copy(quickActionsEnabled = enabled)
                render()
            }
        }
        stagedLayout.visibleOverflowActions.forEach { action ->
            binding.studioOverflowActions.addView(createOverflowAction(action))
        }
        binding.studioOverflowMenu.visibility = View.VISIBLE
        CustomFontManager.applyToViewTree(binding.root, userPreferences.customFontPath)
    }

    private fun createRailAction(action: RailActionId, zone: RailMenuZone): ImageButton = ImageButton(this).apply {
        layoutParams = LinearLayout.LayoutParams(42.dp, 42.dp).apply { bottomMargin = 6.dp }
        background = AppCompatResources.getDrawable(context, R.drawable.solipsism_blend_button_background)
        contentDescription = getString(descriptor(action).label)
        setImageResource(descriptor(action).icon)
        imageTintList = getColorStateListFromTheme(R.attr.iconColor)
        setPadding(8.dp, 8.dp, 8.dp, 8.dp)
        isFocusable = true
        setOnLongClickListener { beginDrag(this, action); true }
        setOnDragListener { _, event -> onRailItemDrop(zone, action, event) }
        addMoveAccessibilityActions(this, action, isRail = true)
    }

    private fun createQuickAction(action: RailActionId): ImageButton = ImageButton(this).apply {
        layoutParams = LinearLayout.LayoutParams(40.dp, 40.dp).apply {
            marginStart = 2.dp
            marginEnd = 2.dp
        }
        background = AppCompatResources.getDrawable(context, R.drawable.browser_overflow_quick_button_background)
        contentDescription = getString(descriptor(action).label)
        setImageResource(descriptor(action).icon)
        imageTintList = getColorStateListFromTheme(R.attr.colorOnSurface)
        setPadding(8.dp, 8.dp, 8.dp, 8.dp)
        isFocusable = true
        setOnLongClickListener { beginDrag(this, action); true }
        setOnDragListener { _, event -> onQuickItemDrop(action, event) }
        addMoveAccessibilityActions(this, action, isRail = false, isQuick = true)
    }

    private fun createOverflowAction(action: RailActionId): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            46.dp
        ).apply { bottomMargin = 3.dp }
        background = AppCompatResources.getDrawable(context, R.drawable.browser_overflow_menu_item_background)
        isFocusable = true
        isClickable = true
        contentDescription = getString(descriptor(action).label)
        setPadding(6.dp, 4.dp, 8.dp, 4.dp)
        addView(ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(30.dp, 30.dp)
            background = null
            isClickable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setImageResource(descriptor(action).icon)
            imageTintList = getColorStateListFromTheme(R.attr.colorOnSurfaceVariant)
        })
        addView(TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 8.dp
            }
            setText(descriptor(action).label)
            setTextColor(themeColor(R.attr.colorOnSurface))
            textSize = 15f
            maxLines = 1
        })
        setOnLongClickListener { beginDrag(this, action); true }
        addMoveAccessibilityActions(this, action, isRail = false)
    }

    private fun beginDrag(source: View, action: RailActionId) {
        source.startDragAndDrop(
            ClipData.newPlainText("rail-action", action.name),
            View.DragShadowBuilder(source),
            action,
            0
        )
    }

    private fun onZoneDrop(zone: RailMenuZone, view: View, event: DragEvent): Boolean = when (event.action) {
        DragEvent.ACTION_DRAG_STARTED -> event.localState is RailActionId
        DragEvent.ACTION_DRAG_ENTERED -> { view.alpha = 0.82f; true }
        DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> { view.alpha = 1f; true }
        DragEvent.ACTION_DROP -> moveToZone(event.localState as? RailActionId, zone, null)
        else -> true
    }

    private fun onRailItemDrop(zone: RailMenuZone, target: RailActionId, event: DragEvent): Boolean = when (event.action) {
        DragEvent.ACTION_DRAG_STARTED -> event.localState is RailActionId
        DragEvent.ACTION_DRAG_ENTERED -> { binding.studioRail.alpha = 0.82f; true }
        DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> { binding.studioRail.alpha = 1f; true }
        DragEvent.ACTION_DROP -> moveToZone(event.localState as? RailActionId, zone, target)
        else -> true
    }

    private fun onOverflowDrop(view: View, event: DragEvent): Boolean = when (event.action) {
        DragEvent.ACTION_DRAG_STARTED -> event.localState is RailActionId
        DragEvent.ACTION_DRAG_ENTERED -> { binding.studioOverflowMenu.alpha = 0.82f; true }
        DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> { binding.studioOverflowMenu.alpha = 1f; true }
        DragEvent.ACTION_DROP -> moveToOverflow(event.localState as? RailActionId)
        else -> true
    }

    private fun onQuickDrop(view: View, event: DragEvent): Boolean = when (event.action) {
        DragEvent.ACTION_DRAG_STARTED -> event.localState is RailActionId
        DragEvent.ACTION_DRAG_ENTERED -> { view.alpha = 0.82f; true }
        DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> { view.alpha = 1f; true }
        DragEvent.ACTION_DROP -> moveToQuick(event.localState as? RailActionId, null)
        else -> true
    }

    private fun onQuickItemDrop(target: RailActionId, event: DragEvent): Boolean = when (event.action) {
        DragEvent.ACTION_DRAG_STARTED -> event.localState is RailActionId
        DragEvent.ACTION_DRAG_ENTERED -> { binding.studioQuickActions.alpha = 0.82f; true }
        DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> { binding.studioQuickActions.alpha = 1f; true }
        DragEvent.ACTION_DROP -> moveToQuick(event.localState as? RailActionId, target)
        else -> true
    }

    private fun moveToZone(action: RailActionId?, zone: RailMenuZone, before: RailActionId?): Boolean {
        action ?: return false
        val existingRailCount = stagedLayout.railActions.count { it != action && it != RailActionId.TABS }
        if (action !in stagedLayout.railActions && existingRailCount >= RailMenuLayout.MAX_MOVABLE_RAIL_ACTIONS) {
            announce(getString(R.string.rail_menu_studio_limit_reached))
            return false
        }
        val targetActions = when (zone) {
            RailMenuZone.TOP -> stagedLayout.topActions
            RailMenuZone.ADDRESS -> stagedLayout.addressActions
            RailMenuZone.BOTTOM -> stagedLayout.bottomActions
        }.filterNot { it == action }.toMutableList()
        val position = before?.let(targetActions::indexOf)?.takeIf { it >= 0 } ?: targetActions.size
        targetActions.add(position, action)
        stagedLayout = RailMenuLayoutCodec.normalise(
            stagedLayout.copy(
                topActions = if (zone == RailMenuZone.TOP) targetActions else stagedLayout.topActions - action,
                addressActions = if (zone == RailMenuZone.ADDRESS) targetActions else stagedLayout.addressActions - action,
                bottomActions = if (zone == RailMenuZone.BOTTOM) targetActions else stagedLayout.bottomActions - action,
                quickActions = stagedLayout.quickActions - action,
                overflowActions = stagedLayout.overflowActions - action
            )
        )
        render()
        announce(getString(R.string.rail_menu_studio_moved_to_rail, getString(descriptor(action).label)))
        return true
    }

    private fun moveToOverflow(action: RailActionId?): Boolean {
        action ?: return false
        if (action == RailActionId.TABS) {
            announce(getString(R.string.rail_menu_studio_tabs_required))
            return false
        }
        if (action !in stagedLayout.railActions && action !in stagedLayout.quickActions) return false
        stagedLayout = RailMenuLayoutCodec.normalise(
            stagedLayout.copy(
                topActions = stagedLayout.topActions - action,
                addressActions = stagedLayout.addressActions - action,
                bottomActions = stagedLayout.bottomActions - action,
                quickActions = stagedLayout.quickActions - action,
                overflowActions = (stagedLayout.overflowActions + action)
                    .distinct()
                    .sortedBy(RailActionId.entries::indexOf)
            )
        )
        render()
        announce(getString(R.string.rail_menu_studio_moved_to_menu, getString(descriptor(action).label)))
        return true
    }

    private fun moveToQuick(action: RailActionId?, before: RailActionId?): Boolean {
        action ?: return false
        if (action == RailActionId.TABS) {
            announce(getString(R.string.rail_menu_studio_tabs_required))
            return false
        }
        val existingQuickActions = stagedLayout.quickActions - action
        if (action !in stagedLayout.quickActions &&
            existingQuickActions.size >= RailMenuLayout.MAX_QUICK_ACTIONS
        ) {
            announce(getString(R.string.rail_menu_studio_quick_actions_limit_reached))
            return false
        }
        val position = before?.let(existingQuickActions::indexOf)?.takeIf { it >= 0 }
            ?: existingQuickActions.size
        val quickActions = existingQuickActions.toMutableList().apply { add(position, action) }
        stagedLayout = RailMenuLayoutCodec.normalise(
            stagedLayout.copy(
                topActions = stagedLayout.topActions - action,
                addressActions = stagedLayout.addressActions - action,
                bottomActions = stagedLayout.bottomActions - action,
                quickActions = quickActions,
                overflowActions = stagedLayout.overflowActions - action
            )
        )
        render()
        announce(getString(R.string.rail_menu_studio_return_to_quick_actions))
        return true
    }

    private fun addMoveAccessibilityActions(
        view: View,
        action: RailActionId,
        isRail: Boolean,
        isQuick: Boolean = false
    ) {
        if (isRail && action != RailActionId.TABS) {
            ViewCompat.addAccessibilityAction(view, getString(R.string.rail_menu_studio_return_to_menu)) { _, _ ->
                moveToOverflow(action); true
            }
        }
        if (isRail) {
            ViewCompat.addAccessibilityAction(view, getString(R.string.rail_menu_studio_move_earlier)) { _, _ ->
                moveWithinRail(action, -1); true
            }
            ViewCompat.addAccessibilityAction(view, getString(R.string.rail_menu_studio_move_later)) { _, _ ->
                moveWithinRail(action, 1); true
            }
        }
        if (!isRail) {
            ViewCompat.addAccessibilityAction(view, getString(R.string.rail_menu_studio_add_to_rail)) { _, _ ->
                moveToZone(action, RailMenuZone.BOTTOM, null); true
            }
        }
        if (!isRail && !isQuick) {
            ViewCompat.addAccessibilityAction(view, getString(R.string.rail_menu_studio_return_to_quick_actions)) { _, _ ->
                moveToQuick(action, null); true
            }
        }
    }

    private fun moveWithinRail(action: RailActionId, direction: Int) {
        val zone = when {
            action in stagedLayout.topActions -> RailMenuZone.TOP
            action in stagedLayout.addressActions -> RailMenuZone.ADDRESS
            else -> RailMenuZone.BOTTOM
        }
        val currentActions = when (zone) {
            RailMenuZone.TOP -> stagedLayout.topActions
            RailMenuZone.ADDRESS -> stagedLayout.addressActions
            RailMenuZone.BOTTOM -> stagedLayout.bottomActions
        }
        val currentIndex = currentActions.indexOf(action)
        val targetIndex = (currentIndex + direction).takeIf { it in currentActions.indices } ?: return
        val moved = currentActions.toMutableList().apply {
            removeAt(currentIndex)
            add(targetIndex, action)
        }
        stagedLayout = RailMenuLayoutCodec.normalise(
            when (zone) {
                RailMenuZone.TOP -> stagedLayout.copy(topActions = moved)
                RailMenuZone.ADDRESS -> stagedLayout.copy(addressActions = moved)
                RailMenuZone.BOTTOM -> stagedLayout.copy(bottomActions = moved)
            }
        )
        render()
        announce(getString(R.string.rail_menu_studio_moved_on_rail, getString(descriptor(action).label)))
    }

    private fun announce(message: String) {
        binding.studioRoot.announceForAccessibility(message)
    }

    private fun themeColor(attr: Int): Int = obtainStyledAttributes(intArrayOf(attr)).let {
        try {
            it.getColor(0, 0)
        } finally {
            it.recycle()
        }
    }

    private fun getColorStateListFromTheme(attr: Int) = obtainStyledAttributes(intArrayOf(attr)).let {
        try {
            it.getColorStateList(0)
        } finally {
            it.recycle()
        }
    }

    private fun descriptor(action: RailActionId): StudioActionDescriptor = when (action) {
        RailActionId.TABS -> StudioActionDescriptor(R.drawable.ic_action_tabs, R.string.tabs)
        RailActionId.REFRESH -> StudioActionDescriptor(R.drawable.ic_action_refresh, R.string.action_refresh)
        RailActionId.UTILITY -> StudioActionDescriptor(
            userPreferences.railUtilityAction.iconRes,
            userPreferences.railUtilityAction.labelRes
        )
        RailActionId.BACK -> StudioActionDescriptor(R.drawable.ic_action_back, R.string.action_back)
        RailActionId.FORWARD -> StudioActionDescriptor(R.drawable.ic_action_forward, R.string.action_forward)
        RailActionId.HOME -> StudioActionDescriptor(R.drawable.ic_action_home, R.string.action_homepage)
        RailActionId.ADD_BOOKMARK -> StudioActionDescriptor(R.drawable.ic_action_star, R.string.action_add_bookmark)
        RailActionId.NEW_TAB -> StudioActionDescriptor(R.drawable.ic_action_plus, R.string.action_new_tab)
        RailActionId.INCOGNITO -> StudioActionDescriptor(R.drawable.incognito_mode, R.string.action_incognito)
        RailActionId.FEELING_LUCKY -> StudioActionDescriptor(R.drawable.ic_action_invert, R.string.action_feeling_lucky)
        RailActionId.ADD_TO_HOME -> StudioActionDescriptor(R.drawable.ic_webpage, R.string.action_add_to_homescreen)
        RailActionId.HISTORY -> StudioActionDescriptor(R.drawable.ic_history, R.string.action_history)
        RailActionId.DOWNLOADS -> StudioActionDescriptor(R.drawable.ic_settings_download, R.string.action_downloads)
        RailActionId.BOOKMARKS -> StudioActionDescriptor(R.drawable.ic_bookmark, R.string.action_bookmarks)
        RailActionId.FIND -> StudioActionDescriptor(R.drawable.ic_search, R.string.action_find)
        RailActionId.READ_ALOUD -> StudioActionDescriptor(R.drawable.ic_settings_audio, R.string.action_read_aloud)
        RailActionId.COPY_LINK -> StudioActionDescriptor(R.drawable.ic_insert, R.string.action_copy)
        RailActionId.SCREENSHOT -> StudioActionDescriptor(R.drawable.ic_action_screenshot, R.string.action_screenshot)
        RailActionId.USER_AGENT -> StudioActionDescriptor(R.drawable.ic_action_desktop, R.string.title_user_agent)
        RailActionId.BLOCK_ELEMENT -> StudioActionDescriptor(R.drawable.ic_settings_text, R.string.block_element)
        RailActionId.COOKIE_MANAGER -> StudioActionDescriptor(R.drawable.ic_settings_privacy, R.string.cookie_manager)
        RailActionId.SETTINGS -> StudioActionDescriptor(R.drawable.ic_action_settings, R.string.settings)
        RailActionId.OVERFLOW -> StudioActionDescriptor(R.drawable.ic_action_more_vertical, R.string.action_more)
    }

    private data class StudioActionDescriptor(@DrawableRes val icon: Int, @StringRes val label: Int)

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private companion object {
        const val STATE_LAYOUT = "rail_menu_studio_layout"
    }
}
