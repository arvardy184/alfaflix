package com.application.alfaflix.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entities.
 *
 * Each stores only the fields a screen needs rather than mirroring the whole API response.
 * The TMDB `movieId` is the primary key everywhere, which makes deduplication a property of
 * the schema rather than something application code has to remember.
 */

/** A movie the user saved. Room owns this data outright; it is never fetched. */
@Entity(tableName = "favorites")
data class FavoriteMovieEntity(
    @PrimaryKey val movieId: Int,
    val title: String,
    val releaseDate: String?,
    val posterPath: String?,
    val voteAverage: Double?,
    val addedAt: Long,
)

/**
 * A movie the user opened. Re-opening updates the existing row's [viewedAt] instead of
 * appending a duplicate.
 */
@Entity(tableName = "recently_viewed")
data class RecentlyViewedEntity(
    @PrimaryKey val movieId: Int,
    val title: String,
    val releaseDate: String?,
    val posterPath: String?,
    val voteAverage: Double?,
    val viewedAt: Long,
)

/** A cached full detail record. [cachedAt] drives the freshness window. */
@Entity(tableName = "movie_details")
data class MovieDetailEntity(
    @PrimaryKey val movieId: Int,
    val imdbId: String?,
    val title: String,
    val tagline: String?,
    val overview: String?,
    val releaseDate: String?,
    val runtimeMinutes: Int?,
    val genres: List<String>,
    val voteAverage: Double?,
    val voteCount: Int?,
    val posterPath: String?,
    val backdropPath: String?,
    val status: String?,
    val cachedAt: Long,
)

/**
 * One movie in the persisted discovery list.
 *
 * [position] preserves the order TMDB returned across every page loaded so far.
 */
@Entity(tableName = "discover_movies")
data class DiscoverMovieEntity(
    @PrimaryKey val movieId: Int,
    val position: Int,
    val title: String,
    val releaseDate: String?,
    val posterPath: String?,
    val voteAverage: Double?,
)

/**
 * Pagination bookkeeping for the discovery list: a single row.
 *
 * Written in the same transaction as the movies it describes, so the list and its
 * bookkeeping can never disagree. A missing row means discovery was never loaded, which is
 * what separates a first load from a list that is legitimately empty.
 */
@Entity(tableName = "discover_meta")
data class DiscoverMetaEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val loadedPages: Int,
    val totalPages: Int,
    /** When page 1 last loaded successfully. Appending later pages does not change it. */
    val refreshedAt: Long,
    /**
     * Which incarnation of the list this is: incremented by every refresh. A page request
     * remembers the epoch it started under, so a response that raced a refresh is recognisable
     * even though the refresh leaves `loadedPages` at the same value as before.
     */
    val epoch: Int,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}

/** A cached review. The composite key lets a review id repeat across movies. */
@Entity(
    tableName = "movie_reviews",
    primaryKeys = ["movieId", "reviewId"],
    indices = [Index("movieId")],
)
data class ReviewEntity(
    val movieId: Int,
    val reviewId: String,
    val author: String,
    val rating: Double?,
    val content: String,
    val createdAt: String?,
    val url: String?,
    val position: Int,
)

/**
 * When a movie's reviews last refreshed successfully.
 *
 * Separate from the review rows so a movie with genuinely no reviews still records a
 * successful fetch instead of looking permanently unloaded.
 */
@Entity(tableName = "review_meta")
data class ReviewMetaEntity(
    @PrimaryKey val movieId: Int,
    val refreshedAt: Long,
)

/**
 * A favorite saved before the TMDB migration, identified only by its IMDb ID.
 *
 * Parked here by the 1 → 2 migration and moved into `favorites` once TMDB resolves the IMDb
 * ID to a TMDB movie ID — see `LegacyFavoritesMigrator`.
 */
@Entity(tableName = "legacy_favorites")
data class LegacyFavoriteEntity(
    @PrimaryKey val imdbId: String,
    val title: String,
    val year: String,
    val posterUrl: String?,
    val addedAt: Long,
)
