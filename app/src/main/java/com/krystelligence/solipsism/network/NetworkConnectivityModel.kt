package com.krystelligence.solipsism.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.Reusable
import io.reactivex.rxjava3.core.Observable
import javax.inject.Inject

/**
 * A model that supplies network connectivity status updates.
 */
@Reusable
class NetworkConnectivityModel @Inject constructor(
    private val connectivityManager: ConnectivityManager
) {

    /**
     * An infinite observable that emits a boolean value whenever the network condition changes.
     * Emitted value is true when the network has validated internet access.
     */
    fun connectivity(): Observable<Boolean> = Observable.create { emitter ->
        fun isUsable(network: Network?): Boolean {
            val capabilities = network?.let(connectivityManager::getNetworkCapabilities)
            return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = emitter.onNext(isUsable(network))

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                emitter.onNext(
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                )
            }

            override fun onLost(network: Network) = emitter.onNext(isUsable(connectivityManager.activeNetwork))
        }

        emitter.onNext(isUsable(connectivityManager.activeNetwork))
        runCatching { connectivityManager.registerDefaultNetworkCallback(callback) }
            .onFailure(emitter::onError)
        emitter.setCancellable { runCatching { connectivityManager.unregisterNetworkCallback(callback) } }
    }
}
