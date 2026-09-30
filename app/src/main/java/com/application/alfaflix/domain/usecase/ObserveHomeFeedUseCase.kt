package com.application.alfaflix.domain.usecase

import com.application.alfaflix.domain.model.HomeFeed
import com.application.alfaflix.domain.model.Movie
import com.application.alfaflix.domain.model.MovieListItem
import com.application.alfaflix.domain.repository.FavoritesRepository
import com.application.alfaflix.domain.repository.MovieRepository
import com.application.alfaflix.domain.repository.RecentlyViewedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * Assembles the home screen from three independent sources: the persisted discovery list,
 * view history, and favorite state.
 *
 * Composing them here rather than in the ViewModel means the screen observes one stream and
 * cannot render a half-updated combination — for example the grid already knowing a movie is
 * saved while the history row still shows it unsaved.
 */
class ObserveHomeFeedUseCase @Inject constructor(
    private val movieRepository: MovieRepository,
    private val favoritesRepository: FavoritesRepository,
    private val recentlyViewedRepository: RecentlyViewedRepository,
) {

    operator fun invoke(): Flow<HomeFeed> = combine(
        movieRepository.observeDiscovery(),
        recentlyViewedRepository.observeRecentlyViewed(),
        favoritesRepository.observeFavoriteIds(),
    ) { discovery, recentlyViewed, favoriteIds ->
        HomeFeed(
            recentlyViewed = recentlyViewed.toListItems(favoriteIds),
            movies = discovery.movies?.toListItems(favoriteIds),
            lastUpdatedAt = discovery.lastUpdatedAt,
            refresh = discovery.refresh,
            append = discovery.append,
            hasMore = discovery.hasMore,
        )
    }

    private fun List<Movie>.toListItems(favoriteIds: Set<Int>): List<MovieListItem> =
        map { MovieListItem(movie = it, isFavorite = it.id in favoriteIds) }
}
