package com.hitachi.kiosk.di

import com.hitachi.kiosk.core.domain.device.DeviceIdentityProvider
import com.hitachi.kiosk.core.hardware.identity.IminDeviceIdentityProvider
import com.hitachi.kiosk.devtools.BindingOverrides
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.Optional
import javax.inject.Singleton

/**
 * Binds the hardware ports to the `:core:hardware` implementations (architecture §10, technical-design §4.3). These are
 * the release bindings; a debug [BindingOverrides] (declared in [BackendModule]) wraps them when present.
 */
@Module
@InstallIn(SingletonComponent::class)
object HardwareBindingsModule {
    /** FR-002: the serial number used for activation. */
    @Provides
    @Singleton
    fun deviceIdentityProvider(
        real: IminDeviceIdentityProvider,
        overrides: Optional<BindingOverrides>,
    ): DeviceIdentityProvider = overrides.map { it.deviceIdentityProvider(real) }.orElse(real)
}
