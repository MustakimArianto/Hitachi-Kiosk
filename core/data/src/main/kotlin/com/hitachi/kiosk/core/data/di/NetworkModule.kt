package com.hitachi.kiosk.core.data.di

import com.hitachi.kiosk.core.data.api.ApiConfig
import com.hitachi.kiosk.core.data.api.BackendHttp
import com.hitachi.kiosk.core.data.api.DeviceCredentialsProvider
import com.hitachi.kiosk.core.data.api.InMemoryDeviceCredentialsStore
import com.hitachi.kiosk.core.data.api.KioskBackend
import com.hitachi.kiosk.core.data.api.NetworkInterceptors
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * The backend HTTP stack (architecture §7, technical-design §5.1). `:app` provides [ApiConfig] and binds
 * `BackendApi`/`PaymentGateway` to `DefaultBackendApi`/`BackendPaymentGateway`. HTTP 401 goes to the
 * `UnauthorizedHandler` bound in [RepositoryModule].
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    @KioskBackend
    fun provideOkHttpClient(
        config: ApiConfig,
        credentials: DeviceCredentialsProvider,
        @NetworkInterceptors extraInterceptors: Set<@JvmSuppressWildcards Interceptor>,
    ): OkHttpClient = BackendHttp.client(config, credentials, extraInterceptors)

    @Provides
    @Singleton
    @KioskBackend
    fun provideRetrofit(
        config: ApiConfig,
        @KioskBackend client: OkHttpClient,
    ): Retrofit = BackendHttp.retrofit(config.baseUrl, client)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindingsModule {
    /** The in-memory copy of the encrypted credentials, kept current by `DeviceSession` (DEV-014). */
    @Binds
    abstract fun bindDeviceCredentialsProvider(store: InMemoryDeviceCredentialsStore): DeviceCredentialsProvider

    /** Empty unless a build contributes interceptors (the debug app adds the mock-server redirect). */
    @Multibinds
    @NetworkInterceptors
    abstract fun networkInterceptors(): Set<Interceptor>
}
