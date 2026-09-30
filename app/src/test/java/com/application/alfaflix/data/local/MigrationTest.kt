package com.application.alfaflix.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.application.alfaflix.data.local.database.AlfaflixDatabase
import com.application.alfaflix.data.local.database.Migrations
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The 1 → 2 migration moves the app from OMDb (IMDb IDs) to TMDB (numeric IDs).
 *
 * The version-1 database is built by hand from the exported `1.json` schema, filled with the
 * kind of data an installed OMDb-era app would hold, then opened through Room with the real
 * migration. Room validates the migrated schema against version 2's on open, so a mistake in
 * the migration SQL fails here rather than on a user's device.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test.db"
    private lateinit var migrated: AlfaflixDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
        createVersion1Database()
    }

    @After
    fun tearDown() {
        if (::migrated.isInitialized) migrated.close()
        context.deleteDatabase(dbName)
    }

    private fun createVersion1Database() {
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName), null)
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
            "INSERT INTO favorites VALUES ('tt0372784', 'Batman Begins', '2005', 'movie', 'http://p/1.jpg', 111)",
            "INSERT INTO favorites VALUES ('tt0468569', 'The Dark Knight', '2008', 'movie', NULL, 222)",
            "INSERT INTO recently_viewed VALUES ('tt0372784', 'Batman Begins', '2005', 'movie', NULL, 333)",
            "INSERT INTO collection_movies VALUES ('caped_crusaders', 'tt0372784', 'Batman Begins', '2005', 'movie', NULL, 0)",
        ).forEach(db::execSQL)
        db.version = 1
        db.close()
    }

    private fun migrate(): AlfaflixDatabase =
        Room.databaseBuilder(context, AlfaflixDatabase::class.java, dbName)
            .addMigrations(Migrations.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
            .also {
                migrated = it
                // Opening triggers the migration and Room's schema validation.
                it.openHelper.writableDatabase
            }

    @Test
    fun `the migrated database passes Room's schema validation`() {
        migrate()
    }

    @Test
    fun `favorites are preserved in the legacy queue with their original order and data`() = runTest {
        val db = migrate()

        val legacy = db.legacyFavoriteDao().all()

        assertEquals(listOf("tt0372784", "tt0468569"), legacy.map { it.imdbId })
        assertEquals(listOf("Batman Begins", "The Dark Knight"), legacy.map { it.title })
        assertEquals(listOf(111L, 222L), legacy.map { it.addedAt })
        assertEquals("2005", legacy.first().year)
        assertEquals("http://p/1.jpg", legacy.first().posterUrl)
    }

    @Test
    fun `the new favorites table starts empty and is usable`() = runTest {
        val db = migrate()

        assertEquals(emptyList<Any>(), db.favoriteDao().observeAll().first())
        db.favoriteDao().insert(
            com.application.alfaflix.data.local.entity.FavoriteMovieEntity(
                movieId = 272, title = "Batman Begins", releaseDate = "2005-06-10",
                posterPath = "/p.jpg", voteAverage = 7.7, addedAt = 1,
            ),
        )
        assertEquals(listOf(272), db.favoriteDao().observeIds().first())
    }

    @Test
    fun `OMDb-era caches and history are dropped and the new tables exist`() = runTest {
        val db = migrate()

        assertEquals(emptyList<Any>(), db.recentlyViewedDao().observeAll().first())
        assertNull(db.movieDetailDao().cachedAt(1))
        assertNull("discovery was never loaded", db.discoveryDao().meta())
        assertEquals(emptyList<Any>(), db.discoveryDao().observeMovies().first())
        assertEquals(emptyList<Any>(), db.reviewDao().observeReviews(1).first())
    }
}
