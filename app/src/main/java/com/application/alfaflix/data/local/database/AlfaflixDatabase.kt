package com.application.alfaflix.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.application.alfaflix.data.local.dao.DiscoveryDao
import com.application.alfaflix.data.local.dao.FavoriteDao
import com.application.alfaflix.data.local.dao.LegacyFavoriteDao
import com.application.alfaflix.data.local.dao.MovieDetailDao
import com.application.alfaflix.data.local.dao.RecentlyViewedDao
import com.application.alfaflix.data.local.dao.ReviewDao
import com.application.alfaflix.data.local.entity.DiscoverMetaEntity
import com.application.alfaflix.data.local.entity.DiscoverMovieEntity
import com.application.alfaflix.data.local.entity.FavoriteMovieEntity
import com.application.alfaflix.data.local.entity.LegacyFavoriteEntity
import com.application.alfaflix.data.local.entity.MovieDetailEntity
import com.application.alfaflix.data.local.entity.RecentlyViewedEntity
import com.application.alfaflix.data.local.entity.ReviewEntity
import com.application.alfaflix.data.local.entity.ReviewMetaEntity

/**
 * The local database.
 *
 * Two kinds of data live here and they are not equivalent:
 *
 *  - **Owned data** — favorites and view history. Room is their single source of truth and
 *    losing them is data loss.
 *  - **Cached data** — discovery, movie details and reviews. Reconstructible from the API;
 *    losing them costs a refresh.
 *
 * Version 2 moved movie identity from IMDb IDs (OMDb) to TMDB IDs. See [Migrations].
 */
@Database(
    entities = [
        FavoriteMovieEntity::class,
        RecentlyViewedEntity::class,
        MovieDetailEntity::class,
        DiscoverMovieEntity::class,
        DiscoverMetaEntity::class,
        ReviewEntity::class,
        ReviewMetaEntity::class,
        LegacyFavoriteEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AlfaflixDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun recentlyViewedDao(): RecentlyViewedDao
    abstract fun movieDetailDao(): MovieDetailDao
    abstract fun discoveryDao(): DiscoveryDao
    abstract fun reviewDao(): ReviewDao
    abstract fun legacyFavoriteDao(): LegacyFavoriteDao

    companion object {
        const val NAME = "alfaflix.db"
    }
}
