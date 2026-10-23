package com.hitachi.kiosk.core.data.api

import com.hitachi.kiosk.core.data.api.interceptor.AuthInterceptor
import com.hitachi.kiosk.core.data.api.interceptor.HeadersInterceptor
import com.hitachi.kiosk.core.data.api.interceptor.IdempotencyKeyInterceptor
import okhttp3.CertificatePinner
import okhttp3.ConnectionSpec
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/** OkHttp timeouts (NFR-010, technical-design §5.1). Tests pass shorter ones. */
internal data class NetworkTimeouts(
    val connect: Duration = 10.seconds,
    val read: Duration = 15.seconds,
    val write: Duration = 15.seconds,
    val call: Duration = 30.seconds,
)

/** Builds the backend client and Retrofit (technical-design §5.1); used by `NetworkModule` and by tests. */
internal object BackendHttp {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun client(
        config: ApiConfig,
        credentials: DeviceCredentialsProvider,
        extraInterceptors: Collection<Interceptor> = emptyList(),
        timeouts: NetworkTimeouts = NetworkTimeouts(),
    ): OkHttpClient {
        val builder =
            OkHttpClient
                .Builder()
                .connectTimeout(timeouts.connect.toJavaDuration())
                .readTimeout(timeouts.read.toJavaDuration())
                .writeTimeout(timeouts.write.toJavaDuration())
                .callTimeout(timeouts.call.toJavaDuration())
                .connectionSpecs(connectionSpecs(config.allowCleartext))
                // Architecture §7 order: idempotency → auth → headers → extras (debug logging).
                .addInterceptor(IdempotencyKeyInterceptor())
                .addInterceptor(AuthInterceptor(credentials))
                .addInterceptor(HeadersInterceptor(config.appVersion))
        extraInterceptors.forEach { builder.addInterceptor(it) }
        certificatePinner(config)?.let { builder.certificatePinner(it) }
        return builder.build()
    }

    fun retrofit(
        baseUrl: String,
        client: OkHttpClient,
    ): Retrofit =
        Retrofit
            .Builder()
            .baseUrl(normalizeBaseUrl(baseUrl))
            .client(client)
            .addConverterFactory(KioskJson.asConverterFactory(jsonMediaType))
            .build()

    /** TLS 1.2+ only (TR-SEC-02); plain HTTP only when [allowCleartext] (debug, mock host). */
    fun connectionSpecs(allowCleartext: Boolean): List<ConnectionSpec> =
        if (allowCleartext) {
            listOf(
                ConnectionSpec.MODERN_TLS,
                ConnectionSpec.CLEARTEXT,
            )
        } else {
            listOf(ConnectionSpec.MODERN_TLS)
        }

    /** Pins the base URL host when pins are configured (TR-SEC-03). */
    fun certificatePinner(config: ApiConfig): CertificatePinner? =
        config.certificatePins.takeIf { it.isNotEmpty() }?.let { pins ->
            val host = normalizeBaseUrl(config.baseUrl).toHttpUrl().host
            CertificatePinner
                .Builder()
                .apply { pins.forEach { add(host, it) } }
                .build()
        }

    fun normalizeBaseUrl(baseUrl: String): String = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
}
