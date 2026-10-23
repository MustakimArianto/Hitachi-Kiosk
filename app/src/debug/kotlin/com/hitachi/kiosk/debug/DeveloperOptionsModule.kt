package com.hitachi.kiosk.debug

import android.content.Intent
import com.hitachi.kiosk.BuildConfig
import com.hitachi.kiosk.core.data.api.NetworkInterceptors
import com.hitachi.kiosk.core.domain.backend.BackendApi
import com.hitachi.kiosk.core.domain.device.DeviceIdentityProvider
import com.hitachi.kiosk.core.domain.time.Clock
import com.hitachi.kiosk.devtools.BindingOverrides
import com.hitachi.kiosk.devtools.DeveloperMenu
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.Interceptor
import javax.inject.Singleton

/** Debug-only bindings for Developer options (DEV-009). The release build has none of this module. */
@Module
@InstallIn(SingletonComponent::class)
object DeveloperOptionsModule {
    @Provides
    fun developerMenu(): DeveloperMenu =
        DeveloperMenu { context ->
            context.startActivity(Intent(context, DeveloperOptionsActivity::class.java))
        }

    @Provides
    @Singleton
    fun bindingOverrides(
        store: DeveloperSettingsStore,
        clock: Clock,
    ): BindingOverrides =
        object : BindingOverrides {
            override fun backendApi(real: BackendApi): BackendApi =
                SwitchableBackendApi(real = real, settings = { store.settings.value }, clock = clock)

            override fun deviceIdentityProvider(real: DeviceIdentityProvider): DeviceIdentityProvider =
                SwitchableDeviceIdentityProvider(real = real, settings = { store.settings.value })
        }

    @Provides
    @IntoSet
    @NetworkInterceptors
    fun mockServerInterceptor(store: DeveloperSettingsStore): Interceptor =
        MockServerInterceptor(mockBaseUrl = BuildConfig.MOCK_BASE_URL, settings = { store.settings.value })
}
