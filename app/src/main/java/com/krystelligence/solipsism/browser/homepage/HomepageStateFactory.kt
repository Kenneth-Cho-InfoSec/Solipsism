package com.krystelligence.solipsism.browser.homepage

import android.app.Application
import com.krystelligence.solipsism.AppTheme
import com.krystelligence.solipsism.database.bookmark.BookmarkRepository
import com.krystelligence.solipsism.preference.UserPreferences
import io.reactivex.rxjava3.core.Single
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

class HomepageStateFactory @Inject constructor(
    private val application: Application,
    private val bookmarkRepository: BookmarkRepository,
    private val userPreferences: UserPreferences,
) {
    fun create(): Single<HomepageUiState> = bookmarkRepository.getAllBookmarksSorted().map { bookmarks ->
        HomepageUiState(
            bookmarks = bookmarks.take(MAX_SHORTCUTS),
            bookmarksVisible = userPreferences.homepageBookmarksEnabled,
            bookmarkColumns = userPreferences.homepageBookmarkColumns.coerceIn(1, 4),
            wallpaper = resolveWallpaper(),
            wallpaperOpacity = userPreferences.homepageWallpaperOpacity.percent(),
            wallpaperPositionX = userPreferences.homepageWallpaperPositionX.percent(),
            wallpaperPositionY = userPreferences.homepageWallpaperPositionY.percent(),
            dateTimeVisible = userPreferences.homepageDateTimeEnabled,
            timePattern = validDateFormat(userPreferences.homepageTimeFormat, "HH:mm"),
            datePattern = validDateFormat(
                userPreferences.homepageDateFormat,
                "EEEE, d MMMM yyyy",
            ),
            dateTimeOpacity = userPreferences.homepageDateTimeOpacity.percent(),
            mottoVisible = userPreferences.homepageMottoEnabled,
            motto = userPreferences.homepageMotto,
            mottoSizeSp = userPreferences.homepageMottoSize.coerceIn(10, 32).toFloat(),
            mottoOpacity = userPreferences.homepageMottoOpacity.percent(),
        )
    }

    private fun resolveWallpaper(): HomepageUiState.Wallpaper = when (userPreferences.homepageWallpaperMode) {
        WALLPAPER_CUSTOM -> userPreferences.homepageWallpaperPath
            ?.let(::File)
            ?.takeIf(File::isFile)
            ?.let { HomepageUiState.Wallpaper.Custom(it.absolutePath) }
            ?: bundledWallpaper()
        WALLPAPER_BLACK -> HomepageUiState.Wallpaper.Black
        else -> bundledWallpaper()
    }

    private fun bundledWallpaper(): HomepageUiState.Wallpaper.Bundled =
        HomepageUiState.Wallpaper.Bundled(
            when (userPreferences.useTheme.effective(application)) {
                AppTheme.LIGHT -> "homepage_wallpaper_light.jpg"
                AppTheme.DARK -> "homepage_wallpaper_dark.jpg"
                AppTheme.BLACK -> "homepage_wallpaper.jpg"
                AppTheme.SYSTEM -> error("System theme must be resolved before selecting a wallpaper")
            },
        )

    private fun validDateFormat(value: String, fallback: String): String =
        value.takeIf(String::isNotBlank)?.takeIf { pattern ->
            runCatching { SimpleDateFormat(pattern, Locale.getDefault()) }.isSuccess
        } ?: fallback

    private fun Int.percent(): Float = coerceIn(0, 100) / 100f

    private companion object {
        const val MAX_SHORTCUTS = 8
        const val WALLPAPER_CUSTOM = 1
        const val WALLPAPER_BLACK = 2
    }
}
