package com.hitachi.kiosk.core.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.data.api.NetworkErrorSignal
import com.hitachi.kiosk.core.domain.device.Connectivity
import com.hitachi.kiosk.core.testing.fake.FakeBackendApi
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import kotlin.time.Duration.Companion.seconds

/**
 * The Android adapter of the connectivity monitor (FR-028, TR-NET-11): `ConnectivityManager` callbacks plus the E14
 * call, around [ConnectivityEngine] (whose logic `ConnectivityEngineTest` covers).
 */
@RunWith(RobolectricTestRunner::class)
class DefaultConnectivityMonitorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val wifi = ShadowNetwork.newInstance(1)
    private val cellular = ShadowNetwork.newInstance(2)
    private val backend = FakeBackendApi()

    private fun TestScope.monitor() =
        DefaultConnectivityMonitor(context, backend, NetworkErrorSignal(), backgroundScope)

    private fun capabilities(vararg capability: Int): NetworkCapabilities =
        ShadowNetworkCapabilities.newInstance().also { caps ->
            capability.forEach { shadowOf(caps).addCapability(it) }
        }

    private val validatedCapabilities
        get() =
            capabilities(NetworkCapabilities.NET_CAPABILITY_INTERNET, NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    private fun callbacks() = shadowOf(manager).networkCallbacks

    private fun capabilitiesChanged(
        network: Network,
        capabilities: NetworkCapabilities,
    ) = callbacks().forEach { it.onCapabilitiesChanged(network, capabilities) }

    private fun TestScope.settle() {
        advanceTimeBy(2.seconds)
        runCurrent()
    }

    @Test
    fun `validated and healthy is online, connected is checking, lost is offline`() =
        runTest {
            val monitor = monitor()

            capabilitiesChanged(wifi, validatedCapabilities)
            settle()
            assertThat(monitor.connectivity.value).isEqualTo(Connectivity.ONLINE)

            capabilitiesChanged(wifi, capabilities(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            settle()
            assertThat(monitor.connectivity.value).isEqualTo(Connectivity.CHECKING)

            callbacks().forEach { it.onLost(wifi) }
            settle()
            assertThat(monitor.connectivity.value).isEqualTo(Connectivity.OFFLINE)

            capabilitiesChanged(wifi, capabilities())
            settle()
            assertThat(monitor.connectivity.value).isEqualTo(Connectivity.OFFLINE)
        }

    @Test
    fun `a validated network with the gateway down is offline`() =
        runTest {
            backend.health.succeed(SampleData.healthGatewayDown)
            val monitor = monitor()

            capabilitiesChanged(wifi, validatedCapabilities)
            settle()

            assertThat(monitor.connectivity.value).isEqualTo(Connectivity.OFFLINE)
            assertThat(backend.callsTo(FakeBackendApi.Endpoint.E14)).isNotEmpty()
        }

    @Test
    fun `the old network being lost after a handover does not take the monitor offline`() =
        runTest {
            val monitor = monitor()
            capabilitiesChanged(wifi, validatedCapabilities)
            settle()

            capabilitiesChanged(cellular, validatedCapabilities)
            callbacks().forEach { it.onLost(wifi) }
            settle()

            assertThat(monitor.connectivity.value).isEqualTo(Connectivity.ONLINE)
        }

    @Test
    fun `a signal-strength update does not trigger another probe`() =
        runTest {
            monitor()
            capabilitiesChanged(wifi, validatedCapabilities)
            settle()
            val probes = backend.callsTo(FakeBackendApi.Endpoint.E14).size

            capabilitiesChanged(wifi, validatedCapabilities)
            settle()

            assertThat(backend.callsTo(FakeBackendApi.Endpoint.E14)).hasSize(probes)
        }

    @Test
    fun `it registers for the default network`() =
        runTest {
            monitor()

            assertThat(callbacks()).isNotEmpty()
        }
}
