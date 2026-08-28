package com.krystelligence.solipsism.browser.homepage

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import coil3.load
import com.krystelligence.solipsism.AppTheme
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.browser.di.DiskScheduler
import com.krystelligence.solipsism.browser.di.MainScheduler
import com.krystelligence.solipsism.browser.image.ImageLoader
import com.krystelligence.solipsism.databinding.ViewNativeHomepageBinding
import com.krystelligence.solipsism.preference.UserPreferences
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.subscribeBy
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class NativeHomepageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private val binding = ViewNativeHomepageBinding.inflate(LayoutInflater.from(context), this, true)
    private val handler = Handler(Looper.getMainLooper())
    private var state: HomepageUiState? = null
    private var refreshAction: (() -> Unit)? = null
    private var disposeAction: (() -> Unit)? = null
    private val clockUpdate = object : Runnable {
        override fun run() {
            updateDateTime()
            handler.postDelayed(this, MILLIS_PER_MINUTE)
        }
    }

    fun render(
        state: HomepageUiState,
        adapter: HomepageShortcutAdapter,
        isLightTheme: Boolean,
    ) {
        this.state = state
        binding.homepageShortcuts.apply {
            layoutManager = GridLayoutManager(context, state.bookmarkColumns)
            this.adapter = adapter
            isVisible = state.bookmarksVisible && state.bookmarks.isNotEmpty()
        }
        adapter.submitList(state.bookmarks)

        binding.homepageWallpaper.alpha = state.wallpaperOpacity
        binding.homepageWallpaper.focalX = state.wallpaperPositionX
        binding.homepageWallpaper.focalY = state.wallpaperPositionY
        when (val wallpaper = state.wallpaper) {
            HomepageUiState.Wallpaper.Black -> {
                binding.homepageWallpaper.load(null)
                binding.homepageWallpaper.setBackgroundColor(android.graphics.Color.BLACK)
            }
            is HomepageUiState.Wallpaper.Bundled -> {
                binding.homepageWallpaper.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                binding.homepageWallpaper.load("file:///android_asset/${wallpaper.assetName}")
            }
            is HomepageUiState.Wallpaper.Custom -> {
                binding.homepageWallpaper.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                binding.homepageWallpaper.load(File(wallpaper.path))
            }
        }
        binding.homepageOverlay.setBackgroundResource(
            if (isLightTheme) R.drawable.homepage_overlay_light else R.drawable.homepage_overlay_dark,
        )
        val primaryText = if (isLightTheme) Color.BLACK else Color.WHITE
        val secondaryText = if (isLightTheme) Color.rgb(72, 72, 72) else Color.rgb(220, 220, 220)
        binding.homepageDateTime.setTextColor(primaryText)
        binding.homepageTitle.setTextColor(primaryText)
        binding.homepageMotto.setTextColor(secondaryText)
        binding.homepageDateTime.isVisible = state.dateTimeVisible
        binding.homepageDateTime.alpha = state.dateTimeOpacity
        binding.homepageMotto.isVisible = state.mottoVisible
        binding.homepageMotto.text = state.motto
        binding.homepageMotto.alpha = state.mottoOpacity
        binding.homepageMotto.setTextSize(TypedValue.COMPLEX_UNIT_SP, state.mottoSizeSp)
        runCatching {
            Typeface.createFromAsset(context.assets, "fonts/google_sans_flex_500.ttf")
        }.getOrNull()?.let { typeface ->
            binding.homepageDateTime.typeface = typeface
            binding.homepageTitle.typeface = typeface
        }
        updateDateTime()
    }

    fun setRefreshAction(action: () -> Unit) {
        refreshAction = action
    }

    fun setDisposeAction(action: () -> Unit) {
        disposeAction = action
    }

    fun refresh() = refreshAction?.invoke()

    fun dispose() {
        handler.removeCallbacks(clockUpdate)
        refreshAction = null
        disposeAction?.invoke()
        disposeAction = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refresh()
        handler.removeCallbacks(clockUpdate)
        handler.post(clockUpdate)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(clockUpdate)
        super.onDetachedFromWindow()
    }

    private fun updateDateTime() {
        val value = state ?: return
        if (!value.dateTimeVisible) return
        val now = Date()
        binding.homepageDateTime.text = buildString {
            append(SimpleDateFormat(value.timePattern, Locale.getDefault()).format(now))
            append("  •  ")
            append(SimpleDateFormat(value.datePattern, Locale.getDefault()).format(now))
        }
    }

    class Factory @Inject constructor(
        private val stateFactory: HomepageStateFactory,
        private val imageLoader: ImageLoader,
        private val userPreferences: UserPreferences,
        @DiskScheduler private val diskScheduler: Scheduler,
        @MainScheduler private val mainScheduler: Scheduler,
    ) {
        fun create(context: Context, onOpen: (String) -> Unit): NativeHomepageView {
            val view = NativeHomepageView(context)
            val disposables = CompositeDisposable()
            val adapter = HomepageShortcutAdapter(imageLoader, onOpen)
            val refresh = {
                disposables.clear()
                stateFactory.create()
                    .subscribeOn(diskScheduler)
                    .observeOn(mainScheduler)
                    .subscribeBy(onSuccess = { state ->
                        view.render(
                            state,
                            adapter,
                            userPreferences.useTheme.effective(context) == AppTheme.LIGHT,
                        )
                    })
                    .also(disposables::add)
                Unit
            }
            view.setRefreshAction(refresh)
            view.setDisposeAction(disposables::dispose)
            return view
        }
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
