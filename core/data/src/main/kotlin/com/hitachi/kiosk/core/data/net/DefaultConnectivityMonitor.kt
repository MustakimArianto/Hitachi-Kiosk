package com.hitachi.kiosk.core.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.hitachi.kiosk.core.data.api.NetworkErrorSignal
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.backend.BackendApi
import com.hitachi.kiosk.core.domain.device.Connectivity
import com.hitachi.kiosk.core.domain.device.ConnectivityMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Connectivity from the default network's validation state and the E14 health probe (FR-028, TR-NET-11,
 * technical-design §7.3). ONLINE needs both a validated network and a healthy backend and payment gateway;
 * [ConnectivityEngine] holds that logic and the probe schedule, and this class only feeds it
 * `ConnectivityManager.registerDefaultNetworkCallback` readings and the E14 call.
 *
 * `:app` builds it, with [scope] the application scope and [backend] the bound [BackendApi] (so a debug build's
 * fake backend drives the probe too), and binds it through the optional `BindingOverrides` (TD-11).
 */
class DefaultConnectivityMonitor(
    context: Context,
    backend: BackendApi,
    networkErrors: NetworkErrorSignal,
    scope: CoroutineScope,
) : ConnectivityMonitor {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val network = MutableStateFlow(initial())
    private val engine =
        ConnectivityEngine(
            scope = scope,
            network = network,
            probe = { backend.canTakePayments() },
            networkErrors = networkErrors.reports,
        )

    override val connectivity: StateFlow<Connectivity> = engine.connectivity

    private val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                this@DefaultConnectivityMonitor.network.value = capabilities.toStatus(network)
            }

            override fun onLost(network: Network) {
                // A handover can report the old network lost after the new one is the default: ignore that.
                val current = this@DefaultConnectivityMonitor.network
                if (current.value.id == null || current.value.id == network.networkHandle) {
                    current.value = NetworkStatus(NetworkState.NONE)
                }
            }

            override fun onUnavailable() {
                network.value = NetworkStatus(NetworkState.NONE)
            }
        }

    init {
        // App-lifetime singleton, so the callback is never unregistered.
        manager.registerDefaultNetworkCallback(callback)
    }

    private fun initial(): NetworkStatus {
        val active = manager.activeNetwork ?: return NetworkStatus(NetworkState.NONE)
        return manager.getNetworkCapabilities(active)?.toStatus(active) ?: NetworkStatus(NetworkState.NONE)
    }

    private fun NetworkCapabilities.toStatus(network: Network): NetworkStatus {
        val state =
            when {
                hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> NetworkState.VALIDATED
                hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> NetworkState.UNVALIDATED
                else -> NetworkState.NONE
            }
        return NetworkStatus(state, network.networkHandle)
    }
}

/**
 * The E14 probe: `true` only when the backend and the payment gateway are both `OK` (api-spec E14). Any failure,
 * including a network error or an unreadable body, is unhealthy. E14 failures are not reported to
 * [NetworkErrorSignal], so a failing probe cannot trigger itself.
 */
internal suspend fun BackendApi.canTakePayments(): Boolean =
    when (val result = health()) {
        is Outcome.Success -> result.value.canTakePayments
        is Outcome.Failure -> false
    }
