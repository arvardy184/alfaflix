package com.application.alfaflix.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.application.alfaflix.data.local.database.AlfaflixDatabase
import com.application.alfaflix.data.local.database.Migrations
import com.application.alfaflix.data.local.entity.DiscoverMovieEntity
import com.application.alfaflix.data.local.entity.FavoriteMovieEntity
import com.application.alfaflix.data.local.entity.RecentlyViewedEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Verifies persistence against real on-device SQLite, on disk rather than in memory.
 *
 * This is what the JVM suite cannot prove: that data genuinely **survives the database being
 * closed and reopened** — what "favorites persist and discovery is readable after an app
 * restart" means to a user — and that the 1 → 2 migration works on the device's own SQLite.
 *
 * Run with an emulator or device attached:
 *
 *     ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class FavoritesPersistenceTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val databaseName = "persistence_test.db"

    private fun openDatabase(): AlfaflixDatabase =
        Room.databaseBuilder(context, AlfaflixDatabase::class.java, databaseName)
            .addMigrations(Migrations.MIGRATION_1_2)
            .build()

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun favoritesSurviveClosingAndReopeningTheDatabase() = runBlocking {
        val first = openDatabase()
        first.favoriteDao().insert(favorite(155, "The Dark Knight", addedAt = 1_000L))
        first.close()

        // A fresh instance, as if the process had been killed and relaunched.
        val second = openDatabase()
        val favorites = second.favoriteDao().observeAll().first()
        second.close()

        assertEquals(1, favorites.size)
        assertEquals("The Dark Knight", favorites.first().title)
        assertEquals(155, favorites.first().movieId)
    }

    @Test
    fun recentlyViewedDeduplicatesAcrossSessions() = runBlocking {
        val first = openDatabase()
        first.recentlyViewedDao().upsert(recentlyViewed(1, viewedAt = 1_000L))
        first.close()

        val second = openDatabase()
        second.recentlyViewedDao().upsert(recentlyViewed(1, viewedAt = 2_000L))
        val entries = second.recentlyViewedDao().observeAll().first()
        second.close()

        assertEquals("re-viewing must not create a second row", 1, entries.size)
        assertEquals(2_000L, entries.first().viewedAt)
    }

    @Test
    fun discoveryPagesAndBookkeepingSurviveReopening() = runBlocking {
        val first = openDatabase()
        val dao = first.discoveryDao()
        dao.replaceAll((1..3).map { discover(it) }, totalPages = 5, refreshedAt = 100L)
        val epoch = dao.meta()!!.epoch
        dao.appendPage(expectedEpoch = epoch, page = 2, movies = (4..6).map { discover(it) }, totalPages = 5)
        first.close()

        val second = openDatabase()
        val movies = second.discoveryDao().observeMovies().first()
        val meta = second.discoveryDao().meta()
        second.close()

        assertEquals(listOf(1, 2, 3, 4, 5, 6), movies.map { it.movieId })
        assertEquals(2, meta!!.loadedPages)
        assertEquals(5, meta.totalPages)
        assertEquals(100L, meta.refreshedAt)
    }

    @Test
    fun theDatabaseFileIsActuallyCreatedOnDisk() = runBlocking {
        val database = openDatabase()
        database.favoriteDao().insert(favorite(1, "Anything", addedAt = 1L))
        database.close()

        val file = File(context.getDatabasePath(databaseName).absolutePath)
        assertTrue("the database must be written to disk, not held in memory", file.exists())
    }

    @Test
    fun versionOneFavoritesSurviveTheMigrationOnDevice() = runBlocking {
        val legacy = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(databaseName), null)
        listOf(
            "CREATE TABLE IF NOT EXISTS `favorites` (`imdbId` TEXT NOT NULL, `title` TEXT NOT NULL, `year` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, `posterUrl` TEXT, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`imdbId`))",
            "CREATE TABLE IF NOT EXISTS `recently_viewed` (`imdbId` TEXT NOT NULL, `title` TEXT NOT NULL, `year` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, `posterUrl` TEXT, `viewedAt` INTEGER NOT NULL, PRIMARY KEY(`imdbId`))",
            "CREATE TABLE IF NOT EXISTS `movie_details` (`imdbId` TEXT NOT NULL, `title` TEXT NOT NULL, `year` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, `posterUrl` TEXT, `rated` TEXT, `released` TEXT, `runtimeMinutes` INTEGER, " +
                "`genres` TEXT NOT NULL, `director` TEXT, `writers` TEXT NOT NULL, `cast` TEXT NOT NULL, `plot` TEXT, " +
                "`languages` TEXT NOT NULL, `countries` TEXT NOT NULL, `awards` TEXT, `imdbRating` REAL, `imdbVotes` INTEGER, " +
                "`metascore` INTEGER, `ratings` TEXT NOT NULL, `boxOffice` TEXT, `cachedAt` INTEGER NOT NULL, PRIMARY KEY(`imdbId`))",
            "CREATE TABLE IF NOT EXISTS `collection_movies` (`collectionId` TEXT NOT NULL, `imdbId` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, `year` TEXT NOT NULL, `type` TEXT NOT NULL, `posterUrl` TEXT, `position` INTEGER NOT NULL, " +
                "PRIMARY KEY(`collectionId`, `imdbId`))",
            "CREATE INDEX IF NOT EXISTS `index_collection_movies_collectionId` ON `collection_movies` (`collectionId`)",
            "CREATE TABLE IF NOT EXISTS `collection_refresh` (`collectionId` TEXT NOT NULL, `refreshedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`collectionId`))",
            "INSERT INTO favorites VALUES ('tt0372784', 'Batman Begins', '2005', 'movie', NULL, 111)",
        ).forEach(legacy::execSQL)
        legacy.version = 1
        legacy.close()

        val migrated = openDatabase()
        val parked = migrated.legacyFavoriteDao().all()
        migrated.close()

        assertEquals(listOf("tt0372784"), parked.map { it.imdbId })
        assertEquals(111L, parked.single().addedAt)
    }

    private fun favorite(movieId: Int, title: String, addedAt: Long) = FavoriteMovieEntity(
        movieId = movieId,
        title = title,
        releaseDate = "2008-07-16",
        posterPath = null,
        voteAverage = 8.5,
        addedAt = addedAt,
    )

    private fun recentlyViewed(movieId: Int, viewedAt: Long) = RecentlyViewedEntity(
        movieId = movieId,
        title = "Movie $movieId",
        releaseDate = "2005-06-10",
        posterPath = null,
        voteAverage = null,
        viewedAt = viewedAt,
    )

    private fun discover(movieId: Int) = DiscoverMovieEntity(
        movieId = movieId,
        position = movieId,
        title = "Movie $movieId",
        releaseDate = null,
        posterPath = null,
        voteAverage = null,
    )
}
