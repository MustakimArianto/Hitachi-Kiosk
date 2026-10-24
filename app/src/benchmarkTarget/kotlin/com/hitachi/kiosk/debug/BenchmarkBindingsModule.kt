package com.hitachi.kiosk.debug

import com.hitachi.kiosk.core.domain.backend.BackendApi
import com.hitachi.kiosk.core.domain.device.ConnectivityMonitor
import com.hitachi.kiosk.core.domain.device.DeviceIdentityProvider
import com.hitachi.kiosk.core.domain.time.Clock
import com.hitachi.kiosk.core.testing.fake.FakeBackendApi
import com.hitachi.kiosk.core.testing.fake.FakeConnectivityMonitor
import com.hitachi.kiosk.core.testing.fake.FakeDeviceIdentityProvider
import com.hitachi.kiosk.devtools.BindingOverrides
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The benchmark builds only (`benchmarkRelease`, `nonMinifiedRelease`; DEV-031): the backend, the serial number and
 * the connectivity are the `:core:testing` fakes on the happy path, so `:benchmark` can sign in, bind "Urban Grill" and
 * reach Idle without a network (technical-design §4.3 TD-11, §13). There are no Developer options here. Release has
 * none of this module (scripts/check-release-apk.sh).
 */
@Module
@InstallIn(SingletonComponent::class)
object BenchmarkBindingsModule {
    @Provides
    @Singleton
    fun bindingOverrides(clock: Clock): BindingOverrides {
        val backend = FakeBackendApi(clock)
        val identity = FakeDeviceIdentityProvider()
        val connectivity = FakeConnectivityMonitor()
        return object : BindingOverrides {
            override fun backendApi(real: BackendApi): BackendApi = backend

            override fun deviceIdentityProvider(real: DeviceIdentityProvider): DeviceIdentityProvider = identity

            override fun connectivityMonitor(real: ConnectivityMonitor): ConnectivityMonitor = connectivity
        }
    }
}
