package com.krystelligence.solipsism.html.homepage

import com.krystelligence.solipsism.R
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
                ?.let { "url(\"$it\") center center / cover no-repeat" }
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
                        .replace("{GOOGLE_SANS_FONT}", googleSansFont)
                }
                charset { UTF8 }
                body {
                    val shortcutTemplate = findId("shortcut_template").removeElement()
                    id("bookmark_shortcuts") {
                        bookmarks.take(MAX_SHORTCUTS).forEach { bookmark ->
                            appendChild(shortcutTemplate.clone {
                                attr("href", bookmark.url)
                                id("shortcut_icon") { text(bookmark.shortcutInitial()) }
                                id("shortcut_title") { text(bookmark.title.ifBlank { bookmark.url }) }
                            })
                        }
                    }
                }
            }
        }
        .map { content -> Pair(createHomePage(), content) }
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
        val wallpaper = File(application.filesDir, DEFAULT_WALLPAPER_FILE)
        if (!wallpaper.exists()) {
            application.assets.open(DEFAULT_WALLPAPER_ASSET).use { input ->
                wallpaper.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return wallpaper
    }

    private fun defaultWallpaperCss(): String =
        "url(\"${defaultWallpaperFile().toURI()}\") center center / cover no-repeat"

    private val googleSansFont: String
        get() {
            val encodedFont = application.assets.open(GOOGLE_SANS_FONT_ASSET).use { input ->
                Base64.encodeToString(input.readBytes(), Base64.NO_WRAP)
            }
            return "url(\"data:font/truetype;base64,$encodedFont\") format(\"truetype\")"
        }

    companion object {

        const val FILENAME = "homepage.html"
        private const val MAX_SHORTCUTS = 8
        private const val HOMEPAGE_WALLPAPER_CUSTOM = 1
        private const val HOMEPAGE_WALLPAPER_BLACK = 2
        private const val DEFAULT_WALLPAPER_ASSET = "homepage_wallpaper.jpg"
        private const val DEFAULT_WALLPAPER_FILE = "homepage_wallpaper.jpg"
        private const val GOOGLE_SANS_FONT_ASSET = "fonts/google_sans_flex_500.ttf"

    }

    private fun Bookmark.Entry.shortcutInitial(): String =
        title.ifBlank { url }.trim().firstOrNull()?.uppercase().orEmpty()

}
