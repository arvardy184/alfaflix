package com.application.alfaflix.data.repository

import com.application.alfaflix.core.result.AppResult
import com.application.alfaflix.core.result.Cached
import com.application.alfaflix.core.result.RefreshState
import com.application.alfaflix.core.time.TimeProvider
import com.application.alfaflix.data.local.dao.DiscoveryDao
import com.application.alfaflix.data.local.dao.MovieDetailDao
import com.application.alfaflix.data.local.dao.ReviewDao
import com.application.alfaflix.data.local.entity.DiscoverMetaEntity
import com.application.alfaflix.data.local.entity.DiscoverMovieEntity
import com.application.alfaflix.data.local.mapper.toDiscoverEntity
import com.application.alfaflix.data.local.mapper.toDomain
import com.application.alfaflix.data.local.mapper.toEntity
import com.application.alfaflix.data.remote.MovieRemoteDataSource
import com.application.alfaflix.domain.model.DiscoveryFeed
import com.application.alfaflix.domain.model.Movie
import com.application.alfaflix.domain.model.MovieDetail
import com.application.alfaflix.domain.model.MoviePage
import com.application.alfaflix.domain.model.Review
import com.application.alfaflix.domain.model.Trailer
import com.application.alfaflix.domain.model.TrailerSelector
import com.application.alfaflix.domain.repository.MovieRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Movie data with a persisted, cache-first tier.
 *
 * Refresh state lives in memory ([RefreshTracker]) while the content lives in Room, so a
 * failed or cancelled refresh can never corrupt or erase what is already on disk: content is
 * only written after a request has fully succeeded, and each write is one transaction.
 */
