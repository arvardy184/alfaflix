package com.application.alfaflix.data.remote

import com.application.alfaflix.core.result.AppError
import com.application.alfaflix.core.result.AppResult
import com.application.alfaflix.testing.FakeTmdbApi
import com.application.alfaflix.testing.detailDto
import com.application.alfaflix.testing.listDto
import com.application.alfaflix.testing.reviewDto
import com.application.alfaflix.testing.videoDto
import com.application.alfaflix.data.remote.dto.FindResponseDto
import com.application.alfaflix.data.remote.dto.MovieDetailDto
import com.application.alfaflix.data.remote.dto.ReviewsResponseDto
import com.application.alfaflix.data.remote.dto.VideosResponseDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * The data source turns every way a TMDB request can go wrong — transport exceptions and
 * failing HTTP statuses — into one typed [AppResult], and never lets a Retrofit type escape.
 */
class MovieRemoteDataSourceTest {

    private val api = FakeTmdbApi()
    private val dataSource = MovieRemoteDataSource(api = api, accessToken = "test-token")

    private fun http(code: Int) = HttpException(
        Response.error<Any>(code, "{}".toResponseBody("application/json".toMediaType())),
    )

    // region success mapping

    @Test
    fun `a discover page maps movies and pagination`() = runTest {
        api.discoverHandler = { listDto(page = 2, totalPages = 40, 10, 11) }

        val page = (dataSource.discover(page = 2) as AppResult.Success).data

        assertEquals(listOf(10, 11), page.movies.map { it.id })
        assertEquals(2, page.page)
        assertEquals(40, page.totalPages)
        assertEquals(listOf(2), api.discoverPages)
    }

    @Test
    fun `a search matching nothing is a success with an empty page, not a failure`() = runTest {
        api.searchHandler = { _, page -> listDto(page, totalPages = 0) }

        val result = dataSource.search("zzzzzzzz", page = 1)

        assertTrue("expected Success, got $result", result is AppResult.Success)
        assertEquals(emptyList<Any>(), (result as AppResult.Success).data.movies)
    }

    @Test
    fun `detail maps to the domain model`() = runTest {
        api.detailHandler = { detailDto(it, "The Matrix") }

        val detail = (dataSource.detail(603) as AppResult.Success).data

        assertEquals("The Matrix", detail.title)
        assertEquals(603, detail.id)
    }

    @Test
    fun `an unmappable detail record is reported as not found`() = runTest {
        api.detailHandler = { MovieDetailDto(id = null, title = null) }

        assertEquals(AppResult.Failure(AppError.NotFound), dataSource.detail(1))
    }

    @Test
    fun `reviews and videos map`() = runTest {
        api.reviewsHandler = { ReviewsResponseDto(results = listOf(reviewDto("a"), reviewDto("b"))) }
        api.videosHandler = { VideosResponseDto(results = listOf(videoDto("k1"))) }

        assertEquals(2, (dataSource.reviews(1) as AppResult.Success).data.size)
        assertEquals("k1", (dataSource.videos(1) as AppResult.Success).data.single().key)
    }

    @Test
    fun `find resolves an imdb id or reports no match`() = runTest {
        api.findHandler = { FindResponseDto(movieResults = listOf(com.application.alfaflix.testing.itemDto(77))) }
        assertEquals(77, (dataSource.findByImdbId("tt1") as AppResult.Success).data?.id)

        api.findHandler = { FindResponseDto(movieResults = emptyList()) }
        assertNull((dataSource.findByImdbId("tt2") as AppResult.Success).data)
    }

    // endregion

    // region HTTP failures

    @Test
    fun `401 means the token was rejected`() = runTest {
        api.discoverHandler = { throw http(401) }

        assertEquals(AppResult.Failure(AppError.InvalidApiKey), dataSource.discover(1))
    }

    @Test
    fun `404 means not found`() = runTest {
        api.detailHandler = { throw http(404) }

        assertEquals(AppResult.Failure(AppError.NotFound), dataSource.detail(1))
    }

    @Test
    fun `429 means rate limited`() = runTest {
        api.discoverHandler = { throw http(429) }

        assertEquals(AppResult.Failure(AppError.RateLimited), dataSource.discover(1))
    }

    @Test
    fun `other statuses preserve their code`() = runTest {
        api.discoverHandler = { throw http(503) }

        assertEquals(AppResult.Failure(AppError.Server(503)), dataSource.discover(1))
    }

    // endregion

    // region transport failures

    @Test
    fun `an unknown host means offline`() = runTest {
        api.discoverHandler = { throw UnknownHostException("no dns") }

        assertEquals(AppResult.Failure(AppError.Offline), dataSource.discover(1))
    }

    @Test
    fun `a socket timeout is distinguished from being offline`() = runTest {
        api.discoverHandler = { throw SocketTimeoutException("too slow") }

        assertEquals(AppResult.Failure(AppError.Timeout), dataSource.discover(1))
    }

    @Test
    fun `any other io failure is treated as offline`() = runTest {
        api.discoverHandler = { throw IOException("connection reset") }

        assertEquals(AppResult.Failure(AppError.Offline), dataSource.discover(1))
    }

    @Test
    fun `an unexpected exception becomes a generic failure`() = runTest {
        api.discoverHandler = { throw IllegalStateException("bad json") }

        assertEquals(AppResult.Failure(AppError.Unknown), dataSource.discover(1))
    }

    @Test
    fun `cancellation propagates instead of being swallowed as a failure`() = runTest {
        api.discoverHandler = { throw CancellationException("cancelled") }

        var propagated = false
        try {
            dataSource.discover(1)
        } catch (_: CancellationException) {
            propagated = true
        }

        assertTrue(
            "CancellationException must not be converted into an AppResult.Failure — " +
                "swallowing it breaks structured concurrency",
            propagated,
        )
    }

    // endregion

    @Test
    fun `a blank token fails fast without issuing a request`() = runTest {
        val unconfigured = MovieRemoteDataSource(api = api, accessToken = "")

        assertEquals(AppResult.Failure(AppError.MissingApiKey), unconfigured.discover(1))
        assertEquals(AppResult.Failure(AppError.MissingApiKey), unconfigured.search("x", 1))
        assertEquals(AppResult.Failure(AppError.MissingApiKey), unconfigured.detail(1))
        assertEquals(AppResult.Failure(AppError.MissingApiKey), unconfigured.reviews(1))
        assertEquals(AppResult.Failure(AppError.MissingApiKey), unconfigured.videos(1))
        assertEquals(emptyList<Int>(), api.discoverPages)
        assertEquals(0, api.detailCallCount + api.reviewsCallCount + api.videosCallCount)
    }
}
