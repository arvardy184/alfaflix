package com.application.alfaflix.data.repository

import com.application.alfaflix.core.time.TimeProvider
import com.application.alfaflix.data.local.dao.FavoriteDao
import com.application.alfaflix.data.local.entity.FavoriteMovieEntity
import com.application.alfaflix.data.local.mapper.toDomain
import com.application.alfaflix.data.local.mapper.toFavoriteEntity
import com.application.alfaflix.domain.model.Movie
import com.application.alfaflix.domain.repository.FavoritesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Favorites, backed entirely by Room.
 *
 * There is no remote counterpart by design: this is user-authored data, which is exactly
 * why it keeps working with no connection.
 */
@Singleton
class FavoritesRepositoryImpl @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val timeProvider: TimeProvider,
) : FavoritesRepository {

    override fun observeFavorites(): Flow<List<Movie>> =
        favoriteDao.observeAll().map { entities -> entities.map(FavoriteMovieEntity::toDomain) }

    override fun observeFavoriteIds(): Flow<Set<Int>> =
        favoriteDao.observeIds()
            .map { it.toSet() }
            // Room re-emits the whole table on any write. Without this, favoriting movie A
            // would recompose every screen observing movie B's state for no reason.
            .distinctUntilChanged()

    override suspend fun isFavorite(movieId: Int): Boolean = favoriteDao.exists(movieId)

    override suspend fun add(movie: Movie) {
        favoriteDao.insert(movie.toFavoriteEntity(addedAt = timeProvider.nowMillis()))
    }

    override suspend fun remove(movieId: Int) {
        favoriteDao.delete(movieId)
    }
}
