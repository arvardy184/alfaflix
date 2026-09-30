package com.application.alfaflix.data.remote

import com.application.alfaflix.core.di.ApiKey
import com.application.alfaflix.core.result.AppError
import com.application.alfaflix.core.result.AppResult
import com.application.alfaflix.data.remote.api.TmdbApi
import com.application.alfaflix.data.remote.mapper.toDomainOrNull
import com.application.alfaflix.data.remote.mapper.toMoviePage
import com.application.alfaflix.data.remote.mapper.toReviews
import com.application.alfaflix.data.remote.mapper.toVideos
import com.application.alfaflix.domain.model.Movie
import com.application.alfaflix.domain.model.MovieDetail
import com.application.alfaflix.domain.model.MoviePage
import com.application.alfaflix.domain.model.MovieVideo
import com.application.alfaflix.domain.model.Review
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only place that talks to TMDB.
 *
 * Its job is to turn every way a request can go wrong into one typed [AppResult]: thrown
 * transport exceptions and failing HTTP statuses are both mapped to [AppError] by cause. No
 * Retrofit or OkHttp type escapes this class.
 *
 * Retrofit's suspend functions are already main-safe (OkHttp dispatches on its own pool),
 * so no explicit dispatcher switch is needed here.
 */
@Singleton
class MovieRemoteDataSource @Inject constructor(
    private val api: TmdbApi,
    @param:ApiKey private val accessToken: String,
) {

    suspend fun discover(page: Int): AppResult<MoviePage> = call {
        AppResult.Success(api.discoverMovies(page = page).toMoviePage(page))
    }

    suspend fun search(query: String, page: Int): AppResult<MoviePage> = call {
        AppResult.Success(api.searchMovies(query = query, page = page).toMoviePage(page))
    }

    suspend fun detail(movieId: Int): AppResult<MovieDetail> = call {
        // A record we cannot map has no identity or title, so it cannot be shown.
        api.movieDetail(movieId).toDomainOrNull()
            ?.let { AppResult.Success(it) }
            ?: AppResult.Failure(AppError.NotFound)
    }

    suspend fun reviews(movieId: Int): AppResult<List<Review>> = call {
        AppResult.Success(api.movieReviews(movieId).toReviews())
    }

    suspend fun videos(movieId: Int): AppResult<List<MovieVideo>> = call {
        AppResult.Success(api.movieVideos(movieId).toVideos())
    }

    /** The TMDB movie for an IMDb ID, or null when TMDB has no match. */
    suspend fun findByImdbId(imdbId: String): AppResult<Movie?> = call {
        AppResult.Success(api.findByImdbId(imdbId).movieResults.orEmpty().firstNotNullOfOrNull { it.toDomainOrNull() })
    }

    /**
     * Runs a request, failing fast without a token so a fresh clone gets a self-explanatory
     * error instead of an opaque HTTP 401.
     */
    private suspend fun <T> call(block: suspend () -> AppResult<T>): AppResult<T> {
        if (accessToken.isBlank()) return AppResult.Failure(AppError.MissingApiKey)
        return safeCall(block)
    }
}

/**
 * Maps transport and HTTP failures to [AppError].
 *
 * [CancellationException] is rethrown first and deliberately: swallowing it would break
 * structured concurrency, so a cancelled request would look like a failed one and leave
 * stale state behind. Ordering matters throughout — [SocketTimeoutException] and
 * [UnknownHostException] both extend [IOException], so they must be caught before it.
 */
private suspend fun <T> safeCall(block: suspend () -> AppResult<T>): AppResult<T> = try {
    block()
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (_: UnknownHostException) {
    AppResult.Failure(AppError.Offline)
} catch (_: ConnectException) {
    AppResult.Failure(AppError.Offline)
} catch (_: SocketTimeoutException) {
    AppResult.Failure(AppError.Timeout)
} catch (http: HttpException) {
    AppResult.Failure(http.code().toAppError())
} catch (_: IOException) {
    AppResult.Failure(AppError.Offline)
} catch (_: Exception) {
    AppResult.Failure(AppError.Unknown)
}

private fun Int.toAppError(): AppError = when (this) {
    HTTP_UNAUTHORIZED -> AppError.InvalidApiKey
    HTTP_NOT_FOUND -> AppError.NotFound
    HTTP_TOO_MANY_REQUESTS -> AppError.RateLimited
    else -> AppError.Server(this)
}

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_NOT_FOUND = 404
private const val HTTP_TOO_MANY_REQUESTS = 429
