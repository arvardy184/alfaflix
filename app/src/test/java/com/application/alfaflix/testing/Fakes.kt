package com.application.alfaflix.testing

import com.application.alfaflix.core.network.NetworkMonitor
import com.application.alfaflix.core.time.TimeProvider
import com.application.alfaflix.data.remote.api.TmdbApi
import com.application.alfaflix.data.remote.dto.FindResponseDto
import com.application.alfaflix.data.remote.dto.GenreDto
import com.application.alfaflix.data.remote.dto.MovieDetailDto
import com.application.alfaflix.data.remote.dto.MovieListItemDto
import com.application.alfaflix.data.remote.dto.MovieListResponseDto
import com.application.alfaflix.data.remote.dto.ReviewDto
import com.application.alfaflix.data.remote.dto.ReviewsResponseDto
import com.application.alfaflix.data.remote.dto.VideoDto
import com.application.alfaflix.data.remote.dto.VideosResponseDto
import com.application.alfaflix.domain.model.Movie
import com.application.alfaflix.domain.model.MovieDetail
import com.application.alfaflix.domain.model.Review
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A clock the test controls, so cache-freshness behaviour is deterministic. */
class FakeTimeProvider(var now: Long = 1_000_000L) : TimeProvider {
    override fun nowMillis(): Long = now

    fun advanceBy(millis: Long) {
        now += millis
    }
}

class FakeNetworkMonitor(initiallyOnline: Boolean = true) : NetworkMonitor {
    private val online = MutableStateFlow(initiallyOnline)
    override val isOnline: Flow<Boolean> = online

    fun setOnline(value: Boolean) {
        online.value = value
    }
}

/**
 * A scriptable TMDB API.
 *
 * Faking at the Retrofit-interface boundary rather than at the data source keeps the real
 * response-mapping and error-classification logic under test. Handlers are suspend lambdas,
 * so a test can stall a call to control interleaving or throw to simulate a failure.
 */
class FakeTmdbApi : TmdbApi {

    var discoverHandler: suspend (page: Int) -> MovieListResponseDto = { page -> listDto(page, totalPages = 1) }
    var searchHandler: suspend (query: String, page: Int) -> MovieListResponseDto =
        { _, page -> listDto(page, totalPages = 0) }
    var detailHandler: suspend (id: Int) -> MovieDetailDto = { id -> detailDto(id) }
    var reviewsHandler: suspend (id: Int) -> ReviewsResponseDto = { ReviewsResponseDto(results = emptyList()) }
    var videosHandler: suspend (id: Int) -> VideosResponseDto = { VideosResponseDto(results = emptyList()) }
    var findHandler: suspend (imdbId: String) -> FindResponseDto = { FindResponseDto(movieResults = emptyList()) }

    val discoverPages = mutableListOf<Int>()
    val searchCalls = mutableListOf<Pair<String, Int>>()
    var detailCallCount = 0
        private set
    var reviewsCallCount = 0
        private set
    var videosCallCount = 0
        private set

    override suspend fun discoverMovies(
        page: Int,
        sortBy: String,
        includeAdult: Boolean,
        includeVideo: Boolean,
        minVoteCount: Int,
        language: String,
    ): MovieListResponseDto {
        discoverPages += page
        return discoverHandler(page)
    }

    override suspend fun searchMovies(
        query: String,
        page: Int,
        includeAdult: Boolean,
        language: String,
    ): MovieListResponseDto {
        searchCalls += query to page
        return searchHandler(query, page)
    }

    override suspend fun movieDetail(movieId: Int, language: String): MovieDetailDto {
        detailCallCount++
        return detailHandler(movieId)
    }

    override suspend fun movieReviews(movieId: Int, page: Int, language: String): ReviewsResponseDto {
        reviewsCallCount++
        return reviewsHandler(movieId)
    }

    override suspend fun movieVideos(movieId: Int): VideosResponseDto {
        videosCallCount++
        return videosHandler(movieId)
    }

    override suspend fun findByImdbId(imdbId: String, externalSource: String, language: String): FindResponseDto =
        findHandler(imdbId)
}

// region DTO builders

fun itemDto(id: Int, title: String = "Movie $id") = MovieListItemDto(
    id = id,
    title = title,
    releaseDate = "2005-06-10",
    posterPath = "/poster$id.jpg",
    voteAverage = 7.5,
    voteCount = 500,
)

fun listDto(page: Int, totalPages: Int, vararg ids: Int) = MovieListResponseDto(
    page = page,
    results = ids.map { itemDto(it) },
    totalPages = totalPages,
    totalResults = totalPages * 20,
)

fun detailDto(id: Int, title: String = "Movie $id") = MovieDetailDto(
    id = id,
    imdbId = "tt$id",
    title = title,
    tagline = "A tagline",
    overview = "An overview.",
    releaseDate = "2005-06-10",
    runtime = 140,
    genres = listOf(GenreDto(1, "Action"), GenreDto(2, "Drama")),
    voteAverage = 8.2,
    voteCount = 1234,
    posterPath = "/poster$id.jpg",
    backdropPath = "/backdrop$id.jpg",
    status = "Released",
)

fun reviewDto(id: String, author: String = "reviewer-$id", content: String = "Great film.") =
    ReviewDto(id = id, author = author, content = content, createdAt = "2021-06-23T15:58:09.183Z")

fun videoDto(
    key: String,
    type: String = "Trailer",
    official: Boolean = true,
    site: String = "YouTube",
    publishedAt: String? = "2020-01-01T00:00:00.000Z",
) = VideoDto(key = key, name = "$type $key", site = site, type = type, official = official, publishedAt = publishedAt)

// endregion

// region domain builders

fun movie(id: Int, title: String = "Movie $id") = Movie(
    id = id,
    title = title,
    releaseDate = "2005-06-10",
    posterPath = "/poster$id.jpg",
    voteAverage = 7.5,
)

fun movieDetail(id: Int, title: String = "Movie $id") = MovieDetail(
    id = id,
    imdbId = "tt$id",
    title = title,
    tagline = "A tagline",
    overview = "An overview.",
    releaseDate = "2005-06-10",
    runtimeMinutes = 140,
    genres = listOf("Action", "Drama"),
    voteAverage = 8.2,
    voteCount = 1234,
    posterPath = "/poster$id.jpg",
    backdropPath = "/backdrop$id.jpg",
    status = "Released",
)

fun review(id: String, author: String = "reviewer-$id", content: String = "Great film.") = Review(
    id = id,
    author = author,
    rating = 8.0,
    content = content,
    createdAt = "2021-06-23T15:58:09.183Z",
    url = null,
)

// endregion
