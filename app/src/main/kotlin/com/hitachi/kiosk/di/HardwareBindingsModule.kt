package com.hitachi.kiosk.di

import android.content.Context
import com.hitachi.kiosk.core.data.api.NetworkErrorSignal
import com.hitachi.kiosk.core.data.net.DefaultConnectivityMonitor
import com.hitachi.kiosk.core.domain.backend.BackendApi
import com.hitachi.kiosk.core.domain.device.ConnectivityMonitor
import com.hitachi.kiosk.core.domain.device.DeviceIdentityProvider
import com.hitachi.kiosk.core.hardware.identity.IminDeviceIdentityProvider
import com.hitachi.kiosk.devtools.BindingOverrides
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
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

    /**
     * FR-028: online, checking or offline, for the offline banner and the checkout gate. The probe uses the bound
     * [BackendApi] (so a debug build's fakes drive it) and runs in the application scope, which `:core:data` cannot
     * name, so `:app` builds the monitor.
     */
    @Provides
    @Singleton
    fun connectivityMonitor(
        @ApplicationContext context: Context,
        backend: BackendApi,
        networkErrors: NetworkErrorSignal,
        @ApplicationScope scope: CoroutineScope,
        overrides: Optional<BindingOverrides>,
    ): ConnectivityMonitor {
        val real = DefaultConnectivityMonitor(context, backend, networkErrors, scope)
        return overrides.map { it.connectivityMonitor(real) }.orElse(real)
    }
}
