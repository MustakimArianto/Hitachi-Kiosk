package com.hitachi.kiosk.core.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.hitachi.kiosk.core.domain.device.Connectivity
import com.hitachi.kiosk.core.domain.device.ConnectivityMonitor
import com.hitachi.kiosk.core.domain.log.KioskLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connectivity from the default network's validation state (FR-028, technical-design §7.3): validated → ONLINE,
 * connected but not validated yet → CHECKING, no network → OFFLINE.
 *
 * This is the 0.3.0 monitor behind the Menu's offline banner. DEV-051 (0.5.0) adds the E14 health probe, which
 * must also pass for ONLINE, and the 1 s debounce. `:app` binds it, with the optional debug override (TD-11).
 */
@Singleton
class DefaultConnectivityMonitor
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : ConnectivityMonitor {
        private val manager = context.getSystemService(ConnectivityManager::class.java)
        private val state = MutableStateFlow(initial())

        override val connectivity: StateFlow<Connectivity> = state.asStateFlow()

        private val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities,
                ) {
                    update(capabilities.toConnectivity())
                }

                override fun onLost(network: Network) {
                    update(Connectivity.OFFLINE)
                }

                override fun onUnavailable() {
                    update(Connectivity.OFFLINE)
                }
            }

        init {
            // App-lifetime singleton, so the callback is never unregistered.
            manager.registerDefaultNetworkCallback(callback)
        }

        private fun initial(): Connectivity =
            manager.activeNetwork
                ?.let(manager::getNetworkCapabilities)
                ?.toConnectivity()
                ?: Connectivity.OFFLINE

        private fun update(next: Connectivity) {
            if (state.value != next) KioskLog.i("connectivity_changed", "state" to next)
            state.value = next
        }

        private fun NetworkCapabilities.toConnectivity(): Connectivity =
            when {
                hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> Connectivity.ONLINE
                hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> Connectivity.CHECKING
                else -> Connectivity.OFFLINE
            }
    }
