package com.application.alfaflix.domain.repository

import com.application.alfaflix.core.result.AppResult
import com.application.alfaflix.core.result.Cached
import com.application.alfaflix.domain.model.DiscoveryFeed
import com.application.alfaflix.domain.model.Movie
import com.application.alfaflix.domain.model.MovieDetail
import com.application.alfaflix.domain.model.MoviePage
import com.application.alfaflix.domain.model.Review
import com.application.alfaflix.domain.model.Trailer
import kotlinx.coroutines.flow.Flow

/**
 * Movie data, from wherever it happens to live.
 *
 * The tiering is the app's offline strategy:
 *
 *  - **Discovery, detail and reviews** are persisted in Room and observed, so the UI keeps
 *    showing content when a refresh fails instead of collapsing into an error screen.
 *  - **Search is not cached.** Live search wants fresh results and arbitrary typed queries
 *    have a near-zero hit rate.
 *  - **Trailers are not cached.** Playback needs a connection regardless, and a video ID is
 *    one small request.
 */
interface MovieRepository {

    /** Searches TMDB. Never reads or writes the cache. A query matching nothing is an empty page. */
    suspend fun search(query: String, page: Int): AppResult<MoviePage>

    /** The persisted discovery list, emitting again as refreshes and page loads progress. */
    fun observeDiscovery(): Flow<DiscoveryFeed>

    /**
     * Refreshes the first page of discovery. A failure leaves the persisted list untouched.
     *
     * @param force bypasses the freshness window. Used by pull-to-refresh.
     */
    suspend fun refreshDiscovery(force: Boolean): AppResult<Unit>

    /**
     * Loads and persists the next discovery page. Does nothing when there is no next page or
     * a page load is already running, so callers may invoke it freely from scroll events.
     */
    suspend fun loadMoreDiscovery(): AppResult<Unit>

    /** A random movie from data already on the device, or null when nothing is cached. */
    suspend fun randomCachedMovie(): Movie?

    fun observeMovieDetail(movieId: Int): Flow<Cached<MovieDetail>>

    suspend fun refreshMovieDetail(movieId: Int, force: Boolean): AppResult<Unit>

    fun observeReviews(movieId: Int): Flow<Cached<List<Review>>>

    suspend fun refreshReviews(movieId: Int, force: Boolean): AppResult<Unit>

    /** The movie's trailer, or null when it has none. Always a network request. */
    suspend fun loadTrailer(movieId: Int): AppResult<Trailer?>
}
