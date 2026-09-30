package com.application.alfaflix.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.application.alfaflix.core.network.NetworkMonitor
import com.application.alfaflix.core.result.AppError
import com.application.alfaflix.core.result.AppResult
import com.application.alfaflix.core.result.Cached
import com.application.alfaflix.core.result.RefreshState
import com.application.alfaflix.core.result.Staleness
import com.application.alfaflix.domain.model.MovieDetail
import com.application.alfaflix.domain.model.Review
import com.application.alfaflix.domain.model.Trailer
import com.application.alfaflix.domain.repository.MovieRepository
import com.application.alfaflix.domain.repository.RecentlyViewedRepository
import com.application.alfaflix.domain.usecase.MovieDetailState
import com.application.alfaflix.domain.usecase.ObserveMovieDetailUseCase
import com.application.alfaflix.domain.usecase.ToggleFavoriteUseCase
import com.application.alfaflix.presentation.navigation.MovieDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MovieDetailUiState {

    data object Loading : MovieDetailUiState

    /** Nothing cached and the fetch failed. */
    data class Error(val error: AppError) : MovieDetailUiState

    /**
     * The movie itself, plus reviews and trailer as sections that each carry their own
     * state — a failed section never hides the movie.
     */
    data class Content(
        val detail: MovieDetail,
        val isFavorite: Boolean,
        val staleness: Staleness?,
        val isRefreshing: Boolean,
        val isOffline: Boolean,
        val reviews: ReviewsUiState,
        val trailer: TrailerUiState,
    ) : MovieDetailUiState
}

sealed interface ReviewsUiState {
    data object Loading : ReviewsUiState

    /** Fetched successfully and there are none. */
    data object Empty : ReviewsUiState

    data class Loaded(val reviews: List<Review>) : ReviewsUiState

    data class Failed(val error: AppError) : ReviewsUiState
}

sealed interface TrailerUiState {
    data object Loading : TrailerUiState

    /** Fetched successfully and TMDB lists no trailer. */
    data object None : TrailerUiState

    data class Available(val trailer: Trailer) : TrailerUiState

    data class Failed(val error: AppError) : TrailerUiState
}

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeMovieDetail: ObserveMovieDetailUseCase,
    private val movieRepository: MovieRepository,
    private val recentlyViewedRepository: RecentlyViewedRepository,
    private val toggleFavorite: ToggleFavoriteUseCase,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    /**
     * The screen reconstructs itself from this ID alone, which is what makes the
     * destination deep-linkable and safe across process death.
     */
    private val movieId: Int = checkNotNull(savedStateHandle[MovieDetailRoute.MOVIE_ID_ARG]) {
        "MovieDetailViewModel requires a ${MovieDetailRoute.MOVIE_ID_ARG} argument"
    }

    private val detailState = observeMovieDetail(movieId)

    private val reviewsState = movieRepository.observeReviews(movieId).map { it.toReviewsUiState() }

    private val trailerState = MutableStateFlow<TrailerUiState>(TrailerUiState.Loading)
    private var trailerJob: Job? = null

    val uiState: StateFlow<MovieDetailUiState> = combine(
        detailState,
        reviewsState,
        trailerState,
        networkMonitor.isOnline,
    ) { detail, reviews, trailer, isOnline ->
        detail.toUiState(reviews = reviews, trailer = trailer, isOnline = isOnline)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = MovieDetailUiState.Loading,
    )

    init {
        // Three independent loads. Each settles into its own state, so one failing cannot
        // hide the others.
        viewModelScope.launch { movieRepository.refreshMovieDetail(movieId, force = false) }
        viewModelScope.launch { movieRepository.refreshReviews(movieId, force = false) }
        loadTrailer()

        // Recorded once per screen instance. `take(1)` is what guarantees that — the
        // underlying flow re-emits on every favorite change and refresh, and recording on
        // each emission would keep rewriting the history timestamp.
        detailState
            .map { it.cached.data }
            .filterNotNull()
            .take(1)
            .onEach { recentlyViewedRepository.record(it.toMovie()) }
            .launchIn(viewModelScope)
    }

    /** Retries the movie detail itself. */
    fun retry() {
        viewModelScope.launch { movieRepository.refreshMovieDetail(movieId, force = true) }
    }

    fun retryReviews() {
        viewModelScope.launch { movieRepository.refreshReviews(movieId, force = true) }
    }

    fun retryTrailer() = loadTrailer()

    fun onToggleFavorite() {
        val detail = (uiState.value as? MovieDetailUiState.Content)?.detail ?: return
        viewModelScope.launch { toggleFavorite(detail.toMovie()) }
    }

    private fun loadTrailer() {
        trailerJob?.cancel()
        trailerState.value = TrailerUiState.Loading
        trailerJob = viewModelScope.launch {
            trailerState.value = when (val result = movieRepository.loadTrailer(movieId)) {
                is AppResult.Success -> result.data?.let { TrailerUiState.Available(it) } ?: TrailerUiState.None
                is AppResult.Failure -> TrailerUiState.Failed(result.error)
            }
        }
    }

    private fun Cached<List<Review>>.toReviewsUiState(): ReviewsUiState {
        val reviews = data
        return when {
            // Cached reviews stay visible even while a refresh fails.
            reviews != null && reviews.isEmpty() -> ReviewsUiState.Empty
            reviews != null -> ReviewsUiState.Loaded(reviews)
            refresh is RefreshState.Failed -> ReviewsUiState.Failed(refresh.error)
            else -> ReviewsUiState.Loading
        }
    }

    private fun MovieDetailState.toUiState(
        reviews: ReviewsUiState,
        trailer: TrailerUiState,
        isOnline: Boolean,
    ): MovieDetailUiState {
        val detail = cached.data

        return when {
            // Cached content is shown even while a refresh fails — the user came here to
            // read about a movie, and an error screen would take that away.
            detail != null -> MovieDetailUiState.Content(
                detail = detail,
                isFavorite = isFavorite,
                staleness = Staleness.from(
                    lastUpdatedAt = cached.lastUpdatedAt,
                    refresh = cached.refresh,
                    isOffline = !isOnline,
                ),
                isRefreshing = cached.isRefreshing,
                isOffline = !isOnline,
                reviews = reviews,
                trailer = trailer,
            )

            cached.refresh is RefreshState.Failed ->
                MovieDetailUiState.Error((cached.refresh as RefreshState.Failed).error)

            else -> MovieDetailUiState.Loading
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
