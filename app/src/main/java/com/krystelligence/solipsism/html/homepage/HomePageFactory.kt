package com.krystelligence.solipsism.html.homepage

import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.AppTheme
import com.krystelligence.solipsism.browser.theme.ThemeProvider
import com.krystelligence.solipsism.constant.FILE
import com.krystelligence.solipsism.constant.UTF8
import com.krystelligence.solipsism.database.Bookmark
import com.krystelligence.solipsism.database.bookmark.BookmarkRepository
import com.krystelligence.solipsism.html.HtmlPageFactory
import com.krystelligence.solipsism.html.jsoup.andBuild
import com.krystelligence.solipsism.html.jsoup.body
import com.krystelligence.solipsism.html.jsoup.charset
import com.krystelligence.solipsism.html.jsoup.clone
import com.krystelligence.solipsism.html.jsoup.findId
import com.krystelligence.solipsism.html.jsoup.id
import com.krystelligence.solipsism.html.jsoup.parse
import com.krystelligence.solipsism.html.jsoup.removeElement
import com.krystelligence.solipsism.html.jsoup.style
import com.krystelligence.solipsism.html.jsoup.title
import com.krystelligence.solipsism.preference.UserPreferences
import android.app.Application
import android.util.Base64
import io.reactivex.rxjava3.core.Single
import com.google.android.material.R as MaterialR
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject
import javax.inject.Inject

/**
 * A factory for the home page.
 */
