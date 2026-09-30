package com.application.alfaflix.presentation.search

import androidx.lifecycle.SavedStateHandle
import com.application.alfaflix.core.result.AppError
import com.application.alfaflix.core.result.AppResult
import com.application.alfaflix.domain.model.MoviePage
import com.application.alfaflix.domain.usecase.SearchMoviesUseCase
import com.application.alfaflix.domain.usecase.ToggleFavoriteUseCase
import com.application.alfaflix.testing.FakeFavoritesRepository
import com.application.alfaflix.testing.FakeMovieRepository
import com.application.alfaflix.testing.FakeNetworkMonitor
import com.application.alfaflix.testing.MainDispatcherRule
import com.application.alfaflix.testing.movie
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Live search behaviour.
 *
 * Two families of assertion matter most:
 *
 *  - **Debounce** — typing a word must cost one request, not one per keystroke.
 *  - **Stale responses** — every request belongs to a generation that is invalidated the
 *    instant the input changes. Responses for an abandoned query — first page, retry, or
 *    pagination, including the A → B → A case — must never reach the screen.
 */
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeMovieRepository()
    private val favorites = FakeFavoritesRepository()
    private val networkMonitor = FakeNetworkMonitor()

    private fun viewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) = SearchViewModel(
        savedStateHandle = savedStateHandle,
        searchMovies = SearchMoviesUseCase(repository),
        toggleFavorite = ToggleFavoriteUseCase(favorites),
        favoritesRepository = favorites,
        networkMonitor = networkMonitor,
    )

    /** A page of consecutive movie ids starting at [firstId]. */
    private fun pageOf(page: Int, totalPages: Int, firstId: Int, count: Int = 3) =
        MoviePage(movies = (firstId until firstId + count).map { movie(it) }, page = page, totalPages = totalPages)

    // region debounce

    @Test
    fun `typing a word issues exactly one request, not one per keystroke`() =
        runTest(mainDispatcherRule.scheduler) {
            repository.searchHandler = { _, page -> AppResult.Success(pageOf(page, 1, 1)) }
            val viewModel = viewModel()

            listOf("b", "ba", "bat", "batm", "batma", "batman").forEach { keystroke ->
                viewModel.onQueryChange(keystroke)
                advanceTimeBy(50)
            }
            advanceUntilIdle()

            assertEquals(1, repository.searchCallCount)
            assertEquals("batman", repository.lastQuery)
        }

    @Test
    fun `pausing between words issues one request per settled query`() =
        runTest(mainDispatcherRule.scheduler) {
            repository.searchHandler = { _, page -> AppResult.Success(pageOf(page, 1, 1)) }
            val viewModel = viewModel()

            viewModel.onQueryChange("batman")
            advanceUntilIdle()
            viewModel.onQueryChange("inception")
            advanceUntilIdle()

            assertEquals(2, repository.searchCallCount)
        }

    @Test
    fun `a single character never reaches the network`() = runTest(mainDispatcherRule.scheduler) {
        val viewModel = viewModel()

        viewModel.onQueryChange("b")
        advanceUntilIdle()

        assertEquals(0, repository.searchCallCount)
        assertEquals(SearchUiState.Stage.Idle, viewModel.uiState.value.stage)
    }

    @Test
    fun `re-typing the identical query does not repeat the request`() =
        runTest(mainDispatcherRule.scheduler) {
            repository.searchHandler = { _, page -> AppResult.Success(pageOf(page, 1, 1)) }
            val viewModel = viewModel()

            viewModel.onQueryChange("batman")
            advanceUntilIdle()
            viewModel.onQueryChange("batman ")
            advanceUntilIdle()

            assertEquals(1, repository.searchCallCount)
        }

    // endregion

    // region first page states

    @Test
    fun `a query with results shows content and the total pages`() = runTest(mainDispatcherRule.scheduler) {
        repository.searchHandler = { _, page -> AppResult.Success(pageOf(page, totalPages = 4, firstId = 1)) }
        val viewModel = viewModel()

        viewModel.onQueryChange("batman")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SearchUiState.Stage.Content, state.stage)
        assertEquals(listOf(1, 2, 3), state.movies.map { it.id })
        assertEquals(4, state.totalPages)
        assertTrue(state.canLoadMore)
    }

    @Test
    fun `a query matching nothing is an empty state, not an error`() = runTest(mainDispatcherRule.scheduler) {
        repository.searchHandler = { _, page -> AppResult.Success(MoviePage.empty(page)) }
        val viewModel = viewModel()

        viewModel.onQueryChange("zzzzzz")
        advanceUntilIdle()

        assertEquals(SearchUiState.Stage.Empty, viewModel.uiState.value.stage)
    }

    @Test
    fun `a failed search shows an error that retry can recover from`() = runTest(mainDispatcherRule.scheduler) {
        repository.searchHandler = { _, _ -> AppResult.Failure(AppError.Offline) }
        val viewModel = viewModel()
        viewModel.onQueryChange("batman")
        advanceUntilIdle()
        assertEquals(SearchUiState.Stage.Error(AppError.Offline), viewModel.uiState.value.stage)

        repository.searchHandler = { _, page -> AppResult.Success(pageOf(page, 1, 1)) }
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(SearchUiState.Stage.Content, viewModel.uiState.value.stage)
    }

    @Test
    fun `clearing the query returns to idle`() = runTest(mainDispatcherRule.scheduler) {
        repository.searchHandler = { _, page -> AppResult.Success(pageOf(page, 1, 1)) }
        val viewModel = viewModel()
        viewModel.onQueryChange("batman")
        advanceUntilIdle()

        viewModel.onClearQuery()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SearchUiState.Stage.Idle, state.stage)
        assertEquals("", state.query)
        assertTrue(state.movies.isEmpty())
    }

    @Test
    fun `a query saved before process death is restored and searched again`() =
        runTest(mainDispatcherRule.scheduler) {
            repository.searchHandler = { _, page -> AppResult.Success(pageOf(page, 1, 1)) }

            val viewModel = viewModel(SavedStateHandle(mapOf("query" to "batman")))
            advanceUntilIdle()

            assertEquals("batman", viewModel.uiState.value.query)
            assertEquals(SearchUiState.Stage.Content, viewModel.uiState.value.stage)
        }

    @Test
    fun `the typed query is written to saved state so it survives recreation`() =
        runTest(mainDispatcherRule.scheduler) {
            val handle = SavedStateHandle()
            val viewModel = viewModel(handle)

            viewModel.onQueryChange("inception")
            advanceUntilIdle()

            assertEquals("inception", handle.get<String>("query"))
        }

    // endregion

    // region pagination

    @Test
    fun `loading the next page appends without duplicates`() = runTest(mainDispatcherRule.scheduler) {
        repository.searchHandler = { _, page ->
            // Page two repeats movie 3 from page one, as a shifting result set can.
            AppResult.Success(if (page == 1) pageOf(1, 2, 1) else pageOf(2, 2, 3))
        }
        val viewModel = viewModel()
        viewModel.onQueryChange("batman")
        advanceUntilIdle()

        viewModel.loadNextPage()
        advanceUntilIdle()

        assertEquals(listOf(1, 2, 3, 4, 5), viewModel.uiState.value.movies.map { it.id })
        assertEquals(2, viewModel.uiState.value.currentPage)
    }

    @Test
    fun `the last page stops further loading`() = runTest(mainDispatcherRule.scheduler) {
        repository.searchHandler = { _, page -> AppResult.Success(pageOf(page, totalPages = 2, firstId = page * 10)) }
        val viewModel = viewModel()
        viewModel.onQueryChange("batman")
        advanceUntilIdle()
        viewModel.loadNextPage()
        advanceUntilIdle()
        val callsAfterLastPage = repository.searchCallCount

        assertFalse(viewModel.uiState.value.canLoadMore)
        viewModel.loadNextPage()
        advanceUntilIdle()

        assertEquals(callsAfterLastPage, repository.searchCallCount)
    }

    @Test
    fun `repeated load-next-page calls in one frame request the page once`() =
        runTest(mainDispatcherRule.scheduler) {
            val gate = CompletableDeferred<Unit>()
            repository.searchHandler = { _, page ->
                if (page > 1) gate.await()
                AppResult.Success(pageOf(page, 3, page * 10))
            }
            val viewModel = viewModel()
            viewModel.onQueryChange("batman")
            advanceUntilIdle()
            val requestsBefore = repository.searchCallCount

            repeat(5) { viewModel.loadNextPage() }
            advanceUntilIdle()
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(1, repository.searchCallCount - requestsBefore)
        }

    @Test
    fun `a failed next page keeps existing results and can be retried`() = runTest(mainDispatcherRule.scheduler) {
        repository.searchHandler = { _, page ->
            if (page == 1) AppResult.Success(pageOf(1, 3, 1)) else AppResult.Failure(AppError.Offline)
        }
        val viewModel = viewModel()
        viewModel.onQueryChange("batman")
        advanceUntilIdle()

        viewModel.loadNextPage()
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertEquals("existing results stay on screen", listOf(1, 2, 3), state.movies.map { it.id })
        assertEquals(SearchUiState.Stage.Content, state.stage)
        assertEquals(AppError.Offline, state.nextPageError)
        assertFalse(state.isLoadingNextPage)

        repository.searchHandler = { _, page -> AppResult.Success(pageOf(page, 3, page * 10)) }
        viewModel.loadNextPage()
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals(null, state.nextPageError)
        assertEquals(6, state.movies.size)
    }

    // endregion

    // region stale responses

    @Test
    fun `a next page still in flight is discarded the moment the query changes`() =
        runTest(mainDispatcherRule.scheduler) {
            val slowPageTwo = CompletableDeferred<Unit>()
            repository.searchHandler = { query, page ->
                if (query == "batman" && page == 2) {
                    // Uncancellable: models a response that has already returned and is
                    // resuming, which cancellation alone cannot stop.
                    withContext(NonCancellable) { slowPageTwo.await() }
                    AppResult.Success(pageOf(2, 3, 100))
                } else {
                    AppResult.Success(pageOf(1, 3, if (query == "batman") 1 else 500))
                }
            }
            val viewModel = viewModel()
            viewModel.onQueryChange("batman")
            advanceUntilIdle()
            viewModel.loadNextPage()
            runCurrent()

            // The query changes; the old page is still outstanding and *within* the debounce.
            viewModel.onQueryChange("inception")
            assertFalse(
                "the loading footer for the abandoned query goes away immediately",
                viewModel.uiState.value.isLoadingNextPage,
            )
            slowPageTwo.complete(Unit)
            advanceUntilIdle()

            val ids = viewModel.uiState.value.movies.map { it.id }
            assertEquals("only inception's results are shown", listOf(500, 501, 502), ids)
        }

    @Test
    fun `pagination is refused while the field no longer matches the visible results`() =
        runTest(mainDispatcherRule.scheduler) {
            repository.searchHandler = { _, page -> AppResult.Success(pageOf(page, 3, page * 10)) }
            val viewModel = viewModel()
            viewModel.onQueryChange("batman")
            advanceUntilIdle()
            val callsBefore = repository.searchCallCount

            // Typing has begun but the debounce has not elapsed: results still on screen
            // belong to "batman", which is already condemned.
            viewModel.onQueryChange("batmans")
            viewModel.loadNextPage()
            runCurrent()

            assertEquals(callsBefore, repository.searchCallCount)
        }

    @Test
    fun `a slow first page for an abandoned query never overwrites the newer one`() =
        runTest(mainDispatcherRule.scheduler) {
            val slowBatman = CompletableDeferred<Unit>()
            repository.searchHandler = { query, page ->
                if (query == "batman") withContext(NonCancellable) { slowBatman.await() }
                AppResult.Success(pageOf(page, 1, if (query == "batman") 1 else 500))
            }
            val viewModel = viewModel()

            viewModel.onQueryChange("batman")
            advanceTimeBy(450) // debounce elapsed, request now in flight
            viewModel.onQueryChange("inception")
            advanceUntilIdle()
            slowBatman.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf(500, 501, 502), viewModel.uiState.value.movies.map { it.id })
        }

    @Test
    fun `switching from A to B and back to A ignores the first A's late response`() =
        runTest(mainDispatcherRule.scheduler) {
            val firstA = CompletableDeferred<Unit>()
            var aCalls = 0
            repository.searchHandler = { query, page ->
                if (query == "aaa" && ++aCalls == 1) {
                    withContext(NonCancellable) { firstA.await() }
                    // The stale reply carries a marker id so a leak is unmistakable.
                    AppResult.Success(pageOf(page, 1, 900))
                } else {
                    AppResult.Success(pageOf(page, 1, if (query == "aaa") 1 else 500))
                }
            }
            val viewModel = viewModel()

            viewModel.onQueryChange("aaa")
            advanceTimeBy(450) // first A in flight
            viewModel.onQueryChange("bbb")
            viewModel.onQueryChange("aaa") // back to A: the text now looks valid again
            advanceUntilIdle()
            firstA.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                "the second A's results win; the first A's late reply is stale",
                listOf(1, 2, 3),
                viewModel.uiState.value.movies.map { it.id },
            )
        }

    @Test
    fun `a retry that is superseded by typing cannot apply its result`() = runTest(mainDispatcherRule.scheduler) {
        repository.searchHandler = { _, _ -> AppResult.Failure(AppError.Offline) }
        val viewModel = viewModel()
        viewModel.onQueryChange("batman")
        advanceUntilIdle()

        val slowRetry = CompletableDeferred<Unit>()
        repository.searchHandler = { query, page ->
            if (query == "batman") withContext(NonCancellable) { slowRetry.await() }
            AppResult.Success(pageOf(page, 1, if (query == "batman") 1 else 500))
        }
        viewModel.retry()
        runCurrent()
        viewModel.onQueryChange("inception")
        advanceUntilIdle()
        slowRetry.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(500, 501, 502), viewModel.uiState.value.movies.map { it.id })
    }

    @Test
    fun `clearing the query while a request is in flight leaves the screen idle`() =
        runTest(mainDispatcherRule.scheduler) {
            val slow = CompletableDeferred<Unit>()
            repository.searchHandler = { _, page ->
                withContext(NonCancellable) { slow.await() }
                AppResult.Success(pageOf(page, 1, 1))
            }
            val viewModel = viewModel()
            viewModel.onQueryChange("batman")
            advanceTimeBy(450)

            viewModel.onClearQuery()
            slow.complete(Unit)
            advanceUntilIdle()

            assertEquals(SearchUiState.Stage.Idle, viewModel.uiState.value.stage)
            assertTrue(viewModel.uiState.value.movies.isEmpty())
        }

    // endregion

    // region favorites and connectivity

    @Test
    fun `favorite state on results follows the shared favorites source`() = runTest(mainDispatcherRule.scheduler) {
        repository.searchHandler = { _, page -> AppResult.Success(pageOf(page, 1, 1)) }
        val viewModel = viewModel()
        viewModel.onQueryChange("batman")
        advanceUntilIdle()

        favorites.add(movie(2))
        advanceUntilIdle()

        assertEquals(
            listOf(false, true, false),
            viewModel.uiState.value.movies.map { it.isFavorite },
        )
    }

    @Test
    fun `toggling a favorite from results updates the source`() = runTest(mainDispatcherRule.scheduler) {
        repository.searchHandler = { _, page -> AppResult.Success(pageOf(page, 1, 1)) }
        val viewModel = viewModel()
        viewModel.onQueryChange("batman")
        advanceUntilIdle()

        viewModel.onToggleFavorite(viewModel.uiState.value.movies.first())
        advanceUntilIdle()

        assertTrue(favorites.isFavorite(1))
        assertTrue(viewModel.uiState.value.movies.first().isFavorite)
    }

    @Test
    fun `connectivity is reflected in the state`() = runTest(mainDispatcherRule.scheduler) {
        val viewModel = viewModel()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isOffline)

        networkMonitor.setOnline(false)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isOffline)
    }

    // endregion
}
