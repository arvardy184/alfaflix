package com.application.alfaflix.presentation.detail

import androidx.lifecycle.SavedStateHandle
import com.application.alfaflix.core.result.AppError
import com.application.alfaflix.core.result.AppResult
import com.application.alfaflix.core.result.Cached
import com.application.alfaflix.core.result.RefreshState
import com.application.alfaflix.core.result.Staleness
import com.application.alfaflix.domain.model.Trailer
import com.application.alfaflix.domain.usecase.ObserveMovieDetailUseCase
import com.application.alfaflix.domain.usecase.ToggleFavoriteUseCase
import com.application.alfaflix.presentation.navigation.MovieDetailRoute
import com.application.alfaflix.testing.FakeFavoritesRepository
import com.application.alfaflix.testing.FakeMovieRepository
import com.application.alfaflix.testing.FakeNetworkMonitor
import com.application.alfaflix.testing.FakeRecentlyViewedRepository
import com.application.alfaflix.testing.MainDispatcherRule
import com.application.alfaflix.testing.movieDetail
import com.application.alfaflix.testing.review
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The detail screen composes three independent loads — the movie, its reviews and its
 * trailer. The property under test throughout: **a failed section never hides the movie**.
 */
class MovieDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val movies = FakeMovieRepository()
    private val favorites = FakeFavoritesRepository()
    private val recentlyViewed = FakeRecentlyViewedRepository()
    private val networkMonitor = FakeNetworkMonitor()

    private fun viewModel(movieId: Int = 1) = MovieDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf(MovieDetailRoute.MOVIE_ID_ARG to movieId)),
        observeMovieDetail = ObserveMovieDetailUseCase(movies, favorites),
        movieRepository = movies,
        recentlyViewedRepository = recentlyViewed,
        toggleFavorite = ToggleFavoriteUseCase(favorites),
        networkMonitor = networkMonitor,
    )

    private fun TestScope.subscribed(viewModel: MovieDetailViewModel) = also {
        backgroundScope.launch { viewModel.uiState.collect() }
    }

    private fun cachedDetail(id: Int = 1, title: String = "Batman Begins") =
        Cached(data = movieDetail(id, title), lastUpdatedAt = 500L)

    private fun MovieDetailViewModel.content() = uiState.value as MovieDetailUiState.Content

    // region the movie itself

    @Test
    fun `nothing cached yet shows loading and triggers all three loads`() = runTest(mainDispatcherRule.scheduler) {
        val viewModel = viewModel()
        subscribed(viewModel)
        advanceUntilIdle()

        assertEquals(MovieDetailUiState.Loading, viewModel.uiState.value)
        assertEquals(1, movies.refreshDetailCallCount)
        assertEquals(1, movies.refreshReviewsCallCount)
        assertEquals(1, movies.trailerCallCount)
    }

    @Test
    fun `cached detail renders as content`() = runTest(mainDispatcherRule.scheduler) {
        movies.setDetail(1, cachedDetail())
        val viewModel = viewModel()
        subscribed(viewModel)
        advanceUntilIdle()

        val content = viewModel.content()
        assertEquals("Batman Begins", content.detail.title)
        assertFalse(content.isFavorite)
        assertNull(content.staleness)
    }

    @Test
    fun `a failed fetch with nothing cached is an error`() = runTest(mainDispatcherRule.scheduler) {
        movies.setDetail(1, Cached(null, null, RefreshState.Failed(AppError.Offline)))
        val viewModel = viewModel()
        subscribed(viewModel)
        advanceUntilIdle()

        assertEquals(MovieDetailUiState.Error(AppError.Offline), viewModel.uiState.value)
    }

    @Test
    fun `cached detail survives a failed refresh with a staleness explanation`() =
        runTest(mainDispatcherRule.scheduler) {
            movies.setDetail(1, cachedDetail().copy(refresh = RefreshState.Failed(AppError.Server(500))))
            val viewModel = viewModel()
            subscribed(viewModel)
            advanceUntilIdle()

            val content = viewModel.content()
            assertEquals("Batman Begins", content.detail.title)
            assertEquals(Staleness.Reason.REFRESH_FAILED, content.staleness?.reason)
        }

    @Test
    fun `offline is flagged on the content`() = runTest(mainDispatcherRule.scheduler) {
        networkMonitor.setOnline(false)
        movies.setDetail(1, cachedDetail())
        val viewModel = viewModel()
        subscribed(viewModel)
        advanceUntilIdle()

        assertTrue(viewModel.content().isOffline)
        assertEquals(Staleness.Reason.OFFLINE, viewModel.content().staleness?.reason)
    }

    @Test
    fun `retry forces a detail refresh`() = runTest(mainDispatcherRule.scheduler) {
        val viewModel = viewModel()
        advanceUntilIdle()
        val before = movies.refreshDetailCallCount

        viewModel.retry()
        advanceUntilIdle()

        assertEquals(before + 1, movies.refreshDetailCallCount)
    }

    // endregion

    // region reviews

    @Test
    fun `reviews are loading until the first fetch settles`() = runTest(mainDispatcherRule.scheduler) {
        movies.setDetail(1, cachedDetail())
        val viewModel = viewModel()
        subscribed(viewModel)
        advanceUntilIdle()

        assertEquals(ReviewsUiState.Loading, viewModel.content().reviews)
    }

    @Test
    fun `cached reviews render as loaded`() = runTest(mainDispatcherRule.scheduler) {
        movies.setDetail(1, cachedDetail())
        movies.setReviews(1, Cached(listOf(review("a"), review("b")), lastUpdatedAt = 1L))
        val viewModel = viewModel()
        subscribed(viewModel)
        advanceUntilIdle()

        val reviews = viewModel.content().reviews as ReviewsUiState.Loaded
        assertEquals(listOf("a", "b"), reviews.reviews.map { it.id })
    }

    @Test
    fun `a movie with no reviews shows the empty state, distinct from loading`() =
        runTest(mainDispatcherRule.scheduler) {
            movies.setDetail(1, cachedDetail())
            movies.setReviews(1, Cached(emptyList(), lastUpdatedAt = 1L))
            val viewModel = viewModel()
            subscribed(viewModel)
            advanceUntilIdle()

            assertEquals(ReviewsUiState.Empty, viewModel.content().reviews)
        }

    @Test
    fun `a reviews failure leaves the movie visible and offers retry`() = runTest(mainDispatcherRule.scheduler) {
        movies.setDetail(1, cachedDetail())
        movies.setReviews(1, Cached(null, null, RefreshState.Failed(AppError.Timeout)))
        val viewModel = viewModel()
        subscribed(viewModel)
        advanceUntilIdle()

        val content = viewModel.content()
        assertEquals("Batman Begins", content.detail.title)
        assertEquals(ReviewsUiState.Failed(AppError.Timeout), content.reviews)

        val before = movies.refreshReviewsCallCount
        viewModel.retryReviews()
        advanceUntilIdle()
        assertEquals(before + 1, movies.refreshReviewsCallCount)
    }

    @Test
    fun `cached reviews stay visible when a later refresh fails`() = runTest(mainDispatcherRule.scheduler) {
        movies.setDetail(1, cachedDetail())
        movies.setReviews(1, Cached(listOf(review("a")), 1L, RefreshState.Failed(AppError.Offline)))
        val viewModel = viewModel()
        subscribed(viewModel)
        advanceUntilIdle()

        assertTrue(viewModel.content().reviews is ReviewsUiState.Loaded)
    }

    // endregion

    // region trailer

    @Test
    fun `an available trailer is exposed`() = runTest(mainDispatcherRule.scheduler) {
        val trailer = Trailer("abc123", "Official Trailer", isOfficial = true)
        movies.trailerHandler = { AppResult.Success(trailer) }
        movies.setDetail(1, cachedDetail())
        val viewModel = viewModel()
        subscribed(viewModel)
        advanceUntilIdle()

        assertEquals(TrailerUiState.Available(trailer), viewModel.content().trailer)
    }

    @Test
    fun `a movie without a trailer shows none, distinct from a failure`() = runTest(mainDispatcherRule.scheduler) {
        movies.trailerHandler = { AppResult.Success(null) }
        movies.setDetail(1, cachedDetail())
        val viewModel = viewModel()
        subscribed(viewModel)
        advanceUntilIdle()

        assertEquals(TrailerUiState.None, viewModel.content().trailer)
    }

    @Test
    fun `a trailer failure leaves the movie visible and can be retried to success`() =
        runTest(mainDispatcherRule.scheduler) {
            movies.trailerHandler = { AppResult.Failure(AppError.Offline) }
            movies.setDetail(1, cachedDetail())
            val viewModel = viewModel()
            subscribed(viewModel)
            advanceUntilIdle()

            assertEquals("Batman Begins", viewModel.content().detail.title)
            assertEquals(TrailerUiState.Failed(AppError.Offline), viewModel.content().trailer)

            val trailer = Trailer("k", "Trailer", true)
            movies.trailerHandler = { AppResult.Success(trailer) }
            viewModel.retryTrailer()
            advanceUntilIdle()

            assertEquals(TrailerUiState.Available(trailer), viewModel.content().trailer)
        }

    @Test
    fun `the trailer shows loading while it is being fetched`() = runTest(mainDispatcherRule.scheduler) {
        val gate = CompletableDeferred<Unit>()
        movies.trailerHandler = { gate.await(); AppResult.Success(null) }
        movies.setDetail(1, cachedDetail())
        val viewModel = viewModel()
        subscribed(viewModel)
        advanceUntilIdle()

        assertEquals(TrailerUiState.Loading, viewModel.content().trailer)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(TrailerUiState.None, viewModel.content().trailer)
    }

    @Test
    fun `sections fail independently of each other`() = runTest(mainDispatcherRule.scheduler) {
        movies.trailerHandler = { AppResult.Failure(AppError.Timeout) }
        movies.setDetail(1, cachedDetail())
        movies.setReviews(1, Cached(listOf(review("a")), 1L))
        val viewModel = viewModel()
        subscribed(viewModel)
        advanceUntilIdle()

        val content = viewModel.content()
        assertTrue("reviews unaffected by the trailer failing", content.reviews is ReviewsUiState.Loaded)
        assertTrue(content.trailer is TrailerUiState.Failed)
        assertEquals("Batman Begins", content.detail.title)
    }

    // endregion

    // region history and favorites

    @Test
    fun `opening a movie records it in history exactly once`() = runTest(mainDispatcherRule.scheduler) {
        movies.setDetail(1, cachedDetail())
        val viewModel = viewModel()
        subscribed(viewModel)
        advanceUntilIdle()

        // Later emissions (a favorite toggle, a refresh) must not re-record it.
        favorites.add(movieDetail(1).toMovie())
        advanceUntilIdle()
        movies.setDetail(1, cachedDetail(title = "Batman Begins (updated)"))
        advanceUntilIdle()

        val history = recentlyViewed.observeRecentlyViewed().first()
        assertEquals(listOf(1), history.map { it.id })
        assertEquals("the first-seen title, recorded once", "Batman Begins", history.single().title)
    }

    @Test
    fun `toggling favorite updates the shared source and the content`() = runTest(mainDispatcherRule.scheduler) {
        movies.setDetail(1, cachedDetail())
        val viewModel = viewModel()
        subscribed(viewModel)
        advanceUntilIdle()

        viewModel.onToggleFavorite()
        advanceUntilIdle()
        assertTrue(favorites.isFavorite(1))
        assertTrue(viewModel.content().isFavorite)

        viewModel.onToggleFavorite()
        advanceUntilIdle()
        assertFalse(favorites.isFavorite(1))
    }

    @Test
    fun `the view model is identified by the tmdb id from navigation`() = runTest(mainDispatcherRule.scheduler) {
        movies.setDetail(603, cachedDetail(603, "The Matrix"))
        val viewModel = viewModel(movieId = 603)
        subscribed(viewModel)
        advanceUntilIdle()

        assertEquals(603, viewModel.content().detail.id)
    }

    // endregion
}
