package com.application.alfaflix.presentation.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.application.alfaflix.R
import com.application.alfaflix.domain.model.Trailer
import com.application.alfaflix.ui.components.ShimmerBox
import com.application.alfaflix.ui.components.TmdbImages
import com.application.alfaflix.ui.components.asMessage
import com.application.alfaflix.ui.components.openUrl
import com.application.alfaflix.ui.components.isRetryable
import com.application.alfaflix.ui.theme.Spacing

/**
 * The trailer section.
 *
 * Its states are independent of the rest of the screen: whatever happens here, the movie's
 * information stays visible above it.
 */
@Composable
fun TrailerSection(
    state: TrailerUiState,
    movieTitle: String,
    isOffline: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        TrailerUiState.Loading -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(TRAILER_ASPECT_RATIO),
                shape = RoundedCornerShape(12.dp),
            )
            Text(
                text = stringResource(R.string.trailer_loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TrailerUiState.None -> SectionMessage(
            text = stringResource(R.string.trailer_none),
            modifier = modifier,
        )

        is TrailerUiState.Failed -> SectionMessage(
            text = stringResource(R.string.trailer_failed) + " " + state.error.asMessage(),
            onRetry = onRetry.takeIf { state.error.isRetryable },
            modifier = modifier,
        )

        is TrailerUiState.Available -> TrailerPlayer(
            trailer = state.trailer,
            movieTitle = movieTitle,
            isOffline = isOffline,
            modifier = modifier,
        )
    }
}

@Composable
private fun TrailerPlayer(
    trailer: Trailer,
    movieTitle: String,
    isOffline: Boolean,
    modifier: Modifier = Modifier,
) {
    // Saved so a rotation does not send the user back to the thumbnail. The video itself
    // restarts after rotation — the embed has no state worth persisting.
    var isPlaying by rememberSaveable(trailer.youtubeKey) { mutableStateOf(false) }
    var playbackFailed by rememberSaveable(trailer.youtubeKey) { mutableStateOf(false) }
    var showOfflineNotice by rememberSaveable(trailer.youtubeKey) { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        when {
            playbackFailed -> PlaybackFallback()

            isPlaying -> LocalTrailerPlayer.current(
                trailer.youtubeKey,
                { playbackFailed = true },
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
            )

            else -> TrailerThumbnail(
                trailer = trailer,
                movieTitle = movieTitle,
                onPlay = {
                    // Playback needs a connection. Say so up front instead of showing an
                    // empty player that never loads.
                    if (isOffline) showOfflineNotice = true else {
                        showOfflineNotice = false
                        isPlaying = true
                    }
                },
            )
        }

        if (showOfflineNotice && isOffline && !isPlaying) {
            Text(
                text = stringResource(R.string.trailer_needs_connection),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = trailer.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
            OpenOnYouTubeButton(trailer)
        }
    }
}

@Composable
private fun TrailerThumbnail(
    trailer: Trailer,
    movieTitle: String,
    onPlay: () -> Unit,
) {
    val playLabel = stringResource(R.string.trailer_play_description, movieTitle)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(TRAILER_ASPECT_RATIO)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button, onClickLabel = playLabel, onClick = onPlay)
            .semantics { contentDescription = playLabel },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = TmdbImages.youtubeThumbnail(trailer.youtubeKey),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(Color.Black.copy(alpha = 0.65f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

/** Shown when YouTube will not play the video embedded: the honest way out is YouTube itself. */
@Composable
private fun PlaybackFallback() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(TRAILER_ASPECT_RATIO)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.VideocamOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.trailer_playback_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

@Composable
private fun OpenOnYouTubeButton(trailer: Trailer) {
    val context = LocalContext.current
    TextButton(onClick = { openUrl(context, trailer.watchUrl) }) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.trailer_open_youtube),
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
}

/** A one-line status with an optional retry, shared by the trailer and review sections. */
@Composable
internal fun SectionMessage(
    text: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (onRetry != null) {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

private const val TRAILER_ASPECT_RATIO = 16f / 9f
