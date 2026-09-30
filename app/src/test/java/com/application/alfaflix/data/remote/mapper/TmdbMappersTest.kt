package com.application.alfaflix.data.remote.mapper

import com.application.alfaflix.data.remote.dto.AuthorDetailsDto
import com.application.alfaflix.data.remote.dto.GenreDto
import com.application.alfaflix.data.remote.dto.MovieDetailDto
import com.application.alfaflix.data.remote.dto.MovieListItemDto
import com.application.alfaflix.data.remote.dto.MovieListResponseDto
import com.application.alfaflix.data.remote.dto.ReviewDto
import com.application.alfaflix.data.remote.dto.ReviewsResponseDto
import com.application.alfaflix.data.remote.dto.VideoDto
import com.application.alfaflix.data.remote.dto.VideosResponseDto
import com.application.alfaflix.testing.detailDto
import com.application.alfaflix.testing.itemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TMDB signals "no value" with empty strings, zeros and nulls. These tests pin down that each
 * becomes a genuine null once, in the mapper, and that malformed input never throws.
 */
class TmdbMappersTest {

    // region list items

    @Test
    fun `a complete list item maps every field`() {
        val movie = itemDto(603, "The Matrix").toDomainOrNull()

        assertNotNull(movie)
        assertEquals(603, movie!!.id)
        assertEquals("The Matrix", movie.title)
        assertEquals("2005-06-10", movie.releaseDate)
        assertEquals("2005", movie.releaseYear)
        assertEquals("/poster603.jpg", movie.posterPath)
        assertEquals(7.5, movie.voteAverage!!, 0.0)
    }

    @Test
    fun `an item without an id or a title is dropped`() {
        assertNull(MovieListItemDto(id = null, title = "No id").toDomainOrNull())
        assertNull(MovieListItemDto(id = 1, title = null).toDomainOrNull())
        assertNull(MovieListItemDto(id = 1, title = "   ").toDomainOrNull())
    }

    @Test
    fun `empty release date and missing poster become null`() {
        val movie = MovieListItemDto(id = 1, title = "Upcoming", releaseDate = "", posterPath = null).toDomainOrNull()!!

        assertNull(movie.releaseDate)
        assertNull(movie.releaseYear)
        assertNull(movie.posterPath)
    }

    @Test
    fun `an unrated movie has no score rather than zero`() {
        val movie = MovieListItemDto(id = 1, title = "Unrated", voteAverage = 0.0).toDomainOrNull()!!

        assertNull(movie.voteAverage)
    }

    @Test
    fun `a page drops unusable records and collapses repeated ids`() {
        val page = MovieListResponseDto(
            page = 2,
            results = listOf(itemDto(1), MovieListItemDto(id = null), itemDto(1), itemDto(2)),
            totalPages = 7,
        ).toMoviePage(requestedPage = 2)

        assertEquals(listOf(1, 2), page.movies.map { it.id })
        assertEquals(2, page.page)
        assertEquals(7, page.totalPages)
        assertTrue(page.hasMore)
    }

    @Test
    fun `the last page reports no more pages`() {
        val page = MovieListResponseDto(page = 3, results = listOf(itemDto(1)), totalPages = 3).toMoviePage(3)

        assertFalse(page.hasMore)
    }

    @Test
    fun `a response missing every field still maps to an empty page`() {
        val page = MovieListResponseDto().toMoviePage(requestedPage = 1)

        assertEquals(emptyList<Any>(), page.movies)
        assertEquals(1, page.page)
        assertEquals(0, page.totalPages)
        assertFalse(page.hasMore)
    }

    // endregion

    // region detail

