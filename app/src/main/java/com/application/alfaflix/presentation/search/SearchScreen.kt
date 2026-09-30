package com.application.alfaflix.presentation.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.application.alfaflix.R
import com.application.alfaflix.core.result.AppError
import com.application.alfaflix.domain.model.MovieListItem
import com.application.alfaflix.ui.components.EmptyState
import com.application.alfaflix.ui.components.ErrorState
import com.application.alfaflix.ui.components.LoadMoreEffect
import com.application.alfaflix.ui.components.MoviePosterCard
import com.application.alfaflix.ui.components.MoviePosterCardSkeleton
import com.application.alfaflix.ui.components.paginationFooter
import com.application.alfaflix.ui.theme.Spacing

@Composable
fun SearchScreen(
    onMovieClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SearchScreenContent(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onClearQuery = viewModel::onClearQuery,
        onLoadNextPage = viewModel::loadNextPage,
        onRetry = viewModel::retry,
        onToggleFavorite = viewModel::onToggleFavorite,
        onMovieClick = onMovieClick,
        modifier = modifier,
    )
}

/**
 * Stateless so it can be previewed and tested without a ViewModel or a network.
 */
@Composable
fun SearchScreenContent(
    uiState: SearchUiState,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
    onToggleFavorite: (MovieListItem) -> Unit,
    onMovieClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        SearchField(
            query = uiState.query,
            onQueryChange = onQueryChange,
            onClearQuery = onClearQuery,
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when (val stage = uiState.stage) {
                SearchUiState.Stage.Idle -> IdleOrOfflineState(isOffline = uiState.isOffline)

                SearchUiState.Stage.Loading -> SkeletonGrid()

                SearchUiState.Stage.Empty -> EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = stringResource(R.string.empty_no_results_title),
                    subtitle = stringResource(R.string.empty_no_results_subtitle),
                    modifier = Modifier.align(Alignment.Center),
                )

                is SearchUiState.Stage.Error -> SearchErrorState(
                    error = stage.error,
                    isOffline = uiState.isOffline,
                    onRetry = onRetry,
                    modifier = Modifier.align(Alignment.Center),
                )

                SearchUiState.Stage.Content -> ResultsGrid(
                    uiState = uiState,
                    onLoadNextPage = onLoadNextPage,
                    onToggleFavorite = onToggleFavorite,
                    onMovieClick = onMovieClick,
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        label = { Text(stringResource(R.string.search_field_label)) },
        placeholder = { Text(stringResource(R.string.search_field_placeholder)) },
        singleLine = true,
        leadingIcon = {
            // Decorative: the field's own label already describes its purpose.
            Icon(Icons.Outlined.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearQuery) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.search_clear),
                    )
                }
            }
        },
        // Results already arrive as the user types; the action just dismisses the keyboard.
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
    )
}

@Composable
private fun IdleOrOfflineState(isOffline: Boolean) {
    if (isOffline) {
        // Search is the one browsing feature that cannot work offline, so this state points
        // the user at the ones that can rather than leaving them at a dead end.
        EmptyState(
            icon = Icons.Outlined.CloudOff,
            title = stringResource(R.string.empty_offline_search_title),
            subtitle = stringResource(R.string.empty_offline_search_subtitle),
        )
    } else {
        EmptyState(
            icon = Icons.Outlined.Search,
            title = stringResource(R.string.empty_search_title),
            subtitle = stringResource(R.string.empty_search_subtitle),
        )
    }
}

@Composable
private fun SearchErrorState(
    error: AppError,
    isOffline: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isOffline || error == AppError.Offline) {
        EmptyState(
            icon = Icons.Outlined.CloudOff,
            title = stringResource(R.string.empty_offline_search_title),
            subtitle = stringResource(R.string.empty_offline_search_subtitle),
            modifier = modifier,
        )
    } else {
        ErrorState(error = error, onRetry = onRetry, modifier = modifier)
    }
}

@Composable
private fun ResultsGrid(
    uiState: SearchUiState,
    onLoadNextPage: () -> Unit,
    onToggleFavorite: (MovieListItem) -> Unit,
    onMovieClick: (Int) -> Unit,
) {
    val gridState = rememberLazyGridState()

    LoadMoreEffect(
        gridState = gridState,
        // After a failure the user retries explicitly rather than the grid re-requesting.
        enabled = uiState.canLoadMore && !uiState.isLoadingNextPage && uiState.nextPageError == null,
        onLoadMore = onLoadNextPage,
    )

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(
            items = uiState.movies,
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
            isLoading = uiState.isLoadingNextPage,
            error = uiState.nextPageError,
            onRetry = onLoadNextPage,
        )
    }
}

/** A skeleton grid that mirrors the real layout so content arrival does not shift the page. */
@Composable
private fun SkeletonGrid() {
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

private const val SKELETON_COUNT = 6
