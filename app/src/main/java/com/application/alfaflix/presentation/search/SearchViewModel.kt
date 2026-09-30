package com.application.alfaflix.presentation.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.application.alfaflix.core.network.NetworkMonitor
import com.application.alfaflix.core.result.AppResult
import com.application.alfaflix.domain.model.MovieListItem
import com.application.alfaflix.domain.model.MoviePage
import com.application.alfaflix.domain.repository.FavoritesRepository
import com.application.alfaflix.domain.usecase.SearchMoviesUseCase
import com.application.alfaflix.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Live search.
 *
 * **Every request belongs to a generation.** The generation is bumped, and in-flight work is
 * cancelled, *the instant the input changes* — not after the debounce. Only the debounced
 * network call is delayed. A response is applied only if its generation is still current, so
 * a slow reply for an abandoned query can never touch the screen, including the A → B → A
 * case where the query text alone would look valid again.
 *
 * Cancellation is cooperative: a response that has already returned and is resuming cannot
 * be cancelled, which is why the generation check exists as a second guard rather than
 * relying on cancellation alone. `CancellationException` is never caught here, so
 * cancelling always stops work instead of turning into an error state.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val searchMovies: SearchMoviesUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    favoritesRepository: FavoritesRepository,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** Bumped on every input change and retry. Confined to the main thread. */
    private var generation = 0L

    /** The trimmed query most recently scheduled; edits that do not change it are no-ops. */
    private var scheduledQuery = ""

    /** The trimmed query the results currently on screen belong to. */
    private var resultsQuery = ""

    private var firstPageJob: Job? = null
    private var nextPageJob: Job? = null

    private var favoriteIds: Set<Int> = emptySet()

    init {
        // Favorite state is derived from the shared source rather than tracked here, so a
        // toggle made on any other screen is reflected in these results immediately.
        favoritesRepository.observeFavoriteIds()
            .onEach { ids ->
                favoriteIds = ids
                _uiState.update { state ->
                    state.copy(movies = state.movies.map { it.copy(isFavorite = it.id in ids) })
                }
            }
            .launchIn(viewModelScope)

        networkMonitor.isOnline
            .onEach { online -> _uiState.update { it.copy(isOffline = !online) } }
            .launchIn(viewModelScope)

        // After process death the field comes back with its text; search it again rather than
        // showing a query with no results.
        savedStateHandle.get<String>(QUERY_KEY)?.takeIf { it.isNotBlank() }?.let(::onQueryChange)
    }

    fun onQueryChange(query: String) {
        savedStateHandle[QUERY_KEY] = query
        _uiState.update { it.copy(query = query) }

        val trimmed = query.trim()
        // Whitespace-only edits do not change what is being searched.
        if (trimmed == scheduledQuery) return
        scheduledQuery = trimmed

        val requestGeneration = invalidateInFlightWork()

        if (trimmed.length < SearchMoviesUseCase.MIN_QUERY_LENGTH) {
            showIdle()
            return
        }

        firstPageJob = viewModelScope.launch {
            // Only the request waits; the invalidation above already happened.
            delay(DEBOUNCE_MILLIS)
            loadFirstPage(trimmed, requestGeneration)
        }
    }

    fun onClearQuery() = onQueryChange("")

    /** Re-runs the first page for the current query after a failure, without debouncing. */
    fun retry() {
        val query = scheduledQuery
        if (query.length < SearchMoviesUseCase.MIN_QUERY_LENGTH) return

        val requestGeneration = invalidateInFlightWork()
        firstPageJob = viewModelScope.launch { loadFirstPage(query, requestGeneration) }
    }

    /**
     * Appends the next page of results.
     *
     * Ignored while a page is already loading — a lazy grid can fire its "near the end"
     * signal on consecutive frames — and ignored when the visible results no longer match
     * the text in the field, since those results are already condemned.
     */
    fun loadNextPage() {
        val state = _uiState.value
        if (!state.canLoadMore || state.isLoadingNextPage) return
        if (scheduledQuery != resultsQuery) return

        val requestGeneration = generation
        val query = resultsQuery
        val page = state.currentPage + 1

        // Claim the guard synchronously, before launching: the launched body has not run yet
        // when a second call arrives in the same frame.
        _uiState.update { it.copy(isLoadingNextPage = true, nextPageError = null) }

        nextPageJob = viewModelScope.launch {
            val result = searchMovies(query, page)
            if (requestGeneration != generation) return@launch

            when (result) {
                is AppResult.Success -> appendPage(result.data)
                is AppResult.Failure -> _uiState.update {
                    it.copy(isLoadingNextPage = false, nextPageError = result.error)
                }
            }
        }
    }

    fun onToggleFavorite(item: MovieListItem) {
        viewModelScope.launch { toggleFavorite(item.movie) }
    }

    /** Condemns everything in flight and returns the generation new work must carry. */
    private fun invalidateInFlightWork(): Long {
        firstPageJob?.cancel()
        nextPageJob?.cancel()
        _uiState.update { it.copy(isLoadingNextPage = false, nextPageError = null) }
        return ++generation
    }

    private fun showIdle() {
        resultsQuery = ""
        _uiState.update {
            it.copy(stage = SearchUiState.Stage.Idle, movies = emptyList(), currentPage = 0, totalPages = 0)
        }
    }

    private suspend fun loadFirstPage(query: String, requestGeneration: Long) {
        resultsQuery = query
        _uiState.update {
            it.copy(stage = SearchUiState.Stage.Loading, movies = emptyList(), currentPage = 0, totalPages = 0)
        }

        val result = searchMovies(query, FIRST_PAGE)
        if (requestGeneration != generation) return

        _uiState.update { state ->
            when (result) {
                is AppResult.Success -> state.copy(
                    // An empty result set is a successful request, not an error.
                    stage = if (result.data.movies.isEmpty()) {
                        SearchUiState.Stage.Empty
                    } else {
                        SearchUiState.Stage.Content
                    },
                    movies = result.data.movies.map { MovieListItem(it, isFavorite = it.id in favoriteIds) },
                    currentPage = FIRST_PAGE,
                    totalPages = result.data.totalPages,
                )

                is AppResult.Failure -> state.copy(
                    stage = SearchUiState.Stage.Error(result.error),
                    movies = emptyList(),
                    currentPage = 0,
                    totalPages = 0,
                )
            }
        }
    }

    private fun appendPage(page: MoviePage) {
        _uiState.update { state ->
            // De-duplicate defensively: a result can shift across page boundaries between
            // requests, and a duplicate key would crash a lazy grid.
            val existingIds = state.movies.mapTo(mutableSetOf()) { it.id }
            val newItems = page.movies
                .filter { it.id !in existingIds }
                .map { MovieListItem(movie = it, isFavorite = it.id in favoriteIds) }

            state.copy(
                movies = state.movies + newItems,
                currentPage = page.page,
                totalPages = page.totalPages,
                isLoadingNextPage = false,
                nextPageError = null,
            )
        }
    }

    private companion object {
        const val QUERY_KEY = "query"

        /**
         * Long enough that typing a word produces one request instead of six, short enough
         * that results still feel live.
         */
        const val DEBOUNCE_MILLIS = 400L
        const val FIRST_PAGE = 1
    }
}
