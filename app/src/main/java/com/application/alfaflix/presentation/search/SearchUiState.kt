package com.application.alfaflix.presentation.search

import com.application.alfaflix.core.result.AppError
import com.application.alfaflix.domain.model.MovieListItem

/**
 * Everything the search screen renders.
 *
 * [stage] covers the first page — the state that owns the whole screen. Subsequent pages
 * are represented separately by [isLoadingNextPage] and [nextPageError] so a failure while
 * paginating shows an inline footer and never replaces results the user is already reading.
 */
data class SearchUiState(
    val query: String = "",
    val stage: Stage = Stage.Idle,
    val movies: List<MovieListItem> = emptyList(),
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val isLoadingNextPage: Boolean = false,
    val nextPageError: AppError? = null,
    val isOffline: Boolean = false,
) {

    /** True while TMDB reports more pages than have been loaded. */
    val canLoadMore: Boolean
        get() = stage == Stage.Content && currentPage < totalPages

    sealed interface Stage {
        /** No query yet, or one too short to search. */
        data object Idle : Stage

        /** First page in flight. */
        data object Loading : Stage

        data object Content : Stage

        /** The request succeeded and matched nothing. Not a failure. */
        data object Empty : Stage

        data class Error(val error: AppError) : Stage
    }
}
