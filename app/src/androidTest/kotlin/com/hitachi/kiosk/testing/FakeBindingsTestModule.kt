package com.hitachi.kiosk.testing

import com.hitachi.kiosk.core.domain.backend.BackendApi
import com.hitachi.kiosk.core.domain.device.DeviceIdentityProvider
import com.hitachi.kiosk.core.testing.fake.FakeBackendApi
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
 * Instrumented tests drive the real app, data layer and encrypted storage, with the backend and the serial number
 * replaced by scriptable fakes the test injects (technical-design §14). It replaces the debug Developer options
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
    fun bindingOverrides(
        backend: FakeBackendApi,
        identity: FakeDeviceIdentityProvider,
    ): BindingOverrides =
        object : BindingOverrides {
            override fun backendApi(real: BackendApi): BackendApi = backend

            override fun deviceIdentityProvider(real: DeviceIdentityProvider): DeviceIdentityProvider = identity
        }

    @Provides
    fun developerMenu(): DeveloperMenu = DeveloperMenu { }
}
