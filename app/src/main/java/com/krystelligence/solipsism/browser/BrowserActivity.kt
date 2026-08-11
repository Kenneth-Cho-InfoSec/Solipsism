package com.krystelligence.solipsism.browser

import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.ThemableBrowserActivity
import com.krystelligence.solipsism.animation.AnimationUtils
import com.krystelligence.solipsism.browser.bookmark.BookmarkRecyclerViewAdapter
import com.krystelligence.solipsism.browser.color.ColorAnimator
import com.krystelligence.solipsism.browser.di.MainHandler
import com.krystelligence.solipsism.browser.di.injector
import com.krystelligence.solipsism.browser.image.ImageLoader
import com.krystelligence.solipsism.browser.keys.KeyEventAdapter
import com.krystelligence.solipsism.browser.menu.MenuItemAdapter
import com.krystelligence.solipsism.browser.menu.MenuSelection
import com.krystelligence.solipsism.browser.search.IntentExtractor
import com.krystelligence.solipsism.browser.search.SearchListener
import com.krystelligence.solipsism.browser.search.StyleRemovingTextWatcher
import com.krystelligence.solipsism.browser.tab.BottomDrawerTabRecyclerViewAdapter
import com.krystelligence.solipsism.browser.tab.DesktopTabRecyclerViewAdapter
import com.krystelligence.solipsism.browser.tab.DrawerTabRecyclerViewAdapter
import com.krystelligence.solipsism.browser.tab.TabPager
import com.krystelligence.solipsism.browser.tab.TabViewHolder
import com.krystelligence.solipsism.browser.tab.TabViewState
import com.krystelligence.solipsism.browser.theme.ThemeProvider
import com.krystelligence.solipsism.browser.ui.BookmarkConfiguration
import com.krystelligence.solipsism.browser.ui.TabConfiguration
import com.krystelligence.solipsism.browser.ui.SolipsismRailPosition
import com.krystelligence.solipsism.browser.ui.RailUtilityAction
import com.krystelligence.solipsism.browser.ui.RailActionId
import com.krystelligence.solipsism.browser.ui.UiConfiguration
import com.krystelligence.solipsism.browser.view.ViewDelegate
import com.krystelligence.solipsism.browser.view.delegates.BottomTabViewDelegate
import com.krystelligence.solipsism.browser.view.delegates.DesktopTabViewDelegate
import com.krystelligence.solipsism.browser.view.delegates.DrawerTabViewDelegate
import com.krystelligence.solipsism.browser.download.DownloadPermissionsHelper
import com.krystelligence.solipsism.browser.data.CookieManagerDialog
import com.krystelligence.solipsism.browser.data.CookieManagerRepository
import com.krystelligence.solipsism.browser.view.delegates.SolipsismRailViewDelegate
import com.krystelligence.solipsism.browser.view.targetUrl.LongPress
import com.krystelligence.solipsism.system.SystemBarsController
import com.krystelligence.solipsism.browser.history.DecoyTimeframe
import com.krystelligence.solipsism.constant.HTTP
import com.krystelligence.solipsism.database.Bookmark
import com.krystelligence.solipsism.database.HistoryEntry
import com.krystelligence.solipsism.database.SearchSuggestion
import com.krystelligence.solipsism.database.WebPage
import com.krystelligence.solipsism.database.downloads.DownloadEntry
import com.krystelligence.solipsism.databinding.BrowserActivityBottomBinding
import com.krystelligence.solipsism.databinding.BrowserActivityDesktopBinding
import com.krystelligence.solipsism.databinding.BrowserActivityDrawerBinding
import com.krystelligence.solipsism.databinding.BrowserActivitySolipsismBinding
import com.krystelligence.solipsism.databinding.BrowserBottomTabsBinding
import com.krystelligence.solipsism.dialog.BrowserDialog
import com.krystelligence.solipsism.dialog.DialogItem
import com.krystelligence.solipsism.dialog.SolipsismDialogBuilder
import com.krystelligence.solipsism.extensions.color
import com.krystelligence.solipsism.extensions.drawable
import com.krystelligence.solipsism.extensions.resizeAndShow
import com.krystelligence.solipsism.extensions.snackbar
import com.krystelligence.solipsism.extensions.takeIfInstance
import com.krystelligence.solipsism.extensions.tint
import com.krystelligence.solipsism.qr.QrScannerActivity
import com.krystelligence.solipsism.vault.VaultActivity
import com.krystelligence.solipsism.screenshot.ScreenshotStudioActivity
import com.krystelligence.solipsism.search.SuggestionsAdapter
import com.krystelligence.solipsism.ssl.SslCertificateInfo
import com.krystelligence.solipsism.ssl.createSslDrawableForState
import com.krystelligence.solipsism.ssl.showSslDialog as showSslCertificateDialog
import com.krystelligence.solipsism.utils.ProxyUtils
import com.krystelligence.solipsism.utils.value
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.view.animation.PathInterpolator
import android.widget.AdapterView
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.MenuRes
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.drawable.toDrawable
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.lifecycleScope
import javax.inject.Inject
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.krystelligence.solipsism.release.ReleaseUpdateCoordinator
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The base browser activity that governs the browsing experience for both default and incognito
 * browsers.
 */
abstract class BrowserActivity : ThemableBrowserActivity(), BrowserContract.View {

    private var textToSpeech: TextToSpeech? = null
    private var textToSpeechReady = false
    private var pendingSpeechText: String? = null

    private lateinit var binding: ViewDelegate
    private lateinit var systemBarsController: SystemBarsController
    private lateinit var tabsAdapter: ListAdapter<TabViewState, TabViewHolder>
    private lateinit var bookmarksAdapter: BookmarkRecyclerViewAdapter
    private var activeRecyclerView: RecyclerView? = null
    private var customView: View? = null
    private var customViewOriginalOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var customViewHidSystemUi = false
    private var immersiveFullscreen = false
    private var previousSystemUiVisibility = 0
    private var browserMenuPopup: PopupWindow? = null
    private var bookmarkQuery = ""
    private var currentBookmarks: List<Bookmark> = emptyList()
    private var urlRailTransition: BrowserPresenter.UrlBarTabTransition? = null
    private var railHapticActive = false
    private var railHapticLastMovementAt = 0L

    private var menuItemShare: MenuItem? = null
    private var menuItemCopyLink: MenuItem? = null

    @Inject internal lateinit var presenter: BrowserPresenter
    @Inject internal lateinit var imageLoader: ImageLoader
    @Inject internal lateinit var themeProvider: ThemeProvider
    @Inject internal lateinit var uiConfiguration: UiConfiguration
    @Inject internal lateinit var intentExtractor: IntentExtractor
    @Inject internal lateinit var downloadPermissionsHelper: DownloadPermissionsHelper
    @Inject internal lateinit var cookieManagerRepository: CookieManagerRepository
    @Inject internal lateinit var solipsismDialogBuilder: SolipsismDialogBuilder
    @Inject internal lateinit var tabPager: TabPager
    @Inject @MainHandler internal lateinit var mainHandler: Handler

    private val inputMethodManager: InputMethodManager by lazy {
        getSystemService(InputMethodManager::class.java)
    }

    private val vibrator: Vibrator? by lazy {
        getSystemService(Vibrator::class.java)
    }

    private val railHapticStopRunnable: Runnable = Runnable {
        if (System.currentTimeMillis() - railHapticLastMovementAt >= RAIL_HAPTIC_IDLE_TIMEOUT_MS) {
            stopContinuousRailHaptic()
        } else {
            mainHandler.postDelayed(railHapticStopRunnable, RAIL_HAPTIC_IDLE_TIMEOUT_MS)
        }
    }

    private val expressiveSpatialInterpolator by lazy {
        PathInterpolator(0.2f, 0f, 0f, 1f)
    }

    private val expressiveEffectsInterpolator by lazy {
        PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
    }

