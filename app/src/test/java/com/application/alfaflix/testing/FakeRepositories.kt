package com.application.alfaflix.testing

import com.application.alfaflix.core.result.AppResult
import com.application.alfaflix.core.result.Cached
import com.application.alfaflix.core.result.RefreshState
import com.application.alfaflix.domain.model.DiscoveryFeed
import com.application.alfaflix.domain.model.Movie
import com.application.alfaflix.domain.model.MovieDetail
import com.application.alfaflix.domain.model.MoviePage
import com.application.alfaflix.domain.model.Review
import com.application.alfaflix.domain.model.Trailer
import com.application.alfaflix.domain.repository.FavoritesRepository
import com.application.alfaflix.domain.repository.MovieRepository
import com.application.alfaflix.domain.repository.RecentlyViewedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory repository fakes.
 *
 * Preferred over mocks: they reproduce the real behavioural contract (deduplication,
 * ordering, capping) so a test that passes against a fake is evidence about the system,
 * not just about which methods were called.
 */

class FakeFavoritesRepository : FavoritesRepository {

    private val favorites = MutableStateFlow<Map<Int, Movie>>(emptyMap())

    override fun observeFavorites(): Flow<List<Movie>> = favorites.map { it.values.toList() }

    override fun observeFavoriteIds(): Flow<Set<Int>> = favorites.map { it.keys }

    override suspend fun isFavorite(movieId: Int): Boolean = favorites.value.containsKey(movieId)

    override suspend fun add(movie: Movie) {
        favorites.update { it + (movie.id to movie) }
    }

    override suspend fun remove(movieId: Int) {
        favorites.update { it - movieId }
    }
}

class FakeRecentlyViewedRepository : RecentlyViewedRepository {

    private val entries = MutableStateFlow<List<Movie>>(emptyList())

    override fun observeRecentlyViewed(): Flow<List<Movie>> = entries

    /** Mirrors the real contract: dedupe by id, newest first, capped. */
    override suspend fun record(movie: Movie) {
        entries.update { current ->
            (listOf(movie) + current.filterNot { it.id == movie.id })
                .take(RecentlyViewedRepository.MAX_ENTRIES)
        }
    }
}

class FakeMovieRepository : MovieRepository {

    /** Scriptable so a test can stall a call and control interleaving. */
    var searchHandler: suspend (query: String, page: Int) -> AppResult<MoviePage> =
        { _, page -> AppResult.Success(MoviePage.empty(page)) }

    var searchCallCount: Int = 0
        private set
    var lastQuery: String? = null
        private set
    var lastPage: Int? = null
        private set

    var randomMovie: Movie? = null

    var refreshDiscoveryCallCount: Int = 0
        private set
    var lastForcedRefresh: Boolean? = null
        private set
    var refreshDiscoveryResult: AppResult<Unit> = AppResult.Success(Unit)
    var loadMoreCallCount: Int = 0
        private set

    var refreshDetailResult: AppResult<Unit> = AppResult.Success(Unit)
    var refreshDetailCallCount: Int = 0
        private set
    var refreshReviewsResult: AppResult<Unit> = AppResult.Success(Unit)
    var refreshReviewsCallCount: Int = 0
        private set

    var trailerHandler: suspend (movieId: Int) -> AppResult<Trailer?> = { AppResult.Success(null) }
    var trailerCallCount: Int = 0
        private set

    private val discovery = MutableStateFlow(neverLoaded())
    private val details = mutableMapOf<Int, MutableStateFlow<Cached<MovieDetail>>>()
    private val reviews = mutableMapOf<Int, MutableStateFlow<Cached<List<Review>>>>()

    override suspend fun search(query: String, page: Int): AppResult<MoviePage> {
        searchCallCount++
        lastQuery = query
        lastPage = page
        return searchHandler(query, page)
    }

    override fun observeDiscovery(): Flow<DiscoveryFeed> = discovery

    override suspend fun refreshDiscovery(force: Boolean): AppResult<Unit> {
        refreshDiscoveryCallCount++
        lastForcedRefresh = force
        return refreshDiscoveryResult
    }

    override suspend fun loadMoreDiscovery(): AppResult<Unit> {
        loadMoreCallCount++
        return AppResult.Success(Unit)
    }

    override suspend fun randomCachedMovie(): Movie? = randomMovie

    override fun observeMovieDetail(movieId: Int): Flow<Cached<MovieDetail>> = detailFlow(movieId)

    override suspend fun refreshMovieDetail(movieId: Int, force: Boolean): AppResult<Unit> {
        refreshDetailCallCount++
        return refreshDetailResult
    }

    override fun observeReviews(movieId: Int): Flow<Cached<List<Review>>> = reviewsFlow(movieId)

    override suspend fun refreshReviews(movieId: Int, force: Boolean): AppResult<Unit> {
        refreshReviewsCallCount++
        return refreshReviewsResult
    }

    override suspend fun loadTrailer(movieId: Int): AppResult<Trailer?> {
        trailerCallCount++
        return trailerHandler(movieId)
    }

    fun setDiscovery(feed: DiscoveryFeed) {
        discovery.value = feed
    }

    fun setDetail(movieId: Int, cached: Cached<MovieDetail>) {
        detailFlow(movieId).value = cached
    }

    fun setReviews(movieId: Int, cached: Cached<List<Review>>) {
        reviewsFlow(movieId).value = cached
    }

    private fun detailFlow(movieId: Int) = details.getOrPut(movieId) { MutableStateFlow(Cached.empty()) }

    private fun reviewsFlow(movieId: Int) = reviews.getOrPut(movieId) { MutableStateFlow(Cached.empty()) }

    companion object {
        fun neverLoaded() = DiscoveryFeed(
            movies = null,
            lastUpdatedAt = null,
            refresh = RefreshState.Idle,
            append = RefreshState.Idle,
            hasMore = false,
        )

        fun loaded(
            movies: List<Movie>,
            hasMore: Boolean = true,
            lastUpdatedAt: Long = 1_000L,
            refresh: RefreshState = RefreshState.Idle,
            append: RefreshState = RefreshState.Idle,
        ) = DiscoveryFeed(movies, lastUpdatedAt, refresh, append, hasMore)
    }
}
