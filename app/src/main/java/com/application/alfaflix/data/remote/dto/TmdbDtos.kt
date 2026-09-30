package com.application.alfaflix.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire models for the TMDB API. These never leave the data layer.
 *
 * Every field is nullable with a default: TMDB omits or nulls fields freely depending on the
 * title, and one missing field must never fail deserialization of the whole response.
 */
@Serializable
data class MovieListResponseDto(
    @SerialName("page") val page: Int? = null,
    @SerialName("results") val results: List<MovieListItemDto>? = null,
    @SerialName("total_pages") val totalPages: Int? = null,
    @SerialName("total_results") val totalResults: Int? = null,
)

@Serializable
data class MovieListItemDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
)

@Serializable
data class MovieDetailDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("tagline") val tagline: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("runtime") val runtime: Int? = null,
    @SerialName("genres") val genres: List<GenreDto>? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("status") val status: String? = null,
)

@Serializable
data class GenreDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("name") val name: String? = null,
)

@Serializable
data class ReviewsResponseDto(
    @SerialName("page") val page: Int? = null,
    @SerialName("results") val results: List<ReviewDto>? = null,
    @SerialName("total_pages") val totalPages: Int? = null,
)

@Serializable
data class ReviewDto(
    @SerialName("id") val id: String? = null,
    @SerialName("author") val author: String? = null,
    @SerialName("author_details") val authorDetails: AuthorDetailsDto? = null,
    @SerialName("content") val content: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("url") val url: String? = null,
)

@Serializable
data class AuthorDetailsDto(
    @SerialName("name") val name: String? = null,
    @SerialName("username") val username: String? = null,
    @SerialName("rating") val rating: Double? = null,
)

@Serializable
data class VideosResponseDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("results") val results: List<VideoDto>? = null,
)

@Serializable
data class VideoDto(
    @SerialName("key") val key: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("site") val site: String? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("official") val official: Boolean? = null,
    @SerialName("published_at") val publishedAt: String? = null,
)

@Serializable
data class FindResponseDto(
    @SerialName("movie_results") val movieResults: List<MovieListItemDto>? = null,
)
