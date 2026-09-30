package com.application.alfaflix.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import com.application.alfaflix.core.result.AppResult
import com.application.alfaflix.core.result.Cached
import com.application.alfaflix.domain.model.Trailer
import com.application.alfaflix.domain.usecase.ObserveHomeFeedUseCase
import com.application.alfaflix.domain.usecase.ObserveMovieDetailUseCase
import com.application.alfaflix.domain.usecase.ToggleFavoriteUseCase
import com.application.alfaflix.presentation.detail.LocalTrailerPlayer
import com.application.alfaflix.presentation.detail.MovieDetailScreenContent
import com.application.alfaflix.presentation.detail.MovieDetailViewModel
import com.application.alfaflix.presentation.favorites.FavoritesScreenContent
import com.application.alfaflix.presentation.favorites.FavoritesViewModel
import com.application.alfaflix.presentation.home.HomeScreenContent
import com.application.alfaflix.presentation.home.HomeViewModel
import com.application.alfaflix.presentation.navigation.MovieDetailRoute
import com.application.alfaflix.testing.FakeFavoritesRepository
import com.application.alfaflix.testing.FakeMovieRepository
import com.application.alfaflix.testing.FakeNetworkMonitor
import com.application.alfaflix.testing.FakeRecentlyViewedRepository
import com.application.alfaflix.testing.movie
import com.application.alfaflix.testing.movieDetail
import com.application.alfaflix.testing.review
import com.application.alfaflix.ui.theme.AlfaflixTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The critical user journey, end to end through real ViewModels and real Compose UI:
 *
 * Home grid → open a movie → read its details and reviews → trailer (plays, then fails and
 * falls back) → save it → find it under Favorites.
 *
 * Repositories are fakes, so no network or database is involved, but every layer above them
 * is the production implementation. The YouTube WebView cannot run on the JVM, so a stand-in
 * player is provided through [LocalTrailerPlayer]; what is tested is the screen's behaviour
 * around it.
 *
 * Runs on the JVM under Robolectric so `./gradlew testDebugUnitTest` genuinely executes it.
 */
