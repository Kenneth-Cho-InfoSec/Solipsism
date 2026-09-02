package com.krystelligence.solipsism.browser.engine

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** In-browser Media3 player for media extracted by the experimental Antares bridge. */
@UnstableApi
internal class AntaresMediaPlayerView(
    context: Context,
    private val request: BrowserMediaRequest,
    private val onClose: () -> Unit,
) : FrameLayout(context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val playerView = PlayerView(context)
    private val progress = ProgressBar(context)
    private val errorPanel = LinearLayout(context)
    private val errorText = TextView(context)
    private val titleBar = LinearLayout(context)
    private var player: ExoPlayer? = null
    private var generation = 0
    private var automaticRetriesRemaining = MAX_AUTOMATIC_RETRIES
    private var directFallbackAttempted = false
    private var resumeWhenVisible = false
    private var playbackChromeHidden = false
    var onPlaybackActiveChanged: ((Boolean) -> Unit)? = null
        set(value) {
            field = value
            value?.invoke(playbackChromeHidden)
        }

    init {
        setBackgroundColor(Color.BLACK)
        isFocusable = true
        isFocusableInTouchMode = true
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
        addView(
            playerView,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        addView(progress, LayoutParams(dp(56), dp(56), Gravity.CENTER))

        errorPanel.orientation = LinearLayout.VERTICAL
        errorPanel.gravity = Gravity.CENTER
        errorPanel.setPadding(dp(28), dp(20), dp(28), dp(20))
        errorText.setTextColor(Color.WHITE)
        errorText.textSize = 16f
        errorText.gravity = Gravity.CENTER
        errorPanel.addView(
                errorText,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
        )
        val retry = MaterialButton(context).apply {
            text = "Try again"
            setOnClickListener {
                automaticRetriesRemaining = MAX_AUTOMATIC_RETRIES
                resolveAndPlay()
            }
        }
        errorPanel.addView(
            retry,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(16) },
        )
        errorPanel.visibility = View.GONE
        addView(
            errorPanel,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )

        titleBar.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(16), dp(8))
            background = GradientDrawable().apply {
                setColor(0xB3000000.toInt())
                cornerRadius = dp(24).toFloat()
            }
        }
        val close = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "Close video"
            setOnClickListener { onClose() }
        }
        titleBar.addView(close, LinearLayout.LayoutParams(dp(48), dp(48)))
        titleBar.addView(
            TextView(context).apply {
                text = request.title ?: "Video"
                setTextColor(Color.WHITE)
                textSize = 16f
                maxLines = 1
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        addView(
            titleBar,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64), Gravity.TOP).apply {
                marginStart = dp(8)
                marginEnd = dp(8)
                topMargin = dp(8)
            },
        )
        resolveAndPlay()
    }

    private fun resolveAndPlay(useRenewal: Boolean = true) {
        val currentGeneration = ++generation
        progress.visibility = View.VISIBLE
        errorPanel.visibility = View.GONE
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    AntaresMediaSourceResolver.resolve(
                        request.pageUrl,
                        request.directSource,
                        request.renewalRequest.takeIf { useRenewal },
                        request.cookies,
                    )
                }
            }
            if (currentGeneration != generation) return@launch
            result.onSuccess(::prepare).onFailure(::handleFailure)
        }
    }

    private fun prepare(source: ResolvedMediaSource) {
        player?.release()
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(source.headers)
        val newPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
        player = newPlayer
        playerView.player = newPlayer
        newPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    progress.visibility = View.GONE
                    errorPanel.visibility = View.GONE
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                handleFailure(error)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlaybackChrome(isPlaying)
            }
        })
        val mediaItem = MediaItem.Builder()
            .setUri(source.url)
            .apply { source.mimeType?.let(::setMimeType) }
            .build()
        newPlayer.setMediaItem(mediaItem)
        newPlayer.prepare()
        newPlayer.playWhenReady = true
        postDelayed({
            if (newPlayer === player && newPlayer.playbackState == Player.STATE_BUFFERING) {
                handleFailure(IllegalStateException("The video server did not respond."))
            }
        }, PREPARE_TIMEOUT_MS)
    }

    private fun handleFailure(error: Throwable) {
        Log.e(TAG, "Android media playback failed", error)
        updatePlaybackChrome(false)
        player?.release()
        player = null
        progress.visibility = View.GONE
        if (!directFallbackAttempted && request.directSource != null && request.renewalRequest != null) {
            directFallbackAttempted = true
            errorPanel.visibility = View.GONE
            progress.visibility = View.VISIBLE
            resolveAndPlay(useRenewal = false)
            return
        }
        if (request.renewalRequest != null && automaticRetriesRemaining > 0) {
            automaticRetriesRemaining -= 1
            errorPanel.visibility = View.GONE
            progress.visibility = View.VISIBLE
            postDelayed(::resolveAndPlay, AUTOMATIC_RETRY_DELAY_MS)
            return
        }
        errorText.text = error.message ?: "Unable to play this video."
        errorPanel.visibility = View.VISIBLE
    }

    override fun onDetachedFromWindow() {
        updatePlaybackChrome(false)
        generation += 1
        scope.cancel()
        playerView.player = null
        player?.release()
        player = null
        super.onDetachedFromWindow()
    }

    private fun updatePlaybackChrome(isPlaying: Boolean) {
        if (playbackChromeHidden == isPlaying) return
        playbackChromeHidden = isPlaying
        titleBar.animate().cancel()
        if (isPlaying) {
            titleBar.animate()
                .alpha(0f)
                .setDuration(CHROME_FADE_DURATION_MS)
                .withEndAction { titleBar.visibility = View.INVISIBLE }
                .start()
        } else {
            titleBar.visibility = View.VISIBLE
            titleBar.animate()
                .alpha(1f)
                .setDuration(CHROME_FADE_DURATION_MS)
                .start()
        }
        onPlaybackActiveChanged?.invoke(isPlaying)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        val currentPlayer = player ?: return
        if (visibility == View.VISIBLE) {
            currentPlayer.playWhenReady = resumeWhenVisible
        } else {
            resumeWhenVisible = currentPlayer.playWhenReady
            currentPlayer.playWhenReady = false
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "AntaresMediaPlayer"
        const val PREPARE_TIMEOUT_MS = 20_000L
        const val CHROME_FADE_DURATION_MS = 240L
        const val AUTOMATIC_RETRY_DELAY_MS = 350L
        const val MAX_AUTOMATIC_RETRIES = 2
    }
}
