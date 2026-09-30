package com.application.alfaflix.domain.usecase

import com.application.alfaflix.core.result.AppResult
import com.application.alfaflix.domain.model.MoviePage
import com.application.alfaflix.domain.repository.MovieRepository
import javax.inject.Inject

/**
 * Owns the app's search policy, so it holds regardless of which caller invokes a search.
 *
 * Queries shorter than [MIN_QUERY_LENGTH] never reach the network: single characters return
 * noise and waste requests. The search screen also debounces, but this guarantee does not
 * depend on the UI getting that right.
 */
class SearchMoviesUseCase @Inject constructor(
    private val movieRepository: MovieRepository,
) {

    suspend operator fun invoke(query: String, page: Int): AppResult<MoviePage> {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) {
            return AppResult.Success(MoviePage.empty(page))
        }
        return movieRepository.search(query = trimmed, page = page)
    }

    companion object {
        const val MIN_QUERY_LENGTH = 2
    }
}
