package com.application.alfaflix.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.application.alfaflix.core.result.AppError
import com.application.alfaflix.core.result.RefreshState
import com.application.alfaflix.core.result.Staleness
import com.application.alfaflix.domain.model.Trailer
import com.application.alfaflix.presentation.about.AboutScreen
import com.application.alfaflix.presentation.detail.MovieDetailScreenContent
import com.application.alfaflix.presentation.detail.MovieDetailUiState
import com.application.alfaflix.presentation.detail.ReviewsUiState
import com.application.alfaflix.presentation.detail.TrailerUiState
import com.application.alfaflix.presentation.favorites.FavoritesScreenContent
import com.application.alfaflix.presentation.favorites.FavoritesUiState
import com.application.alfaflix.presentation.home.HomeScreenContent
import com.application.alfaflix.presentation.home.HomeUiState
import com.application.alfaflix.presentation.search.SearchScreenContent
import com.application.alfaflix.presentation.search.SearchUiState
import com.application.alfaflix.domain.model.MovieListItem
import com.application.alfaflix.testing.movie
import com.application.alfaflix.testing.movieDetail
import com.application.alfaflix.testing.review
import com.application.alfaflix.ui.theme.AlfaflixTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Empty, error and partial-failure states.
 *
 * These are the screens users hit when something goes wrong, and they are the easiest to
 * regress unnoticed because the happy path keeps working.
 */
