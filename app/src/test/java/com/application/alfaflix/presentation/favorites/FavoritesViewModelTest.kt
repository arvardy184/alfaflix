package com.application.alfaflix.presentation.favorites

import com.application.alfaflix.domain.usecase.ToggleFavoriteUseCase
import com.application.alfaflix.testing.FakeFavoritesRepository
import com.application.alfaflix.testing.MainDispatcherRule
import com.application.alfaflix.testing.movie
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FavoritesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val favorites = FakeFavoritesRepository()

    private fun viewModel() = FavoritesViewModel(
        favoritesRepository = favorites,
        toggleFavorite = ToggleFavoriteUseCase(favorites),
    )

    private fun TestScope.subscribed(viewModel: FavoritesViewModel) = also {
        backgroundScope.launch { viewModel.uiState.collect() }
    }

    @Test
    fun `no saved movies yields an empty state`() = runTest(mainDispatcherRule.scheduler) {
        val viewModel = viewModel()
        subscribed(viewModel)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isEmpty)
    }

    @Test
    fun `saved movies appear and are marked as favorites`() =
        runTest(mainDispatcherRule.scheduler) {
            favorites.add(movie(1, "Batman Begins"))

            val viewModel = viewModel()
            subscribed(viewModel)
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.movies.size)
            assertTrue(viewModel.uiState.value.movies.first().isFavorite)
        }

    @Test
    fun `unfavoriting removes the movie from the list`() = runTest(mainDispatcherRule.scheduler) {
        favorites.add(movie(1, "Batman Begins"))
        favorites.add(movie(2, "Interstellar"))
        val viewModel = viewModel()
        subscribed(viewModel)
        advanceUntilIdle()

        viewModel.onToggleFavorite(viewModel.uiState.value.movies.first { it.id == 1 })
        advanceUntilIdle()

        assertEquals(listOf(2), viewModel.uiState.value.movies.map { it.id })
        assertFalse(favorites.isFavorite(1))
    }

    @Test
    fun `a movie saved on another screen appears here without any refresh`() =
        runTest(mainDispatcherRule.scheduler) {
            val viewModel = viewModel()
            subscribed(viewModel)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isEmpty)

            favorites.add(movie(9, "Added Elsewhere"))
            advanceUntilIdle()

            assertEquals(listOf(9), viewModel.uiState.value.movies.map { it.id })
        }
}
