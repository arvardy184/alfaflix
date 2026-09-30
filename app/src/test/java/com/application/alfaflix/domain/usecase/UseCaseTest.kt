package com.application.alfaflix.domain.usecase

import com.application.alfaflix.core.result.AppError
import com.application.alfaflix.core.result.AppResult
import com.application.alfaflix.core.result.Cached
import com.application.alfaflix.core.result.RefreshState
import com.application.alfaflix.domain.model.MoviePage
import com.application.alfaflix.domain.repository.RecentlyViewedRepository
import com.application.alfaflix.testing.FakeFavoritesRepository
import com.application.alfaflix.testing.FakeMovieRepository
import com.application.alfaflix.testing.FakeRecentlyViewedRepository
import com.application.alfaflix.testing.movie
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToggleFavoriteUseCaseTest {

    private val favorites = FakeFavoritesRepository()
    private val toggleFavorite = ToggleFavoriteUseCase(favorites)

    @Test
    fun `toggling adds when absent and removes when present`() = runTest {
        val batman = movie(1, "Batman Begins")

        assertTrue("first toggle should save", toggleFavorite(batman))
        assertTrue(favorites.isFavorite(1))

        assertFalse("second toggle should unsave", toggleFavorite(batman))
        assertFalse(favorites.isFavorite(1))
    }

    @Test
    fun `toggling one movie does not affect another`() = runTest {
        toggleFavorite(movie(1))
        toggleFavorite(movie(2))
        toggleFavorite(movie(1))

        assertFalse(favorites.isFavorite(1))
        assertTrue(favorites.isFavorite(2))
    }

    @Test
    fun `favorites are keyed by the tmdb id alone`() = runTest {
        // The same TMDB id with different display data is the same movie, not a second one.
        toggleFavorite(movie(1, "Old title"))
        val saved = toggleFavorite(movie(1, "Renamed"))

        assertFalse("second toggle of the same id removes it", saved)
        assertTrue(favorites.observeFavorites().first().isEmpty())
    }
}

class SearchMoviesUseCaseTest {

    private val repository = FakeMovieRepository()
    private val searchMovies = SearchMoviesUseCase(repository)

    @Test
    fun `a one character query never reaches the network`() = runTest {
        val result = searchMovies(query = "b", page = 1)

        assertEquals(0, repository.searchCallCount)
        assertEquals(emptyList<Any>(), (result as AppResult.Success).data.movies)
    }

    @Test
    fun `a whitespace-only query never reaches the network`() = runTest {
        searchMovies(query = "   ", page = 1)

        assertEquals(0, repository.searchCallCount)
    }

    @Test
    fun `the query is trimmed before it is sent`() = runTest {
        searchMovies(query = "  batman  ", page = 1)

        assertEquals("batman", repository.lastQuery)
    }

    @Test
    fun `a repository failure is passed through unchanged`() = runTest {
        repository.searchHandler = { _, _ -> AppResult.Failure(AppError.Offline) }

        assertEquals(
            AppResult.Failure(AppError.Offline),
            searchMovies(query = "batman", page = 1),
        )
    }

    @Test
    fun `the requested page is forwarded`() = runTest {
        repository.searchHandler = { _, page -> AppResult.Success(MoviePage.empty(page)) }

        searchMovies(query = "batman", page = 3)

        assertEquals(3, repository.lastPage)
    }
}

class ObserveMovieDetailUseCaseTest {

    private val movies = FakeMovieRepository()
    private val favorites = FakeFavoritesRepository()
    private val observeDetail = ObserveMovieDetailUseCase(movies, favorites)

    @Test
    fun `favorite state is joined onto the detail stream`() = runTest {
        favorites.add(movie(1))

        assertTrue(observeDetail(1).first().isFavorite)
        assertFalse(observeDetail(2).first().isFavorite)
    }

    @Test
    fun `cached content and refresh state are passed through`() = runTest {
        movies.setDetail(
            1,
            Cached(data = null, lastUpdatedAt = 500L, refresh = RefreshState.Failed(AppError.Offline)),
        )

        val state = observeDetail(1).first()

        assertEquals(RefreshState.Failed(AppError.Offline), state.cached.refresh)
        assertEquals(500L, state.cached.lastUpdatedAt)
    }
}

class ObserveHomeFeedUseCaseTest {

    private val movies = FakeMovieRepository()
    private val favorites = FakeFavoritesRepository()
    private val recentlyViewed = FakeRecentlyViewedRepository()
    private val observeHomeFeed = ObserveHomeFeedUseCase(movies, favorites, recentlyViewed)

    @Test
    fun `never-loaded discovery stays distinguishable from an empty list`() = runTest {
        assertNull(observeHomeFeed().first().movies)

        movies.setDiscovery(FakeMovieRepository.loaded(emptyList(), hasMore = false))
        assertEquals(emptyList<Any>(), observeHomeFeed().first().movies)
    }

    @Test
    fun `favorite state is applied to the grid and to history from one source`() = runTest {
        favorites.add(movie(1))
        movies.setDiscovery(FakeMovieRepository.loaded(listOf(movie(1), movie(2))))
        recentlyViewed.record(movie(1))

        val feed = observeHomeFeed().first()

        assertTrue(feed.movies!!.single { it.id == 1 }.isFavorite)
        assertFalse(feed.movies!!.single { it.id == 2 }.isFavorite)
        assertTrue("history agrees with the grid", feed.recentlyViewed.single().isFavorite)
    }

    @Test
    fun `refresh, append and paging state are passed through`() = runTest {
        movies.setDiscovery(
            FakeMovieRepository.loaded(
                movies = listOf(movie(1)),
                hasMore = false,
                lastUpdatedAt = 900L,
                refresh = RefreshState.InProgress,
                append = RefreshState.Failed(AppError.Timeout),
            ),
        )

        val feed = observeHomeFeed().first()

        assertEquals(RefreshState.InProgress, feed.refresh)
        assertEquals(RefreshState.Failed(AppError.Timeout), feed.append)
        assertFalse(feed.hasMore)
        assertEquals(900L, feed.lastUpdatedAt)
    }

    @Test
    fun `history is included even before discovery has loaded`() = runTest {
        recentlyViewed.record(movie(5))

        val feed = observeHomeFeed().first()

        assertNull(feed.movies)
        assertEquals(listOf(5), feed.recentlyViewed.map { it.id })
    }
}

class RecentlyViewedContractTest {

    private val recentlyViewed = FakeRecentlyViewedRepository()

    @Test
    fun `re-viewing moves a movie to the top without duplicating it`() = runTest {
        recentlyViewed.record(movie(1, "Batman Begins"))
        recentlyViewed.record(movie(2, "Interstellar"))
        recentlyViewed.record(movie(1, "Batman Begins"))

        val entries = recentlyViewed.observeRecentlyViewed().first()

        assertEquals(2, entries.size)
        assertEquals(1, entries.first().id)
    }

    @Test
    fun `history is capped`() = runTest {
        repeat(RecentlyViewedRepository.MAX_ENTRIES + 5) { recentlyViewed.record(movie(it)) }

        assertEquals(
            RecentlyViewedRepository.MAX_ENTRIES,
            recentlyViewed.observeRecentlyViewed().first().size,
        )
    }
}
