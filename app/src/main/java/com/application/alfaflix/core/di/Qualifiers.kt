package com.application.alfaflix.core.di

import javax.inject.Qualifier

/**
 * Dispatchers are injected rather than referenced as [kotlinx.coroutines.Dispatchers]
 * statics so tests can swap in a deterministic test dispatcher and control virtual time.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** The TMDB read access token, sourced from `local.properties` or the environment via `BuildConfig`. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApiKey

/** A process-lifetime scope for work that must outlive any screen, such as one-off data migration. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
