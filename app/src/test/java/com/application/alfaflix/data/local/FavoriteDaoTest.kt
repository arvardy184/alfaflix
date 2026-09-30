package com.application.alfaflix.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.application.alfaflix.data.local.database.AlfaflixDatabase
import com.application.alfaflix.data.local.entity.FavoriteMovieEntity
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

/**
 * Favorites are the app's single source of truth for favorite state — every screen derives
 * its heart icons from [FavoriteDao.observeIds]. These tests cover that contract.
 */
@RunWith(RobolectricTestRunner::class)
class FavoriteDaoTest {

    private lateinit var database: AlfaflixDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AlfaflixDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `an added favorite is observable by id`() = runTest {
        val dao = database.favoriteDao()

        dao.insert(entity(155, "The Dark Knight", addedAt = 1_000))

        assertEquals(listOf(155), dao.observeIds().first())
        assertTrue(dao.exists(155))
    }

    @Test
    fun `removing a favorite removes it from the observed ids`() = runTest {
        val dao = database.favoriteDao()
        dao.insert(entity(1, "Kept", addedAt = 1_000))
        dao.insert(entity(2, "Removed", addedAt = 2_000))

        dao.delete(2)

        assertEquals(listOf(1), dao.observeIds().first())
        assertFalse(dao.exists(2))
    }

    @Test
    fun `favorites are listed most recently added first`() = runTest {
        val dao = database.favoriteDao()

        dao.insert(entity(1, "Oldest", addedAt = 100))
        dao.insert(entity(2, "Newest", addedAt = 300))
        dao.insert(entity(3, "Middle", addedAt = 200))

        assertEquals(listOf(2, 3, 1), dao.observeAll().first().map { it.movieId })
    }

    @Test
    fun `favoriting the same movie twice does not duplicate it`() = runTest {
        val dao = database.favoriteDao()

        dao.insert(entity(1, "Batman Begins", addedAt = 100))
        dao.insert(entity(1, "Batman Begins", addedAt = 200))

        assertEquals(1, dao.observeAll().first().size)
    }

    @Test
    fun `insertIfAbsent never overwrites an existing favorite`() = runTest {
        val dao = database.favoriteDao()
        dao.insert(entity(1, "Original", addedAt = 100))

        dao.insertIfAbsent(entity(1, "Replacement", addedAt = 999))

        val stored = dao.observeAll().first().single()
        assertEquals("Original", stored.title)
        assertEquals(100L, stored.addedAt)
    }

    @Test
    fun `exists reports false for an unknown movie`() = runTest {
        assertFalse(database.favoriteDao().exists(-1))
    }

    private fun entity(movieId: Int, title: String, addedAt: Long) = FavoriteMovieEntity(
        movieId = movieId,
        title = title,
        releaseDate = "2008-07-16",
        posterPath = null,
        voteAverage = 8.5,
        addedAt = addedAt,
    )
}
