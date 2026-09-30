package com.application.alfaflix.presentation.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.application.alfaflix.R
import com.application.alfaflix.domain.model.MovieDetail
import com.application.alfaflix.ui.components.ErrorState
import com.application.alfaflix.ui.components.PosterImage
import com.application.alfaflix.ui.components.StaleDataBanner
import com.application.alfaflix.ui.components.TmdbImages
import com.application.alfaflix.ui.format.formatIsoDate
import com.application.alfaflix.ui.theme.POSTER_ASPECT_RATIO
import com.application.alfaflix.ui.theme.Spacing

@Composable
fun MovieDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MovieDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MovieDetailScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onToggleFavorite = viewModel::onToggleFavorite,
        onRetry = viewModel::retry,
        onRetryReviews = viewModel::retryReviews,
        onRetryTrailer = viewModel::retryTrailer,
        modifier = modifier,
    )
}

/**
 * The detail screen, stateless so it can be tested and previewed without a ViewModel.
 *
 * Reading order follows what a person came here for: who the movie is, what it is about,
 * the trailer, then what others thought. Secondary facts come last.
 *
 * The favorite action is a button in the header rather than a floating one: a floating
 * button would sit on top of the reviews at the bottom of a long scroll.
 */
@Composable
fun MovieDetailScreenContent(
    uiState: MovieDetailUiState,
    onNavigateBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRetry: () -> Unit,
    onRetryReviews: () -> Unit,
    onRetryTrailer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // System bars are already accounted for by the navigation host's scaffold, so this
    // screen only positions content relative to its own bounds.
    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            MovieDetailUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            is MovieDetailUiState.Error -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                ErrorState(error = uiState.error, onRetry = onRetry)
            }

            is MovieDetailUiState.Content -> DetailBody(
                state = uiState,
                onToggleFavorite = onToggleFavorite,
                onRetry = onRetry,
                onRetryReviews = onRetryReviews,
                onRetryTrailer = onRetryTrailer,
            )
        }

        // Kept outside the scroll container so back is always reachable, including while the
        // screen is loading or showing an error.
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .padding(Spacing.sm)
                .align(Alignment.TopStart),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun DetailBody(
    state: MovieDetailUiState.Content,
    onToggleFavorite: () -> Unit,
    onRetry: () -> Unit,
    onRetryReviews: () -> Unit,
    onRetryTrailer: () -> Unit,
) {
    val detail = state.detail

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Backdrop(detail)

        // The movie stays fully readable offline; this only explains that the record may be
        // out of date.
        state.staleness?.let { staleness ->
            StaleDataBanner(staleness = staleness, onRetry = onRetry)
        }

        Column(
            modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Header(
                detail = detail,
                isFavorite = state.isFavorite,
                onToggleFavorite = onToggleFavorite,
            )

            DetailSection(stringResource(R.string.detail_overview)) {
                Text(
                    text = detail.overview ?: stringResource(R.string.detail_no_overview),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (detail.overview != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            DetailSection(stringResource(R.string.trailer_title)) {
                TrailerSection(
                    state = state.trailer,
                    movieTitle = detail.title,
                    isOffline = state.isOffline,
                    onRetry = onRetryTrailer,
                )
            }

            DetailSection(stringResource(R.string.reviews_title)) {
                ReviewsSection(state = state.reviews, onRetry = onRetryReviews)
            }

            SecondaryFacts(detail)
        }

        // Room at the bottom so the last section is never flush against the screen edge.
        Box(modifier = Modifier.padding(bottom = Spacing.xxl))
    }
}

/** The wide backdrop with a scrim so the back button stays legible over any artwork. */
@Composable
private fun Backdrop(detail: MovieDetail) {
    // 16:9 on a phone held upright, but capped: in landscape the full-width backdrop would fill
    // the whole screen and push the movie's information out of view.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val height = minOf(maxWidth / BACKDROP_ASPECT_RATIO, MAX_BACKDROP_HEIGHT)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            PosterImage(
                url = TmdbImages.backdrop(detail.backdropPath),
                title = detail.title,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(0.dp),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.45f),
                                Color.Transparent,
                                MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
                            ),
                        ),
                    ),
            )
        }
    }
}

