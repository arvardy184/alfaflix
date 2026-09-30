package com.application.alfaflix.domain.usecase

import com.application.alfaflix.domain.model.Movie
import com.application.alfaflix.domain.repository.FavoritesRepository
import javax.inject.Inject

/**
 * Adds or removes a movie from favorites, whichever applies.
 *
 * Callers pass the movie and get back the resulting state; they never have to read the
 * current one first. Centralising the read-then-branch means the toggle behaves identically
 * from Home, Search, Detail and Favorites, and the "is it saved?" check cannot drift out of
 * sync with the write that follows it.
 *
 * @return true if the movie is now saved, false if it was removed. Useful for announcing
 *   the outcome to accessibility services and for confirmation messaging.
 */
class ToggleFavoriteUseCase @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
) {

    suspend operator fun invoke(movie: Movie): Boolean =
        if (favoritesRepository.isFavorite(movie.id)) {
            favoritesRepository.remove(movie.id)
            false
        } else {
            favoritesRepository.add(movie)
            true
        }
}
