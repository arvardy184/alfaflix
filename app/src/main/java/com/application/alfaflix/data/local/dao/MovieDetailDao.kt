package com.application.alfaflix.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.application.alfaflix.data.local.entity.MovieDetailEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieDetailDao {

    @Query("SELECT * FROM movie_details WHERE movieId = :movieId")
    fun observe(movieId: Int): Flow<MovieDetailEntity?>

    @Query("SELECT cachedAt FROM movie_details WHERE movieId = :movieId")
    suspend fun cachedAt(movieId: Int): Long?

    @Upsert
    suspend fun upsert(entity: MovieDetailEntity)
}
