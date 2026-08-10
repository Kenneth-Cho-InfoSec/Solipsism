package com.krystelligence.solipsism.preference

import com.krystelligence.solipsism.AppTheme
import com.krystelligence.solipsism.browser.di.UserPrefs
import com.krystelligence.solipsism.browser.proxy.ProxyChoice
import com.krystelligence.solipsism.browser.search.SearchBoxDisplayChoice
import com.krystelligence.solipsism.browser.search.SearchBoxModel
import com.krystelligence.solipsism.browser.ui.TabConfiguration
import com.krystelligence.solipsism.browser.ui.SolipsismRailPosition
import com.krystelligence.solipsism.audio.AudioPreset
import com.krystelligence.solipsism.browser.view.RenderingMode
import com.krystelligence.solipsism.constant.DEFAULT_ENCODING
import com.krystelligence.solipsism.constant.SCHEME_BOOKMARKS
import com.krystelligence.solipsism.constant.SCHEME_HOMEPAGE
import com.krystelligence.solipsism.device.ScreenSize
import com.krystelligence.solipsism.html.homepage.HomepageSource
import com.krystelligence.solipsism.preference.delegates.booleanPreference
import com.krystelligence.solipsism.preference.delegates.enumPreference
import com.krystelligence.solipsism.preference.delegates.intPreference
import com.krystelligence.solipsism.preference.delegates.longPreference
import com.krystelligence.solipsism.preference.delegates.nullableStringPreference
import com.krystelligence.solipsism.preference.delegates.stringPreference
import com.krystelligence.solipsism.search.SearchEngineProvider
import com.krystelligence.solipsism.search.engine.GoogleSearch
import com.krystelligence.solipsism.database.bookmark.BookmarkSortOrder
import com.krystelligence.solipsism.utils.FileUtils
import android.content.SharedPreferences
import com.krystelligence.solipsism.browser.ui.RailUtilityAction
import com.krystelligence.solipsism.browser.ui.RailMenuLayout
import com.krystelligence.solipsism.browser.ui.RailMenuLayoutCodec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user's preferences.
 */
