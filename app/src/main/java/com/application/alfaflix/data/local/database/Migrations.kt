package com.application.alfaflix.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Schema migrations.
 *
 * Favorites are user-owned data, so no migration here is destructive for them.
 */
object Migrations {

    /**
     * 1 → 2: OMDb (IMDb IDs) to TMDB (numeric movie IDs).
     *
     * - **Favorites are preserved.** A saved movie has only an IMDb ID, and TMDB IDs cannot
     *   be derived offline, so each row is parked in `legacy_favorites`. On the next online
     *   launch `LegacyFavoritesMigrator` asks TMDB to resolve the IMDb ID and moves the row
     *   into `favorites`.
     * - **View history is dropped.** It is a convenience list with no value once its IDs no
     *   longer match anything; it refills as movies are opened.
     * - **Cached OMDb data is dropped.** Detail and collection rows describe a different
     *   catalogue and are rebuilt from TMDB.
     */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `legacy_favorites` (`imdbId` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, `year` TEXT NOT NULL, `posterUrl` TEXT, " +
                    "`addedAt` INTEGER NOT NULL, PRIMARY KEY(`imdbId`))",
            )
            connection.execSQL(
                "INSERT OR IGNORE INTO `legacy_favorites` (`imdbId`, `title`, `year`, `posterUrl`, `addedAt`) " +
                    "SELECT `imdbId`, `title`, `year`, `posterUrl`, `addedAt` FROM `favorites`",
            )

            connection.execSQL("DROP TABLE `favorites`")
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `favorites` (`movieId` INTEGER NOT NULL, " +
                    "`title` TEXT NOT NULL, `releaseDate` TEXT, `posterPath` TEXT, " +
                    "`voteAverage` REAL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`movieId`))",
            )

            connection.execSQL("DROP TABLE `recently_viewed`")
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `recently_viewed` (`movieId` INTEGER NOT NULL, " +
                    "`title` TEXT NOT NULL, `releaseDate` TEXT, `posterPath` TEXT, " +
                    "`voteAverage` REAL, `viewedAt` INTEGER NOT NULL, PRIMARY KEY(`movieId`))",
            )

            connection.execSQL("DROP TABLE `movie_details`")
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `movie_details` (`movieId` INTEGER NOT NULL, " +
                    "`imdbId` TEXT, `title` TEXT NOT NULL, `tagline` TEXT, `overview` TEXT, " +
                    "`releaseDate` TEXT, `runtimeMinutes` INTEGER, `genres` TEXT NOT NULL, " +
                    "`voteAverage` REAL, `voteCount` INTEGER, `posterPath` TEXT, " +
                    "`backdropPath` TEXT, `status` TEXT, `cachedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`movieId`))",
            )

            connection.execSQL("DROP TABLE `collection_movies`")
            connection.execSQL("DROP TABLE `collection_refresh`")

            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `discover_movies` (`movieId` INTEGER NOT NULL, " +
                    "`position` INTEGER NOT NULL, `title` TEXT NOT NULL, `releaseDate` TEXT, " +
                    "`posterPath` TEXT, `voteAverage` REAL, PRIMARY KEY(`movieId`))",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `discover_meta` (`id` INTEGER NOT NULL, " +
                    "`loadedPages` INTEGER NOT NULL, `totalPages` INTEGER NOT NULL, " +
                    "`refreshedAt` INTEGER NOT NULL, `epoch` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `movie_reviews` (`movieId` INTEGER NOT NULL, " +
                    "`reviewId` TEXT NOT NULL, `author` TEXT NOT NULL, `rating` REAL, " +
                    "`content` TEXT NOT NULL, `createdAt` TEXT, `url` TEXT, " +
                    "`position` INTEGER NOT NULL, PRIMARY KEY(`movieId`, `reviewId`))",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_movie_reviews_movieId` ON `movie_reviews` (`movieId`)",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `review_meta` (`movieId` INTEGER NOT NULL, " +
                    "`refreshedAt` INTEGER NOT NULL, PRIMARY KEY(`movieId`))",
            )
        }
    }
}
