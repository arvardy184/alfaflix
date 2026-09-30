package com.application.alfaflix.data.local.di

import android.content.Context
import androidx.room.Room
import com.application.alfaflix.data.local.dao.DiscoveryDao
import com.application.alfaflix.data.local.dao.FavoriteDao
import com.application.alfaflix.data.local.dao.LegacyFavoriteDao
import com.application.alfaflix.data.local.dao.MovieDetailDao
import com.application.alfaflix.data.local.dao.RecentlyViewedDao
import com.application.alfaflix.data.local.dao.ReviewDao
import com.application.alfaflix.data.local.database.AlfaflixDatabase
import com.application.alfaflix.data.local.database.Migrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // Deliberately no fallbackToDestructiveMigration: favorites are user-owned data, and
    // silently dropping them on a schema change would be data loss.
    @Provides
    @Singleton
    fun providesDatabase(@ApplicationContext context: Context): AlfaflixDatabase =
        Room.databaseBuilder(context, AlfaflixDatabase::class.java, AlfaflixDatabase.NAME)
            .addMigrations(Migrations.MIGRATION_1_2)
            .build()

    @Provides
    fun providesFavoriteDao(database: AlfaflixDatabase): FavoriteDao = database.favoriteDao()

    @Provides
    fun providesRecentlyViewedDao(database: AlfaflixDatabase): RecentlyViewedDao =
        database.recentlyViewedDao()

    @Provides
    fun providesMovieDetailDao(database: AlfaflixDatabase): MovieDetailDao =
        database.movieDetailDao()

    @Provides
    fun providesDiscoveryDao(database: AlfaflixDatabase): DiscoveryDao = database.discoveryDao()

    @Provides
    fun providesReviewDao(database: AlfaflixDatabase): ReviewDao = database.reviewDao()

    @Provides
    fun providesLegacyFavoriteDao(database: AlfaflixDatabase): LegacyFavoriteDao =
        database.legacyFavoriteDao()
}