    private val qrScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val scannedValue = result.data?.getStringExtra(QrScannerActivity.EXTRA_SCAN_RESULT)
        if (result.resultCode == RESULT_OK && scannedValue != null) {
            presenter.onSearch(scannedValue)
        }
    }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        presenter.onFileChooserResult(result)
    }

    private fun applySolipsismRailPreferences() {
        configureSearchRefreshOrUtilityButton()
        val railWidth = userPreferences.solipsismRailSize.coerceIn(
            MIN_SOLIPSISM_RAIL_WIDTH_DP,
            MAX_SOLIPSISM_RAIL_WIDTH_DP
        ).dp
        val railPosition = activeSolipsismRailPosition()
        val railOnLeft = railPosition == SolipsismRailPosition.LEFT
        val superCompact = userPreferences.solipsismRailSize <= SUPER_COMPACT_RAIL_WIDTH_DP &&
            !userPreferences.largeAccessibilityTargetsEnabled
        val hideRail = !railPosition.isExperimental &&
            ((userPreferences.fullScreenEnabled && userPreferences.hideRailInFullscreen) ||
                immersiveFullscreen)

        if (railPosition.isExperimental) {
            binding.toolbarLayout.visibility = View.VISIBLE
            applyHorizontalSolipsismRailPreferences(railWidth, superCompact)
            applyQrAndTabsButtonPositions()
            configureHorizontalSolipsismRailConstraints(railWidth)
            binding.contentFrame.applyHorizontalRailMargin(railWidth, railPosition == SolipsismRailPosition.TOP)
            binding.progressView.applyHorizontalRailMargin(railWidth, railPosition == SolipsismRailPosition.TOP)
            binding.addressOverlay?.applyHorizontalAddressMargin(
                railWidth,
                railPosition == SolipsismRailPosition.TOP
            )
            binding.findBar.applyHorizontalFindBarMargin(
                railWidth,
                railPosition == SolipsismRailPosition.TOP
            )
            return
        }

        binding.actionHome.visibility = View.VISIBLE
        binding.actionAddBookmark.visibility = View.VISIBLE
        binding.toolbarLayout.visibility = if (hideRail) View.INVISIBLE else View.VISIBLE

        binding.toolbarLayout.updateLayoutParams<FrameLayout.LayoutParams> {
            width = railWidth
            gravity = if (railOnLeft) Gravity.START else Gravity.END
        }
        binding.toolbarLayout.setPaddingRelative(
            if (superCompact) 1.dp else 10.dp,
            if (superCompact) 30.dp else 42.dp,
            if (superCompact) 1.dp else 10.dp,
            if (superCompact) 16.dp else 28.dp
        )
        binding.tabCountView.setShowCount(!superCompact)
        applySuperCompactTabsButton(superCompact)

        if (superCompact) {
            val railButtonSize = 28.dp
            val urlButtonSize = 27.dp
            val navButtonSize = 28.dp
            val addressRailWidth = 28.dp
            val iconPadding = 5.dp

            binding.homeButton.setSquareSize(railButtonSize)
            binding.tabCountView.setSquareSize(24.dp)
            binding.verticalUrlText?.updateLayoutParams<ViewGroup.LayoutParams> {
                width = 148.dp
            }
            binding.verticalUrlText?.textSize = 11.5f
            binding.settingsButton?.setSquareSize(urlButtonSize)
            binding.searchRefresh.setSquareSize(urlButtonSize)
            binding.actionBack.setSquareSize(navButtonSize)
            binding.actionForward.setSquareSize(navButtonSize)
            binding.actionHome.setSquareSize(navButtonSize)
            binding.actionAddBookmark.setSquareSize(navButtonSize)
            binding.toolbar.setSquareSize(navButtonSize)
            binding.toolbar.minimumHeight = navButtonSize
            binding.toolbar.overflowIcon = drawable(R.drawable.ic_action_more_vertical)?.also {
                it.tint(color(R.color.solipsism_rail_text))
            }
            binding.toolbar.contentInsetStartWithNavigation = 0
            binding.toolbar.setContentInsetsRelative(0, 0)
            listOfNotNull(
                binding.settingsButton,
                binding.searchRefresh,
                binding.actionBack,
                binding.actionForward,
                binding.actionHome,
                binding.actionAddBookmark
            ).forEach { it.setPadding(iconPadding, iconPadding, iconPadding, iconPadding) }

            (binding.verticalUrlText?.parent as? View)?.apply {
                updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    width = addressRailWidth
                    topMargin = 8.dp
                    bottomMargin = 8.dp
                }
                setPaddingRelative(1.dp, 4.dp, 1.dp, 4.dp)
            }
            (binding.actionBack.parent as? View)?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                width = addressRailWidth
            }
            listOf(
                binding.actionForward,
                binding.actionHome,
                binding.actionAddBookmark,
                binding.toolbar
            ).forEach { it.setTopMargin(3.dp) }
        }

        applyQrAndTabsButtonPositions()

        val contentRailWidth = if (hideRail) 0 else railWidth
        binding.contentFrame.applyRailMargin(contentRailWidth, railOnLeft)
        binding.progressView.applyRailMargin(contentRailWidth, railOnLeft)
        binding.addressOverlay?.applyRailMargin(
            railWidth = contentRailWidth,
            railOnLeft = railOnLeft,
            oppositeMargin = resources.getDimensionPixelSize(R.dimen.chrome_outer_margin),
            extraRailMargin = if (hideRail) 0 else ADDRESS_OVERLAY_RAIL_GAP_DP.dp
        )
        binding.findBar.applyRailMargin(
            railWidth = contentRailWidth,
            railOnLeft = railOnLeft,
            oppositeMargin = resources.getDimensionPixelSize(R.dimen.chrome_outer_margin),
            extraRailMargin = if (hideRail) 0 else FIND_BAR_RAIL_GAP_DP.dp
        )
    }

    private fun configureSearchRefreshOrUtilityButton() {
        if (uiConfiguration.tabConfiguration == TabConfiguration.SOLIPSISM) {
            val action = userPreferences.railUtilityAction
            binding.searchRefresh.apply {
                setImageResource(action.iconRes)
                contentDescription = getString(action.labelRes)
                setOnClickListener {
                    when (action) {
                        RailUtilityAction.QR -> presenter.onQrButtonClick()
                        RailUtilityAction.VAULT -> presenter.onVaultButtonClick()
                        RailUtilityAction.SCREENSHOT -> presenter.onScreenshotClick()
                    }
                }
                setOnLongClickListener {
                    when (action) {
                        RailUtilityAction.QR -> presenter.onQrButtonLongClick()
                        RailUtilityAction.VAULT -> presenter.onVaultButtonLongClick()
                        RailUtilityAction.SCREENSHOT -> return@setOnLongClickListener false
                    }
                    true
                }
            }
        } else {
            binding.searchRefresh.setOnClickListener { presenter.onRefreshOrStopClick() }
            binding.searchRefresh.setOnLongClickListener { false }
        }
    }

    private fun setImmersiveFullscreen(enabled: Boolean) {
        immersiveFullscreen = enabled
        if (::systemBarsController.isInitialized) {
            systemBarsController.setImmersiveHidden(enabled)
        }
        if (::binding.isInitialized && ::uiConfiguration.isInitialized &&
            uiConfiguration.tabConfiguration == TabConfiguration.SOLIPSISM
        ) {
            applySolipsismRailPreferences()
        }
        snackbar(if (enabled) R.string.fullscreen_enabled else R.string.fullscreen_disabled)
    }

    private fun applyHorizontalSolipsismRailPreferences(railHeight: Int, superCompact: Boolean) {
        // Horizontal rails are intentionally compact so the full action set can coexist with
        // the address controls on narrow phones.
        val buttonSize = when {
            userPreferences.largeAccessibilityTargetsEnabled -> 48.dp
            superCompact -> 28.dp
            else -> 32.dp
        }
        val iconPadding = if (superCompact) 5.dp else 8.dp
        binding.toolbarLayout.updateLayoutParams<FrameLayout.LayoutParams> {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = railHeight
            gravity = if (activeSolipsismRailPosition() == SolipsismRailPosition.TOP) {
                Gravity.TOP
            } else {
                Gravity.BOTTOM
            }
        }
        binding.toolbarLayout.setPaddingRelative(
            if (superCompact) 1.dp else 10.dp,
            if (superCompact) 1.dp else 8.dp,
            if (superCompact) 1.dp else 10.dp,
            if (superCompact) 1.dp else 8.dp
        )
        binding.tabCountView.setShowCount(!superCompact)
        applySuperCompactTabsButton(superCompact)
        binding.homeButton.setSquareSize(buttonSize)
        binding.settingsButton?.setSquareSize(buttonSize)
        binding.searchRefresh.setSquareSize(buttonSize)
        binding.actionBack.setSquareSize(buttonSize)
        binding.actionForward.setSquareSize(buttonSize)
        binding.actionHome.setSquareSize(buttonSize)
        binding.actionAddBookmark.setSquareSize(buttonSize)
        binding.actionHome.visibility = View.GONE
        binding.actionAddBookmark.visibility = View.GONE
        binding.toolbar.setSquareSize(buttonSize)
        binding.toolbar.minimumHeight = buttonSize
        binding.verticalUrlText?.apply {
            rotation = 0f
            textSize = if (superCompact) 11.5f else 15f
            updateLayoutParams<LinearLayout.LayoutParams> {
                width = 0
                height = ViewGroup.LayoutParams.MATCH_PARENT
                weight = 1f
            }
        }
        binding.addressRail?.apply {
            orientation = LinearLayout.HORIZONTAL
            setPaddingRelative(8.dp, 0, 8.dp, 0)
        }
        binding.railNav?.orientation = LinearLayout.HORIZONTAL
        listOfNotNull(
            binding.settingsButton,
            binding.searchRefresh,
            binding.actionBack,
            binding.actionForward,
            binding.actionHome,
            binding.actionAddBookmark
        ).forEach { it.setPadding(iconPadding, iconPadding, iconPadding, iconPadding) }
        binding.toolbar.overflowIcon = drawable(R.drawable.ic_action_more_vertical)?.also {
            it.tint(color(R.color.solipsism_rail_text))
        }
        binding.toolbar.contentInsetStartWithNavigation = 0
        binding.toolbar.setContentInsetsRelative(0, 0)
    }

    private fun applySuperCompactTabsButton(superCompact: Boolean) {
        if (superCompact) {
            binding.homeImageView.apply {
                setImageResource(R.drawable.ic_action_book)
                contentDescription = getString(R.string.tabs)
                visibility = View.VISIBLE
            }
            binding.tabCountView.visibility = View.GONE
        } else {
            binding.homeImageView.visibility = View.GONE
            binding.tabCountView.visibility = View.VISIBLE
        }
    }

    private fun configureHorizontalSolipsismRailConstraints(railHeight: Int) {
        val parentId = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        val addressRail = binding.addressRail ?: return
        val railNav = binding.railNav ?: return
        val tabsInAddress = binding.homeButton.parent === addressRail

        addressRail.updateLayoutParams<androidx.constraintlayout.widget.ConstraintLayout.LayoutParams> {
            width = 0
            height = (railHeight * 0.8f).roundToInt().coerceAtLeast(1)
            startToStart = -1
            startToEnd = if (tabsInAddress) R.id.search_refresh else R.id.home_button
            endToEnd = -1
            endToStart = R.id.rail_nav
            topToTop = parentId
            topToBottom = -1
            bottomToBottom = parentId
            bottomToTop = -1
            topMargin = 0
            bottomMargin = 0
            marginStart = 8.dp
            marginEnd = 8.dp
        }
        railNav.updateLayoutParams<androidx.constraintlayout.widget.ConstraintLayout.LayoutParams> {
            width = ViewGroup.LayoutParams.WRAP_CONTENT
            height = ViewGroup.LayoutParams.MATCH_PARENT
            startToStart = -1
            startToEnd = -1
            endToEnd = parentId
            topToTop = parentId
            bottomToBottom = parentId
            topMargin = 0
            bottomMargin = 0
        }
        binding.homeButton.updateLayoutParams<androidx.constraintlayout.widget.ConstraintLayout.LayoutParams> {
            if (!tabsInAddress) {
                startToStart = parentId
                startToEnd = -1
                endToEnd = -1
                endToStart = -1
                topToTop = parentId
                bottomToBottom = parentId
            }
        }
        listOf(
            binding.actionBack,
            binding.actionForward,
            binding.actionHome,
            binding.actionAddBookmark,
            binding.toolbar
        ).forEach { view ->
            view.updateLayoutParams<LinearLayout.LayoutParams> {
                topMargin = 0
                bottomMargin = 0
                marginStart = 3.dp
            }
        }
        addressRail.requestLayout()
        railNav.requestLayout()
    }

    private fun View.applyHorizontalRailMargin(railHeight: Int, railAtTop: Boolean) {
        updateLayoutParams<ViewGroup.MarginLayoutParams> {
            marginStart = 0
            marginEnd = 0
            topMargin = if (railAtTop) railHeight else 0
            bottomMargin = if (railAtTop) 0 else railHeight
        }
    }

    private fun View.applyHorizontalAddressMargin(railHeight: Int, railAtTop: Boolean) {
        updateLayoutParams<ViewGroup.MarginLayoutParams> {
            marginStart = resources.getDimensionPixelSize(R.dimen.chrome_outer_margin)
            marginEnd = resources.getDimensionPixelSize(R.dimen.chrome_outer_margin)
            topMargin = if (railAtTop) railHeight + resources.getDimensionPixelSize(R.dimen.chrome_outer_margin) else resources.getDimensionPixelSize(R.dimen.chrome_outer_margin)
        }
    }

    private fun View.applyHorizontalFindBarMargin(railHeight: Int, railAtTop: Boolean) {
        updateLayoutParams<ViewGroup.MarginLayoutParams> {
            marginStart = resources.getDimensionPixelSize(R.dimen.chrome_outer_margin)
            marginEnd = resources.getDimensionPixelSize(R.dimen.chrome_outer_margin)
            topMargin = resources.getDimensionPixelSize(R.dimen.chrome_outer_margin)
            bottomMargin = if (railAtTop) resources.getDimensionPixelSize(R.dimen.chrome_outer_margin) else railHeight + resources.getDimensionPixelSize(R.dimen.chrome_outer_margin)
        }
    }

    private fun View.applyRailMargin(
        railWidth: Int,
        railOnLeft: Boolean,
        oppositeMargin: Int = 0,
        extraRailMargin: Int = 0
    ) {
        updateLayoutParams<ViewGroup.MarginLayoutParams> {
            val railMargin = railWidth + extraRailMargin
            marginStart = if (railOnLeft) railMargin else oppositeMargin
            marginEnd = if (railOnLeft) oppositeMargin else railMargin
        }
    }

    private fun View.setSquareSize(size: Int) {
        updateLayoutParams<ViewGroup.LayoutParams> {
            width = size
            height = size
        }
    }

    /**
     * Renders the user-owned side-rail arrangement. The pre-existing physical controls are moved
     * instead of recreated so their browser state, long presses, and accessibility wiring remain
     * intact. Experimental horizontal rails deliberately retain their established layout.
     */
    private fun applyQrAndTabsButtonPositions() {
        if (activeSolipsismRailPosition().isExperimental) return
        renderRailMenuLayout()
    }

    private fun renderRailMenuLayout() {
        val railBinding = binding as? SolipsismRailViewDelegate ?: return
        val layout = userPreferences.railMenuLayout
        val staticControls = mapOf(
            RailActionId.TABS to binding.homeButton,
            RailActionId.REFRESH to (binding.settingsButton ?: return),
            RailActionId.UTILITY to binding.searchRefresh,
            RailActionId.BACK to binding.actionBack,
            RailActionId.FORWARD to binding.actionForward,
            RailActionId.HOME to binding.actionHome,
            RailActionId.ADD_BOOKMARK to binding.actionAddBookmark
        )
        val controls = staticControls.values + binding.toolbar
        controls.forEach { control -> (control.parent as? ViewGroup)?.removeView(control) }
        railBinding.railTopActions.removeAllViews()
        railBinding.addressTopActions.removeAllViews()
        railBinding.addressBottomActions.removeAllViews()
        railBinding.railBottomActions.removeAllViews()
        railBinding.railNav.removeAllViews()

        layout.topActions.forEach { action ->
            val control = staticControls[action] ?: createConfiguredRailAction(action)
            railBinding.railTopActions.addView(control, railActionLayoutParams())
        }
        layout.addressActions.forEach { action ->
            val control = staticControls[action] ?: createConfiguredRailAction(action)
            val container = if (layout.addressActions.indexOf(action) == 0) {
                railBinding.addressTopActions
            } else {
                railBinding.addressBottomActions
            }
            container.addView(control, railActionLayoutParams())
        }
        // Preserve the familiar refresh-at-top / utility-at-bottom address preset while still
        // allowing Studio to move any action into this region. The first address action is above
        // the URL text and all following actions are below it.
        railBinding.railNav.addView(railBinding.railBottomActions)
        layout.bottomActions.forEach { action ->
            val control = staticControls[action] ?: createConfiguredRailAction(action)
            railBinding.railBottomActions.addView(control, railActionLayoutParams())
        }
        railBinding.railNav.addView(binding.toolbar, railActionLayoutParams())
    }

    private fun railActionLayoutParams(): LinearLayout.LayoutParams {
        val size = binding.actionBack.layoutParams.width.takeIf { it > 0 } ?: 42.dp
        return LinearLayout.LayoutParams(size, size).apply {
            gravity = Gravity.CENTER
            bottomMargin = 6.dp
        }
    }

    private fun createConfiguredRailAction(action: RailActionId): ImageButton = ImageButton(this).apply {
        background = drawable(R.drawable.solipsism_blend_button_background)
        contentDescription = railActionContentDescription(action)
        setImageResource(railActionIcon(action))
        setColorFilter(themeProvider.color(R.attr.iconColor))
        setPadding(8.dp, 8.dp, 8.dp, 8.dp)
        isFocusable = true
        isClickable = true
        isEnabled = railActionAvailable(action)
        setOnClickListener { runConfiguredRailAction(action) }
    }

    private fun railActionAvailable(action: RailActionId): Boolean = when (action) {
        RailActionId.BLOCK_ELEMENT, RailActionId.COOKIE_MANAGER ->
            presenter.viewState.displayUrl.startsWith("http://") || presenter.viewState.displayUrl.startsWith("https://")
        else -> true
    }

    private fun runConfiguredRailAction(action: RailActionId) {
        if (!railActionAvailable(action)) return
        when (action) {
            RailActionId.REFRESH -> presenter.onReloadClick()
            RailActionId.UTILITY -> binding.searchRefresh.performClick()
            RailActionId.BACK -> presenter.onBackClick()
            RailActionId.FORWARD -> presenter.onForwardClick()
            RailActionId.HOME -> presenter.onHomeClick()
            RailActionId.ADD_BOOKMARK -> presenter.onStarClick()
            RailActionId.NEW_TAB -> presenter.onMenuClick(MenuSelection.NEW_TAB)
            RailActionId.INCOGNITO -> presenter.onMenuClick(MenuSelection.NEW_INCOGNITO_TAB)
            RailActionId.FEELING_LUCKY -> presenter.onMenuClick(MenuSelection.FEELING_LUCKY)
            RailActionId.ADD_TO_HOME -> presenter.onMenuClick(MenuSelection.ADD_TO_HOME)
            RailActionId.HISTORY -> presenter.onMenuClick(MenuSelection.HISTORY)
            RailActionId.DOWNLOADS -> presenter.onMenuClick(MenuSelection.DOWNLOADS)
            RailActionId.BOOKMARKS -> presenter.onMenuClick(MenuSelection.BOOKMARKS)
            RailActionId.FIND -> presenter.onMenuClick(MenuSelection.FIND)
            RailActionId.READ_ALOUD -> presenter.onReadPageAloud()
            RailActionId.COPY_LINK -> presenter.onMenuClick(MenuSelection.COPY_LINK)
            RailActionId.SCREENSHOT -> presenter.onScreenshotClick()
            RailActionId.USER_AGENT -> presenter.onUserAgentMenuClick()
            RailActionId.BLOCK_ELEMENT -> presenter.onPickElement()
            RailActionId.COOKIE_MANAGER -> presenter.onCookieManager()
            RailActionId.SETTINGS -> presenter.onMenuClick(MenuSelection.SETTINGS)
            else -> Unit
        }
    }

    @DrawableRes
    private fun railActionIcon(action: RailActionId): Int = when (action) {
        RailActionId.TABS -> R.drawable.ic_action_tabs
        RailActionId.REFRESH -> R.drawable.ic_action_refresh
        RailActionId.UTILITY -> userPreferences.railUtilityAction.iconRes
        RailActionId.BACK -> R.drawable.ic_action_back
        RailActionId.FORWARD -> R.drawable.ic_action_forward
        RailActionId.HOME -> R.drawable.ic_action_home
        RailActionId.ADD_BOOKMARK -> R.drawable.ic_action_star
        RailActionId.NEW_TAB -> R.drawable.ic_action_plus
        RailActionId.INCOGNITO -> R.drawable.incognito_mode
        RailActionId.FEELING_LUCKY -> R.drawable.ic_action_invert
        RailActionId.ADD_TO_HOME -> R.drawable.ic_webpage
        RailActionId.HISTORY -> R.drawable.ic_history
        RailActionId.DOWNLOADS -> R.drawable.ic_settings_download
        RailActionId.BOOKMARKS -> R.drawable.ic_bookmark
        RailActionId.FIND -> R.drawable.ic_search
        RailActionId.READ_ALOUD -> R.drawable.ic_settings_audio
        RailActionId.COPY_LINK -> R.drawable.ic_insert
        RailActionId.SCREENSHOT -> R.drawable.ic_action_screenshot
        RailActionId.USER_AGENT -> R.drawable.ic_action_desktop
        RailActionId.BLOCK_ELEMENT -> R.drawable.ic_settings_text
        RailActionId.COOKIE_MANAGER -> R.drawable.ic_settings_privacy
        RailActionId.SETTINGS -> R.drawable.ic_action_settings
        else -> R.drawable.ic_action_more_vertical
    }

    private fun railActionLabel(action: RailActionId): Int = when (action) {
        RailActionId.TABS -> R.string.tabs
        RailActionId.REFRESH -> R.string.action_refresh
        RailActionId.UTILITY -> userPreferences.railUtilityAction.labelRes
        RailActionId.BACK -> R.string.action_back
        RailActionId.FORWARD -> R.string.action_forward
        RailActionId.HOME -> R.string.action_homepage
        RailActionId.ADD_BOOKMARK -> R.string.action_add_bookmark
        RailActionId.NEW_TAB -> R.string.action_new_tab
        RailActionId.INCOGNITO -> R.string.action_incognito
        RailActionId.FEELING_LUCKY -> R.string.action_feeling_lucky
        RailActionId.ADD_TO_HOME -> R.string.action_add_to_homescreen
        RailActionId.HISTORY -> R.string.action_history
        RailActionId.DOWNLOADS -> R.string.action_downloads
        RailActionId.BOOKMARKS -> R.string.action_bookmarks
        RailActionId.FIND -> R.string.action_find
        RailActionId.READ_ALOUD -> R.string.action_read_aloud
        RailActionId.COPY_LINK -> R.string.action_copy
        RailActionId.SCREENSHOT -> R.string.action_screenshot
        RailActionId.USER_AGENT -> R.string.display_as
        RailActionId.BLOCK_ELEMENT -> R.string.block_element
        RailActionId.COOKIE_MANAGER -> R.string.cookie_manager
        RailActionId.SETTINGS -> R.string.settings
        else -> R.string.action_more
    }

    private fun railActionContentDescription(action: RailActionId): String =
        if (action == RailActionId.USER_AGENT) {
            getString(
                R.string.display_as_current,
                when (userPreferences.userAgentChoice) {
                    2 -> getString(R.string.agent_desktop)
                    3 -> getString(R.string.agent_mobile)
                    4 -> getString(R.string.agent_custom)
                    5 -> getString(R.string.agent_folding)
                    else -> getString(R.string.agent_default)
                }
            )
        } else {
            getString(railActionLabel(action))
        }

    private fun toolbarLayoutForRail(): androidx.constraintlayout.widget.ConstraintLayout = binding.toolbarLayout

    private fun horizontalRailButtonParams(width: Int, height: Int) =
        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(width, height).apply {
            startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        }

    private fun View.setTopMargin(topMargin: Int) {
        updateLayoutParams<ViewGroup.MarginLayoutParams> {
            this.topMargin = topMargin
        }
    }

    private fun View.setStartMargin(startMargin: Int) {
        updateLayoutParams<ViewGroup.MarginLayoutParams> {
            marginStart = startMargin
        }
    }

    private inline fun <reified T : ViewGroup.LayoutParams> View.updateLayoutParams(
        block: T.() -> Unit
    ) {
        val params = layoutParams as? T ?: return
        params.block()
        layoutParams = params
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).roundToInt()

    protected abstract fun isIncognito(): Boolean

    @MenuRes
    protected abstract fun menu(): Int

    @DrawableRes
    protected abstract fun homeIcon(): Int

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tabConfiguration = TabConfiguration.SOLIPSISM
        val bottomTabsBinding = if (tabConfiguration == TabConfiguration.DRAWER_BOTTOM) {
            BrowserBottomTabsBinding.inflate(layoutInflater)
        } else {
            null
        }

        binding = when (tabConfiguration) {
            TabConfiguration.DESKTOP -> DesktopTabViewDelegate(
                BrowserActivityDesktopBinding.inflate(layoutInflater)
            )
            TabConfiguration.DRAWER_SIDE -> DrawerTabViewDelegate(
                BrowserActivityDrawerBinding.inflate(layoutInflater)
            )
            TabConfiguration.DRAWER_BOTTOM -> BottomTabViewDelegate(
                BrowserActivityBottomBinding.inflate(layoutInflater)
            )
            TabConfiguration.SOLIPSISM -> SolipsismRailViewDelegate(
                BrowserActivitySolipsismBinding.inflate(layoutInflater)
            )
        }

        setContentView(binding.root)
        systemBarsController = SystemBarsController(
            activity = this,
            protectionView = binding.root.findViewById(R.id.status_bar_protection),
            userPreferences = userPreferences
        )
        systemBarsController.apply()
        setSupportActionBar(binding.toolbar)

        injector.browser2ComponentBuilder()
            .activity(this)
            .browserFrame(binding.contentFrame)
            .toolbarRoot(binding.uiLayout)
            .browserRoot(binding.browserLayoutContainer)
            .bottomTabsLayout(bottomTabsBinding)
            .toolbar(binding.toolbarLayout)
            .initialIntent(intent)
            .incognitoMode(isIncognito())
            .build()
            .inject(this)
        // Rail configuration reads UiConfiguration, which is provided by the activity component.
        // Apply it only after injection so cold starts do not access an uninitialised property.
        applySolipsismRailPreferences()
        configureSolipsismOverflowMenu()

        if (uiConfiguration.tabConfiguration == TabConfiguration.DESKTOP) {
            tabsAdapter = DesktopTabRecyclerViewAdapter(
                context = this,
                onClick = presenter::onTabClick,
                onLongClick = presenter::onTabLongClick,
                onCloseClick = presenter::onTabClose
            )
            binding.desktopTabsList.adapter = tabsAdapter
            binding.desktopTabsList.layoutManager =
                LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
            binding.desktopTabsList.itemAnimator?.takeIfInstance<SimpleItemAnimator>()
                ?.supportsChangeAnimations = false
            binding.drawerTabsList.isVisible = false
            activeRecyclerView = binding.desktopTabsList
        } else {
            tabsAdapter = if (uiConfiguration.tabConfiguration == TabConfiguration.DRAWER_BOTTOM) {
                BottomDrawerTabRecyclerViewAdapter(
                    themeProvider = themeProvider,
                    onClick = presenter::onTabClick,
                    onLongClick = presenter::onTabLongClick,
                    onCloseClick = presenter::onTabClose,
                    onBackClick = { presenter.onBackClick() },
                    onForwardClick = { presenter.onForwardClick() },
                    onHomeClick = { presenter.onHomeClick() }
                )
            } else {
                DrawerTabRecyclerViewAdapter(
                    onClick = presenter::onTabClick,
                    onLongClick = presenter::onTabLongClick,
                    onCloseClick = presenter::onTabClose
                )
            }
            binding.drawerTabsList.adapter = tabsAdapter
            binding.drawerTabsList.layoutManager = LinearLayoutManager(this)
            binding.drawerTabsList.itemAnimator?.takeIfInstance<SimpleItemAnimator>()
                ?.supportsChangeAnimations = false
            binding.desktopTabsList.isVisible = false
            activeRecyclerView = binding.drawerTabsList

            if (uiConfiguration.tabConfiguration == TabConfiguration.DRAWER_SIDE || uiConfiguration.tabConfiguration == TabConfiguration.SOLIPSISM) {
                binding.drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
                    override fun onDrawerOpened(drawerView: View) {
                        if (drawerView == binding.tabDrawer) {
                            presenter.onTabDrawerMoved(true)
                        } else if (drawerView == binding.bookmarkDrawer) {
                            presenter.onBookmarkDrawerMoved(true)
                        }
                    }

                    override fun onDrawerClosed(drawerView: View) {
                        if (drawerView == binding.tabDrawer) {
                            presenter.onTabDrawerMoved(false)
                        } else if (drawerView == binding.bookmarkDrawer) {
                            presenter.onBookmarkDrawerMoved(false)
                        }
                    }
                })
            }
        }

        bookmarksAdapter = BookmarkRecyclerViewAdapter(
            onClick = presenter::onBookmarkClick,
            onLongClick = presenter::onBookmarkLongClick,
            imageLoader = imageLoader,
            showFavicons = { userPreferences.bookmarkFaviconsEnabled }
        )
        binding.bookmarkListView.adapter = bookmarksAdapter
        binding.bookmarkListView.layoutManager = LinearLayoutManager(this)
        binding.bookmarkSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                bookmarkQuery = s?.toString().orEmpty()
                updateBookmarkList(currentBookmarks)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        presenter.onViewAttached(BrowserStateAdapter(this))
        maybeShowFirstRunDonationDialog()
        window.decorView.post {
            lifecycleScope.launch {
                ReleaseUpdateCoordinator(this@BrowserActivity, userPreferences).check(this@BrowserActivity)
            }
        }

        val suggestionsAdapter = SuggestionsAdapter(this, isIncognito = isIncognito()).apply {
            onSuggestionInsertClick = {
                if (it is SearchSuggestion) {
                    binding.search.setText(it.title)
                    binding.search.setSelection(it.title.length)
                } else {
                    binding.search.setText(it.url)
                    binding.search.setSelection(it.url.length)
                }
            }
        }
        binding.search.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            binding.search.clearFocus()
            hideAddressOverlay()
            presenter.onSearchSuggestionClicked(suggestionsAdapter.getItem(position) as WebPage)
            inputMethodManager.hideSoftInputFromWindow(binding.root.windowToken, 0)
        }
        binding.search.setAdapter(suggestionsAdapter)
        val searchListener = SearchListener(
            onConfirm = {
                presenter.onSearch(binding.search.text.toString())
                hideAddressOverlay()
            },
            inputMethodManager = inputMethodManager
        )
        binding.search.setOnEditorActionListener(searchListener)
        binding.search.setOnKeyListener(searchListener)
        binding.search.addTextChangedListener(StyleRemovingTextWatcher())
        binding.search.setOnFocusChangeListener { _, hasFocus ->
            presenter.onSearchFocusChanged(hasFocus)
            binding.search.selectAll()
            if (hasFocus) {
                showAddressOverlay()
            } else {
                hideAddressOverlay()
            }
        }

        binding.findPrevious.setOnClickListener { presenter.onFindPrevious() }
        binding.findNext.setOnClickListener { presenter.onFindNext() }
        binding.findQuit.setOnClickListener {
            binding.findBar.isVisible = false
            binding.findQuery.clearFocus()
            inputMethodManager.hideSoftInputFromWindow(binding.findQuery.windowToken, 0)
            presenter.onFindDismiss()
        }
        binding.findQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                presenter.onFindInPage(s?.toString().orEmpty())
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
        listOfNotNull(
            binding.homeButton,
            binding.actionBack,
            binding.actionForward,
            binding.actionHome,
            binding.newTabButton,
            binding.searchRefresh,
            binding.searchQr,
            binding.actionAddBookmark,
            binding.tabHeaderButton,
            binding.bookmarkBackButton,
            binding.settingsButton,
            binding.verticalUrlText?.parent as? View
        ).forEach(::applyPhysicalPressFeedback)

        binding.homeButton.setOnClickListener { presenter.onTabCountViewClick() }
        binding.actionBack.setOnClickListener { presenter.onBackClick() }
        binding.actionForward.setOnClickListener { presenter.onForwardClick() }
        binding.actionHome.setOnClickListener { presenter.onHomeClick() }
        binding.actionHome.setOnLongClickListener {
            setImmersiveFullscreen(!immersiveFullscreen)
            true
        }
        binding.newTabButton.setOnClickListener { presenter.onNewTabClick() }
        binding.newTabButton.setOnLongClickListener {
            presenter.onNewTabLongClick()
            true
        }
        configureSearchRefreshOrUtilityButton()
        binding.actionAddBookmark.setOnClickListener { presenter.onStarClick() }
        binding.actionPageTools.setOnClickListener { presenter.onToolsClick() }
        binding.tabHeaderButton.setOnClickListener { presenter.onTabMenuClick() }
        binding.bookmarkBackButton.setOnClickListener { presenter.onBookmarkMenuClick() }
        binding.searchSslStatus.setOnClickListener { presenter.onSslIconClick() }
        binding.verticalUrlText?.setOnClickListener { showAddressOverlay() }
        (binding.verticalUrlText?.parent as? View)?.setOnClickListener { showAddressOverlay() }
        installUrlRailGestures()
        binding.settingsButton?.setOnClickListener {
            presenter.onReloadClick()
        }
        binding.settingsButton?.setOnLongClickListener {
            presenter.onJavaScriptDisabledReload()
            true
        }

        binding.searchQr?.setOnClickListener { presenter.onQrButtonClick() }
        binding.searchQr?.setOnLongClickListener {
            presenter.onQrButtonLongClick()
            true
        }

        tabPager.longPressListener = presenter::onPageLongPress

        onBackPressedDispatcher.addCallback {
            if (immersiveFullscreen) {
                setImmersiveFullscreen(false)
            } else {
                presenter.onNavigateBack()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        intentExtractor.extractUrlFromIntent(intent)?.let(presenter::onNewAction)
        super.onNewIntent(intent)
    }

    override fun onDestroy() {
        stopContinuousRailHaptic()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        super.onDestroy()
        presenter.onViewDetached()
    }

    override fun onPause() {
        stopContinuousRailHaptic()
        super.onPause()
        presenter.onViewHidden()
    }

    override fun onResume() {
        super.onResume()
        presenter.onViewResumed()
        if (::systemBarsController.isInitialized) systemBarsController.apply()
        if (::binding.isInitialized && ::uiConfiguration.isInitialized &&
            uiConfiguration.tabConfiguration == TabConfiguration.SOLIPSISM
        ) {
            applySolipsismRailPreferences()
        }
        intentExtractor.extractUrlFromIntent(intent)?.let(presenter::onNewAction)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::systemBarsController.isInitialized) {
            systemBarsController.applyAfterWindowFocus()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (::uiConfiguration.isInitialized &&
            uiConfiguration.tabConfiguration == TabConfiguration.SOLIPSISM
        ) {
            return false
        }
        menuInflater.inflate(R.menu.main, menu)
        runCatching {
            menu.javaClass
                .getDeclaredMethod("setOptionalIconsVisible", Boolean::class.javaPrimitiveType)
                .invoke(menu, true)
        }
        menuItemShare = menu.findItem(R.id.action_share)
        menuItemCopyLink = menu.findItem(R.id.action_copy)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        if (::uiConfiguration.isInitialized &&
            uiConfiguration.tabConfiguration == TabConfiguration.SOLIPSISM
        ) {
            return false
        }
        menuItemShare?.isVisible = presenter.viewState.enableFullMenu
        menuItemCopyLink?.isVisible = presenter.viewState.enableFullMenu
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_new_tab -> presenter.onMenuClick(MenuSelection.NEW_TAB)
            R.id.action_incognito -> presenter.onMenuClick(MenuSelection.NEW_INCOGNITO_TAB)
            R.id.action_share -> presenter.onMenuClick(MenuSelection.SHARE)
            R.id.action_history -> presenter.onMenuClick(MenuSelection.HISTORY)
            R.id.action_downloads -> presenter.onMenuClick(MenuSelection.DOWNLOADS)
            R.id.action_find -> presenter.onMenuClick(MenuSelection.FIND)
            R.id.action_copy -> presenter.onMenuClick(MenuSelection.COPY_LINK)
            R.id.action_bookmarks -> presenter.onMenuClick(MenuSelection.BOOKMARKS)
            R.id.action_settings -> presenter.onMenuClick(MenuSelection.SETTINGS)
            R.id.action_add_to_homescreen -> presenter.onMenuClick(MenuSelection.ADD_TO_HOME)
            R.id.action_add_bookmark -> presenter.onMenuClick(MenuSelection.ADD_BOOKMARK)
        }
        return super.onOptionsItemSelected(item)
    }

    private fun configureSolipsismOverflowMenu() {
        if (uiConfiguration.tabConfiguration != TabConfiguration.SOLIPSISM) {
            return
        }
        binding.toolbar.menu.clear()
        binding.toolbar.navigationIcon = drawable(R.drawable.ic_action_more_vertical)?.also {
            it.tint(color(R.color.solipsism_rail_text))
        }
        binding.toolbar.setNavigationOnClickListener { showBrowserOverflowMenu() }
        binding.toolbar.setOnClickListener { showBrowserOverflowMenu() }
    }

    private fun showBrowserOverflowMenu() {
        browserMenuPopup?.dismiss()
        val menuView = buildBrowserOverflowMenuView()
        val popupWidth = resources.displayMetrics.widthPixels
            .coerceAtMost(BROWSER_MENU_MAX_WIDTH_DP.dp + (BROWSER_MENU_SCREEN_MARGIN_DP * 2).dp) -
            (BROWSER_MENU_SCREEN_MARGIN_DP * 2).dp
        browserMenuPopup = PopupWindow(
            menuView,
            popupWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())
            elevation = 18.dp.toFloat()
            isOutsideTouchable = true
            setAnimationStyle(android.R.style.Animation_Dialog)
        }

        val location = IntArray(2)
        binding.toolbar.getLocationOnScreen(location)
        val railPosition = activeSolipsismRailPosition()
        val x = if (railPosition.isExperimental) {
            ((resources.displayMetrics.widthPixels - popupWidth) / 2).coerceAtLeast(0)
        } else if (railPosition == SolipsismRailPosition.LEFT) {
            BROWSER_MENU_SCREEN_MARGIN_DP.dp
        } else {
            resources.displayMetrics.widthPixels - popupWidth - BROWSER_MENU_SCREEN_MARGIN_DP.dp
        }
        val y = when (railPosition) {
            SolipsismRailPosition.TOP -> (location[1] + binding.toolbarLayout.height + 12.dp)
                .coerceAtMost(resources.displayMetrics.heightPixels - BROWSER_MENU_SCREEN_MARGIN_DP.dp)
            SolipsismRailPosition.BOTTOM -> (location[1] - 12.dp - binding.toolbarLayout.height)
                .coerceAtLeast(BROWSER_MENU_SCREEN_MARGIN_DP.dp)
            else -> (location[1] - 12.dp).coerceAtLeast(BROWSER_MENU_SCREEN_MARGIN_DP.dp)
        }
        browserMenuPopup?.showAtLocation(binding.root, Gravity.TOP or Gravity.START, x, y)
        menuView.alpha = 0f
        menuView.scaleX = 0.96f
        menuView.scaleY = 0.96f
        menuView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220L)
            .setInterpolator(expressiveSpatialInterpolator)
            .start()
    }

    private fun buildBrowserOverflowMenuView(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = drawable(R.drawable.browser_overflow_menu_background)
            clipToOutline = true
            elevation = 18.dp.toFloat()
            setPadding(9.dp, 9.dp, 9.dp, 9.dp)
        }

        val layout = userPreferences.railMenuLayout
        val quickActions = layout.quickActions.filter(::railActionAvailable)
        if (layout.quickActionsEnabled && quickActions.isNotEmpty()) {
            container.addView(LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    42.dp
                ).apply { bottomMargin = 5.dp }
                gravity = Gravity.CENTER
                orientation = LinearLayout.HORIZONTAL
                quickActions.forEach { action -> addView(createQuickActionButton(action)) }
            })
            container.addView(createMenuDivider())
        }

        layout.visibleOverflowActions
            .filter(::railActionAvailable)
            .forEach { action ->
                container.addView(createActionMenuRow(railActionIcon(action), railActionContentDescription(action)) {
                    browserMenuPopup?.dismiss()
                    runConfiguredRailAction(action)
                })
            }

        return container
    }

    private fun createQuickActionButton(action: RailActionId): ImageButton =
        ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(40.dp, 40.dp).apply {
                marginStart = 2.dp
                marginEnd = 2.dp
            }
            background = drawable(R.drawable.browser_overflow_quick_button_background)
            contentDescription = railActionContentDescription(action)
            setPadding(8.dp, 8.dp, 8.dp, 8.dp)
            setImageResource(railActionIcon(action))
            setColorFilter(themeProvider.color(R.attr.colorOnSurface))
            scaleType = ImageView.ScaleType.CENTER
            isEnabled = railActionAvailable(action)
            setOnClickListener {
                browserMenuPopup?.dismiss()
                runConfiguredRailAction(action)
            }
        }

    private fun createMenuRow(
        icon: Int,
        title: Int,
        selection: MenuSelection
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = drawable(R.drawable.browser_overflow_menu_item_background)
        isClickable = true
        isFocusable = true
        setPadding(4.dp, 3.dp, 6.dp, 3.dp)
        minimumHeight = 38.dp
        addView(ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(30.dp, 30.dp)
            setPadding(6.dp, 6.dp, 6.dp, 6.dp)
            setImageResource(icon)
            setColorFilter(themeProvider.color(R.attr.colorOnSurfaceVariant))
        })
        addView(TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = 7.dp }
            text = getString(title)
            setTextColor(themeProvider.color(R.attr.colorOnSurface))
            textSize = 15f
            maxLines = 1
            includeFontPadding = true
        })
        setOnClickListener {
            browserMenuPopup?.dismiss()
            presenter.onMenuClick(selection)
        }
    }

    private fun createActionMenuRow(icon: Int, title: String, action: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = drawable(R.drawable.browser_overflow_menu_item_background)
            isClickable = true
            isFocusable = true
            setPadding(4.dp, 3.dp, 6.dp, 3.dp)
            minimumHeight = 38.dp
            addView(ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(30.dp, 30.dp)
                setPadding(6.dp, 6.dp, 6.dp, 6.dp)
                setImageResource(icon)
                setColorFilter(themeProvider.color(R.attr.colorOnSurfaceVariant))
            })
            addView(TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = 7.dp }
                text = title
                setTextColor(themeProvider.color(R.attr.colorOnSurface))
                textSize = 15f
                maxLines = 1
            })
            setOnClickListener {
                browserMenuPopup?.dismiss()
                action()
            }
        }

    private fun createMenuDivider(): View =
        View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
                1.dp
            ).apply {
                setMargins(4.dp, 5.dp, 4.dp, 5.dp)
            }
            setBackgroundColor(themeProvider.color(R.attr.colorOutlineVariant))
            alpha = 0.7f
        }

    /**
     * @see BrowserContract.View.openBookmarkDrawer
     */
    override fun openBookmarkDrawer() {
        binding.drawerLayout.openDrawer(binding.bookmarkDrawer)
    }

    /**
     * @see BrowserContract.View.closeBookmarkDrawer
     */
    override fun closeBookmarkDrawer() {
        binding.drawerLayout.closeDrawer(binding.bookmarkDrawer)
    }

    /**
     * @see BrowserContract.View.openTabDrawer
     */
    override fun openTabDrawer() {
        binding.drawerLayout.openDrawer(binding.tabDrawer)
    }

    /**
     * @see BrowserContract.View.closeTabDrawer
     */
    override fun closeTabDrawer() {
        binding.drawerLayout.closeDrawer(binding.tabDrawer)
    }

    /**
     * @see BrowserContract.View.showToolbar
     */
    override fun showToolbar() {
        if (uiConfiguration.tabConfiguration != TabConfiguration.SOLIPSISM) {
            binding.uiLayout.animate().translationY(0f).setDuration(200).start()
        }
    }

    /**
     * @see BrowserContract.View.showToolsDialog
     */
    override fun showToolsDialog(
        areAdsAllowed: Boolean,
        shouldShowAdBlockOption: Boolean,
        shouldShowElementPicker: Boolean
    ) {
        BrowserDialog.showWithIcons(
            this, getString(R.string.dialog_tools_title),
            DialogItem(
                title = R.string.dialog_toggle_desktop,
                icon = R.drawable.ic_action_desktop,
                onClick = presenter::onToggleDesktopAgent
            ),
            DialogItem(
                title = if (areAdsAllowed) R.string.dialog_adblock_disable_for_site else R.string.dialog_adblock_enable_for_site,
                icon = R.drawable.ic_block,
                isConditionMet = shouldShowAdBlockOption,
                onClick = presenter::onToggleAdBlocking
            ),
            DialogItem(
                title = R.string.block_element,
                icon = R.drawable.ic_settings_text,
                isConditionMet = shouldShowElementPicker,
                onClick = presenter::onPickElement
            ),
            DialogItem(
                title = R.string.cookie_manager,
                icon = R.drawable.ic_settings_privacy,
                isConditionMet = shouldShowElementPicker,
                onClick = presenter::onCookieManager
            )
        )
    }

    override fun showUserAgentDialog(currentChoice: Int) {
        val choices = resources.getStringArray(R.array.user_agent)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.display_as)
            .setSingleChoiceItems(choices, (currentChoice - 1).coerceIn(0, choices.lastIndex)) { dialog, which ->
                presenter.onUserAgentChoiceSelected(which + 1)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .resizeAndShow()
    }

    override fun showCustomUserAgentDialog(currentValue: String) {
        BrowserDialog.showEditText(
            this,
            R.string.display_as,
            R.string.agent_custom,
            currentValue,
            R.string.action_ok
        ) { presenter.onCustomUserAgentEntered(it) }
    }

    override fun showCookieManager(url: String) {
        CookieManagerDialog.show(this, url, cookieManagerRepository)
    }

    override fun showScreenshot(bitmap: Bitmap) {
        showScreenshotAnimation(bitmap)
        lifecycleScope.launch {
            runCatching {
                val file = withContext(Dispatchers.IO) {
                    File(cacheDir, "shared/screenshot-studio-source.png").also { it.parentFile?.mkdirs() }.apply {
                        FileOutputStream(this).use { output -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) }
                    }
                }
                delay(SCREENSHOT_ANIMATION_DURATION_MS)
                startActivity(Intent(this@BrowserActivity, ScreenshotStudioActivity::class.java).apply {
                    putExtra(ScreenshotStudioActivity.EXTRA_PATH, file.absolutePath)
                })
            }
                .onFailure { snackbar(R.string.screenshot_failed) }
        }
    }

    override fun showScreenshotCaptureFailed() {
        snackbar(R.string.screenshot_failed)
    }

    override fun openVault() {
        startActivity(Intent(this, VaultActivity::class.java))
    }

    override fun showVaultSaved() {
        snackbar(R.string.vault_saved)
    }

    override fun showVaultSaveFailed() {
        snackbar(R.string.vault_save_failed)
    }

    private fun showScreenshotAnimation(bitmap: Bitmap) {
        val contentFrame = binding.contentFrame
        if (contentFrame.width <= 0 || contentFrame.height <= 0) return

        val radius = 24.dp.toFloat()
        val outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        fun View.applyScreenshotShape() {
            this.outlineProvider = outlineProvider
            this.clipToOutline = true
            this.pivotX = contentFrame.width / 2f
            this.pivotY = contentFrame.height / 2f
        }

        val snapshot = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_XY
            applyScreenshotShape()
        }
        val whiteFilter = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.WHITE)
            alpha = 0.5f
            applyScreenshotShape()
        }

        contentFrame.addView(snapshot)
        contentFrame.addView(whiteFilter)
        if (userPreferences.hapticsEnabled && userPreferences.screenshotHapticsEnabled) {
            vibrator?.takeIf { it.hasVibrator() }?.vibrate(
                    VibrationEffect.createOneShot(
                        userPreferences.screenshotHapticDurationMs.coerceIn(50, 1000).toLong(),
                        ((255 * userPreferences.screenshotHapticIntensity.coerceIn(0, 100)) / 100)
                            .coerceIn(1, 255)
                )
            )
        }

        snapshot.animate()
            .scaleX(SCREENSHOT_SHRINK_SCALE)
            .scaleY(SCREENSHOT_SHRINK_SCALE)
            .setDuration(SCREENSHOT_ANIMATION_DURATION_MS / 2)
            .setInterpolator(expressiveEffectsInterpolator)
            .withEndAction {
                snapshot.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(SCREENSHOT_ANIMATION_DURATION_MS / 2)
                    .setInterpolator(expressiveSpatialInterpolator)
                    .withEndAction {
                        contentFrame.removeView(whiteFilter)
                        contentFrame.removeView(snapshot)
                    }
                    .start()
            }
            .start()
        whiteFilter.animate()
            .alpha(0f)
            .setDuration(SCREENSHOT_ANIMATION_DURATION_MS)
            .setInterpolator(expressiveEffectsInterpolator)
            .start()
    }

    private fun saveScreenshot(bitmap: Bitmap) {
        val fileName = "Solipsism_${System.currentTimeMillis()}.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Solipsism")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to create screenshot media entry")
            try {
                contentResolver.openOutputStream(uri)?.use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                } ?: error("Unable to open screenshot output")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            } catch (error: Throwable) {
                contentResolver.delete(uri, null, null)
                throw error
            }
        } else {
            check(MediaStore.Images.Media.insertImage(contentResolver, bitmap, fileName, null) != null)
        }
    }

    /**
     * @see BrowserContract.View.showLocalFileBlockedDialog
     */
    override fun showLocalFileBlockedDialog() {
        BrowserDialog.showPositiveNegativeDialog(
            this,
            R.string.title_warning,
            R.string.message_blocked_local,
            positiveButton = DialogItem(title = R.string.action_allow) { presenter.onConfirmOpenLocalFile(true) },
            negativeButton = DialogItem(title = R.string.action_dont_allow) { presenter.onConfirmOpenLocalFile(false) },
            onCancel = { presenter.onConfirmOpenLocalFile(false) }
        )
    }

    /**
     * @see BrowserContract.View.showFileChooser
     */
    override fun showFileChooser(intent: Intent) {
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        fileChooserLauncher.launch(Intent.createChooser(intent, getString(R.string.title_file_chooser)))
    }

    /**
     * @see BrowserContract.View.showCustomView
     */
    override fun showCustomView(view: View) {
        customView?.let(binding.root::removeView)
        customView = view
        customViewOriginalOrientation = requestedOrientation
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        view.setBackgroundColor(color(android.R.color.black))
        binding.root.addView(
            view,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        binding.root.bringChildToFront(view)
        binding.uiLayout.isVisible = false
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        view.requestLayout()
        customViewHidSystemUi = true
        previousSystemUiVisibility = window.decorView.systemUiVisibility
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = previousSystemUiVisibility or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    /**
     * @see BrowserContract.View.hideCustomView
     */
    override fun hideCustomView() {
        customView?.let(binding.root::removeView)
        customView = null
        binding.uiLayout.isVisible = true
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        requestedOrientation = customViewOriginalOrientation
        if (customViewHidSystemUi) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.decorView.systemUiVisibility = previousSystemUiVisibility
            customViewHidSystemUi = false
        }
    }

    /**
     * @see BrowserContract.View.clearSearchFocus
     */
    override fun clearSearchFocus() {
        binding.search.clearFocus()
    }

    override fun launchQrScanner() {
        qrScannerLauncher.launch(Intent(this, QrScannerActivity::class.java))
    }

    private fun installUrlRailGestures() {
        val rail = binding.verticalUrlText?.parent as? View ?: return
        var downY = 0f
        var downX = 0f
        var dragProgress = 0f
        val listener = View.OnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.rawY
                    downX = event.rawX
                    dragProgress = 0f
                    urlRailTransition = null
                    tabPager.cancelVerticalTabSwitch()
                    view.animate().cancel()
                    view.animate()
                        .scaleX(0.96f)
                        .scaleY(0.96f)
                        .setDuration(120L)
                        .setInterpolator(expressiveEffectsInterpolator)
                        .start()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - downY
                    val dx = event.rawX - downX
                    if (abs(dy) > URL_RAIL_DRAG_START_DP.dp &&
                        abs(dy) > abs(dx) * 1.15f
                    ) {
                        val direction = if (dy < 0f) 1 else -1
                        val transition = urlRailTransition
                            ?.takeIf { it.direction == direction }
                            ?: presenter.previewUrlBarSwipeTab(direction)
                        if (transition != null) {
                            urlRailTransition = transition
                            dragProgress = (abs(dy) / URL_RAIL_SWIPE_THRESHOLD_DP.dp.toFloat())
                                .coerceIn(0f, 0.98f)
                            tabPager.previewVerticalTabSwitch(
                                currentId = transition.currentId,
                                targetId = transition.targetId,
                                direction = direction,
                                progress = dragProgress
                            )
                            continueRailHaptic(dragProgress)
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val dy = event.rawY - downY
                    val dx = event.rawX - downX
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(180L)
                        .setInterpolator(expressiveSpatialInterpolator)
                        .start()
                    val transition = urlRailTransition
                    if (transition != null && dragProgress >= URL_RAIL_COMMIT_PROGRESS) {
                        stopContinuousRailHaptic()
                        animateUrlRailTabSwitch(view, transition.direction)
                        fadePixelHaptic()
                        tabPager.commitVerticalTabSwitch(
                            targetId = transition.targetId,
                            direction = transition.direction
                        ) {
                            presenter.commitUrlBarSwipeTab(transition.targetId)
                        }
                    } else if (abs(dy) > URL_RAIL_SWIPE_THRESHOLD_DP.dp &&
                        abs(dy) > abs(dx) * 1.2f
                    ) {
                        val direction = if (dy < 0f) 1 else -1
                        val quickTransition = presenter.previewUrlBarSwipeTab(direction)
                        if (quickTransition != null) {
                            stopContinuousRailHaptic()
                            animateUrlRailTabSwitch(view, direction)
                            fadePixelHaptic()
                            tabPager.previewVerticalTabSwitch(
                                currentId = quickTransition.currentId,
                                targetId = quickTransition.targetId,
                                direction = direction,
                                progress = 0.2f
                            )
                            tabPager.commitVerticalTabSwitch(
                                targetId = quickTransition.targetId,
                                direction = direction
                            ) {
                                presenter.commitUrlBarSwipeTab(quickTransition.targetId)
                            }
                        }
                    } else {
                        tabPager.cancelVerticalTabSwitch()
                        view.performClick()
                        showAddressOverlay()
                    }
                    urlRailTransition = null
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    stopContinuousRailHaptic()
                    tabPager.cancelVerticalTabSwitch()
                    urlRailTransition = null
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150L)
                        .setInterpolator(expressiveSpatialInterpolator)
                        .start()
                    true
                }

                else -> true
            }
        }
        rail.setOnTouchListener(listener)
    }

    private fun animateUrlRailTabSwitch(view: View, direction: Int) {
        val distance = 10.dp.toFloat() * direction
        view.animate().cancel()
        view.translationY = -distance
        view.alpha = 0.72f
        view.animate()
            .translationY(0f)
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(240L)
            .setInterpolator(expressiveSpatialInterpolator)
            .start()
    }

    private fun continueRailHaptic(progress: Float) {
        if (!userPreferences.hapticsEnabled || !userPreferences.railHapticsEnabled) return
        railHapticLastMovementAt = System.currentTimeMillis()
        if (!railHapticActive) {
            val progressCurve = if (userPreferences.railHapticCurve == 1) {
                val eased = progress.coerceIn(0f, 1f)
                eased * eased * (3f - 2f * eased)
            } else {
                progress.coerceIn(0f, 1f)
            }
            val intensity = userPreferences.railHapticsIntensity.coerceIn(0, 100) / 100f
            val amplitude = ((32 + (progressCurve * 112f)) * intensity)
                .roundToInt().coerceIn(1, 180)
            vibrator?.takeIf { it.hasVibrator() }?.vibrate(
                VibrationEffect.createOneShot(RAIL_HAPTIC_MAX_DURATION_MS, amplitude)
            )
            railHapticActive = true
        }
        mainHandler.removeCallbacks(railHapticStopRunnable)
        mainHandler.postDelayed(railHapticStopRunnable, RAIL_HAPTIC_IDLE_TIMEOUT_MS)
    }

    private fun stopContinuousRailHaptic() {
        mainHandler.removeCallbacks(railHapticStopRunnable)
        if (railHapticActive) {
            vibrator?.cancel()
            railHapticActive = false
        }
    }

    private fun fadePixelHaptic() {
        if (!userPreferences.hapticsEnabled ||
            !userPreferences.railHapticsEnabled ||
            !userPreferences.railCompletionHapticsEnabled
        ) return
        val intensity = userPreferences.railCompletionHapticsIntensity.coerceIn(0, 100) / 100f
        vibrator?.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0L, 18L, 32L, 14L, 38L, 8L),
                intArrayOf(
                    (145 * intensity).roundToInt(),
                    0,
                    (88 * intensity).roundToInt(),
                    0,
                    (34 * intensity).roundToInt(),
                    0
                ),
                -1
            )
        )
    }

    override fun renderState(viewState: BrowserViewState) {
        renderState(viewState.asPartial())
    }

    fun renderState(viewState: PartialBrowserViewState) {
        viewState.displayUrl?.let { displayUrl ->
            if (!binding.search.hasFocus()) {
                binding.search.setText(displayUrl)
            }
            binding.verticalUrlText?.text = displayUrl
        }
        viewState.progress?.let {
            binding.progressView.progress = it
            binding.progressView.isVisible = it in 1..99
        }
        viewState.isRefresh?.let {
            binding.settingsButton?.setImageResource(
                if (it) R.drawable.ic_action_refresh else R.drawable.ic_action_delete
            )
            if (uiConfiguration.tabConfiguration == TabConfiguration.SOLIPSISM) {
                configureSearchRefreshOrUtilityButton()
            } else {
                binding.searchRefresh.setImageResource(
                    if (it) R.drawable.ic_action_refresh else R.drawable.ic_action_delete
                )
            }
        }
        viewState.isBackEnabled?.let { binding.actionBack.isEnabled = it }
        viewState.isForwardEnabled?.let { binding.actionForward.isEnabled = it }
        viewState.isBookmarked?.let {
            binding.actionAddBookmark.isSelected = it
        }
        viewState.sslState?.let {
            binding.searchSslStatus.setImageDrawable(createSslDrawableForState(it))
            binding.searchSslStatus.updateVisibilityForDrawable()
        }
        viewState.bookmarks?.let(::updateBookmarkList)
        viewState.findInPage?.let { query ->
            val shouldShowFind = query.isNotEmpty() || binding.findBar.isVisible && binding.findQuery.hasFocus()
            binding.findBar.isVisible = shouldShowFind
            if (binding.findQuery.text.toString() != query) {
                binding.findQuery.setText(query)
            }
            if (!shouldShowFind) {
                binding.findQuery.clearFocus()
                inputMethodManager.hideSoftInputFromWindow(binding.findQuery.windowToken, 0)
            }
        }
        val suggestionsAdapter = binding.search.adapter as? SuggestionsAdapter
        suggestionsAdapter?.refreshBookmarks()
    }

    private fun updateBookmarkList(bookmarks: List<Bookmark>) {
        currentBookmarks = bookmarks
        val query = bookmarkQuery.trim().lowercase()
        bookmarksAdapter.submitList(
            if (query.isBlank()) bookmarks else bookmarks.filter {
                it.title.lowercase().contains(query) || it.url.lowercase().contains(query)
            }
        )
    }

    override fun renderTabs(tabs: List<TabViewState>) {
        tabsAdapter.submitList(tabs)
        if (uiConfiguration.tabConfiguration == TabConfiguration.DRAWER_BOTTOM ||
            uiConfiguration.tabConfiguration == TabConfiguration.SOLIPSISM) {
            binding.tabCountView.updateTabCount(tabs.size)
        }
    }

    override fun showAddBookmarkDialog(title: String, url: String, folders: List<String>) {
        solipsismDialogBuilder.showAddBookmarkDialog(this, title, url, folders, presenter::onBookmarkConfirmed)
    }

    override fun showBookmarkOptionsDialog(bookmark: Bookmark.Entry) {
        BrowserDialog.show(
            this, R.string.dialog_bookmark,
            DialogItem(title = R.string.dialog_open_new_tab) {
                presenter.onBookmarkOptionClick(bookmark, BrowserContract.BookmarkOptionEvent.NEW_TAB)
            },
            DialogItem(title = R.string.dialog_open_background_tab) {
                presenter.onBookmarkOptionClick(bookmark, BrowserContract.BookmarkOptionEvent.BACKGROUND_TAB)
            },
            DialogItem(title = R.string.dialog_open_incognito_tab, isConditionMet = !isIncognito()) {
                presenter.onBookmarkOptionClick(bookmark, BrowserContract.BookmarkOptionEvent.INCOGNITO_TAB)
            },
            DialogItem(title = R.string.action_share) {
                presenter.onBookmarkOptionClick(bookmark, BrowserContract.BookmarkOptionEvent.SHARE)
            },
            DialogItem(title = R.string.action_copy) {
                presenter.onBookmarkOptionClick(bookmark, BrowserContract.BookmarkOptionEvent.COPY_LINK)
            },
            DialogItem(title = R.string.dialog_remove_bookmark) {
                presenter.onBookmarkOptionClick(bookmark, BrowserContract.BookmarkOptionEvent.REMOVE)
            },
            DialogItem(title = R.string.action_edit) {
                presenter.onBookmarkOptionClick(bookmark, BrowserContract.BookmarkOptionEvent.EDIT)
            }
        )
    }

    override fun showEditBookmarkDialog(title: String, url: String, folder: String, folders: List<String>) {
        solipsismDialogBuilder.showEditBookmarkDialog(this, title, url, folder, folders, presenter::onBookmarkEditConfirmed)
    }

    override fun showFolderOptionsDialog(folder: Bookmark.Folder) {
        BrowserDialog.show(
            this, R.string.dialog_folder,
            DialogItem(title = R.string.action_rename) {
                presenter.onFolderOptionClick(folder, BrowserContract.FolderOptionEvent.RENAME)
            },
            DialogItem(title = R.string.dialog_remove_folder) {
                presenter.onFolderOptionClick(folder, BrowserContract.FolderOptionEvent.REMOVE)
            }
        )
    }

    override fun showEditFolderDialog(title: String) {
        BrowserDialog.showEditText(
            this, R.string.title_rename_folder, R.string.hint_title, title, R.string.action_ok
        ) { presenter.onBookmarkFolderRenameConfirmed(title, it) }
    }

    override fun showDownloadOptionsDialog(download: DownloadEntry) {
        BrowserDialog.show(
            this, download.title,
            DialogItem(title = R.string.action_donate) {
                openDonationPage()
            },
            DialogItem(title = R.string.dialog_delete_download) {
                presenter.onDownloadOptionClick(download, BrowserContract.DownloadOptionEvent.DELETE)
            },
            DialogItem(title = R.string.dialog_delete_all_downloads) {
                presenter.onDownloadOptionClick(download, BrowserContract.DownloadOptionEvent.DELETE_ALL)
            }
        )
    }

    private fun maybeShowFirstRunDonationDialog() {
        val preferences = getSharedPreferences(DONATION_PREFERENCES, MODE_PRIVATE)
        if (preferences.getBoolean(DONATION_PROMPT_SHOWN, false) || isIncognito()) {
            return
        }

        preferences.edit { putBoolean(DONATION_PROMPT_SHOWN, true) }
        mainHandler.post {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.donation_prompt_title)
                .setMessage(R.string.donation_prompt_message)
                .setPositiveButton(R.string.action_donate) { _, _ -> openDonationPage() }
                .setNegativeButton(R.string.action_not_now, null)
                .resizeAndShow()
        }
    }

    private fun openDonationPage() {
        startActivity(Intent(Intent.ACTION_VIEW, KO_FI_URL.toUri()))
    }

    override fun showHistoryOptionsDialog(historyEntry: HistoryEntry) {
        BrowserDialog.show(
            this, R.string.dialog_history_long_press,
            DialogItem(title = R.string.dialog_open_new_tab) {
                presenter.onHistoryOptionClick(historyEntry, BrowserContract.HistoryOptionEvent.NEW_TAB)
            },
            DialogItem(title = R.string.dialog_open_background_tab) {
                presenter.onHistoryOptionClick(historyEntry, BrowserContract.HistoryOptionEvent.BACKGROUND_TAB)
            },
            DialogItem(title = R.string.dialog_open_incognito_tab, isConditionMet = !isIncognito()) {
                presenter.onHistoryOptionClick(historyEntry, BrowserContract.HistoryOptionEvent.INCOGNITO_TAB)
            },
            DialogItem(title = R.string.action_share) {
                presenter.onHistoryOptionClick(historyEntry, BrowserContract.HistoryOptionEvent.SHARE)
            },
            DialogItem(title = R.string.action_copy) {
                presenter.onHistoryOptionClick(historyEntry, BrowserContract.HistoryOptionEvent.COPY_LINK)
            },
            DialogItem(title = R.string.dialog_remove_from_history) {
                presenter.onHistoryOptionClick(historyEntry, BrowserContract.HistoryOptionEvent.REMOVE)
            }
        )
    }

    override fun showFindInPageDialog() {
        binding.findBar.isVisible = true
        binding.findQuery.requestFocus()
        binding.findQuery.setSelection(binding.findQuery.text.length)
        inputMethodManager.showSoftInput(binding.findQuery, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun speakPageText(text: String) {
        if (text.isBlank()) {
            snackbar(R.string.text_to_speech_no_text)
            return
        }
        if (textToSpeech?.isSpeaking == true) {
            textToSpeech?.stop()
            pendingSpeechText = null
            snackbar(R.string.text_to_speech_stopped)
            return
        }

        pendingSpeechText = text
        if (textToSpeech == null) {
            textToSpeechReady = false
            textToSpeech = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeechReady = true
                    pendingSpeechText?.also {
                        pendingSpeechText = null
                        enqueueSpeech(it)
                    }
                } else {
                    pendingSpeechText = null
                    snackbar(R.string.text_to_speech_unavailable)
                }
            }
        } else if (textToSpeechReady) {
            pendingSpeechText = null
            enqueueSpeech(text)
        }
    }

    private fun enqueueSpeech(text: String) {
        val engine = textToSpeech ?: return
        val chunks = text
            .split(Regex("(?<=[.!?])\\s+"))
            .fold(mutableListOf<String>()) { result, sentence ->
                var remaining = sentence.trim()
                while (remaining.length > TTS_CHUNK_LENGTH) {
                    result += remaining.take(TTS_CHUNK_LENGTH)
                    remaining = remaining.drop(TTS_CHUNK_LENGTH).trimStart()
                }
                if (remaining.isNotBlank()) {
                    val current = result.lastOrNull().orEmpty()
                    if (current.length + remaining.length + 1 <= TTS_CHUNK_LENGTH) {
                        if (result.isEmpty()) result += remaining
                        else result[result.lastIndex] = "$current $remaining"
                    } else {
                        result += remaining
                    }
                }
                result
            }
        chunks.forEachIndexed { index, chunk ->
            engine.speak(
                chunk,
                if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                null,
                "solipsism-read-aloud-$index"
            )
        }
        snackbar(R.string.text_to_speech_started)
    }

    override fun showLinkLongPressDialog(longPress: LongPress) {
        BrowserDialog.show(
            this, longPress.targetUrl,
            DialogItem(title = R.string.dialog_open_new_tab) {
                presenter.onLinkLongPressEvent(longPress, BrowserContract.LinkLongPressEvent.NEW_TAB)
            },
            DialogItem(title = R.string.dialog_open_background_tab) {
                presenter.onLinkLongPressEvent(longPress, BrowserContract.LinkLongPressEvent.BACKGROUND_TAB)
            },
            DialogItem(title = R.string.dialog_open_incognito_tab, isConditionMet = !isIncognito()) {
                presenter.onLinkLongPressEvent(longPress, BrowserContract.LinkLongPressEvent.INCOGNITO_TAB)
            },
            DialogItem(title = R.string.action_share) {
                presenter.onLinkLongPressEvent(longPress, BrowserContract.LinkLongPressEvent.SHARE)
            },
            DialogItem(title = R.string.dialog_copy_link) {
                presenter.onLinkLongPressEvent(longPress, BrowserContract.LinkLongPressEvent.COPY_LINK)
            }
        )
    }

    override fun showImageLongPressDialog(longPress: LongPress) {
        BrowserDialog.show(
            this, longPress.targetUrl,
            DialogItem(title = R.string.dialog_open_new_tab) {
                presenter.onImageLongPressEvent(
                    longPress,
                    BrowserContract.ImageLongPressEvent.NEW_TAB
                )
            },
            DialogItem(title = R.string.dialog_open_background_tab) {
                presenter.onImageLongPressEvent(
                    longPress,
                    BrowserContract.ImageLongPressEvent.BACKGROUND_TAB
                )
            },
            DialogItem(
                title = R.string.dialog_open_incognito_tab,
                isConditionMet = !isIncognito()
            ) {
                presenter.onImageLongPressEvent(
                    longPress,
                    BrowserContract.ImageLongPressEvent.INCOGNITO_TAB
                )
            },
            DialogItem(title = R.string.action_share) {
                presenter.onImageLongPressEvent(
                    longPress,
                    BrowserContract.ImageLongPressEvent.SHARE
                )
            },
            DialogItem(title = R.string.dialog_copy_link) {
                presenter.onImageLongPressEvent(
                    longPress,
                    BrowserContract.ImageLongPressEvent.COPY_LINK
                )
            },
            DialogItem(title = R.string.dialog_download_image) {
                presenter.onImageLongPressEvent(
                    longPress,
                    BrowserContract.ImageLongPressEvent.DOWNLOAD
                )
            })
    }

    /**
     * @see BrowserContract.View.showCloseBrowserDialog
     */
    override fun showCloseBrowserDialog(id: Int) {
        BrowserDialog.show(
            this,
            getString(
                R.string.dialog_title_tab_management,
                tabPager.estimatedMemoryForTab(id)
            ),
            DialogItem(title = R.string.close_tab) {
                presenter.onCloseBrowserEvent(id, BrowserContract.CloseTabEvent.CLOSE_CURRENT)
            },
            DialogItem(title = R.string.close_other_tabs) {
                presenter.onCloseBrowserEvent(id, BrowserContract.CloseTabEvent.CLOSE_OTHERS)
            },
            DialogItem(title = R.string.close_all_tabs, onClick = {
                presenter.onCloseBrowserEvent(id, BrowserContract.CloseTabEvent.CLOSE_ALL)
            })
        )
    }

    override fun showSslDialog(sslCertificateInfo: SslCertificateInfo) {
        showSslCertificateDialog(sslCertificateInfo)
    }

    fun clearAllHistoryFromHistoryPage() {
        presenter.onClearAllHistoryClick()
    }

    fun clearAllDownloadsFromDownloadsPage() {
        presenter.onClearAllDownloadsClick()
        snackbar(R.string.downloads_history_cleared)
    }

    fun showDownloadDecoyModePrompt() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.download_decoy_mode_title)
            .setMessage(R.string.download_decoy_mode_message)
            .setPositiveButton(R.string.download_decoy_mode_start) { _, _ ->
                presenter.onDownloadDecoyModeConfirmed()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    fun showHistoryDecoyModePrompt() {
        val options = arrayOf(
            getString(R.string.history_decoy_mode_4_hours),
            getString(R.string.history_decoy_mode_48_hours),
            getString(R.string.history_decoy_mode_all_time)
        )
        var selectedIndex = 0
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.history_decoy_mode_title)
            // AlertDialog uses the single-choice list as its content. Do not
            // also set a message here, otherwise the message panel can take
            // precedence over the list on some Material dialog themes.
            .setSingleChoiceItems(options, selectedIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton(R.string.history_decoy_mode_start) { _, _ ->
                val timeframe = when (selectedIndex) {
                    1 -> DecoyTimeframe.FORTY_EIGHT_HOURS
                    2 -> DecoyTimeframe.ALL_TIME
                    else -> DecoyTimeframe.FOUR_HOURS
                }
                presenter.onHistoryDecoyModeConfirmed(timeframe)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showAddressOverlay() {
        val overlay = binding.addressOverlay ?: return
        val searchContainer = binding.searchContainer
        val editUrl = presenter.currentUrlForEditing()
        overlay.animate().cancel()
        searchContainer.animate().cancel()
        if (!overlay.isVisible) {
            overlay.alpha = 0f
            overlay.translationY = -overlay.height.coerceAtLeast(72.dp).toFloat()
            overlay.scaleX = 0.98f
            overlay.scaleY = 0.98f
            searchContainer.elevation = 0f
            searchContainer.translationZ = 0f
            overlay.isVisible = true
        }
        if (editUrl.isNotBlank() && binding.search.text.toString() != editUrl) {
            binding.search.setText(editUrl)
            binding.search.setSelection(editUrl.length)
        }
        overlay.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(ADDRESS_OVERLAY_ENTER_DURATION_MS)
            .setInterpolator(expressiveSpatialInterpolator)
            .start()
        searchContainer.animate()
            .translationZ(18.dp.toFloat())
            .setStartDelay(ADDRESS_OVERLAY_SHADOW_DELAY_MS)
            .setDuration(ADDRESS_OVERLAY_SHADOW_DURATION_MS)
            .setInterpolator(expressiveSpatialInterpolator)
            .withEndAction {
                searchContainer.elevation = 18.dp.toFloat()
            }
            .start()
        binding.search.requestFocus()
        inputMethodManager.showSoftInput(binding.search, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideAddressOverlay() {
        val overlay = binding.addressOverlay ?: return
        val searchContainer = binding.searchContainer
        overlay.animate().cancel()
        searchContainer.animate().cancel()
        searchContainer.animate()
            .translationZ(0f)
            .setDuration(ADDRESS_OVERLAY_EXIT_DURATION_MS)
            .setInterpolator(expressiveEffectsInterpolator)
            .withEndAction {
                searchContainer.elevation = 0f
            }
            .start()
        overlay.animate()
            .alpha(0f)
            .translationY(-overlay.height.coerceAtLeast(72.dp).toFloat() * 0.35f)
            .scaleX(0.98f)
            .scaleY(0.98f)
            .setDuration(ADDRESS_OVERLAY_EXIT_DURATION_MS)
            .setInterpolator(expressiveEffectsInterpolator)
            .withEndAction {
                overlay.isVisible = false
                overlay.translationY = 0f
                overlay.scaleX = 1f
                overlay.scaleY = 1f
                searchContainer.translationZ = 0f
            }
            .start()
    }

    private fun applyPhysicalPressFeedback(view: View) {
        view.setOnTouchListener { touchedView, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> touchedView.animate()
                    .scaleX(0.94f)
                    .scaleY(0.94f)
                    .setDuration(PRESS_FEEDBACK_DURATION_MS)
                    .setInterpolator(expressiveEffectsInterpolator)
                    .start()

                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    touchedView.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(RELEASE_FEEDBACK_DURATION_MS)
                        .setInterpolator(expressiveSpatialInterpolator)
                        .start()
                    if (event.actionMasked == android.view.MotionEvent.ACTION_UP && touchedView.isClickable) {
                        touchedView.performClick()
                    }
                }
            }
            false
        }
    }

    private fun ImageView.updateVisibilityForDrawable() {
        visibility = if (drawable == null) {
            View.GONE
        } else {
            View.VISIBLE
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            downloadPermissionsHelper.onRequestPermissionsResult(this, grantResults)
        }
    }
}

private const val SUPER_COMPACT_RAIL_WIDTH_DP = 30
private const val MIN_SOLIPSISM_RAIL_WIDTH_DP = SUPER_COMPACT_RAIL_WIDTH_DP
private const val MAX_SOLIPSISM_RAIL_WIDTH_DP = 96
private const val ADDRESS_OVERLAY_RAIL_GAP_DP = 14
private const val FIND_BAR_RAIL_GAP_DP = 14
private const val URL_RAIL_DRAG_START_DP = 8
private const val URL_RAIL_SWIPE_THRESHOLD_DP = 34
private const val URL_RAIL_COMMIT_PROGRESS = 0.42f
private const val RAIL_HAPTIC_MAX_DURATION_MS = 10_000L
private const val RAIL_HAPTIC_IDLE_TIMEOUT_MS = 140L
private const val SCREENSHOT_ANIMATION_DURATION_MS = 650L
private const val SCREENSHOT_SHRINK_SCALE = 0.70f
private const val BROWSER_MENU_MAX_WIDTH_DP = 258
private const val BROWSER_MENU_SCREEN_MARGIN_DP = 14
private const val DONATION_PREFERENCES = "solipsism_donation"
private const val DONATION_PROMPT_SHOWN = "donationPromptShown"
private const val KO_FI_URL = "https://ko-fi.com/kennethchoinfosec"
private const val ADDRESS_OVERLAY_ENTER_DURATION_MS = 360L
private const val ADDRESS_OVERLAY_EXIT_DURATION_MS = 180L
private const val ADDRESS_OVERLAY_SHADOW_DELAY_MS = 90L
private const val ADDRESS_OVERLAY_SHADOW_DURATION_MS = 320L
private const val PRESS_FEEDBACK_DURATION_MS = 95L
private const val RELEASE_FEEDBACK_DURATION_MS = 260L
private const val TTS_CHUNK_LENGTH = 3500