@RunWith(RobolectricTestRunner::class)
// A phone-sized window: the default Robolectric screen is small enough to fit a single
// poster column, which would push most of the content off-screen.
@Config(qualifiers = "w411dp-h891dp")
class ScreenStatesTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // region search

    @Test
    fun `an untouched search screen invites the user to start`() {
        setSearchContent(SearchUiState())

        composeTestRule.onNodeWithText("Find your next movie").assertIsDisplayed()
    }

    @Test
    fun `a search with no matches explains that, rather than showing an error`() {
        setSearchContent(SearchUiState(query = "zzzz", stage = SearchUiState.Stage.Empty))

        composeTestRule.onNodeWithText("No movies found").assertIsDisplayed()
    }

    @Test
    fun `an offline search points the user at what still works`() {
        setSearchContent(SearchUiState(isOffline = true))

        composeTestRule.onNodeWithText("Search needs a connection").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "You can still browse your favorites and previously loaded movies while offline.",
            )
            .assertIsDisplayed()
    }

    @Test
    fun `a search failure offers a retry that actually re-runs the search`() {
        var retries = 0
        setSearchContent(
            uiState = SearchUiState(query = "batman", stage = SearchUiState.Stage.Error(AppError.Timeout)),
            onRetry = { retries++ },
        )

        composeTestRule.onNodeWithText("Retry").performClick()

        assertEquals(1, retries)
    }

    // endregion

    // region home

    @Test
    fun `an unconfigured token on first launch gives actionable setup guidance and no useless retry`() {
        setHomeContent(HomeUiState.Error(AppError.MissingApiKey))

        composeTestRule
            .onNodeWithText(
                "No TMDB access token is configured. Add TMDB_READ_ACCESS_TOKEN to local.properties and rebuild — see the README.",
            )
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").assertDoesNotExist()
    }

    @Test
    fun `a rejected token is reported as a configuration problem`() {
        setHomeContent(HomeUiState.Error(AppError.InvalidApiKey))

        composeTestRule
            .onNodeWithText(
                "TMDB rejected the configured access token. Check TMDB_READ_ACCESS_TOKEN in local.properties — see the README.",
            )
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").assertDoesNotExist()
    }

    @Test
    fun `a first-launch offline failure is honest and offers retry`() {
        var retries = 0
        setHomeContent(HomeUiState.Error(AppError.Offline), onRefresh = { retries++ })

        composeTestRule.onNodeWithText("You're offline. Check your connection and try again.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").performClick()

        assertEquals(1, retries)
    }

    @Test
    fun `an empty discovery list says so instead of showing a blank screen`() {
        setHomeContent(homeContent(movies = emptyList(), hasMore = false))

        composeTestRule.onNodeWithText("No movies to show").assertIsDisplayed()
    }

    @Test
    fun `cached movies stay visible beside an offline banner`() {
        setHomeContent(
            homeContent(
                movies = listOf(item(1, "Cached Movie")),
                staleness = Staleness(lastUpdatedAt = 1_000L, reason = Staleness.Reason.OFFLINE),
                isOffline = true,
            ),
        )

        composeTestRule.onNodeWithText("You're offline · Showing saved data").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cached Movie", useUnmergedTree = false).assertExists()
    }

    @Test
    fun `a failed next page shows an inline retry that reloads the page`() {
        var loads = 0
        setHomeContent(
            homeContent(
                movies = (1..4).map { item(it, "Movie $it") },
                nextPage = RefreshState.Failed(AppError.Offline),
            ),
            onLoadNextPage = { loads++ },
        )

        // The footer is beyond the viewport, so a lazy grid has not composed it yet: scroll to it.
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText("Retry"))
        composeTestRule.onNodeWithText("Retry").performClick()

        assertEquals(1, loads)
    }

    @Test
    fun `the end of the list is announced`() {
        setHomeContent(homeContent(movies = listOf(item(1, "Only Movie")), hasMore = false))

        composeTestRule.onNodeWithText("You've reached the end").performScrollTo().assertIsDisplayed()
    }

    // endregion

    // region detail sections

    @Test
    fun `a reviews failure leaves the movie visible and retries just the reviews`() {
        var reviewRetries = 0
        var movieRetries = 0
        setDetailContent(
            content(reviews = ReviewsUiState.Failed(AppError.Timeout)),
            onRetry = { movieRetries++ },
            onRetryReviews = { reviewRetries++ },
        )

        composeTestRule.onNodeWithText("An overview.").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Couldn't load reviews.", substring = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").performScrollTo().performClick()

        assertEquals(1, reviewRetries)
        assertEquals(0, movieRetries)
    }

    @Test
    fun `no reviews is a calm empty message, not an error`() {
        setDetailContent(content(reviews = ReviewsUiState.Empty))

        composeTestRule.onNodeWithText("No reviews yet. Be the first to write one on TMDB.").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").assertDoesNotExist()
    }

    @Test
    fun `no trailer is stated plainly and the movie stays visible`() {
        setDetailContent(content(trailer = TrailerUiState.None))

        composeTestRule.onNodeWithText("No trailer is listed for this movie.").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("An overview.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a trailer failure offers a retry for the trailer only`() {
        var trailerRetries = 0
        setDetailContent(
            content(trailer = TrailerUiState.Failed(AppError.Offline)),
            onRetryTrailer = { trailerRetries++ },
        )

        composeTestRule.onNodeWithText("Couldn't load the trailer.", substring = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").performScrollTo().performClick()

        assertEquals(1, trailerRetries)
    }

    @Test
    fun `playing a trailer offline explains that a connection is needed`() {
        setDetailContent(
            content(
                trailer = TrailerUiState.Available(Trailer("abc", "Official Trailer", true)),
                isOffline = true,
            ),
        )

        composeTestRule
            .onNodeWithContentDescriptionText("Play the trailer for Movie 1")
            .performScrollTo()
            .performClick()

        composeTestRule
            .onNodeWithText("Trailers need an internet connection. Connect and try again.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `missing metadata is omitted gracefully instead of showing blanks`() {
        val bare = movieDetail(7, "Bare Bones").copy(
            tagline = null,
            overview = null,
            releaseDate = null,
            runtimeMinutes = null,
            genres = emptyList(),
            voteAverage = null,
            voteCount = null,
            posterPath = null,
            backdropPath = null,
            status = null,
        )
        setDetailContent(
            MovieDetailUiState.Content(
                detail = bare,
                isFavorite = false,
                staleness = null,
                isRefreshing = false,
                isOffline = false,
                reviews = ReviewsUiState.Empty,
                trailer = TrailerUiState.None,
            ),
        )

        composeTestRule.onAllNodesWithText("Bare Bones").onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithText("No synopsis is available for this movie yet.").assertIsDisplayed()
        composeTestRule.onNodeWithText("TMDB user score", substring = true).assertDoesNotExist()
    }

    // endregion

    @Test
    fun `an empty favorites screen explains how to fill it`() {
        composeTestRule.setContent {
            AlfaflixTheme {
                FavoritesScreenContent(uiState = FavoritesUiState(), onMovieClick = {}, onToggleFavorite = {})
            }
        }

        composeTestRule.onNodeWithText("Your favorite movies will appear here").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tap the heart on any movie to save it for later.").assertIsDisplayed()
    }

    @Test
    fun `the about screen carries the required TMDB attribution notice`() {
        composeTestRule.setContent {
            AlfaflixTheme { AboutScreen(onNavigateBack = {}) }
        }

        composeTestRule
            .onNodeWithText(
                "This application uses TMDB and the TMDB APIs but is not endorsed, certified, or otherwise approved by TMDB.",
            )
            .performScrollTo()
            .assertIsDisplayed()
    }

    // region helpers

    private fun item(id: Int, title: String) =
        MovieListItem(movie(id, title).copy(posterPath = null), isFavorite = false)

    private fun homeContent(
        movies: List<MovieListItem>,
        hasMore: Boolean = true,
        nextPage: RefreshState = RefreshState.Idle,
        staleness: Staleness? = null,
        isOffline: Boolean = false,
    ) = HomeUiState.Content(
        recentlyViewed = emptyList(),
        movies = movies,
        isRefreshing = false,
        nextPage = nextPage,
        hasMore = hasMore,
        staleness = staleness,
        isOffline = isOffline,
        canSurpriseMe = movies.isNotEmpty(),
    )

    private fun content(
        reviews: ReviewsUiState = ReviewsUiState.Empty,
        trailer: TrailerUiState = TrailerUiState.None,
        isOffline: Boolean = false,
    ) = MovieDetailUiState.Content(
        detail = movieDetail(1).copy(posterPath = null, backdropPath = null),
        isFavorite = false,
        staleness = null,
        isRefreshing = false,
        isOffline = isOffline,
        reviews = reviews,
        trailer = trailer,
    )

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onNodeWithContentDescriptionText(text: String) =
        onNode(androidx.compose.ui.test.hasContentDescription(text))

    private fun setSearchContent(uiState: SearchUiState, onRetry: () -> Unit = {}) {
        composeTestRule.setContent {
            AlfaflixTheme {
                SearchScreenContent(
                    uiState = uiState,
                    onQueryChange = {},
                    onClearQuery = {},
                    onLoadNextPage = {},
                    onRetry = onRetry,
                    onToggleFavorite = {},
                    onMovieClick = {},
                )
            }
        }
    }

    private fun setHomeContent(
        uiState: HomeUiState,
        onRefresh: () -> Unit = {},
        onLoadNextPage: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            AlfaflixTheme {
                HomeScreenContent(
                    uiState = uiState,
                    onRefresh = onRefresh,
                    onLoadNextPage = onLoadNextPage,
                    onSurpriseMe = {},
                    onToggleFavorite = {},
                    onMovieClick = {},
                    onAboutClick = {},
                )
            }
        }
    }

    private fun setDetailContent(
        uiState: MovieDetailUiState,
        onRetry: () -> Unit = {},
        onRetryReviews: () -> Unit = {},
        onRetryTrailer: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            AlfaflixTheme { DetailHost(uiState, onRetry, onRetryReviews, onRetryTrailer) }
        }
    }

    @Composable
    private fun DetailHost(
        uiState: MovieDetailUiState,
        onRetry: () -> Unit,
        onRetryReviews: () -> Unit,
        onRetryTrailer: () -> Unit,
    ) {
        MovieDetailScreenContent(
            uiState = uiState,
            onNavigateBack = {},
            onToggleFavorite = {},
            onRetry = onRetry,
            onRetryReviews = onRetryReviews,
            onRetryTrailer = onRetryTrailer,
        )
    }

    // endregion
}
