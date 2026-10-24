package com.hitachi.kiosk.core.data.net

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.device.Connectivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The pure connectivity logic with virtual time (FR-028, TR-NET-11, technical-design §7.3): the truth table, the
 * probe cadence, the debounce and the two FR-028 acceptance timings.
 */
class ConnectivityEngineTest {
    private val validated = NetworkStatus(NetworkState.VALIDATED, id = 1)
    private val none = NetworkStatus(NetworkState.NONE)
    private val unvalidated = NetworkStatus(NetworkState.UNVALIDATED, id = 1)

    /** A probe whose answers a test scripts; [times] records the virtual time of every call. */
    private class ScriptedProbe(
        private val scope: TestScope,
    ) {
        var healthy: Boolean = true
        var latency: Duration = Duration.ZERO
        val times = mutableListOf<Long>()
        val calls get() = times.size

        suspend fun probe(): Boolean {
            times += scope.testScheduler.currentTime
            val answer = healthy
            delay(latency)
            return answer
        }
    }

    private class Harness(
        val network: MutableStateFlow<NetworkStatus>,
        val errors: MutableSharedFlow<Unit>,
        val probe: ScriptedProbe,
        val engine: ConnectivityEngine,
        val emitted: MutableList<Connectivity>,
    ) {
        val state get() = engine.connectivity.value
    }

    private fun TestScope.harness(
        initial: NetworkStatus,
        configure: ScriptedProbe.() -> Unit = {},
    ): Harness {
        val probe = ScriptedProbe(this).apply(configure)
        val network = MutableStateFlow(initial)
        val errors = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val engine =
            ConnectivityEngine(
                scope = backgroundScope,
                network = network,
                probe = probe::probe,
                networkErrors = errors,
            )
        val emitted = mutableListOf<Connectivity>()
        backgroundScope.launch { engine.connectivity.collect { emitted += it } }
        runCurrent()
        return Harness(network, errors, probe, engine, emitted)
    }

    private fun TestScope.advance(duration: Duration) {
        advanceTimeBy(duration)
        runCurrent()
    }

    // Truth table -------------------------------------------------------------------------------------------------

    @Test
    fun `no network is offline and is never probed`() =
        runTest {
            val h = harness(none)
            advance(2.minutes)

            assertThat(h.state).isEqualTo(Connectivity.OFFLINE)
            assertThat(h.probe.calls).isEqualTo(0)
        }

    @Test
    fun `a network that is connected but not validated is checking for a moment, then offline (FR-028 AC1)`() =
        runTest {
            val h = harness(unvalidated)
            advance(3.seconds)
            assertThat(h.state).isEqualTo(Connectivity.CHECKING)

            // The 4 s grace and the 1 s debounce: the banner is up within 5 s.
            advance(2.seconds)

            assertThat(h.state).isEqualTo(Connectivity.OFFLINE)
            assertThat(h.probe.calls).isEqualTo(0)
        }

    @Test
    fun `a network that validates later recovers within 10 s (FR-028 AC2)`() =
        runTest {
            val h = harness(unvalidated)
            advance(2.minutes)
            assertThat(h.state).isEqualTo(Connectivity.OFFLINE)

            h.network.value = validated
            advance(2.seconds)

            assertThat(h.state).isEqualTo(Connectivity.ONLINE)
        }

    @Test
    fun `validated network with a healthy probe is online`() =
        runTest {
            val h = harness(validated)
            advance(1.seconds)

            assertThat(h.state).isEqualTo(Connectivity.ONLINE)
        }

    @Test
    fun `validated network with a failing probe is offline`() =
        runTest {
            val h = harness(validated) { healthy = false }
            advance(1.seconds)

            assertThat(h.state).isEqualTo(Connectivity.OFFLINE)
        }

    @Test
    fun `validated network is checking while the first probe is in flight`() =
        runTest {
            val h = harness(validated) { latency = 3.seconds }
            advance(2.seconds)
            assertThat(h.state).isEqualTo(Connectivity.CHECKING)

            advance(2.seconds)
            assertThat(h.state).isEqualTo(Connectivity.ONLINE)
        }

    @Test
    fun `a probe that never answers times out as failed`() =
        runTest {
            val h = harness(validated) { latency = 1.minutes }
            advance(7.seconds)

            assertThat(h.state).isEqualTo(Connectivity.OFFLINE)
        }

    @Test
    fun `losing the network is offline even when the last probe was healthy`() =
        runTest {
            val h = harness(validated)
            advance(1.seconds)

            h.network.value = none
            advance(1.seconds)

            assertThat(h.state).isEqualTo(Connectivity.OFFLINE)
        }

    @Test
    fun `a captive portal after being online ends offline within 5 s`() =
        runTest {
            val h = harness(validated)
            advance(1.seconds)

            h.network.value = unvalidated
            advance(5.seconds)

            assertThat(h.state).isEqualTo(Connectivity.OFFLINE)
        }

    // Probe cadence -----------------------------------------------------------------------------------------------

    @Test
    fun `probes quickly after a network change, then every 30 s while not online`() =
        runTest {
            val h = harness(validated) { healthy = false }
            advance(2.minutes)

            // At once, after 2 s and 4 s (the quick retries), then every 30 s.
            assertThat(h.probe.times).containsExactly(0L, 2_000L, 6_000L, 36_000L, 66_000L, 96_000L).inOrder()
        }

