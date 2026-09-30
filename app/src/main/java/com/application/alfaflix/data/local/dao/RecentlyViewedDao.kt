package com.application.alfaflix.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.application.alfaflix.data.local.entity.RecentlyViewedEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentlyViewedDao {

    @Query("SELECT * FROM recently_viewed ORDER BY viewedAt DESC")
    fun observeAll(): Flow<List<RecentlyViewedEntity>>

    /**
     * Inserts, or updates the existing row when the movie has been viewed before.
     *
     * Deduplication comes from the primary key on `movieId`: re-viewing a movie rewrites its
     * `viewedAt` rather than adding a second row.
     */
    @Upsert
    suspend fun upsert(entity: RecentlyViewedEntity)

    /**
     * Caps history at [keep] rows, deleting the oldest.
     *
     * Expressed as a single statement so the read and the delete cannot interleave with a
     * concurrent write, which a read-then-delete pair would allow.
     */
    @Query(
        """
        DELETE FROM recently_viewed
        WHERE movieId NOT IN (
            SELECT movieId FROM recently_viewed ORDER BY viewedAt DESC LIMIT :keep
        )
        """,
    )
    suspend fun trimTo(keep: Int)
}
