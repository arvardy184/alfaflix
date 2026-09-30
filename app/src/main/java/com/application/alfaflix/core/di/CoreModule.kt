package com.application.alfaflix.core.di

import com.application.alfaflix.core.network.ConnectivityNetworkMonitor
import com.application.alfaflix.core.network.NetworkMonitor
import com.application.alfaflix.core.time.SystemTimeProvider
import com.application.alfaflix.core.time.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

/** Binds the production implementations of the core abstractions. */
@Module
@InstallIn(SingletonComponent::class)
abstract class CoreModule {

    @Binds
    @Singleton
    abstract fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider

    @Binds
    @Singleton
    abstract fun bindNetworkMonitor(impl: ConnectivityNetworkMonitor): NetworkMonitor
}

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {

    @Provides
    @IoDispatcher
    fun providesIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun providesDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}

@Module
@InstallIn(SingletonComponent::class)
object ApplicationScopeModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun providesApplicationScope(
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
}
