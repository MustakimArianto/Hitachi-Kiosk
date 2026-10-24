package com.hitachi.kiosk.core.data.net

import com.hitachi.kiosk.core.domain.device.Connectivity
import com.hitachi.kiosk.core.domain.log.KioskLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** What the platform says about the default network. [VALIDATED] means the OS reached the internet through it. */
internal enum class NetworkState { NONE, UNVALIDATED, VALIDATED }

/**
 * One reading of the default network. [id] tells two networks apart, so a switch between validated networks is a
 * network change that needs a new probe, while a signal-strength update (same [state] and [id]) is not.
 */
internal data class NetworkStatus(
    val state: NetworkState,
    val id: Long? = null,
)

/** The timings of [ConnectivityEngine] (technical-design §7.3, FR-028). */
internal data class ConnectivityTimings(
    /** Probe period while the state is not ONLINE. */
    val probeInterval: Duration = 30.seconds,
    /**
     * Shorter waits for the first retries after a network change, because a freshly reconnected network often cannot
     * reach the backend for a second or two (DNS, routes). They keep FR-028 AC2 (Pay back within 10 s) reachable;
     * after they are used up the period is [probeInterval].
     */
    val fastRetries: List<Duration> = listOf(2.seconds, 4.seconds),
    /** A probe that has not answered by then counts as failed, so a hung call cannot eat the AC2 budget. */
    val probeTimeout: Duration = 5.seconds,
    /** The state must hold this long before it is published, so a flapping network does not flicker the banner. */
    val debounce: Duration = 1.seconds,
)

/**
 * The pure logic of `ConnectivityMonitor` (technical-design §7.3, TR-NET-11, FR-028): it combines the network status
 * with the result of an E14 health probe into [connectivity], and runs the probes. It knows nothing about Android or
 * the backend, so it is tested on the JVM with virtual time; `DefaultConnectivityMonitor` is the thin adapter.
 *
 * | Network | Last probe | Connectivity |
 * |---|---|---|
 * | none | any | OFFLINE |
 * | connected, not validated | any | CHECKING |
 * | validated | none yet, or in flight after a network change | CHECKING |
 * | validated | healthy | ONLINE |
 * | validated | unhealthy or timed out | OFFLINE |
 *
 * Probing, only on a validated network:
 * - once on every network change (a new [NetworkStatus.state] or [NetworkStatus.id]);
 * - while the probe fails: a few quick retries, then every [ConnectivityTimings.probeInterval];
 * - while it succeeds (ONLINE): never on a timer, but at once when [networkErrors] reports a real call that failed
 *   with a network error.
 *
 * The published state is debounced by [ConnectivityTimings.debounce]. At worst the banner appears that long after
 * the network is lost (FR-028 AC1 allows 5 s) and Pay is enabled that long after the probe passes (AC2: 10 s).
 *
 * @param probe `true` only when the backend and the payment gateway are both `OK`; it must not throw.
 * @param networkErrors emits whenever a real call (not E14) failed with a network error.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
internal class ConnectivityEngine(
    scope: CoroutineScope,
    private val network: StateFlow<NetworkStatus>,
    private val probe: suspend () -> Boolean,
    private val networkErrors: Flow<Unit>,
    private val timings: ConnectivityTimings = ConnectivityTimings(),
) {
    private enum class Health { UNKNOWN, HEALTHY, UNHEALTHY }

    private val health = MutableStateFlow(Health.UNKNOWN)

    val connectivity: StateFlow<Connectivity> =
        combine(network, health, ::derive)
            .debounce(timings.debounce)
            .distinctUntilChanged()
            .onEach { KioskLog.i("connectivity_changed", "state" to it) }
            .stateIn(scope, SharingStarted.Eagerly, derive(network.value, Health.UNKNOWN))

    init {
        scope.launch {
            // A StateFlow already skips equal readings, so a signal-strength update does not restart the probe.
            network.collectLatest { status ->
                health.value = Health.UNKNOWN
                if (status.state == NetworkState.VALIDATED) probeWhileValidated()
            }
        }
    }

    private suspend fun probeWhileValidated() {
        var failures = 0
        while (true) {
            if (runProbe()) {
                health.value = Health.HEALTHY
                failures = 0
                // ONLINE: nothing to poll. A real call that fails with a network error asks for a fresh probe.
                networkErrors.first()
            } else {
                health.value = Health.UNHEALTHY
                delay(timings.fastRetries.getOrElse(failures) { timings.probeInterval })
                failures++
            }
        }
    }

    private suspend fun runProbe(): Boolean {
        val healthy = withTimeoutOrNull(timings.probeTimeout) { probe() } ?: false
        KioskLog.d("connectivity_probe", "healthy" to healthy)
        return healthy
    }

    private fun derive(
        status: NetworkStatus,
        health: Health,
    ): Connectivity =
        when (status.state) {
            NetworkState.NONE -> {
                Connectivity.OFFLINE
            }

            NetworkState.UNVALIDATED -> {
                Connectivity.CHECKING
            }

            NetworkState.VALIDATED -> {
                when (health) {
                    Health.UNKNOWN -> Connectivity.CHECKING
                    Health.HEALTHY -> Connectivity.ONLINE
                    Health.UNHEALTHY -> Connectivity.OFFLINE
                }
            }
        }
}