    @Test
    fun `does not poll while online`() =
        runTest {
            val h = harness(validated)
            advance(10.minutes)

            assertThat(h.state).isEqualTo(Connectivity.ONLINE)
            assertThat(h.probe.calls).isEqualTo(1)
        }

    @Test
    fun `a network error while online re-probes at once`() =
        runTest {
            val h = harness(validated)
            advance(5.seconds)
            val before = h.probe.calls

            h.errors.tryEmit(Unit)
            runCurrent()

            assertThat(h.probe.calls).isEqualTo(before + 1)
            assertThat(h.probe.times.last()).isEqualTo(5_000L)
            assertThat(h.state).isEqualTo(Connectivity.ONLINE)
        }

    @Test
    fun `a network error that finds the backend down turns the state offline`() =
        runTest {
            val h = harness(validated)
            advance(5.seconds)

            h.probe.healthy = false
            h.errors.tryEmit(Unit)
            advance(1.seconds)

            assertThat(h.state).isEqualTo(Connectivity.OFFLINE)
        }

    @Test
    fun `network errors while not online do not add probes`() =
        runTest {
            val h = harness(validated) { healthy = false }
            advance(1.seconds)
            val before = h.probe.calls

            repeat(5) { h.errors.tryEmit(Unit) }
            runCurrent()

            assertThat(h.probe.calls).isEqualTo(before)
        }

    @Test
    fun `a switch to another validated network probes again`() =
        runTest {
            val h = harness(validated)
            advance(5.seconds)

            h.network.value = NetworkStatus(NetworkState.VALIDATED, id = 2)
            runCurrent()

            assertThat(h.probe.calls).isEqualTo(2)
        }

    @Test
    fun `an unchanged network reading does not probe again`() =
        runTest {
            val h = harness(validated)
            advance(5.seconds)

            h.network.value = NetworkStatus(NetworkState.VALIDATED, id = 1)
            advance(1.minutes)

            assertThat(h.probe.calls).isEqualTo(1)
        }

    @Test
    fun `a gateway that recovers brings the state back at a later probe`() =
        runTest {
            val h = harness(validated) { healthy = false }
            advance(10.seconds)
            assertThat(h.state).isEqualTo(Connectivity.OFFLINE)

            h.probe.healthy = true
            advance(30.seconds)

            assertThat(h.state).isEqualTo(Connectivity.ONLINE)
        }

    @Test
    fun `an answer that arrives after the network was lost is ignored`() =
        runTest {
            val h = harness(validated) { latency = 3.seconds }
            advance(1.seconds)

            h.network.value = none
            advance(10.seconds)

            assertThat(h.state).isEqualTo(Connectivity.OFFLINE)
        }

    // Debounce ----------------------------------------------------------------------------------------------------

    @Test
    fun `a flap shorter than the debounce emits nothing`() =
        runTest {
            val h = harness(validated)
            advance(2.seconds)
            h.emitted.clear()

            h.network.value = none
            advance(400.milliseconds)
            h.network.value = validated
            advance(5.seconds)

            assertThat(h.emitted).isEmpty()
            assertThat(h.state).isEqualTo(Connectivity.ONLINE)
        }

    @Test
    fun `a change that holds is published after the debounce`() =
        runTest {
            val h = harness(validated)
            advance(2.seconds)

            h.network.value = none
            advance(900.milliseconds)
            assertThat(h.state).isEqualTo(Connectivity.ONLINE)

            advance(200.milliseconds)
            assertThat(h.state).isEqualTo(Connectivity.OFFLINE)
        }

    // FR-028 acceptance timings -----------------------------------------------------------------------------------

    @Test
    fun `FR-028 AC1 airplane mode shows offline within 5 s`() =
        runTest {
            val h = harness(validated)
            advance(2.seconds)
            assertThat(h.state).isEqualTo(Connectivity.ONLINE)

            h.network.value = none
            advance(5.seconds)

            assertThat(h.state).isEqualTo(Connectivity.OFFLINE)
        }

    @Test
    fun `FR-028 AC2 the network coming back re-enables checkout within 10 s`() =
        runTest {
            val h = harness(none)
            advance(30.seconds)
            assertThat(h.state).isEqualTo(Connectivity.OFFLINE)

            h.network.value = validated
            advance(10.seconds)

            assertThat(h.state).isEqualTo(Connectivity.ONLINE)
        }

    @Test
    fun `FR-028 AC2 holds when the first probe fails on the fresh network`() =
        runTest {
            val h = harness(none)
            advance(30.seconds)

            h.probe.healthy = false
            h.network.value = validated
            advance(1.seconds)
            h.probe.healthy = true
            advance(9.seconds)

            assertThat(h.state).isEqualTo(Connectivity.ONLINE)
        }

    @Test
    fun `FR-028 AC2 holds when the first probe hangs`() =
        runTest {
            val h = harness(none)
            advance(30.seconds)

            h.probe.latency = 1.minutes
            h.network.value = validated
            advance(1.seconds)
            h.probe.latency = Duration.ZERO
            advance(9.seconds)

            assertThat(h.state).isEqualTo(Connectivity.ONLINE)
        }
}