@Singleton
class UserPreferences @Inject constructor(
    @UserPrefs preferences: SharedPreferences,
    screenSize: ScreenSize
) {

    /** Show release notes once after the app version changes. */
    var releaseNotesEnabled by preferences.booleanPreference(RELEASE_NOTES_ENABLED, true)

    /** Check for newer stable releases and show an optional reminder. */
    var updateNotificationsEnabled by preferences.booleanPreference(UPDATE_NOTIFICATIONS_ENABLED, true)

    /** Version used to distinguish a first install from an application update. */
    var lastAcknowledgedAppVersion by preferences.nullableStringPreference(LAST_ACKNOWLEDGED_APP_VERSION)

    /** Release tag whose notes have already been dismissed. */
    var releaseNotesShownVersion by preferences.nullableStringPreference(RELEASE_NOTES_SHOWN_VERSION)

    /** Latest release tag already shown in the update reminder. */
    var lastNotifiedReleaseTag by preferences.nullableStringPreference(LAST_NOTIFIED_RELEASE_TAG)

    /** Epoch time until which update reminders remain snoozed. */
    var updateReminderSnoozeUntil by preferences.longPreference(UPDATE_REMINDER_SNOOZE_UNTIL, 0L)

    /**
     * True if Web RTC is enabled in the browser, false otherwise.
     */
    var webRtcEnabled by preferences.booleanPreference(WEB_RTC, false)

    /**
     * True if the browser should block ads, false otherwise.
     */
    var adBlockEnabled by preferences.booleanPreference(BLOCK_ADS, false)

    /**
     * True if the built-in uBlock Origin compatible network filters are enabled.
     */
    var uBlockOriginEnabled by preferences.booleanPreference(UBLOCK_ORIGIN, true)

    /** True when user-defined cosmetic filters should be applied. */
    var cosmeticFiltersEnabled by preferences.booleanPreference(COSMETIC_FILTERS, true)

    /** True when GIF image resources should be blocked. */
    var blockGifImagesEnabled by preferences.booleanPreference(BLOCK_GIF_IMAGES, false)

    /**
     * True if the browser should block images from being loaded, false otherwise.
     */
    var blockImagesEnabled by preferences.booleanPreference(BLOCK_IMAGES, false)

    /**
     * True if the browser should clear the browser cache when the app is exited, false otherwise.
     */
    var clearCacheExit by preferences.booleanPreference(CLEAR_CACHE_EXIT, false)

    /**
     * True if the browser should allow websites to store and access cookies, false otherwise.
     */
    var cookiesEnabled by preferences.booleanPreference(COOKIES, true)

    /**
     * The folder into which files will be downloaded.
     */
    var downloadDirectory by preferences.stringPreference(
        DOWNLOAD_DIRECTORY,
        FileUtils.DEFAULT_DOWNLOAD_PATH
    )

    /** Route eligible direct downloads to the selected external manager. */
    var customDownloadManagerEnabled by preferences.booleanPreference(CUSTOM_DOWNLOAD_MANAGER_ENABLED, false)

    /** Selected external manager package name. */
    var customDownloadManagerPackage by preferences.stringPreference(CUSTOM_DOWNLOAD_MANAGER_PACKAGE, "idm.internet.download.manager")

    /** Comma-separated user-configured manager package names. */
    var customDownloadManagerPackages by preferences.stringPreference(
        CUSTOM_DOWNLOAD_MANAGER_PACKAGES,
        "idm.internet.download.manager,com.dv.adm,org.freedownloadmanager.fdm"
    )

    /** Convert downloaded raster images to JPEG before saving them. */
    var saveImagesAsJpeg by preferences.booleanPreference(SAVE_IMAGES_AS_JPEG, true)

    /** True when eligible downloads should be checked locally before being saved. */
    var virusTotalScanningEnabled by preferences.booleanPreference(VIRUS_TOTAL_SCANNING, true)

    /** Images are excluded by default because they are commonly large and lower risk. */
    var virusTotalScanImages by preferences.booleanPreference(VIRUS_TOTAL_SCAN_IMAGES, false)

    /** Videos are excluded by default because they are commonly very large. */
    var virusTotalScanVideos by preferences.booleanPreference(VIRUS_TOTAL_SCAN_VIDEOS, false)

    /** Periodically refresh the compact on-device malware definitions. */
    var malwareDefinitionsAutoUpdate by preferences.booleanPreference(
        MALWARE_DEFINITIONS_AUTO_UPDATE,
        true
    )

    /** Use VirusTotal as an optional cloud-based second opinion after the local scan. */
    var virusTotalCloudEnabled by preferences.booleanPreference(VIRUS_TOTAL_CLOUD_ENABLED, false)

    /**
     * True if the browser should hide the navigation bar when scrolling, false if it should be
     * immobile.
     */
    var fullScreenEnabled by preferences.booleanPreference(FULL_SCREEN, true)

    /** Hide the Solipsism side rail while full-screen mode is enabled. */
    var hideRailInFullscreen by preferences.booleanPreference(HIDE_RAIL_IN_FULLSCREEN, false)

    /**
     * True if the system status bar should be hidden throughout the app, false if it should be
     * visible.
     */
    var hideStatusBarEnabled by preferences.booleanPreference(HIDE_STATUS_BAR, false)

    /**
     * The URL of the selected homepage.
     */
    var homepage by preferences.stringPreference(HOMEPAGE, SCHEME_HOMEPAGE)

    /** The secured homepage source selected by the user. */
    var homepageSource by preferences.intPreference(HOMEPAGE_SOURCE, HomepageSource.BUILT_IN.value)

    /** Path to the sanitized static homepage HTML file. */
    var homepageHtmlPath by preferences.nullableStringPreference(HOMEPAGE_HTML_PATH)

    /** Whether the built-in homepage motto is shown. */
    var homepageMottoEnabled by preferences.booleanPreference(HOMEPAGE_MOTTO_ENABLED, true)

    /** Text shown below the built-in homepage title. */
    var homepageMotto by preferences.stringPreference(HOMEPAGE_MOTTO, "Ex se sola veritas fluit")

    /** Built-in homepage motto size in sp. */
    var homepageMottoSize by preferences.intPreference(HOMEPAGE_MOTTO_SIZE, 13)

    /** Built-in homepage motto opacity as a percentage. */
    var homepageMottoOpacity by preferences.intPreference(HOMEPAGE_MOTTO_OPACITY, 72)

    /** Whether bookmark shortcuts are shown on the built-in homepage. */
    var homepageBookmarksEnabled by preferences.booleanPreference(HOMEPAGE_BOOKMARKS_ENABLED, true)

    /** Number of bookmark shortcut columns on the built-in homepage. */
    var homepageBookmarkColumns by preferences.intPreference(HOMEPAGE_BOOKMARK_COLUMNS, 3)

    /** Custom wallpaper opacity as a percentage. */
    var homepageWallpaperOpacity by preferences.intPreference(HOMEPAGE_WALLPAPER_OPACITY, 100)

    /** Custom wallpaper horizontal focal point as a percentage. */
    var homepageWallpaperPositionX by preferences.intPreference(HOMEPAGE_WALLPAPER_POSITION_X, 50)

    /** Custom wallpaper vertical focal point as a percentage. */
    var homepageWallpaperPositionY by preferences.intPreference(HOMEPAGE_WALLPAPER_POSITION_Y, 50)

    /**
     * True if cookies should be enabled in incognito mode, false otherwise.
     *
     * WARNING: Cookies will be shared between regular and incognito modes if this is enabled.
     */
    var incognitoCookiesEnabled by preferences.booleanPreference(INCOGNITO_COOKIES, false)

    /**
     * True if the browser should allow execution of javascript, false otherwise.
     */
    var javaScriptEnabled by preferences.booleanPreference(JAVASCRIPT, true)

    /** True when the experimental, unprivileged userscript runtime is enabled. */
    var userscriptsEnabled by preferences.booleanPreference(USER_SCRIPTS, false)

    /**
     * True if the device location should be accessible by websites, false otherwise.
     *
     * NOTE: If this is enabled, permission will still need to be granted on a per-site basis.
     */
    var locationEnabled by preferences.booleanPreference(LOCATION, false)

    /**
     * True if the browser should load pages zoomed out instead of zoomed in so that the text is
     * legible, false otherwise.
     */
    var overviewModeEnabled by preferences.booleanPreference(OVERVIEW_MODE, true)

    /**
     * True if the browser should allow websites to open new windows, false otherwise.
     */
    var popupsEnabled by preferences.booleanPreference(POPUPS, true)

    /** Block pop-up windows that were not initiated by a user gesture. */
    var blockAutomaticPopups by preferences.booleanPreference(BLOCK_AUTOMATIC_POPUPS, false)

    /**
     * True if the app should remember which browser tabs were open and restore them if the browser
     * is automatically closed by the system.
     */
    var restoreLostTabsEnabled by preferences.booleanPreference(RESTORE_LOST_TABS, true)

    /**
     * The index of the chosen search engine.
     *
     * @see SearchEngineProvider
     */
    var searchChoice by preferences.intPreference(SEARCH, 1)

    /**
     * The custom URL which should be used for making searches.
     */
    var searchUrl by preferences.stringPreference(SEARCH_URL, GoogleSearch().queryUrl)

    /**
     * True if the browser should attempt to reflow the text on a web page after zooming in or out
     * of the page.
     */
    var textReflowEnabled by preferences.booleanPreference(TEXT_REFLOW, false)

    /**
     * The index of the text size that should be used in the browser.
     */
    var textSize by preferences.intPreference(TEXT_SIZE, 3)

    /** Absolute path to the user-selected application font, or null for the system font. */
    var customFontPath by preferences.nullableStringPreference(CUSTOM_FONT_PATH)

    /**
     * True if the browser should fit web pages to the view port, false otherwise.
     */
    var useWideViewPortEnabled by preferences.booleanPreference(USE_WIDE_VIEWPORT, true)

    /** Override restrictive viewport metadata so pinch zoom remains available. */
    var allowZoomOnRestrictedPages by preferences.booleanPreference(ALLOW_ZOOM_ON_RESTRICTED_PAGES, false)

    /** Reduce non-essential motion and transitions for users who prefer a calmer interface. */
    var reducedMotionEnabled by preferences.booleanPreference(REDUCED_MOTION, false)

    /** Keep interactive controls at a larger touch target size. */
    var largeAccessibilityTargetsEnabled by preferences.booleanPreference(LARGE_ACCESSIBILITY_TARGETS, false)

    /** Announce important state changes to accessibility services. */
    var accessibilityAnnouncementsEnabled by preferences.booleanPreference(ACCESSIBILITY_ANNOUNCEMENTS, true)

    /**
     * The index of the user agent choice that should be used by the browser.
     *
     * @see UserPreferences.userAgent
     */
    var userAgentChoice by preferences.intPreference(USER_AGENT, 1)

    /**
     * The custom user agent that should be used by the browser.
     */
    var userAgentString by preferences.stringPreference(USER_AGENT_STRING, "")

    /**
     * True if the browser should identify as a recent Chrome build for website compatibility.
     */
    var chrompatibilityModeEnabled by preferences.booleanPreference(CHROMPATIBILITY_MODE, true)

    /**
     * True if the browser should clear the navigation history on app exit, false otherwise.
     */
    var clearHistoryExitEnabled by preferences.booleanPreference(CLEAR_HISTORY_EXIT, false)

    /**
     * True if the browser should clear the browser cookies on app exit, false otherwise.
     */
    var clearCookiesExitEnabled by preferences.booleanPreference(CLEAR_COOKIES_EXIT, false)

    /**
     * The index of the rendering mode that should be used by the browser.
     */
    var renderingMode by preferences.enumPreference(RENDERING_MODE, RenderingMode.NORMAL)

    /**
     * True if third party cookies should be disallowed by the browser, false if they should be
     * allowed.
     */
    var blockThirdPartyCookiesEnabled by preferences.booleanPreference(BLOCK_THIRD_PARTY, true)

    /**
     * True if the browser should extract the theme color from a website and color the UI with it,
     * false otherwise.
     */
    var colorModeEnabled by preferences.booleanPreference(ENABLE_COLOR_MODE, true)

    /**
     * The index of the URL/search box display choice/
     *
     * @see SearchBoxModel
     */
    var urlBoxContentChoice by preferences.enumPreference(
        URL_BOX_CONTENTS,
        SearchBoxDisplayChoice.DOMAIN
    )

    /**
     * True if the browser should invert the display colors of the web page content, false
     * otherwise.
     */
    var invertColors by preferences.booleanPreference(INVERT_COLORS, false)

    /**
     * The index of the theme used by the application.
     */
    var useTheme by preferences.enumPreference(THEME, AppTheme.LIGHT)

    /**
     * The text encoding used by the browser.
     */
    var textEncoding by preferences.stringPreference(TEXT_ENCODING, DEFAULT_ENCODING)

    /**
     * True if the web page storage should be cleared when the app exits, false otherwise.
     */
    var clearWebStorageExitEnabled by preferences.booleanPreference(CLEAR_WEB_STORAGE_EXIT, false)

    /**
     * True if the app should use the navigation drawer UI, false if it should use the traditional
     * desktop browser tabs UI.
     */
    @Deprecated("Superseded by TabConfiguration")
    private var showTabsInDrawer by preferences.booleanPreference(
        SHOW_TABS_IN_DRAWER,
        !screenSize.isTablet()
    )

    var tabConfiguration by preferences.enumPreference(
        TAB_CONFIGURATION,
        TabConfiguration.SOLIPSISM
    )

    /**
     * The width, in dp, of the Solipsism rail.
     */
    var solipsismRailSize by preferences.intPreference(SOLIPSISM_RAIL_SIZE, 72)

    /**
     * True if the Solipsism rail should be pinned to the left edge, false if pinned right.
     */
    var solipsismRailOnLeft by preferences.booleanPreference(SOLIPSISM_RAIL_ON_LEFT, false)

    /** Placement of the Solipsism rail; horizontal placements are experimental. */
    var solipsismRailPosition by preferences.enumPreference(
        SOLIPSISM_RAIL_POSITION,
        if (solipsismRailOnLeft) SolipsismRailPosition.LEFT else SolipsismRailPosition.RIGHT
    )

    /** True when the QR scanner and Tabs controls should exchange their rail positions. */
    var swapQrAndTabsButtons by preferences.booleanPreference(SWAP_QR_AND_TABS_BUTTONS, false)

    var bookmarkDecoyModeEnabled by preferences.booleanPreference(
        BOOKMARK_DECOY_MODE_ENABLED,
        false
    )

    var bookmarkFaviconsEnabled by preferences.booleanPreference(
        BOOKMARK_FAVICONS_ENABLED,
        true
    )

    var bookmarkImportMode by preferences.stringPreference(BOOKMARK_IMPORT_MODE, "merge")

    var bookmarkSortOrder by preferences.enumPreference(
        BOOKMARK_SORT_ORDER,
        BookmarkSortOrder.MANUAL
    )

    /** The action shown by the configurable Solipsism rail utility button. */
    var railUtilityAction by preferences.enumPreference(
        RAIL_UTILITY_ACTION,
        RailUtilityAction.QR
    )

    /** Persisted arrangement created in Rail & Menu Studio. */
    private var railMenuLayoutJson by preferences.stringPreference(RAIL_MENU_LAYOUT, "")

    var railMenuLayout: RailMenuLayout
        get() = RailMenuLayoutCodec.decode(railMenuLayoutJson)
        set(value) {
            railMenuLayoutJson = RailMenuLayoutCodec.encode(value)
        }

    /** When false, page audio is left untouched by Solipsism. */
    var audioEffectsEnabled by preferences.booleanPreference(AUDIO_EFFECTS_ENABLED, false)

    var audioCustomEqEnabled by preferences.booleanPreference(AUDIO_CUSTOM_EQ_ENABLED, false)

    var audioPreset by preferences.enumPreference(AUDIO_PRESET, AudioPreset.FLAT)

    var audioEq60 by preferences.intPreference(AUDIO_EQ_60, 0)
    var audioEq250 by preferences.intPreference(AUDIO_EQ_250, 0)
    var audioEq1000 by preferences.intPreference(AUDIO_EQ_1000, 0)
    var audioEq4000 by preferences.intPreference(AUDIO_EQ_4000, 0)
    var audioEq12000 by preferences.intPreference(AUDIO_EQ_12000, 0)
    var audioPreampDb by preferences.intPreference(AUDIO_PREAMP_DB, 0)
    var audioLimiterEnabled by preferences.booleanPreference(AUDIO_LIMITER_ENABLED, true)
    var audioMonoEnabled by preferences.booleanPreference(AUDIO_MONO_ENABLED, false)
    var audioBalance by preferences.intPreference(AUDIO_BALANCE, 0)

    /** Master switch for browser haptic feedback. */
    var hapticsEnabled by preferences.booleanPreference(HAPTICS_ENABLED, true)

    /** Whether the screenshot animation should provide haptic feedback. */
    var screenshotHapticsEnabled by preferences.booleanPreference(SCREENSHOT_HAPTICS_ENABLED, true)

    /** Duration of screenshot haptic feedback in milliseconds. */
    var screenshotHapticDurationMs by preferences.intPreference(SCREENSHOT_HAPTIC_DURATION_MS, 650)

    /** Screenshot vibration intensity as a percentage. */
    var screenshotHapticIntensity by preferences.intPreference(SCREENSHOT_HAPTIC_INTENSITY, 70)

    /** Whether rail swipe feedback is enabled. */
    var railHapticsEnabled by preferences.booleanPreference(RAIL_HAPTICS_ENABLED, true)

    /** Rail swipe vibration intensity as a percentage. */
    var railHapticsIntensity by preferences.intPreference(RAIL_HAPTICS_INTENSITY, 100)

    /** Rail vibration response curve: 0 is linear, 1 is nonlinear ease-in/ease-out. */
    var railHapticCurve by preferences.intPreference(RAIL_HAPTIC_CURVE, 0)

    /** Whether the completion feedback after a rail tab switch is enabled. */
    var railCompletionHapticsEnabled by preferences.booleanPreference(
        RAIL_COMPLETION_HAPTICS_ENABLED,
        true
    )

    /** Completion feedback intensity as a percentage. */
    var railCompletionHapticsIntensity by preferences.intPreference(
        RAIL_COMPLETION_HAPTICS_INTENSITY,
        100
    )

    /** Feedback for discrete actions such as tabs, bookmarks, and QR scans. */
    var interactionHapticsEnabled by preferences.booleanPreference(INTERACTION_HAPTICS_ENABLED, true)

    var tabsHapticsEnabled by preferences.booleanPreference(TABS_HAPTICS_ENABLED, true)
    var tabsHapticsDurationMs by preferences.intPreference(TABS_HAPTICS_DURATION_MS, 18)
    var tabsHapticsIntensity by preferences.intPreference(TABS_HAPTICS_INTENSITY, 70)
    var bookmarksHapticsEnabled by preferences.booleanPreference(BOOKMARKS_HAPTICS_ENABLED, true)
    var bookmarksHapticsDurationMs by preferences.intPreference(BOOKMARKS_HAPTICS_DURATION_MS, 28)
    var bookmarksHapticsIntensity by preferences.intPreference(BOOKMARKS_HAPTICS_INTENSITY, 100)
    var qrHapticsEnabled by preferences.booleanPreference(QR_HAPTICS_ENABLED, true)
    var qrHapticsDurationMs by preferences.intPreference(QR_HAPTICS_DURATION_MS, 46)
    var qrHapticsIntensity by preferences.intPreference(QR_HAPTICS_INTENSITY, 100)
    var downloadHapticsEnabled by preferences.booleanPreference(DOWNLOAD_HAPTICS_ENABLED, true)
    var downloadHapticsDurationMs by preferences.intPreference(DOWNLOAD_HAPTICS_DURATION_MS, 46)
    var downloadHapticsIntensity by preferences.intPreference(DOWNLOAD_HAPTICS_INTENSITY, 100)
    var adblockHapticsEnabled by preferences.booleanPreference(ADBLOCK_HAPTICS_ENABLED, true)
    var adblockHapticsDurationMs by preferences.intPreference(ADBLOCK_HAPTICS_DURATION_MS, 28)
    var adblockHapticsIntensity by preferences.intPreference(ADBLOCK_HAPTICS_INTENSITY, 90)
    var permissionsHapticsEnabled by preferences.booleanPreference(PERMISSIONS_HAPTICS_ENABLED, true)
    var permissionsHapticsDurationMs by preferences.intPreference(PERMISSIONS_HAPTICS_DURATION_MS, 46)
    var permissionsHapticsIntensity by preferences.intPreference(PERMISSIONS_HAPTICS_INTENSITY, 100)
    var refreshHapticsEnabled by preferences.booleanPreference(REFRESH_HAPTICS_ENABLED, true)
    var refreshHapticsDurationMs by preferences.intPreference(REFRESH_HAPTICS_DURATION_MS, 24)
    var refreshHapticsIntensity by preferences.intPreference(REFRESH_HAPTICS_INTENSITY, 70)

    /**
     * The homepage wallpaper mode: bundled default image, custom user image, or black background.
     */
    var homepageWallpaperMode by preferences.intPreference(HOMEPAGE_WALLPAPER_MODE, 0)

    /**
     * The copied local file path of the user's custom homepage wallpaper, when selected.
     */
    var homepageWallpaperPath by preferences.nullableStringPreference(HOMEPAGE_WALLPAPER_PATH)

    /** True when the date and time should be shown above the homepage title. */
    var homepageDateTimeEnabled by preferences.booleanPreference(HOMEPAGE_DATE_TIME_ENABLED, true)

    /** SimpleDateFormat pattern used for the homepage time. */
    var homepageTimeFormat by preferences.stringPreference(HOMEPAGE_TIME_FORMAT, "HH:mm")

    /** SimpleDateFormat pattern used for the homepage date. */
    var homepageDateFormat by preferences.stringPreference(
        HOMEPAGE_DATE_FORMAT,
        "EEEE, d MMMM yyyy"
    )

    /** Opacity percentage for the homepage date and time. */
    var homepageDateTimeOpacity by preferences.intPreference(HOMEPAGE_DATE_TIME_OPACITY, 80)

    /** The selected fixed accent palette. */
    var accentPalette by preferences.intPreference(ACCENT_PALETTE, 0)

    /** True when the app should use Android's dynamic system accent on API 31+. */
    var matchSystemAccent by preferences.booleanPreference(MATCH_SYSTEM_ACCENT, false)

    /**
     * True if the browser should send a do not track (DNT) header with every GET request, false
     * otherwise.
     */
    var doNotTrackEnabled by preferences.booleanPreference(DO_NOT_TRACK, false)

    /**
     * True if the browser should save form data, false otherwise.
     */
    var saveDataEnabled by preferences.booleanPreference(SAVE_DATA, false)

    /**
     * True if the browser should attempt to remove identifying headers in GET requests, false if
     * the default headers should be left along.
     */
    var removeIdentifyingHeadersEnabled by preferences.booleanPreference(IDENTIFYING_HEADERS, false)

    /**
     * True if the bookmarks tab should be on the opposite side of the screen, false otherwise. If
     * the navigation drawer UI is used, the tab drawer will be displayed on the opposite side as
     * well.
     */
    var bookmarksAndTabsSwapped by preferences.booleanPreference(SWAP_BOOKMARKS_AND_TABS, false)

    /**
     * True if the status bar of the app should always be high contrast, false if it should follow
     * the theme of the app.
     */
    var useBlackStatusBar by preferences.booleanPreference(BLACK_STATUS_BAR, false)

    /**
     * The index of the proxy choice.
     */
    var proxyChoice by preferences.enumPreference(PROXY_CHOICE, ProxyChoice.NONE)

    /**
     * The proxy host used when [proxyChoice] is [ProxyChoice.MANUAL].
     */
    var proxyHost by preferences.stringPreference(USE_PROXY_HOST, "localhost")

    /**
     * The proxy port used when [proxyChoice] is [ProxyChoice.MANUAL].
     */
    var proxyPort by preferences.intPreference(USE_PROXY_PORT, 8118)

    /**
     * The index of the search suggestion choice.
     *
     * @see SearchEngineProvider
     */
    var searchSuggestionChoice by preferences.intPreference(SEARCH_SUGGESTIONS, 1)

    /**
     * The index of the ad blocking hosts file source.
     */
    var hostsSource by preferences.intPreference(HOSTS_SOURCE, 0)

    /**
     * The local file from which ad blocking hosts should be read, depending on the [hostsSource].
     */
    var hostsLocalFile by preferences.nullableStringPreference(HOSTS_LOCAL_FILE)

    /**
     * The remote URL from which ad blocking hosts should be read, depending on the [hostsSource].
     */
    var hostsRemoteFile by preferences.nullableStringPreference(HOSTS_REMOTE_FILE)

    init {
        if (homepage == SCHEME_BOOKMARKS) {
            homepage = SCHEME_HOMEPAGE
        }
        if (HomepageSource.fromValue(homepageSource) == HomepageSource.BUILT_IN &&
            (homepage.startsWith("http://") || homepage.startsWith("https://"))
        ) {
            homepageSource = HomepageSource.DOMAIN.value
        }
    }
}

