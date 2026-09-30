package com.application.alfaflix.presentation.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.application.alfaflix.domain.model.MovieListItem
import com.application.alfaflix.domain.repository.FavoritesRepository
import com.application.alfaflix.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Saved movies.
 *
 * There is no loading, error, or offline state here by design: the data is local, so the
 * screen is either empty or populated and works identically with no connection.
 */
data class FavoritesUiState(
    val movies: List<MovieListItem> = emptyList(),
) {
    val isEmpty: Boolean get() = movies.isEmpty()
}

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    favoritesRepository: FavoritesRepository,
    private val toggleFavorite: ToggleFavoriteUseCase,
) : ViewModel() {

    val uiState: StateFlow<FavoritesUiState> = favoritesRepository.observeFavorites()
        .map { movies ->
            FavoritesUiState(
                // Everything on this screen is saved by definition.
                movies = movies.map { MovieListItem(movie = it, isFavorite = true) },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = FavoritesUiState(),
        )

    fun onToggleFavorite(item: MovieListItem) {
        viewModelScope.launch { toggleFavorite(item.movie) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
