package com.application.alfaflix.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.application.alfaflix.core.result.AppError
import com.application.alfaflix.core.result.AppResult
import com.application.alfaflix.core.result.RefreshState
import com.application.alfaflix.data.local.dao.DiscoveryDao
import com.application.alfaflix.data.local.database.AlfaflixDatabase
import com.application.alfaflix.data.local.entity.DiscoverMovieEntity
import com.application.alfaflix.data.remote.MovieRemoteDataSource
import com.application.alfaflix.data.remote.dto.ReviewsResponseDto
import com.application.alfaflix.data.remote.dto.VideosResponseDto
import com.application.alfaflix.domain.model.DiscoveryFeed
import com.application.alfaflix.testing.FakeTimeProvider
import com.application.alfaflix.testing.FakeTmdbApi
import com.application.alfaflix.testing.detailDto
import com.application.alfaflix.testing.listDto
import com.application.alfaflix.testing.reviewDto
import com.application.alfaflix.testing.videoDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * The offline-first contract, against a real (in-memory) Room database.
 *
 * What matters most and is easiest to regress:
 *
 *  1. Pagination bookkeeping is consistent — no duplicates, no double-appends, correct
 *     termination, and a page that raced a refresh is discarded.
 *  2. A failed or cancelled refresh never destroys or corrupts cached content.
 *  3. Discovery, detail and reviews fail independently.
 */
@RunWith(RobolectricTestRunner::class)
class MovieRepositoryTest {

    private lateinit var database: AlfaflixDatabase
    private lateinit var repository: MovieRepositoryImpl
    private val api = FakeTmdbApi()
    private val time = FakeTimeProvider()

