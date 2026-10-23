package com.hitachi.kiosk.core.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.device.Connectivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities

/** FR-028: the offline banner follows the default network's validation state (DEV-051 adds the E14 probe). */
@RunWith(RobolectricTestRunner::class)
class DefaultConnectivityMonitorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val network = ShadowNetwork.newInstance(1)

    private fun capabilities(vararg capability: Int): NetworkCapabilities =
        ShadowNetworkCapabilities.newInstance().also { caps ->
            capability.forEach { shadowOf(caps).addCapability(it) }
        }

    private fun callbacks() = shadowOf(manager).networkCallbacks

    @Test
    fun `validated is online, connected is checking, lost is offline`() {
        val monitor = DefaultConnectivityMonitor(context)

        callbacks().forEach {
            it.onCapabilitiesChanged(
                network,
                capabilities(NetworkCapabilities.NET_CAPABILITY_INTERNET, NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            )
        }
        assertThat(monitor.connectivity.value).isEqualTo(Connectivity.ONLINE)

        callbacks().forEach {
            it.onCapabilitiesChanged(
                network,
                capabilities(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            )
        }
        assertThat(monitor.connectivity.value).isEqualTo(Connectivity.CHECKING)

        callbacks().forEach { it.onLost(network) }
        assertThat(monitor.connectivity.value).isEqualTo(Connectivity.OFFLINE)

        callbacks().forEach { it.onCapabilitiesChanged(network, capabilities()) }
        assertThat(monitor.connectivity.value).isEqualTo(Connectivity.OFFLINE)
    }

    @Test
    fun `it registers for the default network`() {
        DefaultConnectivityMonitor(context)

        assertThat(callbacks()).isNotEmpty()
    }
}
