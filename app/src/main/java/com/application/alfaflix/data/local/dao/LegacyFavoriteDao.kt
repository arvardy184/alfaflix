package com.application.alfaflix.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.application.alfaflix.data.local.entity.LegacyFavoriteEntity

@Dao
interface LegacyFavoriteDao {

    @Query("SELECT * FROM legacy_favorites ORDER BY addedAt ASC")
    suspend fun all(): List<LegacyFavoriteEntity>

    @Query("SELECT COUNT(*) FROM legacy_favorites")
    suspend fun count(): Int

    @Query("DELETE FROM legacy_favorites WHERE imdbId = :imdbId")
    suspend fun delete(imdbId: String)
}
