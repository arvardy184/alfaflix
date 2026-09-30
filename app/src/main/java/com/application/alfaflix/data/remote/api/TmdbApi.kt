package com.application.alfaflix.data.remote.api

import com.application.alfaflix.data.remote.dto.FindResponseDto
import com.application.alfaflix.data.remote.dto.MovieDetailDto
import com.application.alfaflix.data.remote.dto.MovieListResponseDto
import com.application.alfaflix.data.remote.dto.ReviewsResponseDto
import com.application.alfaflix.data.remote.dto.VideosResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The slice of the TMDB v3 API this app uses.
 *
 * Authentication is not a parameter here: [TmdbAuthInterceptor] attaches the bearer token
 * to every request, so it cannot be forgotten at a call site and never clutters signatures.
 * Failing HTTP statuses surface as `retrofit2.HttpException`.
 */
interface TmdbApi {

    /** Movies ordered by popularity. TMDB serves at most 500 pages of 20 results. */
    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("page") page: Int,
        @Query("sort_by") sortBy: String = SORT_POPULARITY,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("include_video") includeVideo: Boolean = false,
        // Filters out obscure, barely-rated titles that would otherwise pad the grid.
        @Query("vote_count.gte") minVoteCount: Int = MIN_VOTE_COUNT,
        @Query("language") language: String = LANGUAGE,
    ): MovieListResponseDto

    @GET("search/movie")
    suspend fun searchMovies(
        @Query("query") query: String,
        @Query("page") page: Int,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("language") language: String = LANGUAGE,
    ): MovieListResponseDto

    @GET("movie/{id}")
    suspend fun movieDetail(
        @Path("id") movieId: Int,
        @Query("language") language: String = LANGUAGE,
    ): MovieDetailDto

    @GET("movie/{id}/reviews")
    suspend fun movieReviews(
        @Path("id") movieId: Int,
        @Query("page") page: Int = 1,
        @Query("language") language: String = LANGUAGE,
    ): ReviewsResponseDto

    /**
     * Videos for a movie.
     *
     * No `language` filter: TMDB then returns videos in every language, which avoids a
     * movie with an English trailer looking trailer-less because of its locale.
     */
    @GET("movie/{id}/videos")
    suspend fun movieVideos(@Path("id") movieId: Int): VideosResponseDto

    /** Resolves an IMDb ID to a TMDB movie — used only to migrate pre-TMDB favorites. */
    @GET("find/{externalId}")
    suspend fun findByImdbId(
        @Path("externalId") imdbId: String,
        @Query("external_source") externalSource: String = SOURCE_IMDB,
        @Query("language") language: String = LANGUAGE,
    ): FindResponseDto

    companion object {
        const val SORT_POPULARITY = "popularity.desc"
        const val MIN_VOTE_COUNT = 100
        const val LANGUAGE = "en-US"
        const val SOURCE_IMDB = "imdb_id"
    }
}