/** Identity: poster, title, the essential metadata, score, genres and the favorite action. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(
    detail: MovieDetail,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            PosterImage(
                url = TmdbImages.poster(detail.posterPath),
                title = detail.title,
                contentDescription = stringResource(R.string.detail_poster_description, detail.title),
                modifier = Modifier
                    .width(POSTER_WIDTH)
                    .aspectRatio(POSTER_ASPECT_RATIO),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = detail.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                )

                MetadataLine(detail)

                detail.tagline?.let { tagline ->
                    Text(
                        text = tagline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                detail.voteAverage?.let { score ->
                    TmdbScore(score = score, votes = detail.voteCount)
                }
            }
        }

        // Wraps onto further lines on narrow screens and with enlarged fonts, instead of
        // running off the edge.
        if (detail.genres.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                detail.genres.forEach { genre ->
                    // Purely informational, so not focusable or announced as a button.
                    AssistChip(
                        onClick = {},
                        label = { Text(genre) },
                        enabled = false,
                        modifier = Modifier.clearAndSetSemantics { contentDescription = genre },
                    )
                }
            }
        }

        FavoriteButton(
            isFavorite = isFavorite,
            movieTitle = detail.title,
            onToggle = onToggleFavorite,
        )
    }
}

/** "2005 · 140 min", including only the parts that exist so the separator never dangles. */
@Composable
private fun MetadataLine(detail: MovieDetail) {
    val parts = buildList {
        detail.releaseYear?.let(::add)
        detail.runtimeMinutes?.let { add(stringResource(R.string.detail_runtime, it)) }
    }

    if (parts.isNotEmpty()) {
        Text(
            text = parts.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The community score, labelled with its real source. TMDB's score is its own users' average
 * — not IMDb's, not critics' — so the label says so rather than showing a bare number.
 */
@Composable
private fun TmdbScore(score: Double, votes: Int?) {
    val scoreText = "%.1f".format(score)
    val description = if (votes != null) {
        stringResource(R.string.detail_score_description, scoreText, "%,d".format(votes))
    } else {
        stringResource(R.string.detail_score_description_no_votes, scoreText)
    }

    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column {
            Text(
                text = stringResource(R.string.detail_score, scoreText),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = buildString {
                    append(stringResource(R.string.detail_score_source))
                    if (votes != null) {
                        append(" · ")
                        append(stringResource(R.string.detail_score_votes, "%,d".format(votes)))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SecondaryFacts(detail: MovieDetail) {
    val released = formatIsoDate(detail.releaseDate)
    val status = detail.status

    if (released == null && status == null) return

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        released?.let { Fact(stringResource(R.string.detail_released), it) }
        status?.let { Fact(stringResource(R.string.detail_status), it) }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

/**
 * Save / Saved. Filled when saved and outlined when not, so the state is visible without
 * relying on colour alone; the label names the movie for screen readers.
 */
@Composable
private fun FavoriteButton(
    isFavorite: Boolean,
    movieTitle: String,
    onToggle: () -> Unit,
) {
    val label = if (isFavorite) {
        stringResource(R.string.favorite_remove, movieTitle)
    } else {
        stringResource(R.string.favorite_add, movieTitle)
    }
    val text = stringResource(
        if (isFavorite) R.string.favorite_saved_short else R.string.favorite_save_short,
    )
    val icon = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder

    val modifier = Modifier
        .fillMaxWidth()
        .semantics { contentDescription = label }

    if (isFavorite) {
        Button(onClick = onToggle, modifier = modifier) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Text(text, modifier = Modifier.padding(start = Spacing.sm))
        }
    } else {
        OutlinedButton(onClick = onToggle, modifier = modifier) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Text(text, modifier = Modifier.padding(start = Spacing.sm))
        }
    }
}

private const val BACKDROP_ASPECT_RATIO = 16f / 9f
private val MAX_BACKDROP_HEIGHT = 260.dp
private val POSTER_WIDTH = 110.dp
