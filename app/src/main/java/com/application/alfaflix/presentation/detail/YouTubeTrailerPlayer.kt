package com.application.alfaflix.presentation.detail

import android.view.ViewGroup
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/**
 * Plays a YouTube video inside the app using the official IFrame Player API, which the
 * `android-youtube-player` library hosts in a WebView. (YouTube's older native Android
 * player API is deprecated; the IFrame embed is the supported route.)
 *
 * The player is created only when this composable enters composition — that is, after the
 * user taps play — so opening a movie never spins up a WebView or touches the network for a
 * video nobody asked to watch.
 *
 * Playback restrictions are respected, not worked around: when YouTube refuses to play the
 * video in an embedded player (the uploader disabled embedding, or the video is private or
 * removed) [onError] fires and the caller falls back to opening YouTube itself.
 *
 * @param onError called for any failure YouTube reports for this video.
 */
@Composable
fun YouTubeTrailerPlayer(
    videoKey: String,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnError by rememberUpdatedState(onError)

    val playerView = remember(videoKey) {
        YouTubePlayerView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            // Manual initialisation so playback options and the listener are set first.
            enableAutomaticInitialization = false
            initialize(
                object : AbstractYouTubePlayerListener() {
                    override fun onReady(youTubePlayer: YouTubePlayer) {
                        youTubePlayer.loadVideo(videoKey, START_SECONDS)
                    }

                    override fun onError(
                        youTubePlayer: YouTubePlayer,
                        error: PlayerConstants.PlayerError,
                    ) {
                        currentOnError()
                    }
                },
                IFramePlayerOptions.Builder(context)
                    .controls(1)
                    // Related videos at the end come from the same channel only.
                    .rel(0)
                    .build(),
            )
        }
    }

    // Tied to the lifecycle so playback pauses when the app is backgrounded, and the WebView
    // is released when the player leaves composition.
    DisposableEffect(lifecycleOwner, playerView) {
        lifecycleOwner.lifecycle.addObserver(playerView)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(playerView)
            playerView.release()
        }
    }

    AndroidView(
        factory = { playerView },
        modifier = modifier.aspectRatio(PLAYER_ASPECT_RATIO),
    )
}

private const val START_SECONDS = 0f
private const val PLAYER_ASPECT_RATIO = 16f / 9f

/**
 * How the trailer is actually played. Provided as a composition local so the real WebView
 * player can be swapped for a stand-in in UI tests — a WebView cannot run on the JVM, and
 * the behaviour worth testing is what the screen does when playback starts or fails.
 */
val LocalTrailerPlayer = staticCompositionLocalOf<@Composable (videoKey: String, onError: () -> Unit, modifier: Modifier) -> Unit> {
    { videoKey, onError, modifier -> YouTubeTrailerPlayer(videoKey, onError, modifier) }
}
