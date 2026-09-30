package com.application.alfaflix.presentation.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes.
 *
 * Destinations are declared as serializable types rather than string patterns, so argument
 * names and types are checked at compile time instead of failing at runtime on a typo.
 */

@Serializable
data object HomeRoute

@Serializable
data object SearchRoute

@Serializable
data object FavoritesRoute

@Serializable
data object AboutRoute

/**
 * The movie detail destination.
 *
 * Carries only the TMDB movie ID. Passing the whole movie would bloat the saved state, go
 * stale against the cache, and break deep links — the detail screen reconstructs everything
 * it needs from this one value, including after process death.
 */
@Serializable
data class MovieDetailRoute(val movieId: Int) {
    companion object {
        /**
         * Navigation stores type-safe route arguments under their property names, so the
         * ViewModel can read this key straight from its [androidx.lifecycle.SavedStateHandle].
         */
        const val MOVIE_ID_ARG = "movieId"
    }
}
