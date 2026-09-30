package com.application.alfaflix.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MovieFilter
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.application.alfaflix.R
import com.application.alfaflix.core.result.RefreshState
import com.application.alfaflix.domain.model.MovieListItem
import com.application.alfaflix.ui.components.EmptyState
import com.application.alfaflix.ui.components.ErrorState
import com.application.alfaflix.ui.components.LoadMoreEffect
import com.application.alfaflix.ui.components.MoviePosterCard
import com.application.alfaflix.ui.components.MoviePosterCardSkeleton
import com.application.alfaflix.ui.components.SectionHeader
import com.application.alfaflix.ui.components.StaleDataBanner
import com.application.alfaflix.ui.components.paginationFooter
import com.application.alfaflix.ui.theme.Spacing

@Composable
fun HomeScreen(
    onMovieClick: (Int) -> Unit,
    onAboutClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // "Surprise me" is a one-off navigation event delivered over a channel, so it fires
    // exactly once and does not repeat across configuration changes.
    LaunchedEffect(viewModel, onMovieClick) {
        viewModel.surpriseMovie.collect(onMovieClick)
    }

    HomeScreenContent(
        uiState = uiState,
        onRefresh = { viewModel.refresh() },
        onLoadNextPage = viewModel::loadNextPage,
        onSurpriseMe = viewModel::onSurpriseMe,
        onToggleFavorite = viewModel::onToggleFavorite,
        onMovieClick = onMovieClick,
        onAboutClick = onAboutClick,
        modifier = modifier,
    )
}

/** Stateless so it can be tested and previewed without a ViewModel or a network. */
@Composable
fun HomeScreenContent(
    uiState: HomeUiState,
    onRefresh: () -> Unit,
    onLoadNextPage: () -> Unit,
    onSurpriseMe: () -> Unit,
    onToggleFavorite: (MovieListItem) -> Unit,
    onMovieClick: (Int) -> Unit,
    onAboutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        HomeTopBar(
            canSurpriseMe = (uiState as? HomeUiState.Content)?.canSurpriseMe == true,
            onSurpriseMe = onSurpriseMe,
            onAboutClick = onAboutClick,
        )

        when (uiState) {
            HomeUiState.Loading -> DiscoverSkeleton()

            is HomeUiState.Error -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                ErrorState(error = uiState.error, onRetry = onRefresh)
            }

            is HomeUiState.Content -> DiscoverContent(
                state = uiState,
                onRefresh = onRefresh,
                onLoadNextPage = onLoadNextPage,
                onMovieClick = onMovieClick,
                onToggleFavorite = onToggleFavorite,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    canSurpriseMe: Boolean,
    onSurpriseMe: () -> Unit,
    onAboutClick: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.home_title)) },
        actions = {
            IconButton(onClick = onSurpriseMe, enabled = canSurpriseMe) {
                Icon(
                    imageVector = Icons.Outlined.Casino,
                    // The label explains *why* it is unavailable rather than going silent.
                    contentDescription = if (canSurpriseMe) {
                        stringResource(R.string.home_surprise_me_description)
                    } else {
                        stringResource(R.string.home_surprise_me_unavailable)
                    },
                )
            }
            IconButton(onClick = onAboutClick) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.home_about_description),
                )
            }
        },
    )
}

/**
 * The discovery grid — the screen's main event.
 *
 * Pull-to-refresh replaces the list only when the new page arrives, so the movies on screen
 * are never blanked while refreshing. The grid state is remembered per back-stack entry, so
 * returning from a movie's detail lands on the same scroll position.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoverContent(
    state: HomeUiState.Content,
    onRefresh: () -> Unit,
    onLoadNextPage: () -> Unit,
    onMovieClick: (Int) -> Unit,
    onToggleFavorite: (MovieListItem) -> Unit,
) {
    val gridState = rememberLazyGridState()

    LoadMoreEffect(
        gridState = gridState,
        // After a failure the user retries explicitly, so the effect stays quiet.
        enabled = state.hasMore && state.nextPage == RefreshState.Idle && !state.isRefreshing,
        onLoadMore = onLoadNextPage,
    )

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 140.dp),
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.fillMaxSize(),
        ) {
            // One item that is always present, holding the banner and the history row. Their
            // presence changes as the user opens movies and as connectivity flips; if each
            // were its own conditional item, the grid's indices above the fold would shift
            // and returning from a movie would land a row or two off. A single stable item
            // keeps every index below it fixed, so scroll restoration stays exact.
            item(key = "top", span = FULL_WIDTH, contentType = "top") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    // The banner only explains that what is on screen may be out of date; the
                    // content is never removed.
                    if (state.staleness != null) {
                        StaleDataBanner(staleness = state.staleness, onRetry = onRefresh, horizontalPadding = 0.dp)
                    }

                    // Omitted when empty: an empty history row on first launch would be noise.
                    if (state.recentlyViewed.isNotEmpty()) {
                        Column {
                            SectionHeader(
                                stringResource(R.string.home_section_recently_viewed),
                                horizontalPadding = 0.dp,
                            )
                            RecentlyViewedRow(
                                items = state.recentlyViewed,
                                onMovieClick = onMovieClick,
                                onToggleFavorite = onToggleFavorite,
                            )
                        }
                    }
                }
            }

            item(key = "discover_header", span = FULL_WIDTH, contentType = "header") {
                SectionHeader(stringResource(R.string.home_section_discover), horizontalPadding = 0.dp)
            }

            if (state.movies.isEmpty()) {
                item(key = "empty", span = FULL_WIDTH, contentType = "empty") {
                    EmptyState(
                        icon = Icons.Outlined.MovieFilter,
                        title = stringResource(R.string.empty_discover_title),
                        subtitle = stringResource(R.string.empty_discover_subtitle),
                    )
                }
            }

            items(
                items = state.movies,
                // Stable identity: keeps scroll position and avoids recomposing every tile
                // when a page is appended.
                key = { it.id },
                contentType = { "movie" },
            ) { item ->
                MoviePosterCard(
                    item = item,
                    onClick = { onMovieClick(it.id) },
                    onToggleFavorite = onToggleFavorite,
                )
            }

            paginationFooter(
                isLoading = state.nextPage == RefreshState.InProgress,
                error = (state.nextPage as? RefreshState.Failed)?.error,
                onRetry = onLoadNextPage,
            )

            if (!state.hasMore && state.movies.isNotEmpty()) {
                item(key = "end", span = FULL_WIDTH, contentType = "footer") {
                    Text(
                        text = stringResource(R.string.home_end_of_list),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.lg),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentlyViewedRow(
    items: List<MovieListItem>,
    onMovieClick: (Int) -> Unit,
    onToggleFavorite: (MovieListItem) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        items(items = items, key = { it.id }, contentType = { "movie" }) { item ->
            MoviePosterCard(
                item = item,
                onClick = { onMovieClick(it.id) },
                onToggleFavorite = onToggleFavorite,
                modifier = Modifier.width(Spacing.posterWidth),
            )
        }
    }
}

/** Mirrors the real grid, so the first paint does not jump when data lands. */
@Composable
private fun DiscoverSkeleton() {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        userScrollEnabled = false,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(count = SKELETON_COUNT, contentType = { "skeleton" }) {
            MoviePosterCardSkeleton()
        }
    }
}

private val FULL_WIDTH: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan =
    { GridItemSpan(maxLineSpan) }

private const val SKELETON_COUNT = 9
