package com.application.alfaflix.data.local.mapper

import com.application.alfaflix.data.local.entity.DiscoverMovieEntity
import com.application.alfaflix.data.local.entity.FavoriteMovieEntity
import com.application.alfaflix.data.local.entity.MovieDetailEntity
import com.application.alfaflix.data.local.entity.RecentlyViewedEntity
import com.application.alfaflix.data.local.entity.ReviewEntity
import com.application.alfaflix.domain.model.Movie
import com.application.alfaflix.domain.model.MovieDetail
import com.application.alfaflix.domain.model.Review

/**
 * Entity ↔ domain conversion.
 *
 * Keeps Room types out of the domain layer. Timestamps are supplied by the caller rather
 * than read from a clock here, so cache-expiry behaviour stays deterministic under test.
 */

fun FavoriteMovieEntity.toDomain(): Movie = Movie(movieId, title, releaseDate, posterPath, voteAverage)

fun Movie.toFavoriteEntity(addedAt: Long): FavoriteMovieEntity =
    FavoriteMovieEntity(id, title, releaseDate, posterPath, voteAverage, addedAt)

fun RecentlyViewedEntity.toDomain(): Movie = Movie(movieId, title, releaseDate, posterPath, voteAverage)

fun Movie.toRecentlyViewedEntity(viewedAt: Long): RecentlyViewedEntity =
    RecentlyViewedEntity(id, title, releaseDate, posterPath, voteAverage, viewedAt)

fun DiscoverMovieEntity.toDomain(): Movie = Movie(movieId, title, releaseDate, posterPath, voteAverage)

fun Movie.toDiscoverEntity(position: Int): DiscoverMovieEntity =
    DiscoverMovieEntity(id, position, title, releaseDate, posterPath, voteAverage)

fun ReviewEntity.toDomain(): Review = Review(
    id = reviewId,
    author = author,
    rating = rating,
    content = content,
    createdAt = createdAt,
    url = url,
)

fun Review.toEntity(movieId: Int, position: Int): ReviewEntity = ReviewEntity(
    movieId = movieId,
    reviewId = id,
    author = author,
    rating = rating,
    content = content,
    createdAt = createdAt,
    url = url,
    position = position,
)

fun MovieDetailEntity.toDomain(): MovieDetail = MovieDetail(
    id = movieId,
    imdbId = imdbId,
    title = title,
    tagline = tagline,
    overview = overview,
    releaseDate = releaseDate,
    runtimeMinutes = runtimeMinutes,
    genres = genres,
    voteAverage = voteAverage,
    voteCount = voteCount,
    posterPath = posterPath,
    backdropPath = backdropPath,
    status = status,
)

fun MovieDetail.toEntity(cachedAt: Long): MovieDetailEntity = MovieDetailEntity(
    movieId = id,
    imdbId = imdbId,
    title = title,
    tagline = tagline,
    overview = overview,
    releaseDate = releaseDate,
    runtimeMinutes = runtimeMinutes,
    genres = genres,
    voteAverage = voteAverage,
    voteCount = voteCount,
    posterPath = posterPath,
    backdropPath = backdropPath,
    status = status,
    cachedAt = cachedAt,
)
