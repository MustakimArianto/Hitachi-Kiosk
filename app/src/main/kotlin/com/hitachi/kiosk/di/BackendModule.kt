package com.hitachi.kiosk.di

import com.hitachi.kiosk.BuildConfig
import com.hitachi.kiosk.core.data.api.ApiConfig
import com.hitachi.kiosk.core.data.backend.BackendPaymentGateway
import com.hitachi.kiosk.core.data.backend.DefaultBackendApi
import com.hitachi.kiosk.core.domain.backend.BackendApi
import com.hitachi.kiosk.core.domain.payment.PaymentGateway
import com.hitachi.kiosk.devtools.BindingOverrides
import com.hitachi.kiosk.devtools.DeveloperMenu
import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.Optional
import javax.inject.Singleton

/**
 * Binds the backend ports to their real implementations (technical-design §4.3). These are the release bindings.
 * Debug builds contribute [BindingOverrides] and [DeveloperMenu]; release builds leave both empty.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BackendModule {
    @BindsOptionalOf
    abstract fun bindingOverrides(): BindingOverrides

    @BindsOptionalOf
    abstract fun developerMenu(): DeveloperMenu

    companion object {
        /** Backend environment from the build (`-Pkiosk.env`, technical-design §2.3). */
        @Provides
        fun apiConfig(): ApiConfig =
            ApiConfig(
                baseUrl = BuildConfig.API_BASE_URL,
                environment = BuildConfig.KIOSK_ENV,
                certificatePins =
                    BuildConfig.CERT_PINS
                        .split(',')
                        .map(String::trim)
                        .filter(String::isNotEmpty),
                appVersion = BuildConfig.VERSION_NAME,
                allowCleartext = BuildConfig.DEBUG,
            )

        @Provides
        @Singleton
        fun backendApi(
            real: DefaultBackendApi,
            overrides: Optional<BindingOverrides>,
        ): BackendApi = overrides.map { it.backendApi(real) }.orElse(real)

        /** Payments go through the backend (architecture §2), so this follows [backendApi], overrides included. */
        @Provides
        @Singleton
        fun paymentGateway(gateway: BackendPaymentGateway): PaymentGateway = gateway
    }
}
