package com.application.alfaflix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.application.alfaflix.data.local.entity.ReviewEntity
import com.application.alfaflix.data.local.entity.ReviewMetaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewDao {

    @Query("SELECT * FROM movie_reviews WHERE movieId = :movieId ORDER BY position ASC")
    fun observeReviews(movieId: Int): Flow<List<ReviewEntity>>

    /** Null means "never fetched", as opposed to "fetched and there are none". */
    @Query("SELECT refreshedAt FROM review_meta WHERE movieId = :movieId")
    fun observeRefreshedAt(movieId: Int): Flow<Long?>

    @Query("SELECT refreshedAt FROM review_meta WHERE movieId = :movieId")
    suspend fun refreshedAt(movieId: Int): Long?

    /** Replaces a movie's reviews atomically so a reader never sees a half-written set. */
    @Transaction
    suspend fun replaceReviews(movieId: Int, reviews: List<ReviewEntity>, refreshedAt: Long) {
        deleteReviews(movieId)
        insertReviews(reviews)
        upsertMeta(ReviewMetaEntity(movieId, refreshedAt))
    }

    @Query("DELETE FROM movie_reviews WHERE movieId = :movieId")
    suspend fun deleteReviews(movieId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReviews(reviews: List<ReviewEntity>)

    @Upsert
    suspend fun upsertMeta(meta: ReviewMetaEntity)
}
