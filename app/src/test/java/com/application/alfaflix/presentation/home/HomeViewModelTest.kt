package com.application.alfaflix.presentation.home

import app.cash.turbine.test
import com.application.alfaflix.core.result.AppError
import com.application.alfaflix.core.result.AppResult
import com.application.alfaflix.core.result.RefreshState
import com.application.alfaflix.core.result.Staleness
import com.application.alfaflix.domain.usecase.ObserveHomeFeedUseCase
import com.application.alfaflix.domain.usecase.ToggleFavoriteUseCase
import com.application.alfaflix.testing.FakeFavoritesRepository
import com.application.alfaflix.testing.FakeMovieRepository
import com.application.alfaflix.testing.FakeNetworkMonitor
import com.application.alfaflix.testing.FakeRecentlyViewedRepository
import com.application.alfaflix.testing.MainDispatcherRule
import com.application.alfaflix.testing.movie
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The home screen's state machine. The rule that matters most: **content wins** — a failed
 * refresh must never replace movies the user can already see with an error screen.
 */
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeMovieRepository()
    private val favorites = FakeFavoritesRepository()
    private val recentlyViewed = FakeRecentlyViewedRepository()
    private val networkMonitor = FakeNetworkMonitor()

    private fun viewModel() = HomeViewModel(
        observeHomeFeed = ObserveHomeFeedUseCase(repository, favorites, recentlyViewed),
        movieRepository = repository,
        toggleFavorite = ToggleFavoriteUseCase(favorites),
        networkMonitor = networkMonitor,
    )

    /** Keeps the WhileSubscribed state flow active, as a visible screen would. */
    private fun kotlinx.coroutines.test.TestScope.subscribe(viewModel: HomeViewModel) {
        viewModel.uiState.onEach { }.launchIn(backgroundScope)
    }

    // region states

    @Test
    fun `a cold start with nothing cached shows loading`() = runTest(mainDispatcherRule.scheduler) {
        val viewModel = viewModel()
        subscribe(viewModel)
        advanceUntilIdle()

        assertEquals(HomeUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `a failed first load with nothing cached is a full-screen error`() = runTest(mainDispatcherRule.scheduler) {
        repository.setDiscovery(
            FakeMovieRepository.neverLoaded().copy(refresh = RefreshState.Failed(AppError.Offline)),
        )
        val viewModel = viewModel()
        subscribe(viewModel)
        advanceUntilIdle()

        assertEquals(HomeUiState.Error(AppError.Offline), viewModel.uiState.value)
    }

    @Test
    fun `cached movies show as content immediately`() = runTest(mainDispatcherRule.scheduler) {
        repository.setDiscovery(FakeMovieRepository.loaded(listOf(movie(1), movie(2))))
        val viewModel = viewModel()
        subscribe(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value as HomeUiState.Content
        assertEquals(listOf(1, 2), state.movies.map { it.id })
        assertNull("fresh content carries no banner", state.staleness)
    }

    @Test
    fun `content survives a failed refresh and carries a staleness explanation`() =
        runTest(mainDispatcherRule.scheduler) {
            repository.setDiscovery(
                FakeMovieRepository.loaded(
                    movies = listOf(movie(1)),
                    refresh = RefreshState.Failed(AppError.Server(500)),
                ),
            )
            val viewModel = viewModel()
            subscribe(viewModel)
            advanceUntilIdle()

            val state = viewModel.uiState.value as HomeUiState.Content
            assertEquals("the movies stay on screen", listOf(1), state.movies.map { it.id })
            assertEquals(Staleness.Reason.REFRESH_FAILED, state.staleness?.reason)
        }

    @Test
    fun `offline with cached content is content with an offline banner`() = runTest(mainDispatcherRule.scheduler) {
        networkMonitor.setOnline(false)
        repository.setDiscovery(FakeMovieRepository.loaded(listOf(movie(1))))
        val viewModel = viewModel()
        subscribe(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value as HomeUiState.Content
        assertTrue(state.isOffline)
        assertEquals(Staleness.Reason.OFFLINE, state.staleness?.reason)
    }

    @Test
    fun `a loaded but empty list is content, not loading`() = runTest(mainDispatcherRule.scheduler) {
        repository.setDiscovery(FakeMovieRepository.loaded(emptyList(), hasMore = false))
        val viewModel = viewModel()
        subscribe(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value as HomeUiState.Content
        assertTrue(state.movies.isEmpty())
        assertFalse(state.canSurpriseMe)
    }

    @Test
    fun `refreshing while content shows keeps the content and flags the spinner`() =
        runTest(mainDispatcherRule.scheduler) {
            repository.setDiscovery(
                FakeMovieRepository.loaded(listOf(movie(1)), refresh = RefreshState.InProgress),
            )
            val viewModel = viewModel()
            subscribe(viewModel)
            advanceUntilIdle()

            val state = viewModel.uiState.value as HomeUiState.Content
            assertTrue(state.isRefreshing)
            assertEquals(listOf(1), state.movies.map { it.id })
        }

    // endregion

    // region pagination state

    @Test
    fun `next page loading and failure are reported separately from refresh`() =
        runTest(mainDispatcherRule.scheduler) {
            repository.setDiscovery(
                FakeMovieRepository.loaded(listOf(movie(1)), append = RefreshState.Failed(AppError.Timeout)),
            )
            val viewModel = viewModel()
            subscribe(viewModel)
            advanceUntilIdle()

            val state = viewModel.uiState.value as HomeUiState.Content
            assertEquals(RefreshState.Failed(AppError.Timeout), state.nextPage)
            assertFalse("a page failure is not a refresh", state.isRefreshing)
            assertNull("a page failure does not make the whole feed stale", state.staleness)
        }

    @Test
    fun `the end of the list is reported`() = runTest(mainDispatcherRule.scheduler) {
        repository.setDiscovery(FakeMovieRepository.loaded(listOf(movie(1)), hasMore = false))
        val viewModel = viewModel()
        subscribe(viewModel)
        advanceUntilIdle()

        assertFalse((viewModel.uiState.value as HomeUiState.Content).hasMore)
    }

    @Test
    fun `loading the next page asks the repository`() = runTest(mainDispatcherRule.scheduler) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.loadNextPage()
        advanceUntilIdle()

        assertEquals(1, repository.loadMoreCallCount)
    }

    // endregion

    // region refresh

    @Test
    fun `a warm start respects the freshness window`() = runTest(mainDispatcherRule.scheduler) {
        viewModel()
        advanceUntilIdle()

        assertEquals(1, repository.refreshDiscoveryCallCount)
        assertEquals("launch must not force a refresh", false, repository.lastForcedRefresh)
    }

    @Test
    fun `pull to refresh forces past the freshness window`() = runTest(mainDispatcherRule.scheduler) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(true, repository.lastForcedRefresh)
    }

    @Test
    fun `regaining connectivity after a failed load retries automatically`() =
        runTest(mainDispatcherRule.scheduler) {
            networkMonitor.setOnline(false)
            repository.setDiscovery(
                FakeMovieRepository.neverLoaded().copy(refresh = RefreshState.Failed(AppError.Offline)),
            )
            val viewModel = viewModel()
            subscribe(viewModel)
            advanceUntilIdle()
            val callsBefore = repository.refreshDiscoveryCallCount

            networkMonitor.setOnline(true)
            advanceUntilIdle()

            assertEquals(callsBefore + 1, repository.refreshDiscoveryCallCount)
            assertEquals(true, repository.lastForcedRefresh)
        }

    @Test
    fun `regaining connectivity with healthy fresh content does not refetch`() =
        runTest(mainDispatcherRule.scheduler) {
            repository.setDiscovery(FakeMovieRepository.loaded(listOf(movie(1))))
            val viewModel = viewModel()
            subscribe(viewModel)
            advanceUntilIdle()
            val callsBefore = repository.refreshDiscoveryCallCount

            networkMonitor.setOnline(true)
            advanceUntilIdle()

            assertEquals(callsBefore, repository.refreshDiscoveryCallCount)
        }

    // endregion

    // region favorites, history, surprise

    @Test
    fun `favorite state on the grid follows the shared source`() = runTest(mainDispatcherRule.scheduler) {
        repository.setDiscovery(FakeMovieRepository.loaded(listOf(movie(1), movie(2))))
        val viewModel = viewModel()
        subscribe(viewModel)
        advanceUntilIdle()

        viewModel.onToggleFavorite((viewModel.uiState.value as HomeUiState.Content).movies[1])
        advanceUntilIdle()

        assertEquals(
            listOf(false, true),
            (viewModel.uiState.value as HomeUiState.Content).movies.map { it.isFavorite },
        )
    }

    @Test
    fun `recently viewed movies appear alongside the grid`() = runTest(mainDispatcherRule.scheduler) {
        repository.setDiscovery(FakeMovieRepository.loaded(listOf(movie(1))))
        recentlyViewed.record(movie(7))
        val viewModel = viewModel()
        subscribe(viewModel)
        advanceUntilIdle()

        assertEquals(listOf(7), (viewModel.uiState.value as HomeUiState.Content).recentlyViewed.map { it.id })
    }

    @Test
    fun `surprise me emits a cached movie once`() = runTest(mainDispatcherRule.scheduler) {
        repository.randomMovie = movie(42)
        repository.setDiscovery(FakeMovieRepository.loaded(listOf(movie(42))))
        val viewModel = viewModel()
        subscribe(viewModel)
        advanceUntilIdle()

        viewModel.surpriseMovie.test {
            viewModel.onSurpriseMe()
            assertEquals(42, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `surprise me does nothing when nothing is cached`() = runTest(mainDispatcherRule.scheduler) {
        repository.randomMovie = null
        val viewModel = viewModel()
        subscribe(viewModel)
        advanceUntilIdle()

        viewModel.surpriseMovie.test {
            viewModel.onSurpriseMe()
            advanceUntilIdle()
            expectNoEvents()
        }
        assertNotNull(viewModel)
    }

    // endregion
}
