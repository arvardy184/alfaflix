package com.application.alfaflix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.application.alfaflix.data.local.entity.DiscoverMetaEntity
import com.application.alfaflix.data.local.entity.DiscoverMovieEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiscoveryDao {

    @Query("SELECT * FROM discover_movies ORDER BY position ASC")
    fun observeMovies(): Flow<List<DiscoverMovieEntity>>

    @Query("SELECT * FROM discover_meta WHERE id = 0")
    fun observeMeta(): Flow<DiscoverMetaEntity?>

    @Query("SELECT * FROM discover_meta WHERE id = 0")
    suspend fun meta(): DiscoverMetaEntity?

    /** Backs "Surprise me": reads data already on the device, so it works offline. */
    @Query("SELECT * FROM discover_movies ORDER BY RANDOM() LIMIT 1")
    suspend fun randomMovie(): DiscoverMovieEntity?

    /**
     * Replaces the whole list with a freshly loaded first page, atomically.
     *
     * Delete-then-insert rather than upsert so a title that dropped out of TMDB's results
     * disappears too. Because it is one transaction, a reader sees either the old list or
     * the new one — never an empty intermediate state.
     */
    @Transaction
    suspend fun replaceAll(
        movies: List<DiscoverMovieEntity>,
        totalPages: Int,
        refreshedAt: Long,
    ) {
        val nextEpoch = (meta()?.epoch ?: 0) + 1
        deleteAllMovies()
        insertMovies(movies)
        upsertMeta(
            DiscoverMetaEntity(
                loadedPages = 1,
                totalPages = totalPages,
                refreshedAt = refreshedAt,
                epoch = nextEpoch,
            ),
        )
    }

    /**
     * Appends [page] to the list, but only if it is the page that directly follows what is
     * stored.
     *
     * The check and the write share a transaction, so a page response that raced a refresh
     * (which starts a new epoch) or a duplicate request is discarded rather than corrupting the
     * ordering or the bookkeeping.
     *
     * @return true when the page was appended.
     */
    @Transaction
    suspend fun appendPage(
        expectedEpoch: Int,
        page: Int,
        movies: List<DiscoverMovieEntity>,
        totalPages: Int,
    ): Boolean {
        val current = meta() ?: return false
        // Both must hold: the list is still the incarnation the request started under, and this
        // page directly follows what is stored.
        if (current.epoch != expectedEpoch || current.loadedPages != page - 1) return false

        val startPosition = (maxPosition() ?: -1) + 1
        // IGNORE: TMDB's popularity ordering shifts between requests, so a movie already
        // stored can reappear on a later page. The primary key drops the repeat.
        insertMovies(movies.mapIndexed { index, movie -> movie.copy(position = startPosition + index) })
        upsertMeta(current.copy(loadedPages = page, totalPages = totalPages))
        return true
    }

    @Query("SELECT MAX(position) FROM discover_movies")
    suspend fun maxPosition(): Int?

    @Query("DELETE FROM discover_movies")
    suspend fun deleteAllMovies()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMovies(movies: List<DiscoverMovieEntity>)

    @Upsert
    suspend fun upsertMeta(meta: DiscoverMetaEntity)
}
