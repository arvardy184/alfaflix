package com.application.alfaflix.data.repository

import com.application.alfaflix.core.result.AppResult
import com.application.alfaflix.data.local.dao.FavoriteDao
import com.application.alfaflix.data.local.dao.LegacyFavoriteDao
import com.application.alfaflix.data.local.entity.FavoriteMovieEntity
import com.application.alfaflix.data.remote.MovieRemoteDataSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries favorites saved by the OMDb-era app over to TMDB identities.
 *
 * The 1 → 2 database migration parks each old favorite in `legacy_favorites` keyed by IMDb
 * ID. TMDB can resolve an IMDb ID to its own movie ID, but that needs a network, so the work
 * happens here, on a later online launch, rather than inside the migration.
 *
 * Idempotent and resumable: each row is moved individually and only removed once its
 * replacement is stored, so an interrupted run loses nothing and simply continues next time.
 */
@Singleton
class LegacyFavoritesMigrator @Inject constructor(
    private val legacyDao: LegacyFavoriteDao,
    private val favoriteDao: FavoriteDao,
    private val remoteDataSource: MovieRemoteDataSource,
) {

    /** True when there is nothing left to migrate. */
    suspend fun isDone(): Boolean = legacyDao.count() == 0

    /**
     * Resolves and moves as many legacy favorites as possible.
     *
     * A transient failure (offline, rate limited) stops the run and keeps the remaining rows
     * for next time. A definitive "TMDB has no such movie" answer drops that one row, since
     * it can never be resolved.
     */
    suspend fun migrate() {
        for (legacy in legacyDao.all()) {
            when (val result = remoteDataSource.findByImdbId(legacy.imdbId)) {
                is AppResult.Failure -> return

                is AppResult.Success -> {
                    val movie = result.data
                    if (movie != null) {
                        favoriteDao.insertIfAbsent(
                            FavoriteMovieEntity(
                                movieId = movie.id,
                                title = movie.title,
                                releaseDate = movie.releaseDate,
                                posterPath = movie.posterPath,
                                voteAverage = movie.voteAverage,
                                // Keeps the user's original ordering of their favorites.
                                addedAt = legacy.addedAt,
                            ),
                        )
                    }
                    legacyDao.delete(legacy.imdbId)
                }
            }
        }
    }
}
