package com.hitachi.kiosk.testing

import com.hitachi.kiosk.core.domain.backend.BackendApi
import com.hitachi.kiosk.core.domain.device.CardTerminal
import com.hitachi.kiosk.core.domain.device.ConnectivityMonitor
import com.hitachi.kiosk.core.domain.device.DeviceIdentityProvider
import com.hitachi.kiosk.core.testing.fake.FakeBackendApi
import com.hitachi.kiosk.core.testing.fake.FakeCardTerminal
import com.hitachi.kiosk.core.testing.fake.FakeConnectivityMonitor
import com.hitachi.kiosk.core.testing.fake.FakeDeviceIdentityProvider
import com.hitachi.kiosk.debug.DeveloperOptionsModule
import com.hitachi.kiosk.devtools.BindingOverrides
import com.hitachi.kiosk.devtools.DeveloperMenu
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Instrumented tests drive the real app, data layer and encrypted storage, with the backend, the serial number and
 * the connectivity replaced by scriptable fakes the test injects (technical-design §14). It replaces the debug Developer options
 * bindings, so a tester's saved Developer options choices never leak into a test run.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DeveloperOptionsModule::class])
object FakeBindingsTestModule {
    @Provides
    @Singleton
    fun fakeBackend(): FakeBackendApi = FakeBackendApi()

    @Provides
    @Singleton
    fun fakeIdentity(): FakeDeviceIdentityProvider = FakeDeviceIdentityProvider()

    @Provides
    @Singleton
    fun fakeConnectivity(): FakeConnectivityMonitor = FakeConnectivityMonitor()

    @Provides
    @Singleton
    fun fakeCardTerminal(): FakeCardTerminal = FakeCardTerminal()

    @Provides
    fun bindingOverrides(
        backend: FakeBackendApi,
        identity: FakeDeviceIdentityProvider,
        connectivity: FakeConnectivityMonitor,
        cardTerminal: FakeCardTerminal,
    ): BindingOverrides =
        object : BindingOverrides {
            override fun backendApi(real: BackendApi): BackendApi = backend

            override fun deviceIdentityProvider(real: DeviceIdentityProvider): DeviceIdentityProvider = identity

            override fun connectivityMonitor(real: ConnectivityMonitor): ConnectivityMonitor = connectivity

            override fun cardTerminal(real: CardTerminal): CardTerminal = cardTerminal
        }

    @Provides
    fun developerMenu(): DeveloperMenu = DeveloperMenu { }
}