class HomePageFactory @Inject constructor(
    private val application: Application,
    private val bookmarkRepository: BookmarkRepository,
    private val homePageReader: HomePageReader,
    private val themeProvider: ThemeProvider,
    private val userPreferences: UserPreferences
) : HtmlPageFactory {

    private val title = application.getString(R.string.home)

    private fun Int.toColor(): String {
        val string = Integer.toHexString(this)

        return string.substring(2) + string.substring(0, 2)
    }

    private val backgroundColor: String
        get() = themeProvider.color(MaterialR.attr.colorSurface).toColor()
    private val cardColor: String
        get() = themeProvider.color(MaterialR.attr.colorSurfaceContainerHigh).toColor()
    private val textColor: String
        get() = themeProvider.color(R.attr.autoCompleteTitleColor).toColor()
    private val wallpaper: String
        get() = when (userPreferences.homepageWallpaperMode) {
            HOMEPAGE_WALLPAPER_CUSTOM -> userPreferences.homepageWallpaperPath
                ?.let(::File)
                ?.takeIf(File::exists)
                ?.toURI()
                ?.toString()
                ?.let { "url(\"$it\") $wallpaperPosition / cover no-repeat" }
                ?: defaultWallpaperCss()
            HOMEPAGE_WALLPAPER_BLACK -> "none"
            else -> defaultWallpaperCss()
        }

    override fun buildPage(): Single<String> = bookmarkRepository
        .getAllBookmarksSorted()
        .map { bookmarks ->
            parse(homePageReader.provideHtml()) andBuild {
                title { title }
                style { content ->
                    content.replace("--body-bg: {COLOR}", "--body-bg: #$backgroundColor;")
                        .replace("--box-bg: {COLOR}", "--box-bg: #$cardColor;")
                        .replace("--box-txt: {COLOR}", "--box-txt: #$textColor;")
                        .replace("--home-wallpaper: {WALLPAPER}", "--home-wallpaper: $wallpaper;")
                        .replace(
                            "--homepage-datetime-opacity: {DATETIME_OPACITY}",
                            "--homepage-datetime-opacity: $dateTimeOpacity;"
                        )
                        .replace("--home-overlay: {HOME_OVERLAY}", "--home-overlay: $homeOverlay;")
                        .replace("--home-text: {HOME_TEXT}", "--home-text: $homeText;")
                        .replace("--home-text-secondary: {HOME_TEXT_SECONDARY}", "--home-text-secondary: $homeTextSecondary;")
                        .replace("--shortcut-text: {SHORTCUT_TEXT}", "--shortcut-text: $shortcutText;")
                        .replace("--shortcut-border: {SHORTCUT_BORDER}", "--shortcut-border: $shortcutBorder;")
                        .replace("--shortcut-bg: {SHORTCUT_BG}", "--shortcut-bg: $shortcutBackground;")
                        .replace("--shortcut-icon-bg: {SHORTCUT_ICON_BG}", "--shortcut-icon-bg: $shortcutIconBackground;")
                        .replace("--homepage-motto-size: {MOTTO_SIZE}", "--homepage-motto-size: ${mottoSize}px;")
                        .replace("--homepage-motto-opacity: {MOTTO_OPACITY}", "--homepage-motto-opacity: $mottoOpacity;")
                        .replace("--homepage-bookmark-columns: {BOOKMARK_COLUMNS}", "--homepage-bookmark-columns: ${bookmarkColumns};")
                        .replace("--homepage-wallpaper-opacity: {WALLPAPER_OPACITY}", "--homepage-wallpaper-opacity: $wallpaperOpacity;")
                        .replace("--homepage-wallpaper-position: {WALLPAPER_POSITION}", "--homepage-wallpaper-position: $wallpaperPosition;")
                        .replace("{GOOGLE_SANS_FONT}", googleSansFont)
                }
                charset { UTF8 }
                body {
                    val shortcutTemplate = findId("shortcut_template").removeElement()
                    id("bookmark_shortcuts") {
                        if (userPreferences.homepageBookmarksEnabled) {
                            bookmarks.take(MAX_SHORTCUTS).forEach { bookmark ->
                                appendChild(shortcutTemplate.clone {
                                    attr("href", bookmark.url)
                                    id("shortcut_icon") { text(bookmark.shortcutInitial()) }
                                    id("shortcut_title") { text(bookmark.title.ifBlank { bookmark.url }) }
                                })
                            }
                        } else {
                            remove()
                        }
                    }
                    getElementsByClass("motto").first()?.let { motto ->
                        if (userPreferences.homepageMottoEnabled) {
                            motto.text(userPreferences.homepageMotto)
                        } else {
                            motto.remove()
                        }
                    }
                }
            }
        }
        .map { content ->
            val timeFormat = validDateFormat(userPreferences.homepageTimeFormat, "HH:mm")
            val dateFormat = validDateFormat(
                userPreferences.homepageDateFormat,
                "EEEE, d MMMM yyyy"
            )
            val initialDateTime = if (userPreferences.homepageDateTimeEnabled) {
                val now = Date()
                "${SimpleDateFormat(timeFormat, Locale.getDefault()).format(now)}  •  " +
                    SimpleDateFormat(dateFormat, Locale.getDefault()).format(now)
            } else {
                ""
            }
            content
                .replace("{HOME_WALLPAPER_IMAGE}", wallpaperImageDataUri)
                .replace("{DATETIME_INITIAL}", initialDateTime.escapeHtml())
                .replace(
                    "{TIME_FORMAT}",
                    quoteScriptString(if (userPreferences.homepageDateTimeEnabled) timeFormat else "")
                )
                .replace(
                    "{DATE_FORMAT}",
                    quoteScriptString(if (userPreferences.homepageDateTimeEnabled) dateFormat else "")
                )
                .let { Pair(createHomePage(), it) }
        }
        .doOnSuccess { (page, content) ->
            FileWriter(page, false).use {
                it.write(content)
            }
        }
        .map { (page, _) -> "$FILE$page" }

    /**
     * Create the home page file.
     */
    fun createHomePage(): File {
        val generatedHtml = File(application.filesDir, "generated-html")
        generatedHtml.mkdirs()
        return File(generatedHtml, FILENAME)
    }

    private fun defaultWallpaperFile(): File {
        val (asset, targetName) = when (userPreferences.useTheme) {
            AppTheme.LIGHT -> LIGHT_WALLPAPER_ASSET to LIGHT_WALLPAPER_FILE
            AppTheme.DARK -> DARK_WALLPAPER_ASSET to DARK_WALLPAPER_FILE
            AppTheme.BLACK -> DEFAULT_WALLPAPER_ASSET to DEFAULT_WALLPAPER_FILE
        }
        val wallpaper = File(application.filesDir, targetName)
        // Refresh bundled wallpapers so an app update can replace an older cached asset.
        application.assets.open(asset).use { input ->
            wallpaper.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return wallpaper
    }

    private fun defaultWallpaperCss(): String =
        "url(\"data:image/jpeg;base64,${Base64.encodeToString(defaultWallpaperFile().readBytes(), Base64.NO_WRAP)}\") $wallpaperPosition / cover no-repeat"

    private val wallpaperImageDataUri: String
        get() = when (userPreferences.homepageWallpaperMode) {
            HOMEPAGE_WALLPAPER_BLACK -> "data:image/gif;base64,R0lGODlhAQABAAD/ACwAAAAAAQABAAACADs="
            HOMEPAGE_WALLPAPER_CUSTOM -> userPreferences.homepageWallpaperPath
                ?.let(::File)
                ?.takeIf(File::exists)
                ?.let { file ->
                    val mimeType = when (file.extension.lowercase(Locale.ROOT)) {
                        "png" -> "image/png"
                        "webp" -> "image/webp"
                        else -> "image/jpeg"
                    }
                    "data:$mimeType;base64,${Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)}"
                }
                ?: defaultWallpaperImageDataUri()
            else -> defaultWallpaperImageDataUri()
        }

    private fun defaultWallpaperImageDataUri(): String =
        "data:image/jpeg;base64,${Base64.encodeToString(defaultWallpaperFile().readBytes(), Base64.NO_WRAP)}"

    private val googleSansFont: String
        get() {
            val encodedFont = application.assets.open(GOOGLE_SANS_FONT_ASSET).use { input ->
                Base64.encodeToString(input.readBytes(), Base64.NO_WRAP)
            }
            return "url(\"data:font/truetype;base64,$encodedFont\") format(\"truetype\")"
        }

    private val dateTimeOpacity: String
        get() = (userPreferences.homepageDateTimeOpacity.coerceIn(0, 100) / 100.0).toString()

    private val isLightHomepage: Boolean
        get() = userPreferences.useTheme == AppTheme.LIGHT

    private val homeOverlay: String
        get() = if (isLightHomepage) {
            "rgba(255, 255, 255, 0.04)"
        } else {
            "linear-gradient(180deg, rgba(0, 0, 0, 0.10), rgba(0, 0, 0, 0.76))"
        }

    private val homeText: String
        get() = if (isLightHomepage) "rgba(0, 0, 0, 0.94)" else "rgba(255, 255, 255, 0.94)"

    private val homeTextSecondary: String
        get() = if (isLightHomepage) "rgba(0, 0, 0, 0.72)" else "rgba(255, 255, 255, 0.72)"

    private val shortcutText: String
        get() = if (isLightHomepage) "#000000" else "#FFFFFF"

    private val shortcutBorder: String
        get() = if (isLightHomepage) "rgba(0, 0, 0, 0.18)" else "rgba(255, 255, 255, 0.16)"

    private val shortcutBackground: String
        get() = if (isLightHomepage) "rgba(255, 255, 255, 0.62)" else "rgba(18, 24, 24, 0.46)"

    private val shortcutIconBackground: String
        get() = if (isLightHomepage) "rgba(0, 0, 0, 0.10)" else "rgba(255, 255, 255, 0.20)"

    private val mottoSize: Int
        get() = userPreferences.homepageMottoSize.coerceIn(10, 32)

    private val mottoOpacity: String
        get() = (userPreferences.homepageMottoOpacity.coerceIn(0, 100) / 100.0).toString()

    private val bookmarkColumns: Int
        get() = userPreferences.homepageBookmarkColumns.coerceIn(1, 4)

    private val wallpaperOpacity: String
        get() = (userPreferences.homepageWallpaperOpacity.coerceIn(0, 100) / 100.0).toString()

    private val wallpaperPosition: String
        get() = "${userPreferences.homepageWallpaperPositionX.coerceIn(0, 100)}% " +
            "${userPreferences.homepageWallpaperPositionY.coerceIn(0, 100)}%"

    private fun validDateFormat(value: String, fallback: String): String =
        value.takeIf { it.isNotBlank() }?.let { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.getDefault())
                pattern
            }.getOrElse { fallback }
        } ?: fallback

    private fun quoteScriptString(value: String): String =
        JSONObject.quote(value).replace("<", "\\u003c")

    companion object {

        const val FILENAME = "homepage.html"
        private const val MAX_SHORTCUTS = 8
        private const val HOMEPAGE_WALLPAPER_CUSTOM = 1
        private const val HOMEPAGE_WALLPAPER_BLACK = 2
        private const val DEFAULT_WALLPAPER_ASSET = "homepage_wallpaper.jpg"
        private const val DEFAULT_WALLPAPER_FILE = "homepage_wallpaper.jpg"
        private const val LIGHT_WALLPAPER_ASSET = "homepage_wallpaper_light.jpg"
        private const val LIGHT_WALLPAPER_FILE = "homepage_wallpaper_light.jpg"
        private const val DARK_WALLPAPER_ASSET = "homepage_wallpaper_dark.jpg"
        private const val DARK_WALLPAPER_FILE = "homepage_wallpaper_dark.jpg"
        private const val GOOGLE_SANS_FONT_ASSET = "fonts/google_sans_flex_500.ttf"

    }

    private fun Bookmark.Entry.shortcutInitial(): String =
        title.ifBlank { url }.trim().firstOrNull()?.uppercase().orEmpty()

    private fun String.escapeHtml(): String =
        replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

}
