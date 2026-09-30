package com.application.alfaflix.presentation.favorites

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.application.alfaflix.R
import com.application.alfaflix.domain.model.MovieListItem
import com.application.alfaflix.ui.components.EmptyState
import com.application.alfaflix.ui.components.MoviePosterCard
import com.application.alfaflix.ui.theme.Spacing

@Composable
fun FavoritesScreen(
    onMovieClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    FavoritesScreenContent(
        uiState = uiState,
        onMovieClick = onMovieClick,
        onToggleFavorite = viewModel::onToggleFavorite,
        modifier = modifier,
    )
}

/**
 * Saved movies.
 *
 * No loading or error state exists here because the data is local — the screen is either
 * empty or populated, and it behaves identically with no connection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreenContent(
    uiState: FavoritesUiState,
    onMovieClick: (Int) -> Unit,
    onToggleFavorite: (MovieListItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.nav_favorites)) })

        if (uiState.isEmpty) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Outlined.FavoriteBorder,
                    title = stringResource(R.string.empty_favorites_title),
                    subtitle = stringResource(R.string.empty_favorites_subtitle),
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                contentPadding = PaddingValues(Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    items = uiState.movies,
                    key = { it.id },
                    contentType = { "movie" },
                ) { item ->
                    MoviePosterCard(
                        item = item,
                        onClick = { onMovieClick(it.id) },
                        onToggleFavorite = onToggleFavorite,
                        // Unfavoriting removes the tile; animating the departure makes the
                        // cause of the change obvious instead of items snapping around.
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(220, easing = FastOutSlowInEasing),
                            fadeOutSpec = tween(180, easing = FastOutSlowInEasing),
                            placementSpec = tween(260, easing = FastOutSlowInEasing),
                        ),
                    )
                }
            }
        }
    }
}