    @Test
    fun `a complete detail record maps every field`() {
        val detail = detailDto(603, "The Matrix").toDomainOrNull()!!

        assertEquals(603, detail.id)
        assertEquals("tt603", detail.imdbId)
        assertEquals("A tagline", detail.tagline)
        assertEquals("An overview.", detail.overview)
        assertEquals(140, detail.runtimeMinutes)
        assertEquals(listOf("Action", "Drama"), detail.genres)
        assertEquals(8.2, detail.voteAverage!!, 0.0)
        assertEquals(1234, detail.voteCount)
        assertEquals("/backdrop603.jpg", detail.backdropPath)
    }

    @Test
    fun `TMDB's empty conventions become nulls`() {
        val detail = MovieDetailDto(
            id = 9,
            title = "Sparse",
            tagline = "",
            overview = "  ",
            releaseDate = "",
            runtime = 0,
            voteAverage = 0.0,
            voteCount = 0,
            imdbId = "",
            genres = listOf(GenreDto(1, ""), GenreDto(2, "Drama"), GenreDto(3, null)),
        ).toDomainOrNull()!!

        assertNull(detail.tagline)
        assertNull(detail.overview)
        assertNull(detail.releaseDate)
        assertNull(detail.runtimeMinutes)
        assertNull(detail.voteAverage)
        assertNull(detail.voteCount)
        assertNull(detail.imdbId)
        assertEquals("only real genre names survive", listOf("Drama"), detail.genres)
    }

    @Test
    fun `a detail record without an id or title cannot be shown`() {
        assertNull(MovieDetailDto(id = null, title = "x").toDomainOrNull())
        assertNull(MovieDetailDto(id = 1, title = null).toDomainOrNull())
    }

    @Test
    fun `detail to list projection keeps identity`() {
        val movie = detailDto(5).toDomainOrNull()!!.toMovie()

        assertEquals(5, movie.id)
        assertEquals("/poster5.jpg", movie.posterPath)
    }

    // endregion

    // region reviews

    @Test
    fun `a review prefers the display name, then username, then author`() {
        fun review(details: AuthorDetailsDto?, author: String?) = ReviewDto(
            id = "r", author = author, authorDetails = details, content = "Text",
        ).toDomainOrNull()!!.author

        assertEquals("Display", review(AuthorDetailsDto(name = "Display", username = "user"), "author"))
        assertEquals("user", review(AuthorDetailsDto(name = "", username = "user"), "author"))
        assertEquals("author", review(null, "author"))
        assertEquals(ANONYMOUS_AUTHOR, review(null, null))
    }

    @Test
    fun `a review keeps its own score and drops a zero one`() {
        fun score(rating: Double?) = ReviewDto(
            id = "r", content = "Text", authorDetails = AuthorDetailsDto(rating = rating),
        ).toDomainOrNull()!!.rating

        assertEquals(9.0, score(9.0)!!, 0.0)
        assertNull(score(0.0))
        assertNull(score(null))
    }

    @Test
    fun `reviews without an id or without text are dropped`() {
        val reviews = ReviewsResponseDto(
            results = listOf(
                ReviewDto(id = "a", content = "Real"),
                ReviewDto(id = null, content = "No id"),
                ReviewDto(id = "b", content = "  "),
                ReviewDto(id = "a", content = "Duplicate id"),
            ),
        ).toReviews()

        assertEquals(listOf("a"), reviews.map { it.id })
    }

    @Test
    fun `a null review list maps to empty`() {
        assertEquals(emptyList<Any>(), ReviewsResponseDto(results = null).toReviews())
    }

    // endregion

    // region videos

    @Test
    fun `videos map with defaults for missing fields and drop keyless entries`() {
        val videos = VideosResponseDto(
            results = listOf(
                VideoDto(key = "abc", name = null, site = null, type = null, official = null),
                VideoDto(key = null, name = "No key"),
                VideoDto(key = "def", name = "Real", site = "YouTube", type = "Trailer", official = true),
            ),
        ).toVideos()

        assertEquals(listOf("abc", "def"), videos.map { it.key })
        assertFalse("missing official flag means not official", videos[0].official)
        assertEquals("", videos[0].site)
        assertTrue(videos[1].official)
    }

    // endregion
}
