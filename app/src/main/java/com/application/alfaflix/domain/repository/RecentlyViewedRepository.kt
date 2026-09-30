package com.application.alfaflix.domain.repository

import com.application.alfaflix.domain.model.Movie
import kotlinx.coroutines.flow.Flow

/**
 * The movies the user has opened, most recent first.
 *
 * Like favorites, this is device-local data that needs no network.
 */
interface RecentlyViewedRepository {

    fun observeRecentlyViewed(): Flow<List<Movie>>

    /**
     * Records that a movie was opened.
     *
     * Re-opening a movie moves it to the top rather than adding a duplicate, and history is
     * capped so it cannot grow without bound.
     */
    suspend fun record(movie: Movie)

    companion object {
        /** Enough to be useful as a "jump back in" row without becoming a second history screen. */
        const val MAX_ENTRIES = 20
    }
}
