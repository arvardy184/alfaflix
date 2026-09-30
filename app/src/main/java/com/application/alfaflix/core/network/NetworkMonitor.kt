package com.application.alfaflix.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.application.alfaflix.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reports whether the device currently has a validated internet connection.
 *
 * Exists so the app can tell "you are offline" apart from "the server failed", which are
 * different messages with different recovery advice. It is an interface so tests can
 * substitute connectivity instead of relying on the emulator's real network state.
 */
interface NetworkMonitor {
    val isOnline: Flow<Boolean>
}

@Singleton
class ConnectivityNetworkMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : NetworkMonitor {

    override val isOnline: Flow<Boolean> = callbackFlow {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        if (connectivityManager == null) {
            channel.trySend(false)
            channel.close()
            return@callbackFlow
        }

        // Track networks in a set: a device can hold several at once (Wi-Fi and cellular),
        // so losing one does not necessarily mean the device went offline.
        val callback = object : ConnectivityManager.NetworkCallback() {
            private val networks = mutableSetOf<Network>()

            override fun onAvailable(network: Network) {
                networks += network
                channel.trySend(true)
            }

            override fun onLost(network: Network) {
                networks -= network
                channel.trySend(networks.isNotEmpty())
            }
        }

        // VALIDATED matters: a captive-portal Wi-Fi is "connected" but cannot reach TMDB.
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)

        // Callbacks only report changes, so seed the current value.
        channel.trySend(connectivityManager.isCurrentlyOnline())

        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }
        .conflate()
        .distinctUntilChanged()
        .flowOn(ioDispatcher)

    private fun ConnectivityManager.isCurrentlyOnline(): Boolean {
        val capabilities = getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
