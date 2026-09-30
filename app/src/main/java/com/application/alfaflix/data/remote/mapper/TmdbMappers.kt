package com.application.alfaflix.data.remote.mapper

import com.application.alfaflix.data.remote.dto.MovieDetailDto
import com.application.alfaflix.data.remote.dto.MovieListItemDto
import com.application.alfaflix.data.remote.dto.MovieListResponseDto
import com.application.alfaflix.data.remote.dto.ReviewDto
import com.application.alfaflix.data.remote.dto.ReviewsResponseDto
import com.application.alfaflix.data.remote.dto.VideoDto
import com.application.alfaflix.data.remote.dto.VideosResponseDto
import com.application.alfaflix.domain.model.Movie
import com.application.alfaflix.domain.model.MovieDetail
import com.application.alfaflix.domain.model.MoviePage
import com.application.alfaflix.domain.model.MovieVideo
import com.application.alfaflix.domain.model.Review

/**
 * Maps TMDB wire models to domain models.
 *
 * Two invariants hold for everything in this file:
 *
 *  1. **Nothing throws.** Malformed or absent input yields null or an empty list. One
 *     unusable field must never cost the user the whole screen.
 *  2. **TMDB's "empty" conventions never escape.** It sends `""` for a missing overview or
 *     date, `0` for an unknown runtime and `0.0` for an unrated movie; each becomes null
 *     here, once, so no screen has to know those conventions.
 *
 * Records missing an ID or a title return null and are dropped by callers: a movie that
 * cannot be identified cannot be opened, and one that cannot be named cannot be rendered.
 */

private fun String?.orNullIfBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun Double?.toScoreOrNull(): Double? = this?.takeIf { it > 0.0 }

fun MovieListItemDto.toDomainOrNull(): Movie? {
    val movieId = id ?: return null
    val name = title.orNullIfBlank() ?: return null
    return Movie(
        id = movieId,
        title = name,
        releaseDate = releaseDate.orNullIfBlank(),
        posterPath = posterPath.orNullIfBlank(),
        voteAverage = voteAverage.toScoreOrNull(),
    )
}

/** A list page, with unusable records dropped and repeated IDs collapsed. */
fun MovieListResponseDto.toMoviePage(requestedPage: Int): MoviePage = MoviePage(
    movies = results.orEmpty().mapNotNull(MovieListItemDto::toDomainOrNull).distinctBy(Movie::id),
    page = page ?: requestedPage,
    totalPages = totalPages?.coerceAtLeast(0) ?: 0,
)

fun MovieDetailDto.toDomainOrNull(): MovieDetail? {
    val movieId = id ?: return null
    val name = title.orNullIfBlank() ?: return null
    return MovieDetail(
        id = movieId,
        imdbId = imdbId.orNullIfBlank(),
        title = name,
        tagline = tagline.orNullIfBlank(),
        overview = overview.orNullIfBlank(),
        releaseDate = releaseDate.orNullIfBlank(),
        runtimeMinutes = runtime?.takeIf { it > 0 },
        genres = genres.orEmpty().mapNotNull { it.name.orNullIfBlank() },
        voteAverage = voteAverage.toScoreOrNull(),
        voteCount = voteCount?.takeIf { it > 0 },
        posterPath = posterPath.orNullIfBlank(),
        backdropPath = backdropPath.orNullIfBlank(),
        status = status.orNullIfBlank(),
    )
}

fun ReviewDto.toDomainOrNull(): Review? {
    val reviewId = id.orNullIfBlank() ?: return null
    val body = content.orNullIfBlank() ?: return null
    return Review(
        id = reviewId,
        // Prefer the display name, then the username, then the top-level author field.
        author = authorDetails?.name.orNullIfBlank()
            ?: authorDetails?.username.orNullIfBlank()
            ?: author.orNullIfBlank()
            ?: ANONYMOUS_AUTHOR,
        rating = authorDetails?.rating.toScoreOrNull(),
        content = body,
        createdAt = createdAt.orNullIfBlank(),
        url = url.orNullIfBlank(),
    )
}

fun ReviewsResponseDto.toReviews(): List<Review> =
    results.orEmpty().mapNotNull(ReviewDto::toDomainOrNull).distinctBy(Review::id)

fun VideoDto.toDomainOrNull(): MovieVideo? {
    val videoKey = key.orNullIfBlank() ?: return null
    return MovieVideo(
        key = videoKey,
        name = name.orNullIfBlank().orEmpty(),
        site = site.orNullIfBlank().orEmpty(),
        type = type.orNullIfBlank().orEmpty(),
        official = official == true,
        publishedAt = publishedAt.orNullIfBlank(),
    )
}

fun VideosResponseDto.toVideos(): List<MovieVideo> =
    results.orEmpty().mapNotNull(VideoDto::toDomainOrNull)

/** Shown when TMDB has a review but no usable author name. */
const val ANONYMOUS_AUTHOR = "Anonymous"
