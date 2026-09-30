package com.application.alfaflix.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.application.alfaflix.data.local.database.AlfaflixDatabase
import com.application.alfaflix.data.local.entity.RecentlyViewedEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * History has two properties a user would notice: opening the same movie twice must not
 * create two rows, and the list must not grow without bound. Both are enforced here against
 * a real SQLite database.
 */
@RunWith(RobolectricTestRunner::class)
class RecentlyViewedDaoTest {

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
    fun `re-viewing a movie updates it in place instead of duplicating it`() = runTest {
        val dao = database.recentlyViewedDao()

        dao.upsert(entity(1, "Batman Begins", viewedAt = 1_000))
        dao.upsert(entity(2, "Interstellar", viewedAt = 2_000))
        dao.upsert(entity(1, "Batman Begins", viewedAt = 3_000))

        val rows = dao.observeAll().first()

        assertEquals("re-viewing must not create a second row", 2, rows.size)
        assertEquals("the re-viewed movie must move to the top", 1, rows.first().movieId)
        assertEquals(3_000L, rows.first().viewedAt)
    }

    @Test
    fun `rows are ordered most recently viewed first`() = runTest {
        val dao = database.recentlyViewedDao()

        dao.upsert(entity(1, "First", viewedAt = 100))
        dao.upsert(entity(2, "Second", viewedAt = 300))
        dao.upsert(entity(3, "Third", viewedAt = 200))

        assertEquals(listOf(2, 3, 1), dao.observeAll().first().map { it.movieId })
    }

    @Test
    fun `trim keeps exactly the newest rows and discards the oldest`() = runTest {
        val dao = database.recentlyViewedDao()
        repeat(25) { index ->
            dao.upsert(entity(index, "Movie $index", viewedAt = index.toLong()))
        }

        dao.trimTo(20)

        val rows = dao.observeAll().first()
        assertEquals(20, rows.size)
        assertEquals(24, rows.first().movieId)
        assertTrue("the oldest entries must be gone", rows.none { it.movieId == 0 })
        assertTrue(rows.none { it.movieId == 4 })
        assertTrue("the newest entries must survive", rows.any { it.movieId == 5 })
    }

    @Test
    fun `trim is a no-op when the table is under the limit`() = runTest {
        val dao = database.recentlyViewedDao()
        repeat(3) { dao.upsert(entity(it, "Movie $it", viewedAt = it.toLong())) }

        dao.trimTo(20)

        assertEquals(3, dao.observeAll().first().size)
    }

    private fun entity(movieId: Int, title: String, viewedAt: Long) = RecentlyViewedEntity(
        movieId = movieId,
        title = title,
        releaseDate = "2005-06-10",
        posterPath = null,
        voteAverage = null,
        viewedAt = viewedAt,
    )
}
