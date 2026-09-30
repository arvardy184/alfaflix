package com.application.alfaflix.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.application.alfaflix.core.network.NetworkMonitor
import com.application.alfaflix.core.result.AppError
import com.application.alfaflix.core.result.RefreshState
import com.application.alfaflix.core.result.Staleness
import com.application.alfaflix.domain.model.HomeFeed
import com.application.alfaflix.domain.model.MovieListItem
import com.application.alfaflix.domain.repository.MovieRepository
import com.application.alfaflix.domain.usecase.ObserveHomeFeedUseCase
import com.application.alfaflix.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the home screen renders. */
sealed interface HomeUiState {

    /** Cold start with nothing cached. */
    data object Loading : HomeUiState

    /** Nothing cached *and* the first load failed — the only case that earns a full-screen error. */
    data class Error(val error: AppError) : HomeUiState

    data class Content(
        val recentlyViewed: List<MovieListItem>,
        /** The discovery grid. Empty means TMDB returned nothing, not that it is loading. */
        val movies: List<MovieListItem>,
        val isRefreshing: Boolean,
        /** Loading or failure of the *next page*, shown as an inline footer. */
        val nextPage: RefreshState,
        val hasMore: Boolean,
        /** Non-null when showing cached content after a failed or skipped refresh. */
        val staleness: Staleness?,
        val isOffline: Boolean,
        val canSurpriseMe: Boolean,
    ) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeHomeFeed: ObserveHomeFeedUseCase,
    private val movieRepository: MovieRepository,
    private val toggleFavorite: ToggleFavoriteUseCase,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    /**
     * Emits the movie ID chosen by "Surprise me".
     *
     * A [Channel] rather than state: navigating is a one-off event, and holding it in state
     * would re-trigger the navigation on every configuration change.
     */
    private val surpriseChannel = Channel<Int>(Channel.BUFFERED)
    val surpriseMovie: Flow<Int> = surpriseChannel.receiveAsFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        observeHomeFeed(),
        networkMonitor.isOnline,
    ) { feed, isOnline -> feed.toUiState(isOnline = isOnline) }
        .stateIn(
            scope = viewModelScope,
            // Survives a configuration change — and a trip to the detail screen — without
            // re-running the pipeline, so the grid is back instantly at the same position.
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = HomeUiState.Loading,
        )

    init {
        // Respects the freshness window — a warm cache costs no request on launch.
        refresh(force = false)

        // When connectivity returns after a failed load, retry without waiting for the user.
        networkMonitor.isOnline
            .distinctUntilChanged()
            .drop(1)
            .filter { online -> online && uiState.value.needsRetry() }
            .onEach { refresh(force = true) }
            .launchIn(viewModelScope)
    }

    /**
     * @param force true for pull-to-refresh and Retry, which bypass the freshness window.
     */
    fun refresh(force: Boolean = true) {
        viewModelScope.launch { movieRepository.refreshDiscovery(force = force) }
    }

    /** Safe to call from every scroll event: the repository ignores it while a page loads. */
    fun loadNextPage() {
        viewModelScope.launch { movieRepository.loadMoreDiscovery() }
    }

    fun onToggleFavorite(item: MovieListItem) {
        viewModelScope.launch { toggleFavorite(item.movie) }
    }

    fun onSurpriseMe() {
        viewModelScope.launch {
            movieRepository.randomCachedMovie()?.let { surpriseChannel.send(it.id) }
        }
    }

    private fun HomeFeed.toUiState(isOnline: Boolean): HomeUiState = when {
        // Content wins over everything: a failed refresh must never replace movies the user
        // can already see with an error screen.
        movies != null -> HomeUiState.Content(
            recentlyViewed = recentlyViewed,
            movies = movies,
            isRefreshing = refresh == RefreshState.InProgress,
            nextPage = append,
            hasMore = hasMore,
            staleness = Staleness.from(
                lastUpdatedAt = lastUpdatedAt,
                refresh = refresh,
                isOffline = !isOnline,
            ),
            isOffline = !isOnline,
            canSurpriseMe = movies.isNotEmpty(),
        )

        refresh is RefreshState.Failed -> HomeUiState.Error(refresh.error)

        else -> HomeUiState.Loading
    }

    private fun HomeUiState.needsRetry(): Boolean = when (this) {
        is HomeUiState.Error -> true
        is HomeUiState.Content -> staleness?.reason == Staleness.Reason.REFRESH_FAILED ||
            staleness?.reason == Staleness.Reason.OFFLINE
        HomeUiState.Loading -> false
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