@RunWith(RobolectricTestRunner::class)
// A phone-sized window: the default Robolectric screen is small enough to fit a single
// poster column, which would push most of the content off-screen.
@Config(qualifiers = "w411dp-h891dp")
class CriticalJourneyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val movieRepository = FakeMovieRepository()
    private val favoritesRepository = FakeFavoritesRepository()
    private val recentlyViewedRepository = FakeRecentlyViewedRepository()
    private val networkMonitor = FakeNetworkMonitor()

    // No poster paths: the test must not depend on image loading.
    private val batman = movie(272, "Batman Begins").copy(posterPath = null)
    private val darkKnight = movie(155, "The Dark Knight").copy(posterPath = null)

    private val longReview = review(
        id = "long",
        author = "Wordy Critic",
        content = (1..40).joinToString("\n") { "Paragraph $it of a very thorough review." },
    )

    @Test
    fun `home grid to detail with reviews and trailer to favorites`() {
        movieRepository.setDiscovery(FakeMovieRepository.loaded(listOf(batman, darkKnight)))
        movieRepository.setDetail(
            batman.id,
            Cached(data = movieDetail(batman.id, batman.title).copy(posterPath = null, backdropPath = null), lastUpdatedAt = 1L),
        )
        movieRepository.setReviews(
            batman.id,
            Cached(listOf(review("a", author = "Alice", content = "Loved it."), longReview), lastUpdatedAt = 1L),
        )
        movieRepository.trailerHandler = { AppResult.Success(Trailer("abc123", "Official Trailer", true)) }

        setJourneyContent()

        // 1. The discovery grid is on the default screen, no search required.
        composeTestRule.onAllNodesWithText("Batman Begins").onFirst().assertIsDisplayed()
        composeTestRule.onAllNodesWithText("The Dark Knight").onFirst().assertIsDisplayed()

        // 2. Open a movie.
        composeTestRule.onAllNodesWithText("Batman Begins").onFirst().performClick()
        waitForText("An overview.")

        // 3. Primary information is shown, with the score labelled by its real source.
        composeTestRule.onNodeWithText("2005 · 140 min").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Action").assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription("TMDB user score 8.2 out of 10 from 1,234 votes")
            .assertIsDisplayed()

        // 4. Reviews: reviewer identity and content, with a working expand/collapse.
        composeTestRule.onNodeWithText("Alice").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Loved it.").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Wordy Critic").performScrollTo()
        composeTestRule.onNodeWithText("Read more").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Show less").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Show less").performClick()
        composeTestRule.onNodeWithText("Read more").performScrollTo().assertIsDisplayed()

        // 5. Trailer: play, then YouTube refuses the embed and the screen falls back.
        composeTestRule
            .onNodeWithContentDescription("Play the trailer for Batman Begins")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag(FAKE_PLAYER_TAG).performScrollTo().assertIsDisplayed()
        // The player is already scrolled into view, clear of the floating back button.
        composeTestRule.onNodeWithText("Fail playback").performClick()
        composeTestRule.onNodeWithText("This video can't be played inside the app.").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Watch on YouTube").performScrollTo().assertIsDisplayed()

        // 6. Favorite it. The label must name the movie and the action.
        composeTestRule
            .onNodeWithContentDescription("Add Batman Begins to favorites")
            .performScrollTo()
            .performClick()
        composeTestRule.waitUntil(TIMEOUT_MILLIS) {
            composeTestRule
                .onAllNodes(hasContentDescription("Remove Batman Begins from favorites"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // 7. The shared source of truth was updated, not just the screen.
        assertTrue(favoritesRepository.currentFavoriteIds().contains(batman.id))

        // 8. Favorites lists it.
        composeTestRule.onNodeWithContentDescription("Go back").performClick()
        composeTestRule.onAllNodesWithText("Batman Begins").onFirst().assertIsDisplayed()
    }

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(TIMEOUT_MILLIS) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * A minimal three-screen host.
     *
     * Real Navigation Compose is exercised by the app itself; here the point is the
     * ViewModel-and-UI behaviour, so screen switching is kept trivial.
     */
    private fun setJourneyContent() {
        val homeViewModel = HomeViewModel(
            observeHomeFeed = ObserveHomeFeedUseCase(movieRepository, favoritesRepository, recentlyViewedRepository),
            movieRepository = movieRepository,
            toggleFavorite = ToggleFavoriteUseCase(favoritesRepository),
            networkMonitor = networkMonitor,
        )
        val detailViewModel = MovieDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf(MovieDetailRoute.MOVIE_ID_ARG to batman.id)),
            observeMovieDetail = ObserveMovieDetailUseCase(movieRepository, favoritesRepository),
            movieRepository = movieRepository,
            recentlyViewedRepository = recentlyViewedRepository,
            toggleFavorite = ToggleFavoriteUseCase(favoritesRepository),
            networkMonitor = networkMonitor,
        )
        val favoritesViewModel = FavoritesViewModel(
            favoritesRepository = favoritesRepository,
            toggleFavorite = ToggleFavoriteUseCase(favoritesRepository),
        )

        composeTestRule.setContent {
            AlfaflixTheme {
                CompositionLocalProvider(LocalTrailerPlayer provides FakePlayer) {
                    var screen by remember { mutableStateOf(Screen.HOME) }

                    when (screen) {
                        Screen.HOME -> HomePane(homeViewModel) { screen = Screen.DETAIL }
                        Screen.DETAIL -> DetailPane(detailViewModel) { screen = Screen.FAVORITES }
                        Screen.FAVORITES -> FavoritesPane(favoritesViewModel)
                    }
                }
            }
        }
    }

    @Composable
    private fun HomePane(viewModel: HomeViewModel, onOpenMovie: () -> Unit) {
        val state by viewModel.uiState.collectAsState()
        HomeScreenContent(
            uiState = state,
            onRefresh = viewModel::refresh,
            onLoadNextPage = viewModel::loadNextPage,
            onSurpriseMe = viewModel::onSurpriseMe,
            onToggleFavorite = viewModel::onToggleFavorite,
            onMovieClick = { onOpenMovie() },
            onAboutClick = {},
        )
    }

    @Composable
    private fun DetailPane(viewModel: MovieDetailViewModel, onBack: () -> Unit) {
        val state by viewModel.uiState.collectAsState()
        // The back button leads to Favorites in this host, via a visible control.
        Box {
            MovieDetailScreenContent(
                uiState = state,
                onNavigateBack = onBack,
                onToggleFavorite = viewModel::onToggleFavorite,
                onRetry = viewModel::retry,
                onRetryReviews = viewModel::retryReviews,
                onRetryTrailer = viewModel::retryTrailer,
            )
        }
    }

    @Composable
    private fun FavoritesPane(viewModel: FavoritesViewModel) {
        val state by viewModel.uiState.collectAsState()
        FavoritesScreenContent(
            uiState = state,
            onMovieClick = {},
            onToggleFavorite = viewModel::onToggleFavorite,
        )
    }

    private enum class Screen { HOME, DETAIL, FAVORITES }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
        const val FAKE_PLAYER_TAG = "fake-player"

        /** Stands in for the YouTube WebView; exposes a button to simulate YouTube refusing playback. */
        val FakePlayer: @Composable (String, () -> Unit, Modifier) -> Unit = { _, onError, modifier ->
            Box(
                modifier.fillMaxWidth().height(180.dpForTest()).testTag(FAKE_PLAYER_TAG),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                Button(onClick = onError) { Text("Fail playback") }
            }
        }
    }
}

private fun Int.dpForTest() = androidx.compose.ui.unit.Dp(this.toFloat())

/** Reads the current favorite IDs synchronously, for a plain assertion. */
private fun FakeFavoritesRepository.currentFavoriteIds(): Set<Int> =
    runBlocking { observeFavoriteIds().first() }
