package com.application.alfaflix.presentation.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.application.alfaflix.R
import com.application.alfaflix.core.result.AppError
import com.application.alfaflix.domain.model.Review
import com.application.alfaflix.ui.components.ShimmerBox
import com.application.alfaflix.ui.components.asMessage
import com.application.alfaflix.ui.components.isRetryable
import com.application.alfaflix.ui.format.formatIsoDate
import com.application.alfaflix.ui.theme.Spacing

/**
 * The reviews section.
 *
 * Like the trailer, it owns its own loading, empty and failure states, so a reviews outage
 * leaves the rest of the movie readable.
 */
@Composable
fun ReviewsSection(
    state: ReviewsUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        ReviewsUiState.Loading -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            val loading = stringResource(R.string.reviews_loading)
            repeat(LOADING_PLACEHOLDERS) {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .semantics { contentDescription = loading },
                )
            }
        }

        ReviewsUiState.Empty -> SectionMessage(
            text = stringResource(R.string.reviews_empty),
            modifier = modifier,
        )

        is ReviewsUiState.Failed -> SectionMessage(
            text = failureText(state.error),
            onRetry = onRetry.takeIf { state.error.isRetryable },
            modifier = modifier,
        )

        is ReviewsUiState.Loaded -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            state.reviews.forEach { review ->
                // Keyed so expand state follows the review, not its position in the list.
                ReviewCard(review = review)
            }
        }
    }
}

@Composable
private fun failureText(error: AppError): String =
    stringResource(R.string.reviews_failed) + " " + error.asMessage()

/**
 * One review: who wrote it, when, what score they gave, and their words.
 *
 * Long reviews are collapsed to [COLLAPSED_LINES] lines with a real button to expand — a
 * tappable, labelled control rather than a gesture — and the button only appears when the
 * text is actually truncated.
 */
@Composable
private fun ReviewCard(review: Review) {
    var expanded by rememberSaveable(review.id) { mutableStateOf(false) }
    // Remembered once seen: after expanding, the text no longer overflows, but the "Show
    // less" button must stay.
    var isTruncated by rememberSaveable(review.id) { mutableStateOf(false) }

    val date = formatIsoDate(review.createdAt)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(Spacing.md)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            AuthorAvatar(review.author)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = review.author,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (date != null) {
                    Text(
                        text = date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            review.rating?.let { ReviewScore(it) }
        }

        Text(
            text = review.content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout ->
                if (!expanded && layout.hasVisualOverflow) isTruncated = true
            },
        )

        if (isTruncated) {
            val stateLabel = stringResource(
                if (expanded) R.string.reviews_show_less else R.string.reviews_read_more,
            )
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier
                    .align(Alignment.End)
                    .semantics {
                        contentDescription = "$stateLabel, ${review.author}"
                        stateDescription = stateLabel
                    },
            ) {
                Text(stateLabel)
            }
        }
    }
}

/** A circle with the reviewer's initial — TMDB avatars are unreliable and often absent. */
@Composable
private fun AuthorAvatar(author: String) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = author.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString().orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun ReviewScore(score: Double) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(R.string.reviews_rating, formatScore(score)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
}

/** 8.0 → "8", 7.5 → "7.5": whole scores are the common case and read cleaner without ".0". */
internal fun formatScore(score: Double): String =
    if (score % 1.0 == 0.0) score.toInt().toString() else "%.1f".format(score)

private const val COLLAPSED_LINES = 6
private const val LOADING_PLACEHOLDERS = 2