    private val twoHours = TimeUnit.HOURS.toMillis(2)
    private val tenMinutes = TimeUnit.MINUTES.toMillis(10)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AlfaflixDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = newRepository()
    }

    @After
    fun tearDown() = database.close()

    private fun newRepository(discoveryDao: DiscoveryDao = database.discoveryDao()) = MovieRepositoryImpl(
        remoteDataSource = MovieRemoteDataSource(api = api, accessToken = "test-token"),
        movieDetailDao = database.movieDetailDao(),
        discoveryDao = discoveryDao,
        reviewDao = database.reviewDao(),
        timeProvider = time,
    )

    /** Three pages of three movies each: 1-3, 4-6, 7-9. */
    private fun threePagesOfDiscovery() {
        api.discoverHandler = { page ->
            val first = (page - 1) * 3 + 1
            listDto(page, 3, first, first + 1, first + 2)
        }
    }

    private suspend fun feedWhere(predicate: (DiscoveryFeed) -> Boolean) =
        repository.observeDiscovery().first(predicate)

    // region discovery: first load and pagination

    @Test
    fun `before anything is fetched discovery reports never loaded, not empty`() = runTest {
        val feed = repository.observeDiscovery().first()

        assertNull(feed.movies)
        assertNull(feed.lastUpdatedAt)
        assertEquals(false, feed.hasMore)
    }

    @Test
    fun `refreshing persists page one and reports more pages`() = runTest {
        threePagesOfDiscovery()

        assertEquals(AppResult.Success(Unit), repository.refreshDiscovery(force = true))

        val feed = feedWhere { it.movies != null }
        assertEquals(listOf(1, 2, 3), feed.movies!!.map { it.id })
        assertTrue(feed.hasMore)
        assertEquals(time.now, feed.lastUpdatedAt)
    }

    @Test
    fun `an empty result is a loaded feed, not a never-loaded one`() = runTest {
        api.discoverHandler = { page -> listDto(page, totalPages = 0) }

        repository.refreshDiscovery(force = true)

        val feed = feedWhere { it.movies != null }
        assertEquals(emptyList<Any>(), feed.movies)
        assertEquals(false, feed.hasMore)
    }

    @Test
    fun `loading more appends the next page in order`() = runTest {
        threePagesOfDiscovery()
        repository.refreshDiscovery(force = true)

        assertEquals(AppResult.Success(Unit), repository.loadMoreDiscovery())

        val feed = feedWhere { it.movies?.size == 6 }
        assertEquals(listOf(1, 2, 3, 4, 5, 6), feed.movies!!.map { it.id })
        assertEquals(listOf(1, 2), api.discoverPages)
        assertTrue(feed.hasMore)
    }

    @Test
    fun `pagination terminates on the last page and issues no further request`() = runTest {
        threePagesOfDiscovery()
        repository.refreshDiscovery(force = true)
        repository.loadMoreDiscovery()
        repository.loadMoreDiscovery()

        val feed = feedWhere { it.movies?.size == 9 }
        assertEquals(false, feed.hasMore)

        val requestsSoFar = api.discoverPages.size
        repository.loadMoreDiscovery()

        assertEquals("no request past the last page", requestsSoFar, api.discoverPages.size)
        assertEquals(listOf(1, 2, 3), api.discoverPages)
    }

    @Test
    fun `loading more before the first page has loaded does nothing`() = runTest {
        assertEquals(AppResult.Success(Unit), repository.loadMoreDiscovery())

        assertEquals(emptyList<Int>(), api.discoverPages)
    }

    @Test
    fun `a movie repeated on a later page is stored once`() = runTest {
        api.discoverHandler = { page ->
            if (page == 1) listDto(1, 2, 1, 2, 3) else listDto(2, 2, 3, 4, 5)
        }
        repository.refreshDiscovery(force = true)
        repository.loadMoreDiscovery()

        val feed = feedWhere { it.movies?.size == 5 }
        assertEquals(listOf(1, 2, 3, 4, 5), feed.movies!!.map { it.id })
    }

    @Test
    fun `concurrent load-more calls issue a single page request`() = runTest {
        threePagesOfDiscovery()
        repository.refreshDiscovery(force = true)

        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        api.discoverHandler = { page ->
            entered.complete(Unit)
            gate.await()
            listDto(page, 3, 4, 5, 6)
        }

        val first = launch { repository.loadMoreDiscovery() }
        entered.await()
        val second = repository.loadMoreDiscovery()

        assertEquals("the duplicate is a no-op success", AppResult.Success(Unit), second)
        gate.complete(Unit)
        first.join()

        assertEquals("page 2 was requested exactly once", listOf(1, 2), api.discoverPages)
        assertEquals(6, feedWhere { it.movies?.size == 6 }.movies!!.size)
    }

    @Test
    fun `a page that raced a refresh is discarded instead of corrupting the list`() = runTest {
        threePagesOfDiscovery()
        repository.refreshDiscovery(force = true)

        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        api.discoverHandler = { page ->
            if (page == 2) {
                entered.complete(Unit)
                gate.await()
                listDto(2, 3, 4, 5, 6)
            } else {
                listDto(1, 3, 10, 11, 12)
            }
        }

        val pageTwo = launch { repository.loadMoreDiscovery() }
        entered.await()
        // A refresh resets the list to a new page one while page two is still in flight.
        repository.refreshDiscovery(force = true)
        gate.complete(Unit)
        pageTwo.join()

        val feed = feedWhere { it.movies?.map { m -> m.id } == listOf(10, 11, 12) }
        assertEquals("the stale page must not be appended", listOf(10, 11, 12), feed.movies!!.map { it.id })
        assertEquals(RefreshState.Idle, feed.append)
    }

    @Test
    fun `refreshing replaces the list and restarts pagination from page two`() = runTest {
        threePagesOfDiscovery()
        repository.refreshDiscovery(force = true)
        repository.loadMoreDiscovery()
        feedWhere { it.movies?.size == 6 }

        repository.refreshDiscovery(force = true)
        feedWhere { it.movies?.size == 3 }
        repository.loadMoreDiscovery()

        assertEquals("after a refresh the next page requested is 2 again", listOf(1, 2, 1, 2), api.discoverPages)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), feedWhere { it.movies?.size == 6 }.movies!!.map { it.id })
    }

    // endregion

    // region discovery: freshness

    @Test
    fun `a fresh cache issues no request`() = runTest {
        threePagesOfDiscovery()
        repository.refreshDiscovery(force = false)
        time.advanceBy(tenMinutes)

        repository.refreshDiscovery(force = false)

        assertEquals(listOf(1), api.discoverPages)
    }

    @Test
    fun `a stale cache is refreshed`() = runTest {
        threePagesOfDiscovery()
        repository.refreshDiscovery(force = false)
        time.advanceBy(twoHours)

        repository.refreshDiscovery(force = false)

        assertEquals(listOf(1, 1), api.discoverPages)
    }

    @Test
    fun `force bypasses freshness`() = runTest {
        threePagesOfDiscovery()
        repository.refreshDiscovery(force = false)

        repository.refreshDiscovery(force = true)

        assertEquals(listOf(1, 1), api.discoverPages)
    }

    @Test
    fun `appending pages does not change when the feed was last refreshed`() = runTest {
        threePagesOfDiscovery()
        repository.refreshDiscovery(force = true)
        val refreshedAt = time.now
        time.advanceBy(tenMinutes)

        repository.loadMoreDiscovery()

        assertEquals(refreshedAt, feedWhere { it.movies?.size == 6 }.lastUpdatedAt)
    }

    // endregion

    // region discovery: failures and offline

    @Test
    fun `a failed first load with nothing cached reports the failure and no content`() = runTest {
        api.discoverHandler = { throw UnknownHostException() }

        val result = repository.refreshDiscovery(force = true)

        assertEquals(AppResult.Failure(AppError.Offline), result)
        val feed = feedWhere { it.refresh is RefreshState.Failed }
        assertNull(feed.movies)
        assertEquals(RefreshState.Failed(AppError.Offline), feed.refresh)
    }

    @Test
    fun `a failed refresh keeps the cached list and reports the failure beside it`() = runTest {
        threePagesOfDiscovery()
        repository.refreshDiscovery(force = true)
        api.discoverHandler = { throw UnknownHostException() }

        repository.refreshDiscovery(force = true)

        val feed = feedWhere { it.refresh is RefreshState.Failed }
        assertEquals("cached movies survive the failure", listOf(1, 2, 3), feed.movies!!.map { it.id })
        assertTrue(feed.hasMore)
    }

    @Test
    fun `a failed page load keeps the list and can be retried`() = runTest {
        threePagesOfDiscovery()
        repository.refreshDiscovery(force = true)
        api.discoverHandler = { throw UnknownHostException() }

        assertEquals(AppResult.Failure(AppError.Offline), repository.loadMoreDiscovery())
        val failed = feedWhere { it.append is RefreshState.Failed }
        assertEquals(listOf(1, 2, 3), failed.movies!!.map { it.id })

        threePagesOfDiscovery()
        assertEquals(AppResult.Success(Unit), repository.loadMoreDiscovery())
        val recovered = feedWhere { it.movies?.size == 6 }
        assertEquals(RefreshState.Idle, recovered.append)
    }

    @Test
    fun `cached discovery is readable by a fresh repository while offline`() = runTest {
        threePagesOfDiscovery()
        repository.refreshDiscovery(force = true)

        // "Reopening the app": a new repository instance over the same database, no network.
        api.discoverHandler = { throw UnknownHostException() }
        val reopened = newRepository()

        val feed = reopened.observeDiscovery().first { it.movies != null }
        assertEquals(listOf(1, 2, 3), feed.movies!!.map { it.id })
        reopened.refreshDiscovery(force = true)
        assertEquals(
            "the failed refresh did not erase what was cached",
            listOf(1, 2, 3),
            reopened.observeDiscovery().first { it.refresh is RefreshState.Failed }.movies!!.map { it.id },
        )
    }

    @Test
    fun `a cancelled refresh leaves no permanent refreshing state and keeps the cache`() = runTest {
        threePagesOfDiscovery()
        repository.refreshDiscovery(force = true)

        val entered = CompletableDeferred<Unit>()
        api.discoverHandler = {
            entered.complete(Unit)
            CompletableDeferred<Unit>().await()
            listDto(1, 3, 99)
        }
        val job = launch { repository.refreshDiscovery(force = true) }
        entered.await()
        assertEquals(RefreshState.InProgress, feedWhere { it.refresh == RefreshState.InProgress }.refresh)

        job.cancel()
        job.join()

        val feed = feedWhere { it.refresh == RefreshState.Idle }
        assertEquals(listOf(1, 2, 3), feed.movies!!.map { it.id })
    }

    @Test
    fun `a database failure during refresh becomes a failed refresh, not a crash or a stuck spinner`() = runTest {
        val real = database.discoveryDao()
        val failingDao = object : DiscoveryDao by real {
            override suspend fun replaceAll(movies: List<DiscoverMovieEntity>, totalPages: Int, refreshedAt: Long) {
                throw IOException("disk full")
            }
        }
        val failingRepository = newRepository(discoveryDao = failingDao)
        threePagesOfDiscovery()

        val result = failingRepository.refreshDiscovery(force = true)

        assertEquals(AppResult.Failure(AppError.Unknown), result)
        val feed = failingRepository.observeDiscovery().first { it.refresh is RefreshState.Failed }
        assertEquals(RefreshState.Failed(AppError.Unknown), feed.refresh)
        assertNull(feed.movies)
    }

    @Test
    fun `a random cached movie comes from stored discovery and is null when empty`() = runTest {
        assertNull(repository.randomCachedMovie())

        threePagesOfDiscovery()
        repository.refreshDiscovery(force = true)

        assertTrue(repository.randomCachedMovie()!!.id in 1..3)
    }

    // endregion

    // region search

    @Test
    fun `search goes straight to the network and never touches the cache`() = runTest {
        api.searchHandler = { _, page -> listDto(page, 1, 5) }

        val result = repository.search("matrix", page = 1)

        assertEquals(listOf(5), (result as AppResult.Success).data.movies.map { it.id })
        assertEquals(listOf("matrix" to 1), api.searchCalls)
        assertNull("search results are not persisted", repository.observeDiscovery().first().movies)
    }

    // endregion

    // region detail

    @Test
    fun `a detail cache inside its window issues no request`() = runTest {
        repository.refreshMovieDetail(1, force = false)
        time.advanceBy(tenMinutes)

        repository.refreshMovieDetail(1, force = false)

        assertEquals(1, api.detailCallCount)
    }

    @Test
    fun `an expired detail cache is refreshed and force always refreshes`() = runTest {
        repository.refreshMovieDetail(1, force = false)
        time.advanceBy(TimeUnit.DAYS.toMillis(2))
        repository.refreshMovieDetail(1, force = false)
        assertEquals(2, api.detailCallCount)

        repository.refreshMovieDetail(1, force = true)
        assertEquals(3, api.detailCallCount)
    }

    @Test
    fun `detail is persisted and readable again by a fresh repository`() = runTest {
        api.detailHandler = { detailDto(it, "The Matrix") }
        repository.refreshMovieDetail(603, force = true)

        api.detailHandler = { throw UnknownHostException() }
        val cached = newRepository().observeMovieDetail(603).first { it.data != null }

        assertEquals("The Matrix", cached.data!!.title)
        assertEquals("tt603", cached.data!!.imdbId)
    }

    @Test
    fun `a failed detail refresh keeps the cached detail`() = runTest {
        api.detailHandler = { detailDto(it, "Batman Begins") }
        repository.refreshMovieDetail(1, force = true)
        api.detailHandler = { throw UnknownHostException() }

        val result = repository.refreshMovieDetail(1, force = true)

        assertEquals(AppResult.Failure(AppError.Offline), result)
        val state = repository.observeMovieDetail(1).first { it.refresh is RefreshState.Failed }
        assertEquals("Batman Begins", state.data?.title)
    }

    @Test
    fun `a failed detail fetch with nothing cached has no data`() = runTest {
        api.detailHandler = { throw UnknownHostException() }

        repository.refreshMovieDetail(1, force = true)

        val state = repository.observeMovieDetail(1).first { it.refresh is RefreshState.Failed }
        assertNull(state.data)
    }

    // endregion

    // region reviews

    @Test
    fun `reviews are persisted in order and readable by a fresh repository`() = runTest {
        api.reviewsHandler = { ReviewsResponseDto(results = listOf(reviewDto("b"), reviewDto("a"))) }
        repository.refreshReviews(1, force = true)

        api.reviewsHandler = { throw UnknownHostException() }
        val cached = newRepository().observeReviews(1).first { it.data != null }

        assertEquals(listOf("b", "a"), cached.data!!.map { it.id })
    }

    @Test
    fun `a movie with no reviews records a successful fetch, distinct from never loaded`() = runTest {
        assertNull(repository.observeReviews(1).first().data)

        api.reviewsHandler = { ReviewsResponseDto(results = emptyList()) }
        repository.refreshReviews(1, force = true)

        assertEquals(emptyList<Any>(), repository.observeReviews(1).first { it.data != null }.data)
    }

    @Test
    fun `a failed reviews refresh keeps the cached reviews`() = runTest {
        api.reviewsHandler = { ReviewsResponseDto(results = listOf(reviewDto("a"))) }
        repository.refreshReviews(1, force = true)
        api.reviewsHandler = { throw UnknownHostException() }

        repository.refreshReviews(1, force = true)

        val state = repository.observeReviews(1).first { it.refresh is RefreshState.Failed }
        assertEquals(listOf("a"), state.data!!.map { it.id })
    }

    @Test
    fun `reviews are cached per movie`() = runTest {
        api.reviewsHandler = { id -> ReviewsResponseDto(results = listOf(reviewDto("for-$id"))) }
        repository.refreshReviews(1, force = true)
        repository.refreshReviews(2, force = true)

        assertEquals(listOf("for-1"), repository.observeReviews(1).first { it.data != null }.data!!.map { it.id })
        assertEquals(listOf("for-2"), repository.observeReviews(2).first { it.data != null }.data!!.map { it.id })
    }

    // endregion

    // region independence and trailers

    @Test
    fun `reviews can fail while detail succeeds`() = runTest {
        api.reviewsHandler = { throw UnknownHostException() }

        assertEquals(AppResult.Success(Unit), repository.refreshMovieDetail(1, force = true))
        assertEquals(AppResult.Failure(AppError.Offline), repository.refreshReviews(1, force = true))

        assertNotNull(repository.observeMovieDetail(1).first { it.data != null }.data)
        assertEquals(RefreshState.Idle, repository.observeMovieDetail(1).first().refresh)
    }

    @Test
    fun `the trailer is chosen from the movie's videos`() = runTest {
        api.videosHandler = {
            VideosResponseDto(
                results = listOf(
                    videoDto("teaser", type = "Teaser"),
                    videoDto("fan", official = false),
                    videoDto("studio"),
                ),
            )
        }

        val trailer = (repository.loadTrailer(1) as AppResult.Success).data

        assertEquals("studio", trailer?.youtubeKey)
    }

    @Test
    fun `a movie with only teasers has no trailer`() = runTest {
        api.videosHandler = { VideosResponseDto(results = listOf(videoDto("t", type = "Teaser"))) }

        assertNull((repository.loadTrailer(1) as AppResult.Success).data)
    }

    @Test
    fun `a trailer lookup failure is reported, not hidden as no trailer`() = runTest {
        api.videosHandler = { throw UnknownHostException() }

        assertEquals(AppResult.Failure(AppError.Offline), repository.loadTrailer(1))
    }

    // endregion
}
