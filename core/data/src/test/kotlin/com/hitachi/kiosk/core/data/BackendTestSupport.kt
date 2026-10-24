package com.hitachi.kiosk.core.data

import com.hitachi.kiosk.core.data.api.ApiConfig
import com.hitachi.kiosk.core.data.api.ApiErrorMapper
import com.hitachi.kiosk.core.data.api.BackendHttp
import com.hitachi.kiosk.core.data.api.DeviceCredentials
import com.hitachi.kiosk.core.data.api.InMemoryDeviceCredentialsStore
import com.hitachi.kiosk.core.data.api.NetworkErrorSignal
import com.hitachi.kiosk.core.data.api.NetworkTimeouts
import com.hitachi.kiosk.core.data.api.UnauthorizedHandler
import com.hitachi.kiosk.core.data.backend.DefaultBackendApi
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.domain.log.LogLevel
import com.hitachi.kiosk.core.testing.fixture.Fixtures
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

internal const val TEST_APP_VERSION = "0.1.0"
internal val TEST_CREDENTIALS =
    DeviceCredentials(deviceId = SampleData.DEVICE_ID, deviceToken = "dvt_9f2c4a7e1b8d4f0aa3c6e2b17d5f9e01")

/** A [DefaultBackendApi] wired exactly like `NetworkModule`, against [server]. */
internal class BackendHarness(
    server: MockWebServer,
    timeouts: NetworkTimeouts =
        NetworkTimeouts(connect = 5.seconds, read = 5.seconds, write = 5.seconds, call = 10.seconds),
    networkErrors: NetworkErrorSignal = NetworkErrorSignal(),
) {
    val credentials = InMemoryDeviceCredentialsStore().apply { set(TEST_CREDENTIALS) }
    val unauthorizedCalls = AtomicInteger()
    val config =
        ApiConfig(
            baseUrl = server.url("/").toString(),
            environment = "mock",
            certificatePins = emptyList(),
            appVersion = TEST_APP_VERSION,
            allowCleartext = true,
        )
    private val client = BackendHttp.client(config, credentials, timeouts = timeouts)
    val errorMapper = ApiErrorMapper(UnauthorizedHandler { unauthorizedCalls.incrementAndGet() }, networkErrors)
    val api = DefaultBackendApi(BackendHttp.retrofit(config.baseUrl, client), errorMapper)
}

internal fun fixtureResponse(
    name: String,
    code: Int = 200,
    vararg headers: Pair<String, String>,
): MockResponse = jsonResponse(Fixtures.read(name), code, *headers)

internal fun jsonResponse(
    body: String,
    code: Int = 200,
    vararg headers: Pair<String, String>,
): MockResponse =
    MockResponse
        .Builder()
        .code(code)
        .addHeader("Content-Type", "application/json; charset=utf-8")
        .apply { headers.forEach { (name, value) -> addHeader(name, value) } }
        .body(body)
        .build()

internal fun MockWebServer.takeRequestNow(): RecordedRequest = requireNotNull(takeRequest(5, TimeUnit.SECONDS))

internal fun RecordedRequest.jsonBody(): JsonElement = Json.parseToJsonElement(requireNotNull(body).utf8())

internal fun fixtureJson(name: String): JsonElement = Json.parseToJsonElement(Fixtures.read(name))

/** Captures [KioskLog] records for the duration of a test. */
internal class LogCapture {
    data class Record(
        val level: LogLevel,
        val event: String,
        val fields: Map<String, String>,
    )

    val records = mutableListOf<Record>()

    fun install() {
        KioskLog.install {
            level,
            event,
            fields,
            _,
            ->
            synchronized(records) { records += Record(level, event, fields) }
        }
    }

    fun uninstall() {
        KioskLog.install { _, _, _, _ -> }
    }
}
