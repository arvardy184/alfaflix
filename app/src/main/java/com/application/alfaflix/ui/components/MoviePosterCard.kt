package com.application.alfaflix.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.application.alfaflix.R
import com.application.alfaflix.domain.model.MovieListItem
import com.application.alfaflix.ui.theme.POSTER_ASPECT_RATIO
import com.application.alfaflix.ui.theme.Spacing

/**
 * A poster tile with title, year and a favorite toggle.
 *
 * The whole tile opens the movie; the heart is a separate control with its own label, so a
 * screen reader offers two distinct actions rather than one ambiguous target.
 */
@Composable
fun MoviePosterCard(
    item: MovieListItem,
    onClick: (MovieListItem) -> Unit,
    onToggleFavorite: (MovieListItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val movie = item.movie

    Column(
        modifier = modifier
            .clickable(
                // The card's own semantics label carries the title, so the poster inside is
                // decorative and the title is announced exactly once.
                onClickLabel = movie.title,
                onClick = { onClick(item) },
            )
            .padding(bottom = Spacing.sm),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            PosterImage(
                url = TmdbImages.poster(movie.posterPath),
                title = movie.title,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(POSTER_ASPECT_RATIO),
            )

            FavoriteButton(
                isFavorite = item.isFavorite,
                movieTitle = movie.title,
                onToggle = { onToggleFavorite(item) },
                modifier = Modifier.align(Alignment.TopEnd),
            )

            movie.voteAverage?.let { score ->
                RatingBadge(
                    score = score,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(Spacing.sm),
                )
            }
        }

        Text(
            text = movie.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.sm),
        )

        movie.releaseYear?.let { year ->
            Text(
                text = year,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * The favorite toggle.
 *
 * Its label names the movie and the action — "Add Batman Begins to favorites" — because
 * "favorite" alone is meaningless when a screen reader reaches it out of context.
 */
@Composable
fun FavoriteButton(
    isFavorite: Boolean,
    movieTitle: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    // A brief pop on state change: enough to confirm the tap, not enough to delay anything.
    val scale by animateFloatAsState(
        targetValue = if (isFavorite) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "favoriteScale",
    )

    val label = if (isFavorite) {
        stringResource(R.string.favorite_remove, movieTitle)
    } else {
        stringResource(R.string.favorite_add, movieTitle)
    }

    IconButton(
        onClick = onToggle,
        // Already at the 48.dp minimum via IconButton's own sizing.
        modifier = modifier.padding(Spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = label,
                tint = tint ?: if (isFavorite) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.White
                },
                modifier = Modifier
                    .size(18.dp)
                    .scale(scale),
            )
        }
    }
}

/** A poster-shaped skeleton, sized identically to [MoviePosterCard] so nothing shifts. */
@Composable
fun MoviePosterCardSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.clearAndSetSemantics { }) {
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(POSTER_ASPECT_RATIO),
        )
        ShimmerBox(
            modifier = Modifier
                .padding(top = Spacing.sm)
                .fillMaxWidth(0.8f)
                .height(14.dp),
        )
        ShimmerBox(
            modifier = Modifier
                .padding(top = Spacing.xs)
                .fillMaxWidth(0.4f)
                .height(12.dp),
        )
    }
}

/**
 * The TMDB user score on a poster, as a scanning aid.
 *
 * Sits on a dark scrim so it stays legible over any artwork. Only shown when TMDB has votes
 * for the movie — an unrated film shows nothing rather than a misleading "0.0".
 */
@Composable
fun RatingBadge(score: Double, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = "%.1f".format(score),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}
