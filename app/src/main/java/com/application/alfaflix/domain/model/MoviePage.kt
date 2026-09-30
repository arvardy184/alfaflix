package com.application.alfaflix.domain.model

/**
 * One page of movies from a paginated TMDB list.
 *
 * [totalPages] is what makes pagination terminate correctly: there is another page exactly
 * when [page] is below it.
 */
data class MoviePage(
    val movies: List<Movie>,
    val page: Int,
    val totalPages: Int,
) {
    val hasMore: Boolean get() = page < totalPages

    companion object {
        fun empty(page: Int): MoviePage = MoviePage(movies = emptyList(), page = page, totalPages = 0)
    }
}
