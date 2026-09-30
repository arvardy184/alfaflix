package com.application.alfaflix.data.remote.di

import android.content.Context
import com.application.alfaflix.BuildConfig
import com.application.alfaflix.core.di.ApiKey
import com.application.alfaflix.data.remote.api.TmdbApi
import com.application.alfaflix.data.remote.api.TmdbAuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private const val HTTP_CACHE_BYTES = 10L * 1024 * 1024 // 10 MB
private const val TIMEOUT_SECONDS = 20L

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @ApiKey
    fun providesAccessToken(): String = BuildConfig.TMDB_READ_ACCESS_TOKEN

    @Provides
    @Singleton
    fun providesJson(): Json = Json {
        // TMDB adds fields over time; unknown ones must never break deserialization.
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun providesOkHttpClient(
        @ApplicationContext context: Context,
        @ApiKey accessToken: String,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            // Only method, URL and status are logged, and only in debug builds. Bodies are
            // not logged, and the Authorization header is redacted regardless of level.
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
        }

        return OkHttpClient.Builder()
            .addInterceptor(TmdbAuthInterceptor(accessToken))
            .addInterceptor(logging)
            // The app's own persisted caches are the offline strategy; this disk cache only
            // saves bandwidth on repeated identical requests within a session.
            .cache(Cache(context.cacheDir.resolve("http_cache"), HTTP_CACHE_BYTES))
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun providesRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.TMDB_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun providesTmdbApi(retrofit: Retrofit): TmdbApi = retrofit.create(TmdbApi::class.java)
}