@Singleton
class MovieRepositoryImpl @Inject constructor(
    private val remoteDataSource: MovieRemoteDataSource,
    private val movieDetailDao: MovieDetailDao,
    private val discoveryDao: DiscoveryDao,
    private val reviewDao: ReviewDao,
    private val timeProvider: TimeProvider,
) : MovieRepository {

    private val discoveryRefresh = RefreshTracker<Unit>()
    private val discoveryAppend = RefreshTracker<Unit>()
    private val detailRefresh = RefreshTracker<Int>()
    private val reviewsRefresh = RefreshTracker<Int>()

    // region search

    override suspend fun search(query: String, page: Int): AppResult<MoviePage> =
        remoteDataSource.search(query = query, page = page)

    // endregion

    // region discovery

    override fun observeDiscovery(): Flow<DiscoveryFeed> = combine(
        discoveryDao.observeMovies(),
        discoveryDao.observeMeta(),
        discoveryRefresh.observe(Unit),
        discoveryAppend.observe(Unit),
    ) { rows, meta, refresh, append ->
        DiscoveryFeed(
            // A missing meta row means "never fetched", which is what separates a first
            // load from a list that was fetched and is legitimately empty.
            movies = if (meta != null) rows.map(DiscoverMovieEntity::toDomain) else null,
            lastUpdatedAt = meta?.refreshedAt,
            refresh = refresh,
            append = append,
            hasMore = meta?.hasMorePages() == true,
        )
    }

    override suspend fun refreshDiscovery(force: Boolean): AppResult<Unit> {
        val meta = discoveryDao.meta()
        if (!force && meta != null && isFresh(meta.refreshedAt, DISCOVERY_TTL_MILLIS)) {
            return AppResult.Success(Unit)
        }

        // Exclusive: a second refresh while one is running would only repeat its request.
        return discoveryRefresh.track(Unit, exclusive = true) {
            when (val result = remoteDataSource.discover(FIRST_PAGE)) {
                is AppResult.Success -> {
                    val page = result.data
                    discoveryDao.replaceAll(
                        movies = page.movies.mapIndexed { index, movie -> movie.toDiscoverEntity(index) },
                        totalPages = page.totalPages,
                        refreshedAt = timeProvider.nowMillis(),
                    )
                    AppResult.Success(Unit)
                }

                is AppResult.Failure -> result
            }
        }
    }

    override suspend fun loadMoreDiscovery(): AppResult<Unit> {
        // Cheap pre-checks; the authoritative ones are the exclusive tracker below and the
        // transactional page check inside the DAO.
        val meta = discoveryDao.meta() ?: return AppResult.Success(Unit)
        if (!meta.hasMorePages()) return AppResult.Success(Unit)

        return discoveryAppend.track(Unit, exclusive = true) {
            val nextPage = meta.loadedPages + 1
            when (val result = remoteDataSource.discover(nextPage)) {
                is AppResult.Success -> {
                    // The DAO discards the page if a refresh reset the list while this
                    // request was in flight, so stale pages cannot corrupt the ordering.
                    discoveryDao.appendPage(
                        expectedEpoch = meta.epoch,
                        page = nextPage,
                        movies = result.data.movies.map { it.toDiscoverEntity(position = 0) },
                        totalPages = result.data.totalPages,
                    )
                    AppResult.Success(Unit)
                }

                is AppResult.Failure -> result
            }
        }
    }

    override suspend fun randomCachedMovie(): Movie? = discoveryDao.randomMovie()?.toDomain()

    private fun DiscoverMetaEntity.hasMorePages(): Boolean =
        loadedPages < minOf(totalPages, MAX_TMDB_PAGES)

    // endregion

    // region detail

    override fun observeMovieDetail(movieId: Int): Flow<Cached<MovieDetail>> = combine(
        movieDetailDao.observe(movieId),
        detailRefresh.observe(movieId),
    ) { entity, refresh ->
        Cached(data = entity?.toDomain(), lastUpdatedAt = entity?.cachedAt, refresh = refresh)
    }

    override suspend fun refreshMovieDetail(movieId: Int, force: Boolean): AppResult<Unit> {
        val cachedAt = movieDetailDao.cachedAt(movieId)
        if (!force && cachedAt != null && isFresh(cachedAt, DETAIL_TTL_MILLIS)) {
            return AppResult.Success(Unit)
        }

        return detailRefresh.track(movieId) {
            when (val result = remoteDataSource.detail(movieId)) {
                is AppResult.Success -> {
                    movieDetailDao.upsert(result.data.toEntity(cachedAt = timeProvider.nowMillis()))
                    AppResult.Success(Unit)
                }

                // The cached row is left untouched, so observers keep the old content
                // alongside this failure rather than blanking the screen.
                is AppResult.Failure -> result
            }
        }
    }

    // endregion

    // region reviews

    override fun observeReviews(movieId: Int): Flow<Cached<List<Review>>> = combine(
        reviewDao.observeReviews(movieId),
        reviewDao.observeRefreshedAt(movieId),
        reviewsRefresh.observe(movieId),
    ) { rows, refreshedAt, refresh ->
        Cached(
            data = if (refreshedAt != null) rows.map { it.toDomain() } else null,
            lastUpdatedAt = refreshedAt,
            refresh = refresh,
        )
    }

    override suspend fun refreshReviews(movieId: Int, force: Boolean): AppResult<Unit> {
        val refreshedAt = reviewDao.refreshedAt(movieId)
        if (!force && refreshedAt != null && isFresh(refreshedAt, REVIEWS_TTL_MILLIS)) {
            return AppResult.Success(Unit)
        }

        return reviewsRefresh.track(movieId) {
            when (val result = remoteDataSource.reviews(movieId)) {
                is AppResult.Success -> {
                    reviewDao.replaceReviews(
                        movieId = movieId,
                        reviews = result.data.mapIndexed { index, review -> review.toEntity(movieId, index) },
                        refreshedAt = timeProvider.nowMillis(),
                    )
                    AppResult.Success(Unit)
                }

                is AppResult.Failure -> result
            }
        }
    }

    // endregion

    override suspend fun loadTrailer(movieId: Int): AppResult<Trailer?> =
        when (val result = remoteDataSource.videos(movieId)) {
            is AppResult.Success -> AppResult.Success(TrailerSelector.select(result.data))
            is AppResult.Failure -> result
        }

    private fun isFresh(timestamp: Long, ttlMillis: Long): Boolean =
        timeProvider.nowMillis() - timestamp < ttlMillis

    private companion object {
        const val FIRST_PAGE = 1

        /** TMDB refuses to serve discover pages beyond 500. */
        const val MAX_TMDB_PAGES = 500

        /** Popularity shifts through the day; an hour keeps launches cheap without going stale. */
        val DISCOVERY_TTL_MILLIS: Long = TimeUnit.HOURS.toMillis(1)

        /** Detail changes slowly (votes drift, the record itself does not). */
        val DETAIL_TTL_MILLIS: Long = TimeUnit.DAYS.toMillis(1)

        val REVIEWS_TTL_MILLIS: Long = TimeUnit.HOURS.toMillis(6)
    }
}
