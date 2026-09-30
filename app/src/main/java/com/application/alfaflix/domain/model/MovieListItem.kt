package com.application.alfaflix.domain.model

import com.application.alfaflix.core.result.RefreshState

/**
 * A movie plus whether the user has saved it.
 *
 * Favorite state is attached here, derived from one shared source, rather than tracked by
 * each screen. That is what makes Home, Search, Detail and Favorites agree by construction.
 */
data class MovieListItem(
    val movie: Movie,
    val isFavorite: Boolean,
) {
    val id: Int get() = movie.id
}

/** The persisted discovery list plus the state of the work happening behind it. */
data class DiscoveryFeed(
    /** Null until discovery has been fetched at least once — "never loaded", not "empty". */
    val movies: List<Movie>?,
    val lastUpdatedAt: Long?,
    /** Refreshing page 1. Independent of [append], so each shows its own indicator. */
    val refresh: RefreshState,
    /** Loading the next page. */
    val append: RefreshState,
    val hasMore: Boolean,
)

/**
 * Everything the home screen renders, assembled from independent sources.
 *
 * [movies] is null until discovery has loaded once; that is what separates a first load
 * from a fetched list that is legitimately empty.
 */
data class HomeFeed(
    val recentlyViewed: List<MovieListItem>,
    val movies: List<MovieListItem>?,
    val lastUpdatedAt: Long?,
    val refresh: RefreshState,
    val append: RefreshState,
    val hasMore: Boolean,
)
