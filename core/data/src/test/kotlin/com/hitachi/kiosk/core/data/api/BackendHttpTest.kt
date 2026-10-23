package com.hitachi.kiosk.core.data.api

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.data.api.interceptor.AuthInterceptor
import com.hitachi.kiosk.core.data.api.interceptor.HeadersInterceptor
import com.hitachi.kiosk.core.data.api.interceptor.IdempotencyKeyInterceptor
import okhttp3.ConnectionSpec
import okhttp3.Interceptor
import org.junit.Test

class BackendHttpTest {
    private val pin = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    private val backupPin = "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="
    private val prod =
        ApiConfig(
            baseUrl = "https://api.hitachikiosk.example",
            environment = "prod",
            certificatePins = listOf(pin, backupPin),
            appVersion = "1.0.0",
            allowCleartext = false,
        )
    private val credentials = DeviceCredentialsProvider { null }

    @Test
    fun `release settings use TLS only, the spec timeouts and pin the API host`() {
        val client = BackendHttp.client(prod, credentials)

        assertThat(client.connectionSpecs).containsExactly(ConnectionSpec.MODERN_TLS)
        assertThat(client.connectTimeoutMillis).isEqualTo(10_000)
        assertThat(client.readTimeoutMillis).isEqualTo(15_000)
        assertThat(client.writeTimeoutMillis).isEqualTo(15_000)
        assertThat(client.callTimeoutMillis).isEqualTo(30_000)
        assertThat(
            client.certificatePinner.pins
                .map { it.pattern }
                .toSet(),
        ).containsExactly("api.hitachikiosk.example")
        assertThat(client.certificatePinner.pins).hasSize(2)
    }

    @Test
    fun `the mock host may use cleartext and is not pinned`() {
        val mock = prod.copy(baseUrl = "http://10.0.2.2:4010", certificatePins = emptyList(), allowCleartext = true)

        assertThat(
            BackendHttp.connectionSpecs(mock.allowCleartext),
        ).containsExactly(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT)
        assertThat(BackendHttp.certificatePinner(mock)).isNull()
    }

    @Test
    fun `interceptors run idempotency, auth, headers, then the extras`() {
        val extra = Interceptor { it.proceed(it.request()) }

        val client = BackendHttp.client(prod, credentials, listOf(extra))

        assertThat(client.interceptors.map { it.javaClass })
            .containsExactly(
                IdempotencyKeyInterceptor::class.java,
                AuthInterceptor::class.java,
                HeadersInterceptor::class.java,
                extra.javaClass,
            ).inOrder()
    }

    @Test
    fun `the base URL gets a trailing slash`() {
        assertThat(
            BackendHttp.retrofit("http://10.0.2.2:4010", BackendHttp.client(prod, credentials)).baseUrl().toString(),
        ).isEqualTo("http://10.0.2.2:4010/")
        assertThat(BackendHttp.normalizeBaseUrl("https://x.example/api/")).isEqualTo("https://x.example/api/")
    }
}
