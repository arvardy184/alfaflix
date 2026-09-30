package com.application.alfaflix.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.application.alfaflix.data.local.database.AlfaflixDatabase
import com.application.alfaflix.data.local.entity.FavoriteMovieEntity
import com.application.alfaflix.data.remote.MovieRemoteDataSource
import com.application.alfaflix.data.remote.dto.FindResponseDto
import com.application.alfaflix.testing.FakeTmdbApi
import com.application.alfaflix.testing.itemDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.UnknownHostException

/**
 * Favorites saved by the OMDb-era app must survive the move to TMDB identities. The database
 * migration parks them by IMDb ID; this class turns them into real favorites once TMDB can
 * resolve them.
 */
@RunWith(RobolectricTestRunner::class)
class LegacyFavoritesMigratorTest {

    private lateinit var database: AlfaflixDatabase
    private lateinit var migrator: LegacyFavoritesMigrator
    private val api = FakeTmdbApi()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AlfaflixDatabase::class.java,
        ).allowMainThreadQueries().build()
        migrator = LegacyFavoritesMigrator(
            legacyDao = database.legacyFavoriteDao(),
            favoriteDao = database.favoriteDao(),
            remoteDataSource = MovieRemoteDataSource(api, accessToken = "test-token"),
        )
    }

    @After
    fun tearDown() = database.close()

    private fun seedLegacy(imdbId: String, title: String, addedAt: Long) {
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO legacy_favorites (imdbId, title, year, posterUrl, addedAt) " +
                "VALUES ('$imdbId', '$title', '2005', NULL, $addedAt)",
        )
    }

    @Test
    fun `a resolvable legacy favorite becomes a TMDB favorite and is removed from the queue`() = runTest {
        seedLegacy("tt0372784", "Batman Begins", addedAt = 555)
        api.findHandler = { FindResponseDto(movieResults = listOf(itemDto(272, "Batman Begins"))) }

        migrator.migrate()

        val favorite = database.favoriteDao().observeAll().first().single()
        assertEquals(272, favorite.movieId)
        assertEquals("the user's original ordering is preserved", 555L, favorite.addedAt)
        assertTrue(migrator.isDone())
    }

    @Test
    fun `offline leaves the favorites queued for next time`() = runTest {
        seedLegacy("tt1", "One", 1)
        seedLegacy("tt2", "Two", 2)
        api.findHandler = { throw UnknownHostException() }

        migrator.migrate()

        assertFalse("nothing may be lost while offline", migrator.isDone())
        assertEquals(2, database.legacyFavoriteDao().count())
        assertEquals(emptyList<FavoriteMovieEntity>(), database.favoriteDao().observeAll().first())
    }

    @Test
    fun `a failure midway keeps the unresolved rows and keeps the resolved ones`() = runTest {
        seedLegacy("tt1", "One", 1)
        seedLegacy("tt2", "Two", 2)
        api.findHandler = { imdbId ->
            if (imdbId == "tt1") FindResponseDto(movieResults = listOf(itemDto(11))) else throw UnknownHostException()
        }

        migrator.migrate()

        assertEquals(listOf(11), database.favoriteDao().observeAll().first().map { it.movieId })
        assertEquals(listOf("tt2"), database.legacyFavoriteDao().all().map { it.imdbId })
    }

    @Test
    fun `a title TMDB does not know is dropped from the queue rather than retried forever`() = runTest {
        seedLegacy("tt-unknown", "Obscure", 1)
        api.findHandler = { FindResponseDto(movieResults = emptyList()) }

        migrator.migrate()

        assertTrue(migrator.isDone())
        assertEquals(emptyList<FavoriteMovieEntity>(), database.favoriteDao().observeAll().first())
    }

    @Test
    fun `migrating never overwrites a favorite the user already re-saved`() = runTest {
        database.favoriteDao().insert(
            FavoriteMovieEntity(272, "Re-saved", "2005-06-10", null, null, addedAt = 9_999),
        )
        seedLegacy("tt0372784", "Batman Begins", addedAt = 555)
        api.findHandler = { FindResponseDto(movieResults = listOf(itemDto(272))) }

        migrator.migrate()

        val favorite = database.favoriteDao().observeAll().first().single()
        assertEquals("Re-saved", favorite.title)
        assertEquals(9_999L, favorite.addedAt)
        assertTrue(migrator.isDone())
    }

    @Test
    fun `migrating with nothing queued is a no-op`() = runTest {
        migrator.migrate()

        assertTrue(migrator.isDone())
    }
}
