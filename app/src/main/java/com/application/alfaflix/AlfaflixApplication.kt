package com.application.alfaflix

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.application.alfaflix.core.di.ApplicationScope
import com.application.alfaflix.core.network.NetworkMonitor
import com.application.alfaflix.data.repository.LegacyFavoritesMigrator
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application entry point. Hosts the Hilt dependency graph.
 *
 * Also configures Coil's singleton image loader with a **separate** OkHttp client from the
 * one used for the TMDB API. That separation is deliberate: the API client carries an
 * interceptor that attaches the bearer token to every request, and image URLs point at a CDN
 * (and YouTube thumbnails) — sharing the client would send the token to third parties.
 */
@HiltAndroidApp
class AlfaflixApplication : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var legacyFavoritesMigrator: LegacyFavoritesMigrator
    @Inject lateinit var networkMonitor: NetworkMonitor
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        migrateLegacyFavorites()
    }

    /**
     * Moves favorites saved before the TMDB migration onto TMDB IDs. Needs a connection, so
     * it waits for one and is a no-op once nothing is left to migrate.
     */
    private fun migrateLegacyFavorites() {
        applicationScope.launch {
            if (legacyFavoritesMigrator.isDone()) return@launch
            networkMonitor.isOnline.filter { it }.first()
            legacyFavoritesMigrator.migrate()
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, MEMORY_CACHE_FRACTION)
                    .build()
            }
            // Posters are immutable and re-shown constantly across Home, Search and
            // Favorites, so a disk cache saves both bandwidth and visible loading time.
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(DISK_CACHE_BYTES)
                    .build()
            }
            .crossfade(true)
            .build()

    private companion object {
        const val MEMORY_CACHE_FRACTION = 0.20
        const val DISK_CACHE_BYTES = 64L * 1024 * 1024
    }
}
