package com.application.alfaflix.domain.repository

import com.application.alfaflix.domain.model.Movie
import kotlinx.coroutines.flow.Flow

/**
 * Saved movies.
 *
 * Purely local: this data is created by the user, is never fetched, and therefore works
 * identically online and offline.
 */
interface FavoritesRepository {

    fun observeFavorites(): Flow<List<Movie>>

    /**
     * The IDs of every saved movie.
     *
     * The app's single source of truth for favorite state. Screens combine this with their
     * own content rather than tracking favorites themselves, which is what keeps Home,
     * Search, Detail and Favorites consistent by construction.
     */
    fun observeFavoriteIds(): Flow<Set<Int>>

    suspend fun isFavorite(movieId: Int): Boolean

    suspend fun add(movie: Movie)

    suspend fun remove(movieId: Int)
}