private const val WEB_RTC = "webRtc"
private const val BLOCK_ADS = "AdBlock"
private const val UBLOCK_ORIGIN = "uBlockOrigin"
private const val COSMETIC_FILTERS = "cosmeticFilters"
private const val BLOCK_GIF_IMAGES = "blockGifImages"
private const val BLOCK_IMAGES = "blockimages"
private const val CLEAR_CACHE_EXIT = "cache"
private const val COOKIES = "cookies"
private const val DOWNLOAD_DIRECTORY = "downloadLocation"
private const val CUSTOM_DOWNLOAD_MANAGER_ENABLED = "customDownloadManagerEnabled"
private const val CUSTOM_DOWNLOAD_MANAGER_PACKAGE = "customDownloadManagerPackage"
private const val CUSTOM_DOWNLOAD_MANAGER_PACKAGES = "customDownloadManagerPackages"
private const val SAVE_IMAGES_AS_JPEG = "saveImagesAsJpeg"
private const val VIRUS_TOTAL_SCANNING = "virusTotalScanning"
private const val VIRUS_TOTAL_SCAN_IMAGES = "virusTotalScanImages"
private const val VIRUS_TOTAL_SCAN_VIDEOS = "virusTotalScanVideos"
private const val MALWARE_DEFINITIONS_AUTO_UPDATE = "malwareDefinitionsAutoUpdate"
private const val VIRUS_TOTAL_CLOUD_ENABLED = "virusTotalCloudEnabled"
private const val FULL_SCREEN = "fullscreen"
private const val HIDE_RAIL_IN_FULLSCREEN = "hideRailInFullscreen"
private const val HIDE_STATUS_BAR = "hidestatus"
private const val HOMEPAGE = "home"
private const val INCOGNITO_COOKIES = "incognitocookies"
private const val JAVASCRIPT = "java"
private const val USER_SCRIPTS = "userscripts"
private const val LOCATION = "location"
private const val OVERVIEW_MODE = "overviewmode"
private const val POPUPS = "newwindows"
private const val BLOCK_AUTOMATIC_POPUPS = "blockAutomaticPopups"
private const val RESTORE_LOST_TABS = "restoreclosed"
private const val SAVE_PASSWORDS = "passwords"
private const val SEARCH = "search"
private const val SEARCH_URL = "searchurl"
private const val TEXT_REFLOW = "textreflow"
private const val TEXT_SIZE = "textsize"
private const val CUSTOM_FONT_PATH = "customFontPath"
private const val USE_WIDE_VIEWPORT = "wideviewport"
private const val ALLOW_ZOOM_ON_RESTRICTED_PAGES = "allowZoomOnRestrictedPages"
private const val REDUCED_MOTION = "accessibilityReducedMotion"
private const val LARGE_ACCESSIBILITY_TARGETS = "accessibilityLargeTargets"
private const val ACCESSIBILITY_ANNOUNCEMENTS = "accessibilityAnnouncements"
private const val RELEASE_NOTES_ENABLED = "releaseNotesEnabled"
private const val UPDATE_NOTIFICATIONS_ENABLED = "updateNotificationsEnabled"
private const val LAST_ACKNOWLEDGED_APP_VERSION = "lastAcknowledgedAppVersion"
private const val RELEASE_NOTES_SHOWN_VERSION = "releaseNotesShownVersion"
private const val LAST_NOTIFIED_RELEASE_TAG = "lastNotifiedReleaseTag"
private const val UPDATE_REMINDER_SNOOZE_UNTIL = "updateReminderSnoozeUntil"
private const val USER_AGENT = "agentchoose"
private const val USER_AGENT_STRING = "userAgentString"
private const val CHROMPATIBILITY_MODE = "chrompatibilityMode"
private const val CLEAR_HISTORY_EXIT = "clearHistoryExit"
private const val CLEAR_COOKIES_EXIT = "clearCookiesExit"
private const val SAVE_URL = "saveUrl"
private const val RENDERING_MODE = "renderMode"
private const val BLOCK_THIRD_PARTY = "thirdParty"
private const val ENABLE_COLOR_MODE = "colorMode"
private const val URL_BOX_CONTENTS = "urlContent"
private const val INVERT_COLORS = "invertColors"
private const val READING_TEXT_SIZE = "readingTextSize"
private const val THEME = "Theme"
private const val TEXT_ENCODING = "textEncoding"
private const val CLEAR_WEB_STORAGE_EXIT = "clearWebStorageExit"
private const val SHOW_TABS_IN_DRAWER = "showTabsInDrawer"
private const val TAB_CONFIGURATION = "tabConfiguration"
private const val SOLIPSISM_RAIL_SIZE = "solipsismRailSize"
private const val SOLIPSISM_RAIL_ON_LEFT = "solipsismRailOnLeft"
private const val SOLIPSISM_RAIL_POSITION = "solipsismRailPosition"
private const val SWAP_QR_AND_TABS_BUTTONS = "swapQrAndTabsButtons"
private const val BOOKMARK_DECOY_MODE_ENABLED = "bookmarkDecoyModeEnabled"
private const val BOOKMARK_FAVICONS_ENABLED = "bookmarkFaviconsEnabled"
private const val BOOKMARK_IMPORT_MODE = "bookmarkImportMode"
private const val BOOKMARK_SORT_ORDER = "bookmarkSortOrder"
private const val RAIL_UTILITY_ACTION = "railUtilityAction"
private const val RAIL_MENU_LAYOUT = "railMenuLayout"
private const val AUDIO_EFFECTS_ENABLED = "audioEffectsEnabled"
private const val AUDIO_CUSTOM_EQ_ENABLED = "audioCustomEqEnabled"
private const val AUDIO_PRESET = "audioPreset"
private const val AUDIO_EQ_60 = "audioEq60"
private const val AUDIO_EQ_250 = "audioEq250"
private const val AUDIO_EQ_1000 = "audioEq1000"
private const val AUDIO_EQ_4000 = "audioEq4000"
private const val AUDIO_EQ_12000 = "audioEq12000"
private const val AUDIO_PREAMP_DB = "audioPreampDb"
private const val AUDIO_LIMITER_ENABLED = "audioLimiterEnabled"
private const val AUDIO_MONO_ENABLED = "audioMonoEnabled"
private const val AUDIO_BALANCE = "audioBalance"
private const val SCREENSHOT_HAPTICS_ENABLED = "screenshotHapticsEnabled"
private const val SCREENSHOT_HAPTIC_DURATION_MS = "screenshotHapticDurationMs"
private const val SCREENSHOT_HAPTIC_INTENSITY = "screenshotHapticIntensity"
private const val HAPTICS_ENABLED = "hapticsEnabled"
private const val RAIL_HAPTICS_ENABLED = "railHapticsEnabled"
private const val RAIL_HAPTICS_INTENSITY = "railHapticsIntensity"
private const val RAIL_HAPTIC_CURVE = "railHapticCurve"
private const val RAIL_COMPLETION_HAPTICS_ENABLED = "railCompletionHapticsEnabled"
private const val RAIL_COMPLETION_HAPTICS_INTENSITY = "railCompletionHapticsIntensity"
private const val INTERACTION_HAPTICS_ENABLED = "interactionHapticsEnabled"
private const val TABS_HAPTICS_ENABLED = "tabsHapticsEnabled"
private const val TABS_HAPTICS_DURATION_MS = "tabsHapticsDurationMs"
private const val TABS_HAPTICS_INTENSITY = "tabsHapticsIntensity"
private const val BOOKMARKS_HAPTICS_ENABLED = "bookmarksHapticsEnabled"
private const val BOOKMARKS_HAPTICS_DURATION_MS = "bookmarksHapticsDurationMs"
private const val BOOKMARKS_HAPTICS_INTENSITY = "bookmarksHapticsIntensity"
private const val QR_HAPTICS_ENABLED = "qrHapticsEnabled"
private const val QR_HAPTICS_DURATION_MS = "qrHapticsDurationMs"
private const val QR_HAPTICS_INTENSITY = "qrHapticsIntensity"
private const val DOWNLOAD_HAPTICS_ENABLED = "downloadHapticsEnabled"
private const val DOWNLOAD_HAPTICS_DURATION_MS = "downloadHapticsDurationMs"
private const val DOWNLOAD_HAPTICS_INTENSITY = "downloadHapticsIntensity"
private const val ADBLOCK_HAPTICS_ENABLED = "adblockHapticsEnabled"
private const val ADBLOCK_HAPTICS_DURATION_MS = "adblockHapticsDurationMs"
private const val ADBLOCK_HAPTICS_INTENSITY = "adblockHapticsIntensity"
private const val PERMISSIONS_HAPTICS_ENABLED = "permissionsHapticsEnabled"
private const val PERMISSIONS_HAPTICS_DURATION_MS = "permissionsHapticsDurationMs"
private const val PERMISSIONS_HAPTICS_INTENSITY = "permissionsHapticsIntensity"
private const val REFRESH_HAPTICS_ENABLED = "refreshHapticsEnabled"
private const val REFRESH_HAPTICS_DURATION_MS = "refreshHapticsDurationMs"
private const val REFRESH_HAPTICS_INTENSITY = "refreshHapticsIntensity"
private const val HOMEPAGE_WALLPAPER_MODE = "homepageWallpaperMode"
private const val HOMEPAGE_WALLPAPER_PATH = "homepageWallpaperPath"
private const val HOMEPAGE_SOURCE = "homepageSource"
private const val HOMEPAGE_HTML_PATH = "homepageHtmlPath"
private const val HOMEPAGE_MOTTO_ENABLED = "homepageMottoEnabled"
private const val HOMEPAGE_MOTTO = "homepageMotto"
private const val HOMEPAGE_MOTTO_SIZE = "homepageMottoSize"
private const val HOMEPAGE_MOTTO_OPACITY = "homepageMottoOpacity"
private const val HOMEPAGE_BOOKMARKS_ENABLED = "homepageBookmarksEnabled"
private const val HOMEPAGE_BOOKMARK_COLUMNS = "homepageBookmarkColumns"
private const val HOMEPAGE_WALLPAPER_OPACITY = "homepageWallpaperOpacity"
private const val HOMEPAGE_WALLPAPER_POSITION_X = "homepageWallpaperPositionX"
private const val HOMEPAGE_WALLPAPER_POSITION_Y = "homepageWallpaperPositionY"
private const val HOMEPAGE_DATE_TIME_ENABLED = "homepageDateTimeEnabled"
private const val HOMEPAGE_TIME_FORMAT = "homepageTimeFormat"
private const val HOMEPAGE_DATE_FORMAT = "homepageDateFormat"
private const val HOMEPAGE_DATE_TIME_OPACITY = "homepageDateTimeOpacity"
private const val ACCENT_PALETTE = "accentPalette"
private const val MATCH_SYSTEM_ACCENT = "matchSystemAccent"
private const val DO_NOT_TRACK = "doNotTrack"
private const val SAVE_DATA = "saveData"
private const val IDENTIFYING_HEADERS = "removeIdentifyingHeaders"
private const val SWAP_BOOKMARKS_AND_TABS = "swapBookmarksAndTabs"
private const val BLACK_STATUS_BAR = "blackStatusBar"
private const val PROXY_CHOICE = "proxyChoice"
private const val USE_PROXY_HOST = "useProxyHost"
private const val USE_PROXY_PORT = "useProxyPort"
private const val SEARCH_SUGGESTIONS = "searchSuggestionsChoice"
private const val HOSTS_SOURCE = "hostsSource"
private const val HOSTS_LOCAL_FILE = "hostsLocalFile"
private const val HOSTS_REMOTE_FILE = "hostsRemoteFile"
