package com.application.alfaflix.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.application.alfaflix.R
import com.application.alfaflix.core.result.AppError
import com.application.alfaflix.ui.theme.Spacing

/**
 * Calls [onLoadMore] when the grid scrolls within [PREFETCH_DISTANCE] items of its end.
 *
 * Keyed on the item count as well as the trigger, so when a freshly appended page is still
 * short of filling the screen the effect fires again instead of stalling. It is switched off
 * with [enabled] while a page is loading or has failed — after a failure the user retries
 * explicitly rather than the grid hammering a broken endpoint on every frame.
 */
@Composable
fun LoadMoreEffect(
    gridState: LazyGridState,
    enabled: Boolean,
    onLoadMore: () -> Unit,
) {
    val nearEnd by remember(gridState) {
        derivedStateOf {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            lastVisible >= info.totalItemsCount - PREFETCH_DISTANCE
        }
    }
    // Read through derivedStateOf so scrolling does not recompose this on every frame.
    val itemCount by remember(gridState) { derivedStateOf { gridState.layoutInfo.totalItemsCount } }

    LaunchedEffect(nearEnd, enabled, itemCount) {
        if (nearEnd && enabled) onLoadMore()
    }
}

/**
 * The grid's trailing row: a spinner while the next page loads, or an inline error with
 * retry. Inline on purpose — the movies already on screen stay put and readable.
 */
fun LazyGridScope.paginationFooter(
    isLoading: Boolean,
    error: AppError?,
    onRetry: () -> Unit,
) {
    if (isLoading) {
        item(key = "footer_loading", span = { GridItemSpan(maxLineSpan) }, contentType = "footer") {
            val label = stringResource(R.string.loading_more)
            PaginationFooterBox(Modifier.semantics { stateDescription = label }) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
        }
    }

    if (error != null) {
        item(key = "footer_error", span = { GridItemSpan(maxLineSpan) }, contentType = "footer") {
            PaginationFooterBox {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        text = error.asMessage(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (error.isRetryable) {
                        TextButton(onClick = onRetry) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaginationFooterBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Start loading the next page slightly before the user reaches the end. */
private const val PREFETCH_DISTANCE = 6
